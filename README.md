# Wealth

A private, offline Android app to track your **net worth** and **expenses**
across countries and currencies.

Built for people whose financial life spans more than one country — for
example an Indian working in Switzerland with salary and pension in CHF,
savings and investments in INR, and company stock in USD. Everything is
configurable, so it works just as well for any other combination.

> **Status:** early development. Not yet ready for real use.

## Vision

- **One number:** see your total net worth in the currency you choose, with
  breakdowns by country, currency and asset type.
- **Multi-currency by design:** every account keeps its own currency;
  totals are converted with exchange rates you control.
- **Your categories, not ours:** account types (e.g. Pillar 3a, NRE/NRO,
  PPF, EPF, mutual funds) and expense categories ship as sensible defaults
  that you can edit, delete or extend.
- **Private:** data never leaves your phone. No server, no accounts, no
  analytics, no ads. The database is encrypted, the app is locked with
  biometrics or a PIN, and backups are password-encrypted files you own.

## Planned features

**Version 1**
- Multi-currency accounts with a chosen base currency and manual exchange rates
- Assets (bank accounts, pensions, investments, property, gold, cash, …)
  and liabilities (loans, mortgage, credit cards)
- Net worth dashboard
- Expense tracking with monthly summaries by category
- App lock, encrypted database, encrypted backup export/import

**Later**
- Employee stock (RSU/GSU) tracking with vesting schedules
- CSV import of bank statements
- Net worth history and charts
- Recurring entries
- Automatic exchange rates

## Install

APKs will be published on the
[Releases](../../releases) page once v1 is ready.

## Build from source

Requirements: JDK 17 and the Android SDK (Android Studio installs both).

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`.

## Tech

Kotlin · Jetpack Compose (Material 3) · MVVM · Room + SQLCipher · min Android 8.0
