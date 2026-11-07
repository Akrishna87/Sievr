# Sievr

**A free stock screener for Indian markets (NSE and BSE), for Android.** No account, no paywall: the features other
screeners charge for are calculated on the phone from one end-of-day data file that's rebuilt every weekday evening.

**Download:** `Sievr.apk` from the [`app-latest` release](https://github.com/Akrishna87/Sievr/releases/tag/app-latest).

## Features

**Screening**
- 120+ metrics: valuation, profitability, growth, financial health, cash flow, dividends, ownership, analyst targets,
  technicals and 17 candlestick patterns
- 39 ready-made screens: Magic Formula, Graham's defensive investor, Coffee Can, Piotroski 8+, Lynch GARP,
  Buffett-style moats, golden cross, 52-week breakouts on volume, oversold quality, promoter buying, mutual funds buying, pledge red flags…
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
  technical signals, peer comparison and quarterly shareholding from NSE filings (promoter, FII, DII, mutual fund,
  retail, pledges and quarter-on-quarter changes)
- Fair value: interactive DCF, reverse DCF ("what growth is priced in"), Graham number, Graham growth formula, Lynch value, analyst targets
- Piotroski F-score, Altman Z-score, Magic Formula rank, PEG, earnings yield, cash conversion, ROCE consistency
- Compare up to four stocks side by side

**Tracking**
- Multiple watchlists with custom columns
- Portfolio X-ray: import your NSDL/CDSL demat statement PDF (all demat accounts at once) or a broker CSV (Zerodha, Groww, Upstox…), see gains, sector and cap mix, weighted P/E and ROE,
  expected dividends, concentration and red flags (pledges, distress, over-concentration)
- Mutual funds: import your CAS statement PDF (CAMS, KFintech, MF Central, NSDL or CDSL; read on the phone, never
  uploaded) or add funds by name (a holdings CSV works too) with units and amount invested; values refresh with AMFI's
  NAVs every business day, with gains, the last day's change, and asset, category and fund-house mix
- **Family portfolios**: add members (spouse, parents…) and switch between each person's stocks and funds or a
  Family total with a per-member breakdown; imports and manual adds go into whichever member you choose
- **CAGR**: enter the date you first bought a holding to see its yearly growth, plus a money-weighted (XIRR-style)
  CAGR for the whole portfolio
- Price, % move, RSI and 52-week alerts; big-move alerts for watchlists; a daily digest

## How it works

```
pipeline/          Python, runs on GitHub Actions every weekday 19:15 IST
  build_mf.py         mutual fund NAVs from AMFI (nightly, own workflow)
  build_snapshot.py   NSE listings + index lists, BSE's daily bhavcopy (for BSE-only companies and BSE codes),
                      Yahoo Finance prices and fundamentals
  shareholding.py     quarterly shareholding patterns from NSE's XBRL filings
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

**Signing:** without secrets, CI signs with a debug key kept in the Actions cache. Updates only install over the
previous version when they're signed with the same key, and the Actions cache can expire after a week without builds,
so for a permanent key add the repository secrets `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and
`SIGNING_KEY_PASSWORD`.

## Data and licensing

Yahoo Finance data is fine for personal use among friends and family. If Sievr is ever offered publicly, replace
`fetch_prices` and `fetch_fundamentals` in `pipeline/build_snapshot.py` with a licensed Indian market-data feed; the
app and engine only read the snapshot format, so nothing else changes.

Shareholding comes from the quarterly shareholding-pattern filings companies make on NSE. Filings change once a
quarter, so the pipeline re-checks about 500 companies a night (45 minutes at most, with pauses between requests) and
caches the results in `shareholding.json.gz` on the data release; a full first pass takes a few nights.

## Sharing with friends and family

Sievr isn't on the Play Store. Send people this link and ask them to open it on their Android phone:

**https://github.com/Akrishna87/Sievr/releases/download/app-latest/Sievr.apk**

Android will ask them to allow installing apps from their browser (or WhatsApp, if you send the file there) the first
time. To update, they download and open the same link again; it installs over the old version and keeps their screens,
watchlists and portfolio. Market data updates by itself every weekday evening.

Every feature is free for everyone. There is no account, no premium tier and nothing to unlock.

## Disclaimer

Sievr is a research tool, not investment advice, and is not registered with SEBI as an investment adviser or research analyst.
