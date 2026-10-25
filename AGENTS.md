# Project rules for AI agents

## Versioning & backwards compatibility

Until the human explicitly declares the app has reached **1.0**, there is **no
backwards-compatibility constraint of any kind**:

- Data schemas, entity names, table layouts, and column types may change freely
  at any time.
- Room migrations are not required — destructive migration is the default.
  Existing local data on the developer's phone is considered disposable.
- Public API names (DAOs, Composables, route names, SharedPreferences keys,
  etc.) may be renamed or removed without deprecation aliases.
- Stored user content, notes, and preferences will be wiped on schema bumps.
  Do not write migration code unless explicitly asked.
- Feature surfaces (tabs, screens, navigation flow) may be reshaped entirely
  whenever a better idea appears. Do not preserve "for compat" UI.
- Do not warn the user about losing local data on each change. They already
  know. Exception: before starting a change that bumps the Room schema
  version, say so up front (one line) so the user can batch such changes.
- Plan for 1.0 (agreed 2026-10-24, not before): export Room schemas, write
  migrations with migration tests, and add JSON data export/import.

Rewriting from scratch is allowed and often preferred over patching when the
mental model has shifted.

The current state is **pre-1.0**.

When the human writes something like "this is 1.0" or "let's freeze 1.0",
flip this section and start treating data + APIs as durable.

## Default delivery flow

After any code change that affects compiled output, the default expected
hand-off is:

1. `./gradlew :app:testDebugUnitTest :app:assembleDebug` (unit tests must pass)
2. `adb install -r app/build/outputs/apk/debug/app-debug.apk`
3. `adb shell am start -n com.mokie.timelogdemo/.MainActivity`
4. Verify the process is alive and no fresh crash logs.

The shortcut command from the user is "装一下手机" / "install to phone" — treat
that as the full sequence above. Do not ask for confirmation between steps.

## Tooling & paths

- JDK: `/Users/mokietales/dev/jdk17/Contents/Home`
- Android SDK: `/Users/mokietales/dev/android-sdk`
- Project lives at `/Users/mokietales/TimeLolg` (owned by `mokietales`, not
  the original `mokie` account). Do not touch `/Users/mokie/...` paths.

## Naming (post 2026-05-20 redesign)

| Concept | Name | 中文 |
|---|---|---|
| Bucket time accumulates into | **Track** | 跑道 |
| One continuous logged time span | **Session** | 记录 |
| Apportionment row | **SessionTrackAllocation** | — |

A Session can apportion its time across multiple Tracks. The DAO enforces
`SUM(allocation.durationMs) == session.durationMs` strictly (no
"unattributed" time).
