import pandas as pd

import build_snapshot as b


def test_add_etfs_reads_nse_list():
    df = pd.DataFrame({"Symbol": ["NIFTYBEES", "GOLDBEES", "TCS"], "Underlying": ["Nifty 50 Index", "Gold", "x"],
                       "SecurityName": ["Nippon India ETF Nifty 50 BeES", "Nippon India ETF Gold BeES", "dup"],
                       "ISINNumber": ["INF204KB14I2", "INF204KB17I5", "INE467B01029"]})
    stocks = {"TCS": {"name": "Tata Consultancy Services", "isin": "INE467B01029"}}
    assert b.add_etfs(stocks, df) == 2
    assert stocks["NIFTYBEES"]["type"] == "ETF"
    assert stocks["NIFTYBEES"]["industry"] == "Nifty 50"
    assert stocks["GOLDBEES"]["industry"] == "Gold"
    assert stocks["GOLDBEES"]["isin"] == "INF204KB17I5"
    assert stocks["TCS"]["name"] == "Tata Consultancy Services"


def test_etf_kind():
    assert b.etf_kind("Nifty Bank Index", "Kotak Banking ETF") == "Banking"
    assert b.etf_kind("NASDAQ 100", "Motilal Oswal NASDAQ 100 ETF") == "International"
    assert b.etf_kind("Nifty 1D Rate Index", "Liquid ETF") == "Liquid"
