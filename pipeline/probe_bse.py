"""Temporary probe: BSE's daily bhavcopy (all traded scrips) and Yahoo for a BSE-only company."""
import io, csv
import requests

H = {"User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36",
     "Accept": "*/*", "Referer": "https://www.bseindia.com/"}
rows = None
for url in ["https://www.bseindia.com/download/BhavCopy/Equity/BhavCopy_BSE_CM_0_0_0_20261030_F_0000.CSV",
            "https://www.bseindia.com/download/BhavCopy/Equity/BhavCopy_BSE_CM_0_0_0_20261029_F_0000.CSV",
            "https://www.bseindia.com/download/BhavCopy/Equity/EQ_ISINCODE_301026.zip",
            "https://www.bseindia.com/downloads1/List_of_companies.csv"]:
    try:
        r = requests.get(url, headers=H, timeout=60)
        print(url, r.status_code, len(r.content), r.headers.get("content-type"))
        if r.status_code == 200 and rows is None and url.endswith(".CSV"):
            rows = list(csv.DictReader(io.StringIO(r.content.decode("utf-8", "replace"))))
            print("rows", len(rows), "keys", list(rows[0].keys()))
            for x in rows[:3]: print(x)
    except Exception as e:
        print(url, "error", e)

nse = set()
r = requests.get("https://archives.nseindia.com/content/equities/EQUITY_L.csv", headers=H, timeout=60)
for x in csv.DictReader(io.StringIO(r.text)):
    nse.add((x.get(" ISIN NUMBER") or x.get("ISIN NUMBER") or "").strip())
print("nse isins", len(nse))
if rows:
    eq = [x for x in rows if (x.get("FinInstrmTp") or "").strip() in ("STK", "")]
    print("equity rows", len(eq), "series", sorted({(x.get("SctySrs") or "").strip() for x in eq})[:30])
    only = [x for x in eq if x.get("ISIN", "").strip() not in nse]
    print("bse only", len(only))
    liquid = sorted(only, key=lambda x: -float(x.get("TtlTrfVal") or 0))[:8]
    import yfinance as yf
    for x in liquid:
        sym = x["TckrSymb"].strip()
        t = yf.Ticker(sym + ".BO")
        h = t.history(period="5d")
        info = {}
        try:
            info = t.info or {}
        except Exception as e:
            info = {"err": str(e)[:80]}
        print(sym, x.get("FinInstrmId"), x.get("ISIN"), x.get("SctySrs"), x.get("TtlTrfVal"), "| yahoo close", h["Close"].round(2).tolist()[-2:],
              "| info", {k: info.get(k) for k in ("longName", "sector", "trailingPE", "marketCap")})
