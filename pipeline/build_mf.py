#!/usr/bin/env python3
"""
Builds mf.json.gz: every mutual fund scheme's latest NAV, from AMFI's daily NAV file.

  python build_mf.py --out out/mf.json.gz [--previous previous/mf.json.gz]

AMFI (the Association of Mutual Funds in India) publishes all schemes' NAVs each evening at
https://www.amfiindia.com/spages/NAVAll.txt. With the previous day's file, each scheme also gets its
previous NAV, so the app can show the day's change.
"""
from __future__ import annotations

import argparse
import datetime as dt
import gzip
import json
import logging
import os
import re
import sys
import time

import requests

log = logging.getLogger("sievr.mf")
AMFI = "https://www.amfiindia.com/spages/NAVAll.txt"
HEADERS = {"User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"}


def parse_navall(text: str) -> list[dict]:
    """Schemes from AMFI's NAVAll.txt: category headings, fund-house lines and semicolon-separated rows."""
    schemes = []
    category = ""
    amc = ""
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("Scheme Code"):
            continue
        parts = line.split(";")
        if len(parts) >= 6:
            code, isin1, isin2, name, nav, date = (p.strip() for p in parts[:6])
            if not code.isdigit():
                continue
            try:
                value = float(nav)
                day = dt.datetime.strptime(date, "%d-%b-%Y").date().isoformat()
            except ValueError:
                continue  # "N.A." NAVs and bad dates
            if value <= 0:
                continue
            row = {"code": int(code), "name": re.sub(r"\s+", " ", name), "amc": amc, "category": category, "nav": value, "date": day}
            isins = [i for i in (isin1, isin2) if re.fullmatch(r"INF[0-9A-Z]{9}", i or "")]
            if isins:
                row["isin"] = isins
            schemes.append(row)
        elif "Scheme" in line and "(" in line:
            # e.g. "Open Ended Schemes(Equity Scheme - Large Cap Fund)"
            m = re.search(r"\((.*)\)", line)
            category = (m.group(1) if m else line).strip()
        else:
            amc = line
    return schemes


def with_previous(schemes: list[dict], previous: list[dict]) -> list[dict]:
    """Adds prevNav/prevDate from the previous file: its NAV when that was an earlier date, else its own previous."""
    prev = {s["code"]: s for s in previous}
    for s in schemes:
        p = prev.get(s["code"])
        if not p:
            continue
        if p.get("date") and p["date"] < s["date"]:
            s["prevNav"], s["prevDate"] = p["nav"], p["date"]
        elif p.get("date") == s["date"] and p.get("prevNav"):
            s["prevNav"], s["prevDate"] = p["prevNav"], p.get("prevDate")
    return schemes


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", default="out/mf.json.gz")
    ap.add_argument("--previous", default="previous/mf.json.gz")
    args = ap.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

    text = None
    for attempt in range(4):
        try:
            r = requests.get(AMFI, headers=HEADERS, timeout=60)
            r.raise_for_status()
            text = r.content.decode("utf-8", "replace")
            break
        except Exception as e:  # noqa: BLE001
            log.warning("AMFI download failed (%s), retrying", e)
            time.sleep(5 * (attempt + 1))
    if text is None:
        raise SystemExit("Could not download AMFI NAVs")
    schemes = parse_navall(text)
    if len(schemes) < 2000:
        raise SystemExit(f"Only {len(schemes)} schemes parsed; not publishing")
    if os.path.exists(args.previous):
        with gzip.open(args.previous, "rb") as f:
            schemes = with_previous(schemes, json.loads(f.read()).get("schemes", []))
    latest = max(s["date"] for s in schemes)
    data = {"version": 1, "asOf": latest, "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
            "source": "AMFI", "schemes": schemes}
    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    with gzip.open(args.out, "wb", compresslevel=9) as f:
        f.write(json.dumps(data, separators=(",", ":"), ensure_ascii=False).encode())
    log.info("wrote %d schemes, NAVs up to %s, %d bytes; %d with a previous NAV", len(schemes), latest,
             os.path.getsize(args.out), sum(1 for s in schemes if "prevNav" in s))
    return 0


if __name__ == "__main__":
    sys.exit(main())
