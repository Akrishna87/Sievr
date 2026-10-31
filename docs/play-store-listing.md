# Google Play listing for Sievr

Copy-ready text and answers for the Play Console. Character limits are Play's; counts are shown in brackets.

## Store listing

**App name** (max 30)

> Sievr: Stock Screener India  _(27)_

**Short description** (max 80)

> Free NSE stock screener: 120+ metrics, fair value, backtests and alerts.  _(71)_

**Full description** (max 4,000)

> Sievr is a free stock screener for Indian investors. Every feature is included: no subscription, no paywall, no account.
>
> FIND STOCKS
> • Screen every NSE-listed company on 120+ metrics: valuation, profitability, growth, debt, cash flow, dividends, shareholding, analyst targets and technicals
> • 39 ready-made screens, including Magic Formula, Coffee Can, Graham's defensive investor, Piotroski 8+, Peter Lynch GARP, golden cross, 52-week breakouts on volume and promoter buying
> • Describe what you want in plain English, such as "profitable small caps with low debt and ROE above 20", and Sievr builds the screen on your phone
> • Write your own formulas: PE < Industry_PE AND ROCE > 20 AND Price > SMA200
> • Filter by sector, industry, Nifty index or candlestick pattern
> • Sort by any column and export results to CSV
>
> RESEARCH A COMPANY
> • Interactive price charts from 1 month to 5 years, with 50 and 200-day averages
> • Quality, Value, Growth, Momentum and Safety scores from 0 to 100
> • Fair value from your own DCF, a reverse DCF that shows the growth the price assumes, the Graham number and Peter Lynch's method
> • Up to five years of financial statements, Piotroski F-score, Altman Z-score, ROCE and cash conversion
> • Promoter, FII and DII holding, quarterly changes and promoter pledges
> • RSI, MACD, moving-average crossovers and 17 candlestick patterns
> • Peer comparison, and side-by-side comparison of up to four stocks
>
> TEST AND TRACK
> • Backtest any screen over 1 to 5 years against the Nifty 50, with CAGR, drawdown, volatility and Sharpe ratio
> • Get alerts when stocks enter or leave your saved screens
> • Price, daily move, RSI and 52-week high/low alerts
> • Multiple watchlists with the columns you choose
> • Portfolio check-up: import your broker's holdings CSV to see gains, sector mix, weighted P/E and ROE, expected dividends and warning signs
>
> PRIVATE BY DESIGN
> Your screens, watchlists and portfolio stay on your phone. There are no accounts, ads, analytics or trackers. Sievr downloads one market data file each weekday evening and does all its calculations on your device, so it is fast and works offline.
>
> Data: end-of-day prices adjusted for splits and dividends, company financials and quarterly shareholding filings for NSE-listed companies.
>
> Sievr is a research tool, not investment advice. It is not registered with SEBI as an investment adviser or research analyst. Check important figures against company filings, and consider consulting a SEBI-registered adviser before investing. Investments in securities are subject to market risks.

**Category:** Finance
**Tags:** Stock screener, Investing, Stock market
**Contact website:** https://github.com/Akrishna87/Sievr
**Contact email:** _(required by Play: add the address you want users to see)_
**Privacy policy URL:** https://github.com/Akrishna87/Sievr/blob/main/docs/privacy-policy.md

## Graphics

| Asset | Spec | Suggestion |
|---|---|---|
| App icon | 512 × 512 PNG | Export the adaptive icon (white sieve on teal `#0F766E`) |
| Feature graphic | 1024 × 500 | Teal background, "Sievr" wordmark, "Free stock screener for India", a phone showing the Market tab |
| Phone screenshots | 2–8, 1080 × 2400 works | Use the CI emulator screenshots attached to each `app-latest` release, in this order: Market, Magic Formula results, stock page, Screens (plain-English box), Portfolio, Backtest |

## App content answers

**Privacy policy:** URL above.

**Ads:** No, the app contains no ads.

**App access:** All functionality is available without special access (no login).

**Data safety**
- Does your app collect or share any of the required user data types? **No.**
- Is all user data encrypted in transit? The only network request (the market data download) uses HTTPS. Since no user data is collected, Play may not ask this.
- Account creation: **No accounts.**
- Data deletion: users delete everything by uninstalling or clearing app storage.

**Target audience:** 18 and over. The app is not designed for children.

**Content rating questionnaire:** Category "Reference, News, or Educational" (or "All other app types"). Answer **No** to violence, sexual content, language, controlled substances, gambling, user interaction/sharing and location sharing. Expected rating: Everyone / 3+.

**Financial features declaration:** Play asks every app about financial features. Sievr provides **stock market information and analysis tools only**. It does not trade, hold money, offer loans, process payments or give personal investment advice. Choose the option for apps that provide financial information or tools, not services. Include the disclaimer above in the description.

**News app:** No.

**Government app:** No.

**Health:** No.

## Before you submit

1. **Licensed data.** The current pipeline uses Yahoo Finance for prices and fundamentals, which is fine for personal use and testing. A public Play Store app should use a licensed market-data source (see the README). NSE listings, index lists and shareholding filings are public disclosures, but NSE's website terms also apply to automated access. Check them, or get the same data through a vendor.
2. **Signing.** Create an upload key, add the four `SIGNING_*` secrets to the repository (see the README), and enrol in Play App Signing.
3. **Target API.** The app targets API 35, which meets Play's current requirement for new apps.
4. **Testing track.** Upload `Sievr.aab` from the `app-latest` release to Internal testing first, then Closed testing. New personal developer accounts must run a closed test with at least 12 testers for 14 days before applying for production.
