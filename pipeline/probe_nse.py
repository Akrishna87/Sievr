"""Temporary probe: what NSE's shareholding and pledge endpoints return from a CI runner."""
import json, re, sys, time
import requests

H = {"User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36",
     "Accept": "application/json,text/plain,*/*", "Accept-Language": "en-US,en;q=0.9",
     "Referer": "https://www.nseindia.com/companies-listing/corporate-filings-shareholding-pattern"}
s = requests.Session(); s.headers.update(H)
r = s.get("https://www.nseindia.com/", timeout=30); print("home", r.status_code, len(r.content), list(s.cookies.keys()))
for sym in sys.argv[1:] or ["RELIANCE"]:
    for url in [f"https://www.nseindia.com/api/corporate-share-holdings-master?index=equities&symbol={sym}",
                f"https://www.nseindia.com/api/corporate-pledgedata?index=equities&symbol={sym}"]:
        r = s.get(url, timeout=30); print("\n==", url, r.status_code, len(r.content))
        try:
            d = r.json()
        except Exception:
            print(r.text[:300]); continue
        print(json.dumps(d if not isinstance(d, list) else d[:3], indent=1)[:3000])
        if "share-holdings" in url and isinstance(d, list) and d:
            x = d[0].get("xbrl")
            if x:
                xr = s.get(x, timeout=30); print("\n== xbrl", x, xr.status_code, len(xr.content))
                t = xr.text
                facts = re.findall(r"<([\w-]+:[\w]+)[^>]*contextRef=\"([^\"]+)\"[^>]*>([^<]{0,60})<", t)
                print("facts", len(facts))
                for name, ctx, val in facts:
                    if (ctx.endswith("_ContextI") or ctx in ("MainI", "MainD")) and ("Percentage" in name or "Pledge" in name or "Encumb" in name or "NumberOfShares" in name):
                        print("F", name, ctx, val)
                for name, ctx, val in facts:
                    if ("Pledge" in name or "Encumb" in name) and val.strip() not in ("0", ""):
                        print("P", name, ctx, val)
                for name, ctx, val in facts:
                    if ctx in ("MainI", "MainD") and not val.strip().replace(".", "").isdigit():
                        print("M", name, ctx, val)
        time.sleep(1)
