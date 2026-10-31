# Sievr privacy policy

_Last updated: 31 October 2026_

Sievr is a free stock screener for Indian markets. This policy explains what the app does with information.
In short: **Sievr has no accounts, no ads, no analytics and no tracking, and it does not collect or share your
personal data.**

## What stays on your phone

Everything you create in Sievr is stored only on your device, in the app's private storage:

- saved screens and formulas
- watchlists
- portfolio holdings (symbols, quantities and average prices), including any broker CSV file you import
- price and screen alerts
- settings such as theme and notification choices

Sievr never uploads this information. The developer cannot see it.

If Android backup is turned on for your phone, Android may include this file (and only this file — not the
downloaded market data) in your Google backup and when you move to a new phone. That backup is handled by Google
under your Google account settings, not by Sievr.

## What the app downloads

Once a day Sievr downloads a market data file (prices, company financials and shareholding figures for NSE-listed
companies) from GitHub Releases at `github.com/Akrishna87/Sievr`. The request contains nothing about you: no
account, no identifiers, no list of your stocks. Like any web server, GitHub receives your device's IP address and
a basic user-agent string (`Sievr/<version> (Android)`) to deliver the file; GitHub's handling of that is described
in the [GitHub Privacy Statement](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement).

All screening, scoring, valuation, backtesting and alert checks happen on your phone using that file.

## Notifications

Alerts are worked out on your phone after each day's data arrives and shown as local Android notifications. No
push-notification service is used and nothing about your alerts leaves the device. You can turn notifications off in
Android settings at any time.

## Permissions

| Permission | Why |
|---|---|
| Internet, network state | Download the daily market data file |
| Notifications | Show your price, screen and watchlist alerts and the optional daily digest |

Sievr does not ask for your location, contacts, camera, microphone, files or phone identifiers. When you import a
broker CSV, Android's file picker gives Sievr access to that one file only.

## Data collected and shared

None. Sievr does not collect personal or sensitive data, does not share data with third parties, does not sell data,
and contains no third-party advertising or analytics SDKs.

## Children

Sievr is not directed at children under 13 and does not knowingly collect any information from anyone.

## Deleting your data

Uninstalling Sievr, or clearing its storage in Android settings, deletes everything the app has stored.

## Not investment advice

Sievr is a research tool. It is not registered with SEBI as an investment adviser or research analyst and does not
give recommendations.

## Changes and contact

If this policy changes, the new version will be published at this address with a new date. Questions:
open an issue at [github.com/Akrishna87/Sievr/issues](https://github.com/Akrishna87/Sievr/issues).
