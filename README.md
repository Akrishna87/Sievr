# Sievr

**A free stock screener for Indian markets (NSE), for Android.** No account, no paywall: the features other
screeners charge for are calculated on the phone from one end-of-day data file that's rebuilt every weekday evening.

**Download:** `Sievr.apk` from the [`app-latest` release](https://github.com/Akrishna87/Sievr/releases/tag/app-latest).

## Features

**Screening**
- 120+ metrics: valuation, profitability, growth, financial health, cash flow, dividends, ownership, analyst targets,
  technicals and 17 candlestick patterns
- 37 ready-made screens: Magic Formula, Graham's defensive investor, Coffee Can, Piotroski 8+, Lynch GARP,
  Buffett-style moats, golden cross, 52-week breakouts on volume, oversold quality, promoter buying, pledge red flags…
- **Plain-English screens**, worked out offline: *"profitable small caps with low debt, ROE above 20 and rising margins in pharma"*
- **Custom formulas**: `PE < Industry_PE AND ROCE > 20 AND Price > SMA200`
- Sector, industry, index (Nifty 50/100/500, Midcap 150, Smallcap 250…) and pattern filters
- Sortable results with your own columns; CSV export
- **Screen alerts**: a notification when stocks enter or leave a saved screen
- **Backtesting**: replay any screen over 1–5 years with weekly, monthly or quarterly rebalancing against the Nifty 50,
  with CAGR, drawdown, volatility, Sharpe and win rate (and honest warnings about look-ahead and survivorship bias)

**Research**
- Stock pages with interactive price charts (1M to 5Y, with 50/200-day averages), five factor scores
  (quality, value, growth, momentum, safety), an at-a-glance checklist, 4–5 years of financial statements,
  technical signals, peer comparison and shareholding
- Fair value: interactive DCF, reverse DCF ("what growth is priced in"), Graham number, Graham growth formula, Lynch value, analyst targets
- Piotroski F-score, Altman Z-score, Magic Formula rank, PEG, earnings yield, cash conversion, ROCE consistency
- Compare up to four stocks side by side

**Tracking**
- Multiple watchlists with custom columns
- Portfolio X-ray: import a broker CSV (Zerodha, Groww, Upstox…), see gains, sector and cap mix, weighted P/E and ROE,
  expected dividends, concentration and red flags (pledges, distress, over-concentration)
- Price, % move, RSI and 52-week alerts; big-move alerts for watchlists; a daily digest

## How it works

```
pipeline/          Python, runs on GitHub Actions every weekday 19:15 IST
  build_snapshot.py   NSE listings + index lists, Yahoo Finance prices and fundamentals
                      → snapshot.json.gz on the data-latest release (~5–10 MB)
core/              Plain Kotlin, no Android: the engine
  metrics/            metric catalog, enrichment (ratios, technicals, scores)
  formula/            the formula language
  screen/             filters, screens, presets
  nl/                 plain-English → filters
  valuation/          DCF, reverse DCF, Graham, Lynch
  backtest/           weekly-rebalanced backtester
  portfolio/, alerts/ portfolio analytics, alert rules
app/               Jetpack Compose Android app
```

The app downloads the snapshot once a day and does everything else on the phone, so it costs almost nothing to run.

## Building

- Engine tests (any machine with a JDK): `./gradlew :core:test`
- App (needs the Android SDK or Android Studio): `./gradlew :app:assembleDebug`
- Pipeline tests: `cd pipeline && pip install -r requirements.txt pytest && pytest`
- Check a snapshot loads: `./gradlew :core:checkSnapshot -Psnapshot=pipeline/out/snapshot.json.gz`

CI builds the APK on every push and publishes it from `main`. Run the **Market data** workflow by hand once to create
the first snapshot (it takes 1–3 hours the first time while it fetches fundamentals for every company; after that
it refreshes about 600 companies' fundamentals a night and every price).

**Signing:** without secrets, CI signs with a debug key kept in the Actions cache. For a permanent key (needed for
the Play Store), add the repository secrets `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and
`SIGNING_KEY_PASSWORD`.

## Data and licensing

Yahoo Finance data is fine for development and personal use. Before a commercial or Play Store launch, replace
`fetch_prices` and `fetch_fundamentals` in `pipeline/build_snapshot.py` with a licensed Indian market-data feed; the
app and engine only read the snapshot format, so nothing else changes. Quarterly shareholding (promoter / FII / DII /
pledge) fields are supported by the format and the app, and are the next addition to the pipeline.

## Disclaimer

Sievr is a research tool, not investment advice, and is not registered with SEBI as an investment adviser or research analyst.
