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


NEW_LAYOUT = """Scheme Code;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Scheme Name;Plan;Option;Net Asset Value;Date
 
Open Ended Schemes(Children’s Fund - Childrens' Fund)
 
Axis Mutual Fund
 
135762;INF846K01WO1;-;Axis Children's Fund;Direct Plan;Growth Option;30.7694;06-Nov-2026
135763;INF846K01WS2;INF846K01WQ6;Axis Children's Fund;Direct Plan;IDCW Option;28.3978;06-Nov-2026
 
Open Ended Schemes(Debt Scheme - Banking and PSU Fund)
 
Franklin Templeton Mutual Fund
 
129008;INF090I01KR8;-;Franklin India Banking & PSU Debt Fund;Direct Plan;Growth;25.4003;06-Nov-2026
"""


def test_parse_plan_option_layout():
    s = m.parse_navall(NEW_LAYOUT)
    assert [x["code"] for x in s] == [135762, 135763, 129008]
    assert s[0]["name"] == "Axis Children's Fund - Direct Plan - Growth Option" and s[0]["nav"] == 30.7694
    assert s[0]["amc"] == "Axis Mutual Fund" and s[0]["date"] == "2026-11-06"
    assert s[2]["name"] == "Franklin India Banking & PSU Debt Fund - Direct Plan - Growth"
    assert s[2]["category"] == "Debt Scheme - Banking and PSU Fund" and s[2]["amc"] == "Franklin Templeton Mutual Fund"
    assert s[1]["isin"] == ["INF846K01WS2", "INF846K01WQ6"]
