# Data safety: users keep their data through every update

Once real people use the app, their data (often hand-entered, hours of work) is the most valuable thing in
it. Every change must keep existing data and its meaning. If something truly needs a breaking change, stop and
decide it explicitly, with the trade-offs written down; never do it silently.

## Database schema (Room)
- **Export schemas** (`room.schemaDirectory`) and commit them. They are the record of every version.
- **Every version change gets a migration** (auto-migration or manual SQL copied from the exported schema).
  **Never** `fallbackToDestructiveMigration`.
- Additive changes only: new tables, new columns with a default. Don't drop or rename columns that hold data;
  if a field is retired, stop using it and keep the column.
- **Migration test from the oldest version to the latest**, with data in it, validating every step. Run it on
  the JVM (Robolectric + real SQLite; ship the schema JSONs as debug-only assets) so CI runs it on every push.
- Removing a feature: remove the UI and logic, keep the table and the backup fields (old backups still carry
  them; a later version may want them back).

## Meaning of stored values
- Never change what a stored value means: sign conventions (money out negative), what a balance entry's amount
  is, exchange-rate direction, units, time zones of stored days. A changed meaning is a migration of every row,
  not a code tweak.
- **Seed keys are stable.** Default data (categories, types, rules) gets a stable key and a seed version;
  new defaults reach existing installs via the next seed step, which must respect what the user edited or
  deleted (only touch rows still in their default state).
- Dates as epoch day, timestamps as epoch millis.

## Backups
- Version the format. **New versions must still read every older one.**
- With kotlinx.serialization: `ignoreUnknownKeys`, a default for every new field (older backups lack it),
  `@SerialName` instead of renaming a serialized field.
- Keep **one real-shaped backup per format** in test resources and a test that each reads exactly. Freeze the
  previous reader in tests too and check it still reads new backups where that's promised.
- After a restore, run the seed step (an old backup gets newer defaults at once).
- Ask before replacing data ("Replace all data?"), and read/validate the whole file before deleting anything.

## Keys and unreadable data
- Never create a new database key while a database file exists: that silently makes the old data unreadable.
- If the data can't be opened, **set the files aside, never delete**, and offer: restore a backup, try again,
  start fresh (with a confirm).

## Before a release that changes stored data or settings
- Install the last release, create data through the UI, install the new build over it, and check everything
  is still there (automate it: see release-and-upgrades.md).
- Note in the release notes when backups become a format older versions can't open.

## Imports and bulk operations
- Make re-importing the same file a no-op: fingerprint imported rows, remember deleted ones, flag likely
  duplicates. Test "do it twice, nothing changes".
- Never invent data between known points (e.g. balances between two statement dates) unless the user asked
  for a calculation and it's labelled as one.
