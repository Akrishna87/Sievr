"""
Quarterly shareholding patterns from NSE corporate filings.

Every listed company files its shareholding pattern each quarter as XBRL. NSE lists the filings at
`/api/corporate-share-holdings-master` and serves the XBRL from nsearchives. From each filing we take the
category totals (contexts ending in `_ContextI`), as fractions of total shares:

  promoter  ShareholdingOfPromoterAndPromoterGroup
  fii       InstitutionsForeign            (FPIs and other foreign institutions)
  dii       InstitutionsDomestic           (mutual funds, insurers, banks, pension funds…)
  mf        MutualFundsOrUTI
  public    PublicShareholding
  retail    resident individuals holding up to ₹2 lakh

and the promoter pledge: zero when the filing says no promoter shares are pledged, otherwise the pledged
percentage of promoter holding when the filing reports it.

Filings only change once a quarter, so results are cached per company and each night only a slice of
companies is re-checked. A new filing is downloaded only when its XBRL link is new.
"""
from __future__ import annotations

import datetime as dt
import logging
import re
import time
from typing import Any

import requests

log = logging.getLogger("sievr.shareholding")

HOME = "https://www.nseindia.com/"
MASTER = "https://www.nseindia.com/api/corporate-share-holdings-master?index=equities&symbol={symbol}"
HEADERS = {
    "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36",
    "Accept": "application/json,text/plain,*/*",
    "Accept-Language": "en-US,en;q=0.9",
    "Referer": "https://www.nseindia.com/companies-listing/corporate-filings-shareholding-pattern",
}
PERCENT = "ShareholdingAsAPercentageOfTotalNumberOfShares"
CATEGORIES = {
    "promoter": "ShareholdingOfPromoterAndPromoterGroup_ContextI",
    "fii": "InstitutionsForeign_ContextI",
    "dii": "InstitutionsDomestic_ContextI",
    "mf": "MutualFundsOrUTI_ContextI",
    "public": "PublicShareholding_ContextI",
    "retail": "ResidentIndividualShareholdersHoldingNominalShareCapitalUpToRsTwoLakh_ContextI",
}
PROMOTER_CONTEXT = CATEGORIES["promoter"]
FACT = re.compile(r"<([\w-]+):(\w+)\b[^>]*\bcontextRef=\"([^\"]+)\"[^>]*>([^<]*)<")


def parse_xbrl(text: str) -> dict[str, Any] | None:
    """Category percentages (0–100) and pledge from one shareholding-pattern XBRL document."""
    facts: dict[tuple[str, str], str] = {}
    for _prefix, name, context, value in FACT.findall(text):
        facts.setdefault((name, context), value.strip())

    def number(name: str, context: str) -> float | None:
        try:
            return float(facts[(name, context)])
        except (KeyError, ValueError):
            return None

    out: dict[str, Any] = {}
    for key, context in CATEGORIES.items():
        v = number(PERCENT, context)
        if v is not None:
            out[key] = round(v * 100, 2)
    if "promoter" not in out:
        return None

    # Pledge: "no pledged shares" flags give an exact zero; otherwise look for a pledged-percentage fact.
    flags = [v.lower() for (n, c), v in facts.items() if c == "MainI" and n.startswith("WhetherAnySharesHeldByPromotersAreEncumberedUnderPledged")]
    if flags and all(f == "false" for f in flags):
        out["pledged"] = 0.0
    else:
        for (name, context), value in facts.items():
            if context == PROMOTER_CONTEXT and "Pledge" in name and "Percentage" in name and "TotalNumberOfShares" not in name:
                try:
                    out["pledged"] = round(float(value) * 100, 2)
                    break
                except ValueError:
                    continue
    date = facts.get(("DateOfReport", "MainI"))
    if date:
        out["date"] = date
    return out


def _parse_date(text: str | None) -> dt.date | None:
    for fmt in ("%d-%b-%Y", "%Y-%m-%d", "%d-%m-%Y"):
        try:
            return dt.datetime.strptime((text or "").strip().title(), fmt).date()
        except ValueError:
            continue
    return None


