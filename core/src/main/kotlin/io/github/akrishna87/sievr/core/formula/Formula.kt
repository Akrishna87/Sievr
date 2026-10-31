package io.github.akrishna87.sievr.core.formula

import io.github.akrishna87.sievr.core.metrics.Metrics
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Sievr's screening formula language, e.g.
 *
 *     ROE > 20 AND PE < Industry_PE AND Sales_CAGR > 15
 *     (Price / Graham_Number < 0.8) OR (FCF_Yield > 6 AND Debt_to_Equity < 0.5)
 *     Price > SMA_200 AND RSI BETWEEN 40 AND 60
 *
 * Names are metric ids, labels or aliases, case-insensitive (see [Metrics.find]).
 * Supports + − × ÷, comparisons (> >= < <= = !=), BETWEEN … AND …, AND/OR/NOT (also && || !),
 * parentheses and the functions abs, min, max, sqrt, log. A comparison involving a metric a stock
 * doesn't have is false.
 */
class Formula private constructor(val source: String, private val root: Node) {

    /** Metric ids the formula reads. */
    val metricIds: Set<String> = buildSet { root.collect(this) }

    /** Evaluates to a number; booleans are 1.0/0.0; null when a needed metric is missing. */
    fun evaluate(values: (String) -> Double?): Double? = root.eval(values)

    fun matches(values: (String) -> Double?): Boolean = (evaluate(values) ?: 0.0) != 0.0

    override fun toString() = source

    class ParseException(message: String, val position: Int) : Exception(message)

    companion object {
        fun parse(source: String): Formula {
            val tokens = Lexer(source).tokens()
            val parser = Parser(tokens)
            val node = parser.parseExpression()
            parser.expectEnd()
            return Formula(source.trim(), node)
        }

        /** Parses, or returns the error message (with position) instead of throwing. */
        fun check(source: String): Result<Formula> = runCatching { parse(source) }
    }

    // ------------------------------------------------------------------ syntax tree

    private sealed interface Node {
        fun eval(v: (String) -> Double?): Double?
        fun collect(into: MutableSet<String>) {}
    }

    private class Num(val value: Double) : Node {
        override fun eval(v: (String) -> Double?) = value
    }

    private class Ref(val id: String) : Node {
        override fun eval(v: (String) -> Double?) = v(id)
        override fun collect(into: MutableSet<String>) { into += id }
    }

    private class Unary(val op: String, val operand: Node) : Node {
        override fun eval(v: (String) -> Double?): Double? = when (op) {
            "-" -> operand.eval(v)?.let { -it }
            "NOT" -> (operand.eval(v) ?: 0.0).let { if (it == 0.0) 1.0 else 0.0 }
            else -> null
        }
        override fun collect(into: MutableSet<String>) = operand.collect(into)
    }

    private class Binary(val op: String, val left: Node, val right: Node) : Node {
        override fun eval(v: (String) -> Double?): Double? {
            if (op == "AND") {
                val a = left.eval(v) ?: 0.0
                if (a == 0.0) return 0.0
                return if ((right.eval(v) ?: 0.0) != 0.0) 1.0 else 0.0
            }
            if (op == "OR") {
                val a = left.eval(v) ?: 0.0
                if (a != 0.0) return 1.0
                return if ((right.eval(v) ?: 0.0) != 0.0) 1.0 else 0.0
            }
            val a = left.eval(v)
            val b = right.eval(v)
            if (a == null || b == null) return if (op in comparisons) 0.0 else null
            return when (op) {
                "+" -> a + b
                "-" -> a - b
                "*" -> a * b
                "/" -> if (b == 0.0) null else a / b
                ">" -> bool(a > b)
                ">=" -> bool(a >= b)
                "<" -> bool(a < b)
                "<=" -> bool(a <= b)
                "=" -> bool(abs(a - b) < 1e-9)
                "!=" -> bool(abs(a - b) >= 1e-9)
                else -> null
            }
        }
        override fun collect(into: MutableSet<String>) { left.collect(into); right.collect(into) }
    }

