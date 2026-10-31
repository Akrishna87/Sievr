"""Offline tests for the pipeline's pure functions (no network)."""
import numpy as np
import pandas as pd

import build_snapshot as b


def frame(days=400, start=100.0):
    idx = pd.bdate_range("2025-01-01", periods=days)
    close = start * np.exp(np.linspace(0, 0.3, days))
    return pd.DataFrame({"Open": close * 0.99, "High": close * 1.01, "Low": close * 0.98, "Close": close, "Volume": 1000.0}, index=idx)


def test_build_and_align():
    nifty = frame(1600, 20000)
    stock = frame(1600, 100).iloc[5:]  # listed a week later
    stale = frame(300, 50)  # stopped trading long ago
    snap = b.build({"AAA": {"name": "A", "isin": "X"}, "OLD": {"name": "Old"}}, {"AAA": ["NIFTY 50"]},
                   {"AAA.NS": stock, "OLD.NS": stale}, {"NIFTY 50": nifty},
                   {"AAA": {"sector": "Technology", "f": {"pe": 20.0}, "fin": [{"year": 2025, "revenue": 10.0}]}})
    assert len(snap["dailyDates"]) == b.DAILY_BARS
    assert len(snap["weeklyDates"]) <= b.WEEKLY_BARS
    (s,) = snap["stocks"]
    assert s["symbol"] == "AAA" and s["sector"] == "Technology" and s["indices"] == ["NIFTY 50"]
    assert len(s["c"]) == b.DAILY_BARS and len(s["w"]) == len(snap["weeklyDates"])
    assert len(s["v"]) == b.VOLUME_BARS and len(s["ohlc"]) == b.OHLC_BARS
    assert snap["indices"][0]["name"] == "NIFTY 50"
    b.write_gz_json("/tmp/sievr-test/snap.json.gz", snap)
    assert b.read_gz_json("/tmp/sievr-test/snap.json.gz")["asOf"] == snap["asOf"]


def test_info_fields():
    f = b.info_fields({"trailingPE": 25.5, "returnOnEquity": 0.21, "debtToEquity": 35.0, "marketCap": 5e11,
                       "dividendRate": 10.0, "currentPrice": 500.0, "totalRevenue": 2e10, "heldPercentInsiders": 0.55,
                       "trailingEps": float("nan")}, 1.0)
    assert f["pe"] == 25.5 and f["roe"] == 21.0 and f["de"] == 0.35
    assert f["mcap"] == 50000.0 and f["revenue"] == 2000.0 and f["divYield"] == 2.0 and f["insiders"] == 55.0
    assert "eps" not in f


def test_statements():
    cols = [pd.Timestamp("2024-03-31"), pd.Timestamp("2025-03-31")]
    inc = pd.DataFrame({cols[0]: [1e10, 2e9, 1.5e9, 1.0], cols[1]: [1.2e10, 2.5e9, 1.8e9, 1.2]},
                       index=["Total Revenue", "EBIT", "Net Income", "Diluted EPS"])
    cf = pd.DataFrame({cols[0]: [2e9, -5e8], cols[1]: [2.2e9, -6e8]}, index=["Operating Cash Flow", "Capital Expenditure"])
    recs = b.statements([inc, pd.DataFrame(), cf], 1.0)
    assert [r["year"] for r in recs] == [2024, 2025]
    assert recs[1]["revenue"] == 1200.0 and recs[1]["capex"] == 60.0 and recs[1]["eps"] == 1.2
