"""Temporary probe: BSE's scrip list and Yahoo's .BO prices from a CI runner."""
import collections, json
import requests

H = {"User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36",
     "Accept": "application/json, text/plain, */*", "Referer": "https://www.bseindia.com/", "Origin": "https://www.bseindia.com"}
url = "https://api.bseindia.com/BseIndiaAPI/api/ListofScripData/w?Group=&Scripcode=&industry=&segment=Equity&status=Active"
r = requests.get(url, headers=H, timeout=60)
print("list", r.status_code, len(r.content), r.headers.get("content-type"))
try:
    d = r.json()
    rows = d if isinstance(d, list) else d.get("Table") or d
    print("rows", len(rows)); print(json.dumps(rows[:3], indent=1))
    print("groups", collections.Counter(x.get("GROUP") for x in rows).most_common(20))
    print("keys", list(rows[0].keys()))
except Exception as e:
    print("not json", e, r.text[:500])

import yfinance as yf
df = yf.download(["500325.BO", "RELIANCE.BO", "^BSESN", "BSE-500.BO", "^BSE500"], period="5d", group_by="ticker", progress=False)
for t in ["500325.BO", "RELIANCE.BO", "^BSESN", "BSE-500.BO", "^BSE500"]:
    try:
        print(t, df[t]["Close"].dropna().tail(2).round(2).tolist())
    except Exception as e:
        print(t, "none", e)
info = yf.Ticker("500325.BO").info
print("info", {k: info.get(k) for k in ("longName", "sector", "trailingPE", "marketCap", "financialCurrency")})
