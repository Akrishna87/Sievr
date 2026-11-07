#!/usr/bin/env python3
"""
Builds Sievr's daily market snapshot for NSE-listed stocks.

  python build_snapshot.py --out out/ [--cache cache/fundamentals.json] [--refresh 500] [--universe all|nifty500]

Sources:
  * NSE archives: the list of listed equities and index constituents (public CSV files).
  * Yahoo Finance via yfinance: adjusted daily prices, company profiles, ratios and annual
    financial statements.

Yahoo's data is fine for building and testing Sievr. Before publishing the app commercially,
swap `fetch_fundamentals` / `fetch_prices` for a licensed Indian market data feed; the rest of
the pipeline and the app don't change because they only read the snapshot format.

Fundamentals change quarterly, so they are cached between runs and only the `--refresh` oldest
entries are re-fetched each night; prices are fetched in full every run.
"""
from __future__ import annotations

import argparse
import datetime as dt
import gzip
import io
import json
import logging
import math
import os
import sys
import time
from typing import Any, Iterable

import pandas as pd
import requests

import names
import shareholding

log = logging.getLogger("sievr")

NSE_EQUITY_LIST = "https://archives.nseindia.com/content/equities/EQUITY_L.csv"
NSE_ETF_LIST = "https://archives.nseindia.com/content/equities/eq_etfseclist.csv"
NSE_INDEX_LISTS = {
    "NIFTY 50": "https://archives.nseindia.com/content/indices/ind_nifty50list.csv",
    "NIFTY NEXT 50": "https://archives.nseindia.com/content/indices/ind_niftynext50list.csv",
    "NIFTY 100": "https://archives.nseindia.com/content/indices/ind_nifty100list.csv",
    "NIFTY MIDCAP 150": "https://archives.nseindia.com/content/indices/ind_niftymidcap150list.csv",
    "NIFTY SMALLCAP 250": "https://archives.nseindia.com/content/indices/ind_niftysmallcap250list.csv",
    "NIFTY 500": "https://archives.nseindia.com/content/indices/ind_nifty500list.csv",
    "NIFTY MICROCAP 250": "https://archives.nseindia.com/content/indices/ind_niftymicrocap250_list.csv",
}
BENCHMARKS = {"NIFTY 50": "^NSEI", "NIFTY BANK": "^NSEBANK", "NIFTY 500": "^CRSLDX", "SENSEX": "^BSESN", "BSE 500": "BSE-500.BO"}
BSE_BHAVCOPY = "https://www.bseindia.com/download/BhavCopy/Equity/BhavCopy_BSE_CM_0_0_0_{date}_F_0000.CSV"
# BSE groups that are company shares. Left out: F/E (mutual funds, ETFs), IF (REITs, InvITs), G, P and R.
BSE_SERIES = {"A", "B", "T", "X", "XT", "M", "MT", "MS", "TS", "Z"}
HEADERS = {
    "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36",
    "Accept": "text/csv,application/octet-stream,*/*",
    "Accept-Language": "en-US,en;q=0.9",
}
CRORE = 1e7
DAILY_BARS = 300      # enough for a 200-day average and a year of history
VOLUME_BARS = 60
OHLC_BARS = 5
WEEKLY_BARS = 6 * 52 + 2
EPOCH = dt.date(1970, 1, 1)


# ----------------------------------------------------------------------------- universe

def http_get(url: str, tries: int = 4) -> bytes:
    last: Exception | None = None
    for attempt in range(tries):
        try:
            r = requests.get(url, headers=HEADERS, timeout=30)
            r.raise_for_status()
            return r.content
        except Exception as e:  # noqa: BLE001
            last = e
            time.sleep(2 ** attempt)
    raise RuntimeError(f"GET {url} failed: {last}")


def read_csv_url(url: str) -> pd.DataFrame:
    df = pd.read_csv(io.BytesIO(http_get(url)))
    df.columns = [c.strip() for c in df.columns]
    return df


