package io.github.akrishna87.sievr.core.metrics

import io.github.akrishna87.sievr.core.metrics.Category.*
import io.github.akrishna87.sievr.core.technical.CandlePattern

/** Every metric Sievr can screen on. */
object Metrics {
    private fun m(
        id: String, label: String, category: Category, unit: MetricUnit, description: String,
        higherIsBetter: Boolean? = null, aliases: List<String> = emptyList(), historical: Boolean = false,
    ) = Metric(id, label, category, unit, description, higherIsBetter, aliases, historical)

    val all: List<Metric> = listOf(
        // Price & returns
        m("price", "Price", PRICE, MetricUnit.RUPEE, "Last closing price.", aliases = listOf("cmp", "close", "current_price"), historical = true),
        m("chg1d", "1D change", PRICE, MetricUnit.PERCENT, "Change from the previous close.", true, listOf("change", "day_change")),
        m("ret1w", "1W return", PRICE, MetricUnit.PERCENT, "Price return over 1 week.", true, listOf("return_1w")),
        m("ret1m", "1M return", PRICE, MetricUnit.PERCENT, "Price return over 1 month.", true, listOf("return_1m"), historical = true),
        m("ret3m", "3M return", PRICE, MetricUnit.PERCENT, "Price return over 3 months.", true, listOf("return_3m"), historical = true),
        m("ret6m", "6M return", PRICE, MetricUnit.PERCENT, "Price return over 6 months.", true, listOf("return_6m"), historical = true),
        m("ret1y", "1Y return", PRICE, MetricUnit.PERCENT, "Price return over 1 year.", true, listOf("return_1y"), historical = true),
        m("ret3y", "3Y CAGR (price)", PRICE, MetricUnit.PERCENT, "Annualised price return over 3 years.", true, listOf("return_3y")),
        m("ret5y", "5Y CAGR (price)", PRICE, MetricUnit.PERCENT, "Annualised price return over 5 years.", true, listOf("return_5y")),
        m("high52", "52W high", PRICE, MetricUnit.RUPEE, "Highest close in the last 52 weeks.", aliases = listOf("high_52w", "year_high"), historical = true),
        m("low52", "52W low", PRICE, MetricUnit.RUPEE, "Lowest close in the last 52 weeks.", aliases = listOf("low_52w", "year_low"), historical = true),
        m("fromHigh52", "Below 52W high", PRICE, MetricUnit.PERCENT, "How far the price is below its 52-week high, in %.", false, listOf("from_high", "down_from_high"), historical = true),
        m("fromLow52", "Above 52W low", PRICE, MetricUnit.PERCENT, "How far the price is above its 52-week low, in %.", null, listOf("from_low", "up_from_low"), historical = true),
        m("relStrength", "Relative strength vs Nifty", PRICE, MetricUnit.PERCENT, "1-year return minus the Nifty 50's 1-year return.", true, listOf("rs", "relative_strength")),

        // Size
        m("mcap", "Market cap", SIZE, MetricUnit.CRORE, "Market capitalisation in ₹ crore.", aliases = listOf("market_cap", "marketcap", "m_cap")),
        m("capCategory", "Cap category", SIZE, MetricUnit.NUMBER, "1 = large cap (top 100), 2 = mid cap (101–250), 3 = small cap (251–500 by size), 4 = micro cap.", aliases = listOf("cap_category")),
        m("ev", "Enterprise value", SIZE, MetricUnit.CRORE, "Market cap plus debt minus cash, ₹ crore.", aliases = listOf("enterprise_value")),
        m("revenue", "Revenue (TTM)", SIZE, MetricUnit.CRORE, "Sales over the last twelve months, ₹ crore.", true, listOf("sales", "turnover")),
        m("netIncome", "Net profit (TTM)", SIZE, MetricUnit.CRORE, "Net profit over the last twelve months, ₹ crore.", true, listOf("net_profit", "profit", "pat")),

        // Valuation
        m("pe", "P/E", VALUATION, MetricUnit.TIMES, "Price to trailing earnings.", false, listOf("pe_ratio", "price_to_earnings", "p_e")),
        m("fpe", "Forward P/E", VALUATION, MetricUnit.TIMES, "Price to analysts' expected earnings.", false, listOf("forward_pe")),
        m("industryPe", "Industry P/E", VALUATION, MetricUnit.TIMES, "Median P/E of profitable companies in the same industry.", aliases = listOf("industry_pe", "sector_pe")),
        m("peVsIndustry", "P/E vs industry", VALUATION, MetricUnit.TIMES, "P/E divided by the industry P/E. Below 1 is cheaper than peers.", false, listOf("relative_pe")),
        m("pb", "P/B", VALUATION, MetricUnit.TIMES, "Price to book value.", false, listOf("price_to_book", "p_b")),
        m("ps", "P/S", VALUATION, MetricUnit.TIMES, "Market cap to trailing revenue.", false, listOf("price_to_sales", "p_s")),
        m("evEbitda", "EV/EBITDA", VALUATION, MetricUnit.TIMES, "Enterprise value to EBITDA.", false, listOf("ev_ebitda")),
        m("peg", "PEG", VALUATION, MetricUnit.TIMES, "P/E divided by 3-year profit growth. Below 1 can mean growth is cheap.", false, listOf("peg_ratio")),
        m("earningsYield", "Earnings yield", VALUATION, MetricUnit.PERCENT, "EBIT over enterprise value (Greenblatt), or 1/PE when EBIT is missing.", true, listOf("earnings_yield", "ey")),
        m("eps", "EPS", VALUATION, MetricUnit.RUPEE, "Trailing earnings per share.", true, listOf("earnings_per_share")),
        m("bookValue", "Book value / share", VALUATION, MetricUnit.RUPEE, "Shareholders' equity per share.", aliases = listOf("book_value", "bvps")),
        m("grahamNumber", "Graham number", VALUATION, MetricUnit.RUPEE, "√(22.5 × EPS × book value per share): Benjamin Graham's ceiling for a defensive buy.", aliases = listOf("graham_number")),
        m("grahamUpside", "Upside to Graham number", VALUATION, MetricUnit.PERCENT, "How far the Graham number is above the price.", true, listOf("graham_upside")),
        m("dcfValue", "DCF value", VALUATION, MetricUnit.RUPEE, "Sievr's default 10-year DCF value per share (12% discount rate).", aliases = listOf("intrinsic_value", "fair_value")),
        m("dcfUpside", "Upside to DCF value", VALUATION, MetricUnit.PERCENT, "How far the default DCF value is above the price.", true, listOf("dcf_upside", "margin_of_safety")),
        m("magicRank", "Magic Formula rank", VALUATION, MetricUnit.NUMBER, "Joel Greenblatt's rank: earnings yield rank + ROCE rank. Lower is better.", false, listOf("magic_formula", "magic_rank")),

        // Profitability
        m("roe", "ROE", PROFITABILITY, MetricUnit.PERCENT, "Return on equity.", true, listOf("return_on_equity")),
        m("roce", "ROCE", PROFITABILITY, MetricUnit.PERCENT, "Return on capital employed: EBIT / (total assets − current liabilities).", true, listOf("return_on_capital")),
        m("roa", "ROA", PROFITABILITY, MetricUnit.PERCENT, "Return on assets.", true, listOf("return_on_assets")),
        m("opm", "Operating margin", PROFITABILITY, MetricUnit.PERCENT, "Operating profit as % of revenue.", true, listOf("operating_margin", "ebit_margin")),
        m("npm", "Net margin", PROFITABILITY, MetricUnit.PERCENT, "Net profit as % of revenue.", true, listOf("net_margin", "profit_margin")),
        m("gpm", "Gross margin", PROFITABILITY, MetricUnit.PERCENT, "Gross profit as % of revenue.", true, listOf("gross_margin")),
        m("opmChange", "Operating margin change", PROFITABILITY, MetricUnit.PERCENT, "Change in operating margin from the year before, percentage points.", true, listOf("margin_change", "margin_expansion")),
        m("roceMin3", "Lowest ROCE (3Y)", PROFITABILITY, MetricUnit.PERCENT, "Lowest yearly ROCE over the last 3 years, for consistency.", true, listOf("min_roce")),
        m("roe3", "Average ROE (3Y)", PROFITABILITY, MetricUnit.PERCENT, "Average yearly ROE over the last 3 years.", true, listOf("avg_roe", "roe_3y")),

        // Growth
        m("revenueGrowth", "Revenue growth (YoY)", GROWTH, MetricUnit.PERCENT, "Latest quarter's revenue against the same quarter a year ago.", true, listOf("sales_growth", "revenue_growth")),
        m("earningsGrowth", "Profit growth (YoY)", GROWTH, MetricUnit.PERCENT, "Latest quarter's profit against the same quarter a year ago.", true, listOf("profit_growth", "earnings_growth")),
        m("salesCagr3", "Sales CAGR (3Y)", GROWTH, MetricUnit.PERCENT, "Compound yearly revenue growth over 3 years.", true, listOf("sales_cagr", "sales_growth_3y", "revenue_cagr")),
        m("profitCagr3", "Profit CAGR (3Y)", GROWTH, MetricUnit.PERCENT, "Compound yearly net profit growth over 3 years.", true, listOf("profit_cagr", "profit_growth_3y")),
        m("epsCagr3", "EPS CAGR (3Y)", GROWTH, MetricUnit.PERCENT, "Compound yearly EPS growth over 3 years.", true, listOf("eps_cagr", "eps_growth_3y")),
        m("salesGrowthMin3", "Lowest yearly sales growth (3Y)", GROWTH, MetricUnit.PERCENT, "Worst single year of revenue growth in the last 3 years, for consistency.", true, listOf("min_sales_growth")),

        // Financial health
        m("de", "Debt / equity", HEALTH, MetricUnit.RATIO, "Total debt divided by shareholders' equity.", false, listOf("debt_to_equity", "d_e", "debt_equity")),
        m("currentRatio", "Current ratio", HEALTH, MetricUnit.RATIO, "Current assets / current liabilities.", true, listOf("current_ratio")),
        m("quickRatio", "Quick ratio", HEALTH, MetricUnit.RATIO, "Liquid assets / current liabilities.", true, listOf("quick_ratio")),
        m("interestCoverage", "Interest coverage", HEALTH, MetricUnit.TIMES, "EBIT / interest expense.", true, listOf("interest_coverage", "icr")),
        m("netCash", "Net cash", HEALTH, MetricUnit.CRORE, "Cash minus debt, ₹ crore. Positive means more cash than debt.", true, listOf("net_cash")),
        m("debt", "Total debt", HEALTH, MetricUnit.CRORE, "Borrowings, ₹ crore.", false, listOf("borrowings")),
        m("piotroski", "Piotroski F-score", HEALTH, MetricUnit.NUMBER, "0–9 test of profitability, leverage and efficiency. 8–9 is strong.", true, listOf("f_score", "piotroski_score")),
        m("altmanZ", "Altman Z-score", HEALTH, MetricUnit.NUMBER, "Bankruptcy-risk score. Above 3 is safe, below 1.8 is distressed.", true, listOf("altman_z", "z_score")),
        m("beta", "Beta", HEALTH, MetricUnit.NUMBER, "Volatility relative to the market.", aliases = listOf()),

        // Cash flow
        m("fcf", "Free cash flow", CASH_FLOW, MetricUnit.CRORE, "Operating cash flow minus capital spending, ₹ crore.", true, listOf("free_cash_flow")),
        m("ocf", "Operating cash flow", CASH_FLOW, MetricUnit.CRORE, "Cash from operations, ₹ crore.", true, listOf("operating_cash_flow", "cfo")),
        m("fcfYield", "FCF yield", CASH_FLOW, MetricUnit.PERCENT, "Free cash flow as % of market cap.", true, listOf("fcf_yield")),
        m("cashConversion", "Cash conversion", CASH_FLOW, MetricUnit.PERCENT, "Operating cash flow as % of net profit (3-year total).", true, listOf("cash_conversion", "cfo_to_pat")),

        // Dividends
        m("divYield", "Dividend yield", DIVIDENDS, MetricUnit.PERCENT, "Yearly dividend as % of price.", true, listOf("dividend_yield", "yield")),
        m("payout", "Payout ratio", DIVIDENDS, MetricUnit.PERCENT, "Share of profit paid out as dividends.", aliases = listOf("payout_ratio", "dividend_payout")),

        // Ownership
        m("promoter", "Promoter holding", OWNERSHIP, MetricUnit.PERCENT, "Shares held by promoters and the promoter group, from the latest NSE shareholding filing (Yahoo's insider figure when there's no filing).", aliases = listOf("promoter_holding", "insiders")),
        m("pledged", "Promoter pledge", OWNERSHIP, MetricUnit.PERCENT, "Share of promoter holding pledged.", false, listOf("pledge", "pledged_percentage")),
        m("fii", "FII holding", OWNERSHIP, MetricUnit.PERCENT, "Foreign institutional holding.", aliases = listOf("fii_holding", "fpi")),
        m("dii", "DII holding", OWNERSHIP, MetricUnit.PERCENT, "Domestic institutional holding (mutual funds, insurers…).", aliases = listOf("dii_holding")),
        m("institutions", "Institutional holding", OWNERSHIP, MetricUnit.PERCENT, "All institutions (FII + DII).", aliases = listOf("institutional_holding")),
        m("mf", "Mutual fund holding", OWNERSHIP, MetricUnit.PERCENT, "Shares held by mutual funds (part of DII).", aliases = listOf("mf_holding", "mutual_funds")),
        m("retail", "Retail holding", OWNERSHIP, MetricUnit.PERCENT, "Resident individuals holding up to ₹2 lakh each.", aliases = listOf("retail_holding")),
        m("promoterChange", "Promoter holding change (QoQ)", OWNERSHIP, MetricUnit.PERCENT, "Change in promoter holding over the last quarter, percentage points.", true, listOf("promoter_change")),
        m("fiiChange", "FII holding change (QoQ)", OWNERSHIP, MetricUnit.PERCENT, "Change in FII holding over the last quarter, percentage points.", true, listOf("fii_change")),
        m("diiChange", "DII holding change (QoQ)", OWNERSHIP, MetricUnit.PERCENT, "Change in DII holding over the last quarter, percentage points.", true, listOf("dii_change")),
        m("mfChange", "MF holding change (QoQ)", OWNERSHIP, MetricUnit.PERCENT, "Change in mutual fund holding over the last quarter, percentage points.", true, listOf("mf_change")),

        // Analysts
        m("targetPrice", "Analyst target", ANALYSTS, MetricUnit.RUPEE, "Average analyst price target.", aliases = listOf("target_price")),
        m("targetUpside", "Upside to target", ANALYSTS, MetricUnit.PERCENT, "How far the average analyst target is above the price.", true, listOf("target_upside")),
        m("analysts", "Analysts covering", ANALYSTS, MetricUnit.NUMBER, "Number of analysts with an opinion.", aliases = listOf("analyst_count")),
        m("rating", "Analyst rating", ANALYSTS, MetricUnit.NUMBER, "Average rating, 1 = strong buy … 5 = sell.", false, listOf("recommendation")),

        // Technicals
        m("sma20", "20-day average", TECHNICAL, MetricUnit.RUPEE, "Simple moving average of the last 20 closes.", aliases = listOf("sma_20", "dma20")),
        m("sma50", "50-day average", TECHNICAL, MetricUnit.RUPEE, "Simple moving average of the last 50 closes.", aliases = listOf("sma_50", "dma50", "dma_50"), historical = true),
        m("sma200", "200-day average", TECHNICAL, MetricUnit.RUPEE, "Simple moving average of the last 200 closes.", aliases = listOf("sma_200", "dma200", "dma_200"), historical = true),
        m("priceVsSma50", "Price vs 50-day avg", TECHNICAL, MetricUnit.PERCENT, "How far the price is above (+) or below (−) its 50-day average.", aliases = listOf("vs_dma50"), historical = true),
        m("priceVsSma200", "Price vs 200-day avg", TECHNICAL, MetricUnit.PERCENT, "How far the price is above (+) or below (−) its 200-day average.", aliases = listOf("vs_dma200"), historical = true),
        m("rsi14", "RSI (14)", TECHNICAL, MetricUnit.NUMBER, "Relative strength index. Below 30 is oversold, above 70 overbought.", aliases = listOf("rsi"), historical = true),
        m("macd", "MACD", TECHNICAL, MetricUnit.NUMBER, "12-day EMA minus 26-day EMA.", aliases = listOf()),
        m("macdSignal", "MACD signal", TECHNICAL, MetricUnit.NUMBER, "9-day EMA of MACD.", aliases = listOf("macd_signal")),
        m("macdHist", "MACD histogram", TECHNICAL, MetricUnit.NUMBER, "MACD minus its signal line. Turning positive is bullish.", aliases = listOf("macd_hist")),
        m("macdBullCross", "MACD bullish crossover", TECHNICAL, MetricUnit.FLAG, "MACD crossed above its signal line in the last 3 sessions.", aliases = listOf("macd_crossover")),
        m("goldenCross", "Golden cross", TECHNICAL, MetricUnit.FLAG, "50-day average crossed above the 200-day average in the last 10 sessions.", aliases = listOf("golden_cross")),
        m("deathCross", "Death cross", TECHNICAL, MetricUnit.FLAG, "50-day average crossed below the 200-day average in the last 10 sessions.", aliases = listOf("death_cross")),
        m("newHigh52", "New 52W high", TECHNICAL, MetricUnit.FLAG, "Closed at a new 52-week high today.", aliases = listOf("new_high")),
        m("newLow52", "New 52W low", TECHNICAL, MetricUnit.FLAG, "Closed at a new 52-week low today.", aliases = listOf("new_low")),
        m("volume", "Volume", TECHNICAL, MetricUnit.VOLUME, "Shares traded in the last session.", aliases = listOf()),
        m("avgVolume20", "Avg volume (20D)", TECHNICAL, MetricUnit.VOLUME, "Average daily shares traded over 20 sessions.", aliases = listOf("avg_volume")),
        m("volumeRatio", "Volume vs average", TECHNICAL, MetricUnit.TIMES, "Last session's volume divided by the 20-day average.", aliases = listOf("volume_ratio", "relative_volume", "rvol")),
        m("turnover", "Turnover", TECHNICAL, MetricUnit.CRORE, "Value traded in the last session, ₹ crore.", aliases = listOf("traded_value")),
        m("volatility", "Volatility (1Y)", TECHNICAL, MetricUnit.PERCENT, "Annualised standard deviation of daily returns.", false, listOf("volatility_1y")),
        m("atrPct", "ATR %", TECHNICAL, MetricUnit.PERCENT, "Average daily range over recent sessions as % of price.", aliases = listOf("atr")),

        // Scores
        m("qualityScore", "Quality score", SCORES, MetricUnit.SCORE, "0–100: ROE, ROCE, margins, F-score and low debt against the whole market.", true, listOf("quality")),
        m("valueScore", "Value score", SCORES, MetricUnit.SCORE, "0–100: P/E, P/B, EV/EBITDA, earnings, cash-flow and dividend yields against the market.", true, listOf("value")),
        m("growthScore", "Growth score", SCORES, MetricUnit.SCORE, "0–100: sales and profit growth, recent and over 3 years.", true, listOf("growth")),
        m("momentumScore", "Momentum score", SCORES, MetricUnit.SCORE, "0–100: 3, 6 and 12-month returns and trend strength.", true, listOf("momentum")),
        m("safetyScore", "Safety score", SCORES, MetricUnit.SCORE, "0–100: low debt, interest cover, Altman Z, low volatility and pledging.", true, listOf("safety")),
        m("sievrScore", "Sievr score", SCORES, MetricUnit.SCORE, "0–100: the average of quality, value, growth, momentum and safety.", true, listOf("sievr", "overall_score", "score")),
    ) + CandlePattern.entries.map {
        m(it.metricId, it.label, PATTERNS, MetricUnit.FLAG, it.description, aliases = listOf(it.name.lowercase()))
    }

    private val byId: Map<String, Metric> = all.associateBy { it.id }
    private val byName: Map<String, Metric> = buildMap {
        for (metric in all) {
            put(normalize(metric.id), metric)
            put(normalize(metric.label), metric)
            metric.aliases.forEach { put(normalize(it), metric) }
        }
    }

    operator fun get(id: String): Metric? = byId[id]

    /** Finds a metric by id, label or alias, ignoring case, spaces, underscores and punctuation. */
    fun find(name: String): Metric? = byId[name] ?: byName[normalize(name)]

    fun inCategory(category: Category): List<Metric> = all.filter { it.category == category }

    internal fun normalize(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }
}
