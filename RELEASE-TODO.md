# Release TODO

Things to do in a specific future release. Not a general backlog — only work
that is *blocked until* a version has been out long enough.

## v1.6 — remove dead code

- **`DEAD_KEYS` in `JumpscarePlugin`** (the array, its sweep loop at the end
  of `migrateOnce()`, the `DEAD_V14_MIGRATION_KEY` constant, and this entry).
  It names `mode` (a config item before v1.0), `theme` (replaced by the
  per-asset sources in v1.2) and `customSourceMigrated` (v1.4's flag, written
  to every profile while its migration did nothing). v1.5 sweeps all three
  out; the strings only have to stay in the source long enough for updating
  users — including testers who ran pre-1.0 builds from git — to run v1.5
  once. Safe to delete after v1.5 has been live a while. Profiles that never
  update keep the stray keys, which is harmless since nothing reads them.
  Keep `OLD_THEME_KEY` if the source migration still reads it.

## Background

RuneLite persists every `@ConfigItem` default into the user's profile the
first time a plugin loads (`ConfigManager.setDefaultConfiguration`, called
from `PluginManager.loadDefaultPluginConfiguration` with `override=false` on
every start). So **raising a default never reaches an existing install** —
they keep whatever their first version wrote, and `getConfiguration(...) ==
null` is not a test for "the user never set this". Changing a default in
future means shipping a migration keyed to the old value, as v1.5 does for
`chanceDenominator` (1 in 100000 from v1.0, 1 in 10000 from v1.1).
