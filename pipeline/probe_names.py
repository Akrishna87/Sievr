"""Temporary probe: where to get full company names for BSE-only stocks."""
import io, csv, json
import requests

H = {"User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36",
     "Accept": "*/*", "Referer": "https://www.bseindia.com/"}
syms = ["NSE", "CSML", "FLYHI", "HNIL", "MAGANTR", "TIPCO"]
for s in syms:
    r = requests.get("https://query2.finance.yahoo.com/v1/finance/search", params={"q": s + ".BO", "quotesCount": 3, "newsCount": 0}, headers=H, timeout=30)
    try:
        q = r.json().get("quotes", [])
        print("search", s, r.status_code, [(x.get("symbol"), x.get("longname"), x.get("shortname")) for x in q])
    except Exception as e:
        print("search", s, r.status_code, r.text[:200])
for url in ["https://www.bseindia.com/downloads1/List_of_companies.csv", "https://www.bseindia.com/corporates/List_Scrips.html"]:
    r = requests.get(url, headers=H, timeout=60)
    print(url, r.status_code, r.headers.get("content-type"), len(r.content))
    print(r.content[:800].decode("utf-8", "replace"))
import yfinance as yf
for s in syms:
    try:
        i = yf.Ticker(s + ".BO").info
        print("info", s, i.get("longName"), "|", i.get("shortName"), "|", i.get("displayName"))
    except Exception as e:
        print("info", s, "err", e)
