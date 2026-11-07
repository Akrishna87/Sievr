import build_mf as m

SAMPLE = """Scheme Code;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Scheme Name;Net Asset Value;Date

Open Ended Schemes(Equity Scheme - Large Cap Fund)

Axis Mutual Fund

120465;INF846K01DP8;-;Axis Large Cap Fund - Direct Plan - Growth;62.4100;31-Oct-2026
120466;INF846K01DQ6;INF846K01DR4;Axis Large Cap Fund - Direct Plan - IDCW;20.1;31-Oct-2026
120467;-;-;Axis Something Wound Up;N.A.;01-Jan-2020

Open Ended Schemes(Debt Scheme - Liquid Fund)

HDFC Mutual Fund

119091;INF179KB1HP9;-;HDFC  Liquid Fund - Direct Plan - Growth Option;5100.25;31-Oct-2026
"""


def test_parse():
    s = m.parse_navall(SAMPLE)
    assert [x["code"] for x in s] == [120465, 120466, 119091]
    a = s[0]
    assert a["amc"] == "Axis Mutual Fund" and a["category"] == "Equity Scheme - Large Cap Fund"
    assert a["nav"] == 62.41 and a["date"] == "2026-10-31" and a["isin"] == ["INF846K01DP8"]
    assert s[1]["isin"] == ["INF846K01DQ6", "INF846K01DR4"]
    assert s[2]["name"] == "HDFC Liquid Fund - Direct Plan - Growth Option" and s[2]["category"] == "Debt Scheme - Liquid Fund"


def test_previous():
    today = m.parse_navall(SAMPLE)
    yesterday = [{"code": 120465, "nav": 61.0, "date": "2026-10-30"}, {"code": 119091, "nav": 5100.25, "date": "2026-10-31", "prevNav": 5099.0, "prevDate": "2026-10-30"}]
    out = {x["code"]: x for x in m.with_previous(today, yesterday)}
    assert out[120465]["prevNav"] == 61.0 and out[120465]["prevDate"] == "2026-10-30"
    assert out[119091]["prevNav"] == 5099.0  # same day re-run keeps the earlier previous NAV
    assert "prevNav" not in out[120466]