def load_universe(kind: str) -> tuple[dict[str, dict], dict[str, list[str]]]:
    """Returns ({symbol: {name, isin, industry}}, {symbol: [index names]})."""
    memberships: dict[str, list[str]] = {}
    nifty_industry: dict[str, str] = {}
    for index, url in NSE_INDEX_LISTS.items():
        try:
            df = read_csv_url(url)
        except Exception as e:  # noqa: BLE001
            log.warning("index list %s unavailable: %s", index, e)
            continue
        for _, row in df.iterrows():
            sym = str(row.get("Symbol", "")).strip()
            if not sym:
                continue
            memberships.setdefault(sym, []).append(index)
            if isinstance(row.get("Industry"), str):
                nifty_industry[sym] = row["Industry"].strip()

    stocks: dict[str, dict] = {}
    if kind == "all":
        df = read_csv_url(NSE_EQUITY_LIST)
        for _, row in df.iterrows():
            if str(row.get("SERIES", "")).strip() not in ("EQ", "BE", "BZ", "SM", "ST"):
                continue
            sym = str(row["SYMBOL"]).strip()
            stocks[sym] = {"name": str(row["NAME OF COMPANY"]).strip(), "isin": str(row.get("ISIN NUMBER", "")).strip() or None}
    else:
        for sym in memberships:
            if "NIFTY 500" in memberships[sym]:
                stocks[sym] = {"name": sym, "isin": None}
    if kind == "all":
        try:
            log.info("ETFs added: %d", add_etfs(stocks, read_csv_url(NSE_ETF_LIST)))
        except Exception as e:  # noqa: BLE001
            log.warning("ETF list unavailable: %s", e)
    for sym, info in stocks.items():
        info["niftyIndustry"] = nifty_industry.get(sym)
        info["yahoo"] = sym + ".NS"
        info["exchanges"] = ["NSE"]
    if not stocks:
        raise RuntimeError("Empty universe: NSE lists could not be read")
    return stocks, memberships


def _col(df: pd.DataFrame, *words: str) -> str | None:
    """The first column whose name (case and spaces ignored) contains any of `words`."""
    for c in df.columns:
        key = c.lower().replace(" ", "")
        if any(w in key for w in words):
            return c
    return None


def add_etfs(stocks: dict[str, dict], df: pd.DataFrame) -> int:
    """Adds NSE's exchange traded funds to `stocks` (prices only: ETFs have no company fundamentals)."""
    sym_c, isin_c = _col(df, "symbol"), _col(df, "isin")
    name_c, under_c = _col(df, "securityname", "name"), _col(df, "underlying")
    if not sym_c:
        raise RuntimeError(f"unexpected ETF list columns: {list(df.columns)}")
    added = 0
    for _, row in df.iterrows():
        sym = str(row[sym_c]).strip()
        if not sym or sym == "nan" or sym in stocks:
            continue
        under = str(row[under_c]).strip() if under_c and isinstance(row[under_c], str) else ""
        name = str(row[name_c]).strip() if name_c and isinstance(row[name_c], str) else sym
        isin = str(row[isin_c]).strip() if isin_c and isinstance(row[isin_c], str) else None
        stocks[sym] = {"name": name, "isin": isin or None, "type": "ETF", "sector": "ETF", "industry": etf_kind(under, name)}
        added += 1
    return added


def etf_kind(underlying: str, name: str) -> str:
    """A short grouping for an ETF from what it tracks: Gold, Silver, Nifty 50, Bank, Liquid…"""
    t = f"{underlying} {name}".lower()
    for words, kind in [(("gold",), "Gold"), (("silver",), "Silver"), (("liquid", "overnight", "money market"), "Liquid"),
                        (("gilt", "bond", "g-sec", "gsec", "sdl", "psu bond", "bharat bond", "debt"), "Debt"),
                        (("nasdaq", "s&p 500", "hang seng", "us ", "global", "international", "fang", "nyse"), "International"),
                        (("bank",), "Banking"), (("it ", "information tech", "technology"), "IT"),
                        (("next 50", "junior"), "Nifty Next 50"), (("midcap", "mid cap"), "Midcap"), (("smallcap", "small cap"), "Smallcap"),
                        (("sensex",), "Sensex"), (("nifty 50", "nifty50", "nifty bees", "niftybees"), "Nifty 50")]:
        if any(w in t for w in words):
            return kind
    return "Other ETF"