def summarize(quarters: list[dict]) -> dict[str, float] | None:
    """The snapshot's `holding` record: the latest quarter plus changes from the one before (percentage points)."""
    qs = sorted((q for q in quarters if q.get("promoter") is not None), key=lambda q: q.get("date", ""), reverse=True)
    if not qs:
        return None
    latest = qs[0]
    out = {k: latest[k] for k in ("promoter", "pledged", "fii", "dii", "mf", "public", "retail") if latest.get(k) is not None}
    if len(qs) > 1:
        prev = qs[1]
        for k in ("promoter", "fii", "dii", "mf"):
            if latest.get(k) is not None and prev.get(k) is not None:
                out[k + "Change"] = round(latest[k] - prev[k], 2)
    if latest.get("date"):
        out["asOf"] = latest["date"]
    return out


class NseClient:
    """A browser-like session: NSE's API needs the cookies its home page sets."""

    def __init__(self, pause: float = 0.6):
        self.pause = pause
        self.session = requests.Session()
        self.session.headers.update(HEADERS)
        self._warm = 0.0

    def _warm_up(self) -> None:
        if time.time() - self._warm > 600:
            self.session.get(HOME, timeout=30)
            self._warm = time.time()

    def get(self, url: str, json_: bool = True, tries: int = 3) -> Any:
        last: Exception | None = None
        for attempt in range(tries):
            try:
                self._warm_up()
                r = self.session.get(url, timeout=40)
                if r.status_code in (401, 403):
                    self._warm = 0.0  # cookies expired: start again from the home page
                    raise RuntimeError(f"HTTP {r.status_code}")
                r.raise_for_status()
                time.sleep(self.pause)
                return r.json() if json_ else r.text
            except Exception as e:  # noqa: BLE001
                last = e
                time.sleep(2 * (attempt + 1))
        raise RuntimeError(f"{url}: {last}")


def refresh(symbols: list[str], cache: dict[str, dict], limit: int, budget_s: float, client: NseClient | None = None) -> int:
    """
    Re-checks the `limit` least recently checked companies within `budget_s` seconds. Each cache entry is
    {"checked": iso time, "quarters": [{date, promoter, fii, …, "xbrl": url}, …]} with the two latest quarters.
    """
    client = client or NseClient()
    order = sorted(symbols, key=lambda s: cache.get(s, {}).get("checked", ""))
    started = time.time()
    done = 0
    failures = 0
    for sym in order[:limit]:
        if time.time() - started > budget_s:
            log.info("shareholding time budget used up after %d", done)
            break
        if failures >= 25 and done == 0:
            log.warning("NSE keeps refusing; skipping shareholding this run")
            break
        entry = cache.setdefault(sym, {"quarters": []})
        try:
            filings = client.get(MASTER.format(symbol=requests.utils.quote(sym)))
        except Exception as e:  # noqa: BLE001
            failures += 1
            log.debug("shareholding %s: %s", sym, e)
            continue
        if not isinstance(filings, list):
            filings = []
        filings = [f for f in filings if isinstance(f, dict) and f.get("xbrl")]
        filings.sort(key=lambda f: _parse_date(f.get("date")) or dt.date.min, reverse=True)
        known = {q.get("xbrl"): q for q in entry["quarters"]}
        quarters = []
        for f in filings[:2]:
            url = f["xbrl"]
            if url in known:
                quarters.append(known[url])
                continue
            try:
                parsed = parse_xbrl(client.get(url, json_=False))
            except Exception as e:  # noqa: BLE001
                log.debug("xbrl %s: %s", url, e)
                parsed = None
            if parsed:
                parsed["xbrl"] = url
                quarters.append(parsed)
        if quarters:
            entry["quarters"] = quarters
        entry["checked"] = dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds")
        done += 1
        if done % 100 == 0:
            log.info("shareholding %d/%d", done, min(limit, len(order)))
    return done
