"""
Tidies company names. Yahoo's short names and BSE's bhavcopy cut names at 30 characters
("National Stock Exchange of Ind", "TIPCO ENGINEERING INDIA LIMITE") and some sources shout in capitals.
"""
from __future__ import annotations

import re

# Exact names by ISIN, for names no rule can rebuild.
OVERRIDES = {
    "INE721I01024": "National Stock Exchange of India Limited",
}

# Words a cut-off final word is completed to, most likely first.
COMPLETIONS = ["Limited", "Ltd", "India", "Industries", "International", "Infrastructure", "Investments",
               "Technologies", "Enterprises", "Engineering", "Corporation", "Company", "Services", "Solutions",
               "Management", "Finance", "Financial", "Holdings", "Ventures", "Pharmaceuticals"]
SMALL = {"and", "of", "the", "for", "in", "on", "&"}
CUT_AT = 28  # sources cut at 30 characters; anything this long may have been cut


def _complete(last: str, previous: str) -> str | None:
    word = last.rstrip(".")
    low = word.lower()
    if len(low) < 2 and low != "l":
        return None
    if previous.lower() == "of" and "india".startswith(low):
        return "India"
    for full in COMPLETIONS:
        if full.lower().startswith(low) and full.lower() != low:
            return full
    return None


def _word(w: str) -> str:
    """Capitalises the first letter (after any opening bracket or quote) and lower-cases the rest."""
    out, done = [], False
    for ch in w:
        if ch.isalpha():
            out.append(ch if done else ch.upper())
            if done:
                out[-1] = ch.lower()
            done = True
        else:
            out.append(ch)
            if ch == "-":
                done = False
    return "".join(out)


def _case(name: str) -> str:
    if not (name.isupper() or name.islower()):
        return name
    words = []
    for i, w in enumerate(name.split(" ")):
        lw = w.lower()
        core = re.sub(r"[^a-z]", "", lw)
        if i > 0 and lw in SMALL:
            words.append(lw)
        elif 0 < len(core) <= 3 and core not in ("ltd", "pvt", "co") and (i == 0 or not re.search("[aeiou]", core) or core == "i"):
            words.append(w.upper())  # likely an acronym or initial: ABC Gas, MRF, SBI, (I)
        else:
            words.append(_word(w))
    return " ".join(words)


def clean(name: str | None, isin: str | None = None) -> str | None:
    if isin and isin in OVERRIDES:
        return OVERRIDES[isin]
    if not name:
        return name
    name = re.sub(r"\s+", " ", name).strip()
    # BSE abbreviates long names to a final lone "L" ("KARAMTARA ENGINEERING L").
    if re.search(r" L\.?$", name):
        name = re.sub(r" L\.?$", " LIMITED" if name.isupper() else " Limited", name)
    elif len(name) >= CUT_AT:
        parts = name.split(" ")
        if len(parts) >= 2:
            done = _complete(parts[-1], parts[-2])
            if done:
                if name.isupper():
                    done = done.upper()
                parts[-1] = done
                name = " ".join(parts)
    return _case(name)