def load_bse_bhavcopy(today: dt.date | None = None) -> list[dict]:
    """Rows of BSE's most recent daily bhavcopy (every scrip that traded), looking back up to 10 days."""
    import csv

    day = today or dt.date.today()
    for back in range(0, 11):
        d = day - dt.timedelta(days=back)
        if d.weekday() >= 5:
            continue
        try:
            raw = http_get(BSE_BHAVCOPY.format(date=d.strftime("%Y%m%d")), tries=2)
        except Exception:  # noqa: BLE001
            continue
        rows = list(csv.DictReader(io.StringIO(raw.decode("utf-8", "replace"))))
        if rows and "ISIN" in rows[0]:
            log.info("BSE bhavcopy for %s: %d rows", d, len(rows))
            return rows
    log.warning("No BSE bhavcopy found in the last 10 days; continuing with NSE only")
    return []


def merge_bse(stocks: dict[str, dict], rows: list[dict]) -> int:
    """
    Adds BSE to `stocks` in place. Companies on both exchanges (same ISIN) keep their NSE symbol and gain
    "BSE" plus their BSE scrip code; companies only on BSE are added under their BSE symbol (with "-BSE"
    appended if an unrelated NSE company already uses it), priced from Yahoo's `.BO` ticker.
    Returns the number of BSE-only companies added.
    """
    by_isin = {info.get("isin"): sym for sym, info in stocks.items() if info.get("isin")}
    added = 0
    for r in rows:
        isin = (r.get("ISIN") or "").strip()
        sym = (r.get("TckrSymb") or "").strip()
        code = (r.get("FinInstrmId") or "").strip()
        if isin.startswith("INF") and isin in by_isin:   # an NSE ETF also traded on BSE
            info = stocks[by_isin[isin]]
            if "BSE" not in info["exchanges"]:
                info["exchanges"] = info["exchanges"] + ["BSE"]
            info["bseCode"] = code
            continue
        if (r.get("FinInstrmTp") or "STK").strip() != "STK" or not isin.startswith("INE") or not sym:
            continue
        if (r.get("SctySrs") or "").strip() not in BSE_SERIES:
            continue
        if isin in by_isin:
            info = stocks[by_isin[isin]]
            if "BSE" not in info["exchanges"]:
                info["exchanges"] = info["exchanges"] + ["BSE"]
            info["bseCode"] = code
            continue
        key = sym if sym not in stocks else sym + "-BSE"
        if key in stocks:
            continue
        stocks[key] = {"name": (r.get("FinInstrmNm") or sym).strip(), "isin": isin, "niftyIndustry": None,
                       "yahoo": sym + ".BO", "exchanges": ["BSE"], "bseCode": code}
        by_isin[isin] = key
        added += 1
    return added


# ----------------------------------------------------------------------------- prices

def fetch_prices(tickers: list[str], period: str = "7y", batch: int = 150) -> dict[str, pd.DataFrame]:
    """Adjusted daily OHLCV per ticker (DatetimeIndex, columns Open High Low Close Volume)."""
    import yfinance as yf

    out: dict[str, pd.DataFrame] = {}
    for i in range(0, len(tickers), batch):
        chunk = tickers[i:i + batch]
        for attempt in range(3):
            try:
                df = yf.download(chunk, period=period, interval="1d", group_by="ticker", auto_adjust=True,
                                 threads=True, progress=False)
                break
            except Exception as e:  # noqa: BLE001
                log.warning("download batch %d failed (%s), retrying", i, e)
                time.sleep(10 * (attempt + 1))
        else:
            continue
        for t in chunk:
            try:
                sub = df[t] if isinstance(df.columns, pd.MultiIndex) else df
            except KeyError:
                continue
            sub = sub.dropna(how="all")
            if len(sub) and "Close" in sub:
                out[t] = sub[["Open", "High", "Low", "Close", "Volume"]].copy()
        log.info("prices %d/%d", min(i + batch, len(tickers)), len(tickers))
        time.sleep(1)
    return out


