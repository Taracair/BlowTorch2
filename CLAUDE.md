# CLAUDE.md

**Read [`docs/ORCHESTRATION.md`](docs/ORCHESTRATION.md) before doing anything
else in this repo.** It carries the architecture facts that are expensive to
rediscover and the working rules that produced the fixes in `git log`. This
file is only the short version.

## Non-negotiables

- Work on branch **`staging`**. Never commit directly to `main`.
- **Measure before you optimise.** Code reading has produced a confidently wrong
  hypothesis three times in this project. The device is the authority.
- **The maintainer runs the device tests.** Tell them the exact gesture, what a
  failure looks like, and which log command to run. Never report "works" when
  you mean "compiles".
- Instrumentation goes in its **own commit**, marked TEMPORARY, and comes back
  out — but leave the resulting number in a code comment.
- Say plainly what you did **not** verify.

## Build and deploy

```sh
./gradlew :BTLib:testDebugUnitTest                 # JVM tests, no device needed
./gradlew :BT_Free:assembleBtTestDebug             # the flavour actually tested
~/Android/Sdk/platform-tools/adb -s <serial> install -r \
  BT_Free/build/outputs/apk/btTest/debug/BT_Free-btTest-debug.apk
luac5.1 -p BT_Free/assets/share/lua/5.1/*.lua      # Lua is not checked by the build
```

`adb` is not on PATH. The phone is often on USB *and* wifi at once, so always
pass `-s`; the wifi port changes between sessions.

## The three facts that mislead people most

1. **UI → service binder calls are synchronous; service → UI ones are queued.**
   `WindowXCallB` and `SaveSettings` both post to the same `ConnectionHandler`,
   so whatever is queued first delays the rest.
2. **`Window.mBuffer` is UI-thread only** (`warnIfNotUiThread` enforces it), but
   `Connection` legitimately mutates its own `TextTree`s off it. Do not put
   locks in `TextTree`.
3. **Extra-text `WindowToken` settings are never persisted** — `ensureSlots()`
   rebuilds those tokens. Durable per-slot state goes in the slot JSON.

## Scope

Do the task asked. If you find something else, report it and let the maintainer
decide. There is a known-open list; do not add to it unprompted. The repair
boundary is in `docs/ORCHESTRATION.md` §11 — fix what can reach a player, and
say what you are leaving alone.
