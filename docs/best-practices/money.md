# Money, currencies and rates

## Storing and calculating
- **Never `Double`/`Float` for money.** Store `Long` minor units + an ISO 4217 currency code.
- Do maths in `BigDecimal`; round with `HALF_EVEN` only at display or aggregation boundaries, and in one place.
- Decimal places come from the currency (`java.util.Currency.defaultFractionDigits`), not a constant 2.
- Validate codes against `Currency.getAvailableCurrencies()`: Android's `Currency.getInstance` accepts any
  three letters.
- Pick one sign convention (e.g. money out negative) and one for debts (stored positive, subtracted in net
  worth), document it, and never change it afterwards (data-safety.md).

## Exchange rates and prices
- Store rates **per date**, as decimal strings, not per transaction, so every account agrees on a day's rate.
- Value the past with that day's rate (latest on or before; earliest as a fallback).
- Downloaded vs typed: keep the source; **a value the user typed always wins** over a download, with an
  explicit "use downloaded value" to switch back.
- Save a downloaded rate on the day it's *for* (a weekend uses Friday's), and parse JSON numbers exactly as
  written (kotlinx.serialization `JsonPrimitive.content`, not a `Double`).
- Something without a rate or price is left out of totals and shown as missing, never valued at 0 or 1.
- Free/unofficial price sources need a fallback and a polite User-Agent; prices belong to the exchange's
  time zone day.

## Displaying
- Format with `android.icu.number.NumberFormatter` in **the currency's home style** (CHF 1’234.50,
  ₹12,34,567.00, $1,234.50), not the phone's region, if users think of amounts that way.
- Never build number text by hand (`toPlainString() + "%"`): use formatter helpers for money, short money
  (chart axes: 1.25M, 12.5L, 1.2Cr) and percentages.
- Formatted text contains non-breaking spaces and ICU-version-dependent grouping characters. Tests normalise
  spaces and match stable substrings.
- Show changes with an arrow and a sign, never colour alone.
- A "hide figures" switch should mask with a fixed length, so the mask doesn't leak the size of the amount.

## Projections and calculations
- Label anything calculated or projected as such ("· calculated", dashed line, "projection").
- Known values always win over calculated ones; calculate forward from the latest known value.
- Compound calculations per period (monthly interest, yearly growth) round each period the same way the bank
  does (usually `HALF_EVEN` per month) and get tests against a worked example.