def epoch_day(ts: Any) -> int:
    return (pd.Timestamp(ts).date() - EPOCH).days


def r2(x: Any, digits: int = 2) -> float | None:
    try:
        f = float(x)
    except (TypeError, ValueError):
        return None
    if math.isnan(f) or math.isinf(f):
        return None
    return round(f, digits)


def aligned(series: pd.Series, dates: pd.DatetimeIndex, digits: int = 2) -> list[float | None]:
    s = series.reindex(dates)
    return [r2(v, digits) for v in s.tolist()]


def weekly_closes(df: pd.DataFrame) -> pd.Series:
    return df["Close"].dropna().resample("W-FRI").last().dropna()


def price_record(df: pd.DataFrame, daily_dates: pd.DatetimeIndex, weekly_dates: pd.DatetimeIndex) -> dict | None:
    df = df.dropna(subset=["Close"])
    if df.empty or df.index[-1] < daily_dates[-1] - pd.Timedelta(days=14):
        return None  # suspended or delisted
    closes = aligned(df["Close"], daily_dates)
    vols = [None if v is None else int(v) for v in aligned(df["Volume"], daily_dates[-VOLUME_BARS:], 0)]
    tail = df.tail(OHLC_BARS)
    ohlc = [[r2(r.Open), r2(r.High), r2(r.Low), r2(r.Close)] for r in tail.itertuples()]
    ohlc = [b for b in ohlc if None not in b]
    weekly = aligned(weekly_closes(df), weekly_dates)
    return {"c": closes, "v": vols, "ohlc": ohlc, "w": weekly}


# ----------------------------------------------------------------------------- fundamentals

INFO_FIELDS = {
    # yfinance key: (snapshot key, multiplier)
    "trailingPE": ("pe", 1), "forwardPE": ("fpe", 1), "priceToBook": ("pb", 1),
    "trailingEps": ("eps", 1), "bookValue": ("bookValue", 1),
    "returnOnEquity": ("roe", 100), "returnOnAssets": ("roa", 100),
    "operatingMargins": ("opm", 100), "profitMargins": ("npm", 100), "grossMargins": ("gpm", 100),
    "revenueGrowth": ("revenueGrowth", 100), "earningsQuarterlyGrowth": ("earningsGrowth", 100),
    "currentRatio": ("currentRatio", 1), "quickRatio": ("quickRatio", 1), "debtToEquity": ("de", 0.01),
    "beta": ("beta", 1), "heldPercentInsiders": ("insiders", 100), "heldPercentInstitutions": ("institutions", 100),
    "payoutRatio": ("payout", 100), "targetMeanPrice": ("targetPrice", 1),
    "numberOfAnalystOpinions": ("analysts", 1), "recommendationMean": ("rating", 1),
    "marketCap": ("mcap", 1 / CRORE), "totalRevenue": ("revenue", 1 / CRORE),
    "netIncomeToCommon": ("netIncome", 1 / CRORE), "ebitda": ("ebitda", 1 / CRORE),
    "enterpriseValue": ("ev", 1 / CRORE), "totalDebt": ("debt", 1 / CRORE), "totalCash": ("cash", 1 / CRORE),
    "freeCashflow": ("fcf", 1 / CRORE), "operatingCashflow": ("ocf", 1 / CRORE),
    "sharesOutstanding": ("shares", 1 / CRORE),
}
MONEY_KEYS = {"mcap", "revenue", "netIncome", "ebitda", "ev", "debt", "cash", "fcf", "ocf", "eps", "bookValue", "targetPrice"}

