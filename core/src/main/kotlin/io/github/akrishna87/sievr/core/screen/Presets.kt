package io.github.akrishna87.sievr.core.screen

import io.github.akrishna87.sievr.core.screen.Op.*
import io.github.akrishna87.sievr.core.technical.CandlePattern

/** Ready-made screens, grouped by investing style. */
object Presets {
    private fun f(metric: String, op: Op, value: Double, value2: Double? = null) = MetricFilter(metric, op, value, value2)
    private fun flag(metric: String) = MetricFilter(metric, EQ, 1.0)
    private fun formula(text: String) = FormulaFilter(text)

    const val VALUE = "Value"
    const val QUALITY = "Quality"
    const val GROWTH = "Growth"
    const val DIVIDEND = "Income"
    const val MOMENTUM = "Momentum"
    const val TECHNICAL = "Technical"
    const val OWNERSHIP = "Ownership"
    const val GURU = "Famous strategies"

    val categories = listOf(GURU, VALUE, QUALITY, GROWTH, DIVIDEND, MOMENTUM, TECHNICAL, OWNERSHIP)

    val all: List<Screen> = listOf(
        // Famous strategies
        Screen("magic-formula", "Magic Formula", "Joel Greenblatt's good companies at bargain prices: the best combined rank of earnings yield and return on capital. Banks and utilities are left out.",
            listOf(f("mcap", GT, 1000.0), f("magicRank", GT, 0.0)), "magicRank", false, listOf("earningsYield", "roce"), limit = 30, category = GURU),
        Screen("graham-defensive", "Graham's defensive investor", "Benjamin Graham's tests for a conservative buyer: sizeable, liquid, profitable, paying dividends, and P/E × P/B under 22.5.",
            listOf(f("mcap", GT, 2000.0), f("currentRatio", GT, 1.5), f("pe", BETWEEN, 0.1, 15.0), formula("PE * PB < 22.5"), f("divYield", GT, 0.0), f("profitCagr3", GT, 0.0)),
            "grahamUpside", true, listOf("grahamNumber", "pb"), category = GURU),
        Screen("coffee-can", "Coffee Can", "Saurabh Mukherjea's buy-and-forget test: revenue up at least 10% every year and ROCE at least 15% every year.",
            listOf(f("mcap", GT, 500.0), f("salesGrowthMin3", GT, 10.0), f("roceMin3", GT, 15.0)), "roce", true, listOf("salesCagr3"), category = GURU),
        Screen("piotroski", "Piotroski 8+", "Companies passing 8 or 9 of Joseph Piotroski's tests of improving financial strength, at a low price to book.",
            listOf(f("piotroski", GTE, 8.0), f("pb", LT, 3.0)), "piotroski", true, listOf("pb", "roa"), category = GURU),
        Screen("lynch-garp", "Peter Lynch GARP", "Growth at a reasonable price: PEG under 1, steady profit growth and modest debt.",
            listOf(f("peg", BETWEEN, 0.01, 1.0), f("profitCagr3", BETWEEN, 15.0, 50.0), f("de", LT, 0.8)), "peg", false, listOf("pe", "profitCagr3"), category = GURU),
        Screen("buffett-moat", "Buffett-style moats", "High and consistent returns on capital, little debt, real cash generation, trading below a sensible DCF value.",
            listOf(f("roe3", GT, 18.0), f("roceMin3", GT, 15.0), f("de", LT, 0.5), f("cashConversion", GT, 80.0), f("dcfUpside", GT, 0.0)),
            "qualityScore", true, listOf("dcfValue"), category = GURU),

        // Value
        Screen("undervalued-quality", "Undervalued quality", "Profitable, low-debt companies cheaper than their industry.",
            listOf(formula("PE > 0 AND PE < Industry_PE"), f("roe", GT, 15.0), f("de", LT, 0.5)), "valueScore", true, listOf("industryPe"), category = VALUE),
        Screen("below-graham", "Below Graham number", "Trading under the Graham number with positive earnings.",
            listOf(f("grahamUpside", GT, 0.0), f("mcap", GT, 500.0)), "grahamUpside", true, listOf("grahamNumber"), category = VALUE),
        Screen("dcf-margin", "Margin of safety (DCF)", "At least 30% below Sievr's default DCF value, with positive free cash flow.",
            listOf(f("dcfUpside", GT, 30.0), f("fcf", GT, 0.0), f("mcap", GT, 500.0)), "dcfUpside", true, listOf("dcfValue", "fcfYield"), category = VALUE),
        Screen("net-cash", "Net-cash bargains", "More cash than debt and a low P/E.",
            listOf(f("netCash", GT, 0.0), f("pe", BETWEEN, 0.1, 15.0)), "pe", false, listOf("netCash"), category = VALUE),
        Screen("high-fcf-yield", "Cash machines", "Free cash flow yield above 6% with low debt.",
            listOf(f("fcfYield", GT, 6.0), f("de", LT, 1.0)), "fcfYield", true, category = VALUE),
        Screen("low-pb-high-roe", "Low P/B, high ROE", "Priced near book value but earning well on it.",
            listOf(f("pb", BETWEEN, 0.1, 1.5), f("roe", GT, 15.0)), "roe", true, category = VALUE),

        // Quality
        Screen("debt-free", "Debt-free champions", "Almost no debt and a healthy return on equity.",
            listOf(f("de", LT, 0.05), f("roe", GT, 15.0), f("mcap", GT, 500.0)), "roe", true, category = QUALITY),
        Screen("high-roce", "Capital-efficient leaders", "ROCE above 25% with expanding or steady margins.",
            listOf(f("roce", GT, 25.0), f("opmChange", GTE, 0.0), f("mcap", GT, 1000.0)), "roce", true, category = QUALITY),
        Screen("quality-score", "Top quality score", "The highest Sievr quality scores in the market.",
            listOf(f("qualityScore", GT, 80.0), f("mcap", GT, 1000.0)), "qualityScore", true, category = QUALITY),
        Screen("safe-bets", "Fortress balance sheets", "Altman Z above 3, interest cover above 10 and a current ratio above 1.5.",
            listOf(f("altmanZ", GT, 3.0), f("interestCoverage", GT, 10.0), f("currentRatio", GT, 1.5)), "safetyScore", true, category = QUALITY),

        // Growth
        Screen("fast-growers", "Fast growers", "Sales and profit both compounding above 20% a year.",
            listOf(f("salesCagr3", GT, 20.0), f("profitCagr3", GT, 20.0), f("roe", GT, 12.0)), "profitCagr3", true, category = GROWTH),
        Screen("earnings-surge", "Earnings surge", "Latest quarter's profit up 25%+ and revenue up 15%+ on last year.",
            listOf(f("earningsGrowth", GT, 25.0), f("revenueGrowth", GT, 15.0)), "earningsGrowth", true, category = GROWTH),
        Screen("small-cap-gems", "Small-cap gems", "Small companies (₹500–5,000 crore) with high returns, low debt and growth.",
            listOf(f("mcap", BETWEEN, 500.0, 5000.0), f("roe", GT, 18.0), f("de", LT, 0.5), f("salesCagr3", GT, 15.0)), "growthScore", true, category = GROWTH),
        Screen("margin-expansion", "Margin expansion", "Operating margins up at least 2 points on last year, with sales growing.",
            listOf(f("opmChange", GT, 2.0), f("revenueGrowth", GT, 10.0)), "opmChange", true, category = GROWTH),

        // Income
        Screen("dividend-steady", "Steady dividend payers", "A 2%+ yield that the business can afford: sensible payout, good ROE, modest debt.",
            listOf(f("divYield", GT, 2.0), f("payout", BETWEEN, 10.0, 70.0), f("roe", GT, 12.0), f("de", LT, 1.0)), "divYield", true, category = DIVIDEND),
        Screen("high-yield", "High dividend yield", "Yield above 4%.",
            listOf(f("divYield", GT, 4.0), f("mcap", GT, 500.0)), "divYield", true, listOf("payout"), category = DIVIDEND),

        // Momentum
        Screen("strong-uptrend", "Strong uptrend", "Price above the 50-day average, which is above the 200-day, and up 20%+ in 6 months.",
            listOf(formula("Price > SMA50 AND SMA50 > SMA200"), f("ret6m", GT, 20.0)), "ret6m", true, listOf("sma50", "sma200"), category = MOMENTUM),
        Screen("near-high", "Near 52-week highs", "Within 3% of the 52-week high.",
            listOf(f("fromHigh52", LT, 3.0), f("mcap", GT, 500.0)), "ret1y", true, listOf("high52"), category = MOMENTUM),
        Screen("rs-leaders", "Relative strength leaders", "Beating the Nifty 50 by 30+ points over a year with a high momentum score.",
            listOf(f("relStrength", GT, 30.0), f("momentumScore", GT, 70.0)), "relStrength", true, category = MOMENTUM),
        Screen("quality-momentum", "Quality + momentum", "High quality scores and high momentum scores together.",
            listOf(f("qualityScore", GT, 70.0), f("momentumScore", GT, 70.0)), "sievrScore", true, category = MOMENTUM),

        // Technical
        Screen("golden-cross", "Golden cross", "The 50-day average just crossed above the 200-day average.",
            listOf(flag("goldenCross")), "volumeRatio", true, listOf("sma50", "sma200"), category = TECHNICAL),
        Screen("breakout-volume", "52-week high on volume", "A new 52-week high on at least 1.5× the usual volume.",
            listOf(flag("newHigh52"), f("volumeRatio", GT, 1.5)), "volumeRatio", true, category = TECHNICAL),
        Screen("volume-shockers", "Volume shockers", "Traded at least 3× the 20-day average volume today.",
            listOf(f("volumeRatio", GT, 3.0), f("turnover", GT, 1.0)), "volumeRatio", true, listOf("chg1d"), category = TECHNICAL),
        Screen("oversold-quality", "Oversold quality", "RSI under 30 on companies with a quality score above 60.",
            listOf(f("rsi14", LT, 30.0), f("qualityScore", GT, 60.0)), "rsi14", false, category = TECHNICAL),
        Screen("macd-cross", "MACD bullish crossover", "MACD just crossed above its signal line, above the 200-day average.",
            listOf(flag("macdBullCross"), f("priceVsSma200", GT, 0.0)), "momentumScore", true, category = TECHNICAL),
        Screen("bullish-reversal", "Bullish candlestick reversals", "Hammer, bullish engulfing, piercing line or morning star today.",
            listOf(PatternFilter(setOf(CandlePattern.HAMMER, CandlePattern.BULLISH_ENGULFING, CandlePattern.PIERCING_LINE, CandlePattern.MORNING_STAR).map { it.metricId }.toSet())),
            "volumeRatio", true, category = TECHNICAL),
        Screen("bearish-reversal", "Bearish candlestick reversals", "Shooting star, bearish engulfing, dark cloud cover or evening star today.",
            listOf(PatternFilter(setOf(CandlePattern.SHOOTING_STAR, CandlePattern.BEARISH_ENGULFING, CandlePattern.DARK_CLOUD_COVER, CandlePattern.EVENING_STAR).map { it.metricId }.toSet())),
            "volumeRatio", true, category = TECHNICAL),

        // Ownership
        Screen("promoter-confidence", "High promoter holding", "Promoters own over 60% with little or no pledging.",
            listOf(f("promoter", GT, 60.0), formula("NOT (Pledged > 5)"), f("de", LT, 1.0)), "promoter", true, listOf("pledged"), category = OWNERSHIP),
        Screen("promoter-buying", "Promoters buying", "Promoter holding up over the last quarter.",
            listOf(f("promoterChange", GT, 0.5)), "promoterChange", true, listOf("promoter"), category = OWNERSHIP),
        Screen("fii-buying", "FIIs buying", "Foreign institutions raised their stake last quarter.",
            listOf(f("fiiChange", GT, 1.0)), "fiiChange", true, listOf("fii"), category = OWNERSHIP),
        Screen("mf-buying", "Mutual funds buying", "Mutual funds raised their stake last quarter in profitable companies.",
            listOf(f("mfChange", GT, 0.5), f("roe", GT, 12.0)), "mfChange", true, listOf("mf", "dii"), category = OWNERSHIP),
        Screen("smart-money", "Institutions buying, promoters holding", "FIIs or mutual funds added last quarter while promoters kept or raised their stake.",
            listOf(formula("(FII_Change > 0.5 OR MF_Change > 0.5) AND Promoter_Change >= 0"), f("promoter", GT, 40.0)), "fiiChange", true, listOf("mfChange", "promoter"), category = OWNERSHIP),
        Screen("pledge-risk", "Pledge red flags", "Promoters have pledged over 25% of their shares. Useful as a list to avoid.",
            listOf(f("pledged", GT, 25.0)), "pledged", true, listOf("promoter"), category = OWNERSHIP),
        Screen("institution-favourites", "Institutional favourites", "Over 30% held by institutions, with a target upside.",
            listOf(f("institutions", GT, 30.0), f("targetUpside", GT, 10.0)), "targetUpside", true, listOf("institutions", "analysts"), category = OWNERSHIP),
    )

    operator fun get(id: String): Screen? = all.firstOrNull { it.id == id }
}