    private class Between(val value: Node, val low: Node, val high: Node) : Node {
        override fun eval(v: (String) -> Double?): Double? {
            val x = value.eval(v) ?: return 0.0
            val lo = low.eval(v) ?: return 0.0
            val hi = high.eval(v) ?: return 0.0
            return bool(x >= minOf(lo, hi) && x <= maxOf(lo, hi))
        }
        override fun collect(into: MutableSet<String>) { value.collect(into); low.collect(into); high.collect(into) }
    }

    private class Call(val name: String, val args: List<Node>) : Node {
        override fun eval(v: (String) -> Double?): Double? {
            val values = args.map { it.eval(v) ?: return null }
            return when (name) {
                "abs" -> abs(values[0])
                "sqrt" -> if (values[0] >= 0) sqrt(values[0]) else null
                "log" -> if (values[0] > 0) ln(values[0]) else null
                "min" -> values.min()
                "max" -> values.max()
                else -> null
            }
        }
        override fun collect(into: MutableSet<String>) = args.forEach { it.collect(into) }
    }

    // ------------------------------------------------------------------ lexer

    private enum class T { NUM, NAME, OP, LPAREN, RPAREN, COMMA, END }
    private data class Token(val type: T, val text: String, val pos: Int)

    private class Lexer(val s: String) {
        fun tokens(): List<Token> {
            val out = mutableListOf<Token>()
            var i = 0
            while (i < s.length) {
                val ch = s[i]
                when {
                    ch.isWhitespace() -> i++
                    ch.isDigit() || (ch == '.' && i + 1 < s.length && s[i + 1].isDigit()) -> {
                        val start = i
                        while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == '_')) i++
                        val text = s.substring(start, i).replace("_", "")
                        // "ROE > 15%" means the same as "ROE > 15": percentages are stored as percent.
                        if (i < s.length && s[i] == '%') i++
                        if (text.count { it == '.' } > 1) throw ParseException("Bad number '$text'", start)
                        out += Token(T.NUM, text, start)
                    }
                    ch.isLetter() || ch == '_' -> {
                        val start = i
                        while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_' || s[i] == '.')) i++
                        out += Token(T.NAME, s.substring(start, i).trimEnd('.'), start)
                    }
                    ch == '(' -> { out += Token(T.LPAREN, "(", i); i++ }
                    ch == ')' -> { out += Token(T.RPAREN, ")", i); i++ }
                    ch == ',' -> { out += Token(T.COMMA, ",", i); i++ }
                    else -> {
                        val two = if (i + 1 < s.length) s.substring(i, i + 2) else ""
                        val op = when {
                            two in setOf(">=", "<=", "!=", "<>", "==", "&&", "||") -> two
                            ch in "+-*/><=!×÷" -> ch.toString()
                            else -> throw ParseException("Unexpected '$ch'", i)
                        }
                        val normal = when (op) {
                            "<>" -> "!="; "==" -> "="; "&&" -> "AND"; "||" -> "OR"; "!" -> "NOT"
                            "×" -> "*"; "÷" -> "/"; else -> op
                        }
                        out += Token(T.OP, normal, i)
                        i += op.length
                    }
                }
            }
            out += Token(T.END, "", s.length)
            return out
        }
    }

    // ------------------------------------------------------------------ parser

    private class Parser(val tokens: List<Token>) {
        var pos = 0
        val peek get() = tokens[pos]

        fun isKeyword(word: String) = peek.type == T.NAME && peek.text.equals(word, ignoreCase = true)
        fun isOp(op: String) = (peek.type == T.OP && peek.text == op) || isKeyword(op)

        fun parseExpression(): Node = parseOr()

        fun parseOr(): Node {
            var left = parseAnd()
            while (isOp("OR")) { pos++; left = Binary("OR", left, parseAnd()) }
            return left
        }

        fun parseAnd(): Node {
            var left = parseNot()
            while (isOp("AND")) { pos++; left = Binary("AND", left, parseNot()) }
            return left
        }

        fun parseNot(): Node {
            if (isOp("NOT")) { pos++; return Unary("NOT", parseNot()) }
            return parseComparison()
        }

        fun parseComparison(): Node {
            val left = parseAdditive()
            if (isKeyword("BETWEEN")) {
                pos++
                val low = parseAdditive()
                if (!isOp("AND")) throw ParseException("BETWEEN needs '… AND …'", peek.pos)
                pos++
                val high = parseAdditive()
                return Between(left, low, high)
            }
            if (peek.type == T.OP && peek.text in comparisons) {
                val op = tokens[pos++].text
                return Binary(op, left, parseAdditive())
            }
            return left
        }

        fun parseAdditive(): Node {
            var left = parseMultiplicative()
            while (peek.type == T.OP && (peek.text == "+" || peek.text == "-")) {
                val op = tokens[pos++].text
                left = Binary(op, left, parseMultiplicative())
            }
            return left
        }

        fun parseMultiplicative(): Node {
            var left = parseUnary()
            while (peek.type == T.OP && (peek.text == "*" || peek.text == "/")) {
                val op = tokens[pos++].text
                left = Binary(op, left, parseUnary())
            }
            return left
        }

        fun parseUnary(): Node {
            if (peek.type == T.OP && peek.text == "-") { pos++; return Unary("-", parseUnary()) }
            if (peek.type == T.OP && peek.text == "+") { pos++; return parseUnary() }
            return parsePrimary()
        }

        fun parsePrimary(): Node {
            val t = peek
            when (t.type) {
                T.NUM -> { pos++; return Num(t.text.toDoubleOrNull() ?: throw ParseException("Bad number '${t.text}'", t.pos)) }
                T.LPAREN -> {
                    pos++
                    val inner = parseExpression()
                    if (peek.type != T.RPAREN) throw ParseException("Missing ')'", peek.pos)
                    pos++
                    return inner
                }
                T.NAME -> {
                    pos++
                    val lower = t.text.lowercase()
                    if (peek.type == T.LPAREN && lower in functions) {
                        pos++
                        val args = mutableListOf<Node>()
                        if (peek.type != T.RPAREN) {
                            args += parseExpression()
                            while (peek.type == T.COMMA) { pos++; args += parseExpression() }
                        }
                        if (peek.type != T.RPAREN) throw ParseException("Missing ')' after $lower(…", peek.pos)
                        pos++
                        val arity = functions.getValue(lower)
                        if (args.size < arity.first || args.size > arity.last) {
                            throw ParseException("$lower takes ${if (arity.first == arity.last) "${arity.first}" else "${arity.first} or more"} value(s)", t.pos)
                        }
                        return Call(lower, args)
                    }
                    if (lower == "true" || lower == "yes") return Num(1.0)
                    if (lower == "false" || lower == "no") return Num(0.0)
                    val metric = Metrics.find(t.text) ?: throw ParseException("Unknown metric '${t.text}'", t.pos)
                    return Ref(metric.id)
                }
                T.END -> throw ParseException("Formula ends too early", t.pos)
                else -> throw ParseException("Unexpected '${t.text}'", t.pos)
            }
        }

        fun expectEnd() {
            if (peek.type != T.END) throw ParseException("Unexpected '${peek.text}'", peek.pos)
        }
    }
}

private val comparisons = setOf(">", ">=", "<", "<=", "=", "!=")
private val functions = mapOf("abs" to 1..1, "sqrt" to 1..1, "log" to 1..1, "min" to 2..20, "max" to 2..20)
private fun bool(b: Boolean) = if (b) 1.0 else 0.0