STATEMENT_ROWS = {
    "revenue": ["Total Revenue", "Operating Revenue"],
    "grossProfit": ["Gross Profit"],
    "ebit": ["EBIT", "Operating Income"],
    "ebitda": ["EBITDA", "Normalized EBITDA"],
    "interest": ["Interest Expense", "Interest Expense Non Operating"],
    "netIncome": ["Net Income Common Stockholders", "Net Income", "Net Income From Continuing Operation Net Minority Interest"],
    "eps": ["Diluted EPS", "Basic EPS"],
    "totalAssets": ["Total Assets"],
    "currentAssets": ["Current Assets"],
    "currentLiabilities": ["Current Liabilities"],
    "totalLiabilities": ["Total Liabilities Net Minority Interest"],
    "equity": ["Stockholders Equity", "Common Stock Equity"],
    "debt": ["Total Debt"],
    "cash": ["Cash And Cash Equivalents", "Cash Cash Equivalents And Short Term Investments"],
    "retainedEarnings": ["Retained Earnings"],
    "ocf": ["Operating Cash Flow"],
    "capex": ["Capital Expenditure"],
    "dividendsPaid": ["Cash Dividends Paid", "Common Stock Dividend Paid"],
    "shares": ["Ordinary Shares Number", "Share Issued"],
}
PER_SHARE = {"eps"}
COUNT_KEYS = {"shares"}


def info_fields(info: dict, fx: float) -> dict[str, float]:
    """Maps a yfinance `info` dict to snapshot raw fields. `fx` converts the financial currency to INR."""
    out: dict[str, float] = {}
    for key, (name, mult) in INFO_FIELDS.items():
        v = r2(info.get(key), 6)
        if v is None:
            continue
        value = v * mult
        if name in MONEY_KEYS and name not in ("mcap", "ev", "targetPrice"):
            value *= fx
        out[name] = round(value, 4)
    price = r2(info.get("currentPrice") or info.get("regularMarketPrice"), 4)
    rate = r2(info.get("dividendRate"), 4)
    if price and rate is not None:
        out["divYield"] = round(rate / price * 100, 4)
    elif r2(info.get("trailingAnnualDividendYield"), 6) is not None:
        out["divYield"] = round(info["trailingAnnualDividendYield"] * 100, 4)
    return out


def statements(frames: Iterable[pd.DataFrame], fx: float) -> list[dict]:
    """Merges yfinance annual income, balance and cash-flow frames into per-year records, oldest first."""
    years: dict[int, dict] = {}
    for frame in frames:
        if frame is None or frame.empty:
            continue
        for col in frame.columns:
            year = pd.Timestamp(col).year
            rec = years.setdefault(year, {"year": year})
            for name, rows in STATEMENT_ROWS.items():
                if name in rec:
                    continue
                for row in rows:
                    if row in frame.index:
                        v = r2(frame.at[row, col], 6)
                        if v is not None:
                            if name in COUNT_KEYS:
                                v = v / CRORE
                            elif name in PER_SHARE:
                                v = v * fx
                            else:
                                v = v / CRORE * fx
                            if name in ("capex", "dividendsPaid", "interest"):
                                v = abs(v)
                            rec[name] = round(v, 4)
                            break
    # Keep years that have at least revenue or net income.
    recs = [r for _, r in sorted(years.items()) if "revenue" in r or "netIncome" in r]
    return recs[-5:]


_fx_cache: dict[str, float] = {}


def to_inr(currency: str | None) -> float:
    import yfinance as yf

    if not currency or currency == "INR":
        return 1.0
    if currency not in _fx_cache:
        try:
            hist = yf.Ticker(f"{currency}INR=X").history(period="5d")
            _fx_cache[currency] = float(hist["Close"].dropna().iloc[-1])
        except Exception:  # noqa: BLE001
            _fx_cache[currency] = float("nan")
    return _fx_cache[currency]


def fetch_fundamentals(ticker: str) -> dict | None:
    import yfinance as yf

    t = yf.Ticker(ticker)
    info = t.info or {}
    if not info or info.get("quoteType") not in (None, "EQUITY"):
        return None
    fx = to_inr(info.get("financialCurrency"))
    if math.isnan(fx):
        fx = 1.0
    rec: dict[str, Any] = {
        "name": info.get("longName") or info.get("shortName"),
        "sector": info.get("sector"),
        "industry": info.get("industry"),
        "f": info_fields(info, fx),
        "fetched": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
    }
    try:
        rec["fin"] = statements([t.income_stmt, t.balance_sheet, t.cashflow], fx)
    except Exception as e:  # noqa: BLE001
        log.debug("statements %s: %s", ticker, e)
        rec["fin"] = []
    return rec


