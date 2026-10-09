# Reading bank statements reliably and privately

## Privacy first
- Real statements never enter the repo, test resources, issues, logs or chat. Unit tests use **invented**
  statements in the real layout.
- To check a parser against a real file, run it locally with **count-only output** (rows read, rows flagged,
  totals add up: yes/no). For a new layout, log the extracted text with **every digit masked**. Delete every
  temporary copy (device files, logcat) afterwards.

## Design
- Parsers work on plain text (one per layout), so they're pure functions and fast to test. File reading
  (PDF, CSV, .xlsx) is a separate layer.
- **Rows must add up** to the statement's own totals (opening + in − out = closing, or the summary's debit and
  credit totals). If they don't, flag every row for checking rather than guessing. Allow only the rounding the
  issuer documents (e.g. ±1 on a rounded "total due").
- Use the running balance to decide direction when columns are ambiguous; use text positions when only the
  column says debit vs credit.
- Snapshot files (holdings exports) aren't merged: the latest date is the value; re-uploading a date replaces
  it.
- Give the parser hints for the account guess (issuer name, account type) instead of guessing from currency
  alone.
- Keep every row on import and let categories decide what counts; "don't import" rules hide data and surprise
  people later.
- Re-importing must be safe: fingerprints, remembered deletions, possible-duplicate detection (data-safety.md).

## Getting text out of files
- Android 15+ has PDF text extraction built in (`PdfRenderer` pages `getTextContents()`); Android 12–14 via
  the Play system update extension 13. Otherwise ask for CSV. *(time-sensitive)*
- `getTextContents()` gives a page as one block without positions; `searchText(query)` gives boxes
  (top-down coordinates, in points). Use positions sparingly (header columns found once, reused).
- Text quirks seen in real PDFs: currency signs extracted as letters, rows wrapping before/after the amount,
  narration wrapping mid-word, page headers/footers between rows, dates just outside the period.
- PDFs drawn with Android's `PdfDocument` extract one glyph per line; build PDF test fixtures from text runs.
- `.xlsx` is a zip of XML: `java.util.zip` + `javax.xml` read it without a library.
- CSV: auto-detect columns but let the user correct the mapping.
