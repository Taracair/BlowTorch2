# CLAUDE.md

**Read [`docs/ORCHESTRATION.md`](docs/ORCHESTRATION.md) before doing anything
else in this repo.** It is the full guide: architecture facts that are expensive
to rediscover, the working method, the device lab, and a catalogue of mistakes
already made here. Then read [`docs/HANDOFF.md`](docs/HANDOFF.md) for where
things stand and what to pick up next. This file is only the short version.

## The ten rules

1. **Measure before you touch.** Reading this code has produced a confident,
   wrong hypothesis at least six times. The device is the authority.
2. **Never `adb uninstall`.** Always `install -r`. Uninstalling destroys the
   maintainer's server list and profiles.
3. **The maintainer runs the device tests.** Give the exact gesture, what a
   failure looks like, and the log command. Never say "works" when you mean
   "compiles".
4. **Say what you did not verify.** Every time.
5. **Instrumentation goes in its own commit, comes back out, and leaves its
   number in a code comment.**
6. **Do not guess mechanisms.** A measurement is a fact; the explanation is a
   guess until checked.
7. **Fix the cause, not the symptom.** Remove the throw rather than quieten the
   log; a wider `catch` moves the symptom away from the cause.
8. **"Behaviour-preserving" needs an argument, not an assertion.**
9. **Prefer barriers to fixes** — the class of bug prevented at the point of
   cause.
10. **Stay in scope.** Report what else you find; let the maintainer decide.

Work on branch **`staging`**, never `main`.

## Build and deploy

```sh
./gradlew :BTLib:testDebugUnitTest                 # JVM tests, no device needed
./gradlew :BT_Free:assembleBtTestDebug             # the flavour actually tested
luac5.1 -p BT_Free/assets/share/lua/5.1/*.lua      # the build does NOT check Lua
~/Android/Sdk/platform-tools/adb -s <serial> install -r \
  BT_Free/build/outputs/apk/btTest/debug/BT_Free-btTest-debug.apk
```

`adb` is not on PATH. The phone is often on USB *and* wifi at once, so always
pass `-s`; the wifi port changes between sessions and `connect` on a stale port
will not recover an `offline` device.

## The facts that mislead people most

1. **UI → service binder calls are synchronous; service → UI ones are queued.**
   `WindowXCallB` and `SaveSettings` both post to the same `ConnectionHandler`,
   so whatever is queued first delays the rest.
2. **`static` fields exist twice** — once per process. A cache invalidated in
   the UI does nothing for `:stellar`, which is where settings I/O runs.
3. **`Window.mBuffer` is UI-thread only** (`warnIfNotUiThread` enforces it), but
   `Connection` legitimately mutates its own `TextTree`s off it. Do not put
   locks in `TextTree`.
4. **Extra-text `WindowToken` settings are never persisted** — `ensureSlots()`
   rebuilds those tokens. Durable per-slot state goes in the slot JSON.
5. **In `buttonwindow.lua`, "nothing selected" is `{}`, not `nil`.** A `== nil`
   check passes it straight through.
6. **Do not move the `MAIN`/`LAUNCHER` intent filter to another component** —
   pinned home screen icons are keyed on the component name.
7. **The main window's text belongs to the service, not the UI.**
   `MainWindow.initWindow` does `tmp.setBuffer(w.getBuffer())` — the UI `Window`
   *adopts* the service-side `WindowToken` buffer. Anything shown to the player
   that is not written into that buffer disappears the next time the windows are
   rebuilt, which is what switching worlds does. Send text via
   `Connection.sendBytesToWindow`, which buffers then notifies;
   `notifyMainWindow` is only for callers that already buffered.
8. **⋮ is structurally above every overlay.** `gameplay_chrome_overlay` is a
   later sibling of `window_container` in `window_layout.xml`, and the overlays
   (frame, mapper, extra text) live inside the container. So "the overlay covers
   ⋮" is a visibility complaint, never a z-order one — and the real hazard is the
   reverse: ⋮ silently takes touches from anything parked under it.

## Evidence

Text pasted from the game window is **not** a network capture — a paste once
appeared to show malformed GMCP that the wire never carried. Use
`logs/gmcp.log` or logcat.