def refresh_fundamentals(symbols: list[str], cache: dict[str, dict], limit: int, budget_s: float,
                         yahoo: dict[str, str] | None = None) -> int:
    """Re-fetches the `limit` stalest (or missing) entries within `budget_s` seconds."""
    order = sorted(symbols, key=lambda s: cache.get(s, {}).get("fetched", ""))
    started = time.time()
    done = 0
    for sym in order[:limit]:
        if time.time() - started > budget_s:
            log.info("fundamentals time budget used up after %d", done)
            break
        for attempt in range(3):
            try:
                rec = fetch_fundamentals((yahoo or {}).get(sym, sym + ".NS"))
                if rec:
                    cache[sym] = rec
                done += 1
                break
            except Exception as e:  # noqa: BLE001
                msg = str(e)
                wait = 30 * (attempt + 1) if "Too Many Requests" in msg or "429" in msg else 2
                log.warning("fundamentals %s: %s (retry in %ds)", sym, msg[:120], wait)
                time.sleep(wait)
        if done % 50 == 0 and done:
            log.info("fundamentals %d/%d", done, min(limit, len(order)))
        time.sleep(0.3)
    return done


# ----------------------------------------------------------------------------- assemble

def build(universe: dict[str, dict], memberships: dict[str, list[str]], prices: dict[str, pd.DataFrame],
          bench: dict[str, pd.DataFrame], cache: dict[str, dict], holdings: dict[str, dict] | None = None) -> dict:
    ref = bench.get("NIFTY 50")
    if ref is None or ref.empty:
        ref = max(prices.values(), key=len)
    all_dates = ref["Close"].dropna().index
    daily_dates = all_dates[-DAILY_BARS:]
    weekly_dates = weekly_closes(ref).index[-WEEKLY_BARS:]

    stocks = []
    for sym, base in sorted(universe.items()):
        df = prices.get(base.get("yahoo", sym + ".NS"))
        if df is None:
            continue
        rec = price_record(df, daily_dates, weekly_dates)
        if rec is None:
            continue
        fund = cache.get(sym, {})
        stock = {
            "symbol": sym,
            "name": names.clean(fund.get("name") or base["name"], base.get("isin")),
            "isin": base.get("isin"),
            "type": base.get("type"),
            "sector": base.get("sector") or fund.get("sector") or base.get("niftyIndustry"),
            "industry": base.get("industry") or fund.get("industry") or base.get("niftyIndustry"),
            "indices": memberships.get(sym, []),
            "exchanges": base.get("exchanges", ["NSE"]),
            "bseCode": base.get("bseCode"),
            "f": fund.get("f", {}),
            "fin": fund.get("fin", []),
            "holding": shareholding.summarize((holdings or {}).get(sym, {}).get("quarters", [])),
            **rec,
        }
        stocks.append({k: v for k, v in stock.items() if v not in (None, [], {})})

    indices = []
    for name, df in bench.items():
        if df is None or df.empty:
            continue
        indices.append({"name": name, "c": aligned(df["Close"], daily_dates), "w": aligned(weekly_closes(df), weekly_dates)})

    return {
        "version": 1,
        "asOf": daily_dates[-1].date().isoformat(),
        "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
        "source": "NSE (listings, index constituents, shareholding filings); BSE (listings); Yahoo Finance (prices, fundamentals)",
        "dailyDates": [epoch_day(d) for d in daily_dates],
        "weeklyDates": [epoch_day(d) for d in weekly_dates],
        "stocks": stocks,
        "indices": indices,
    }


def write_gz_json(path: str, data: Any) -> int:
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    raw = json.dumps(data, separators=(",", ":"), ensure_ascii=False, allow_nan=False).encode()
    with gzip.open(path, "wb", compresslevel=9) as f:
        f.write(raw)
    return os.path.getsize(path)


def read_gz_json(path: str) -> Any:
    with gzip.open(path, "rb") as f:
        return json.loads(f.read())


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", default="out")
    ap.add_argument("--cache", default="cache/fundamentals.json.gz")
    ap.add_argument("--refresh", type=int, default=600, help="fundamentals to re-fetch this run")
    ap.add_argument("--budget-minutes", type=float, default=150, help="time limit for fundamentals")
    ap.add_argument("--holding-cache", default="cache/shareholding.json.gz")
    ap.add_argument("--holding-refresh", type=int, default=500, help="companies whose NSE shareholding filings to re-check")
    ap.add_argument("--holding-budget-minutes", type=float, default=45)
    ap.add_argument("--universe", choices=["all", "nifty500"], default="all")
    ap.add_argument("--limit", type=int, default=0, help="only the first N symbols (testing)")
    ap.add_argument("--no-bse", action="store_true", help="NSE only")
    args = ap.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

    universe, memberships = load_universe(args.universe)
    if args.universe == "all" and not args.no_bse:
        log.info("BSE-only companies added: %d", merge_bse(universe, load_bse_bhavcopy()))
    symbols = sorted(universe)
    if args.limit:
        # Nifty 50 first, then BSE-only companies, so a small test run covers both exchanges.
        priority = sorted(symbols, key=lambda s: (0 if "NIFTY 50" in memberships.get(s, []) else
                                                  1 if universe[s].get("exchanges") == ["BSE"] or universe[s].get("type") == "ETF" else 2, s))
        symbols = priority[: args.limit]
        universe = {s: universe[s] for s in symbols}
    log.info("universe: %d stocks", len(symbols))

    bench = {name: df for name, t in BENCHMARKS.items() for df in [fetch_prices([t]).get(t)] if df is not None}
    yahoo = {s: universe[s].get("yahoo", s + ".NS") for s in symbols}
    prices = fetch_prices([yahoo[s] for s in symbols])
    log.info("prices for %d stocks", len(prices))
    if len(prices) < max(10, len(symbols) // 3):
        raise SystemExit(f"Too few prices downloaded ({len(prices)}); not publishing")

    cache: dict[str, dict] = {}
    if os.path.exists(args.cache):
        cache = read_gz_json(args.cache)
        log.info("fundamentals cache: %d entries", len(cache))
    with_prices = [s for s in symbols if yahoo[s] in prices]
    etfs = {s for s in with_prices if universe[s].get("type") == "ETF"}
    log.info("ETFs with prices: %d", len(etfs))
    with_prices = [s for s in with_prices if s not in etfs]   # no fundamentals or shareholding for ETFs
    missing = sum(1 for s in with_prices if s not in cache)
    refresh_fundamentals(with_prices, cache, max(args.refresh, missing if missing < 3000 else args.refresh), args.budget_minutes * 60, yahoo)
    write_gz_json(args.cache, cache)

    holdings: dict[str, dict] = {}
    if os.path.exists(args.holding_cache):
        holdings = read_gz_json(args.holding_cache)
        log.info("shareholding cache: %d entries", len(holdings))
    if args.holding_refresh > 0:
        try:
            # Shareholding filings come from NSE, so only companies listed there.
            on_nse = [s for s in with_prices if "NSE" in universe[s].get("exchanges", ["NSE"])]
            missing = sum(1 for s in on_nse if s not in holdings)
            n = shareholding.refresh(on_nse, holdings, max(args.holding_refresh, min(missing, 3000)), args.holding_budget_minutes * 60)
            log.info("shareholding refreshed for %d companies", n)
        except Exception as e:  # noqa: BLE001
            log.warning("shareholding refresh failed: %s", e)
        write_gz_json(args.holding_cache, holdings)

    snapshot = build(universe, memberships, prices, bench, cache, holdings)
    size = write_gz_json(os.path.join(args.out, "snapshot.json.gz"), snapshot)
    meta = {"asOf": snapshot["asOf"], "generatedAt": snapshot["generatedAt"], "stocks": len(snapshot["stocks"]), "bytes": size}
    with open(os.path.join(args.out, "meta.json"), "w") as f:
        json.dump(meta, f)
    log.info("wrote %s", meta)
    return 0


if __name__ == "__main__":
    sys.exit(main())
