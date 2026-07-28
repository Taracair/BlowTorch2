# Working on BlowTorch 2

This file is for whoever works on this code next — an AI assistant, or a human
with no AI at all. It exists because the expensive knowledge in this project is
not the code. It is the handful of facts that took a device, a logcat and two
wrong guesses each to establish.

Two halves:

- **Part 1 — how this codebase is shaped.** Facts that are costly to rediscover
  and easy to get confidently wrong.
- **Part 2 — how to work on it.** The method that produced the fixes in
  `git log`. It is not generic advice; every rule here is in the repo because
  breaking it already cost us a wrong diagnosis.

If you read nothing else, read [Part 2, rule 1](#1-measure-before-you-touch).

---

## Part 1 — How this codebase is shaped

### It is two processes, and that explains most surprises

| | UI process | Service process (`:stellar`) |
|---|---|---|
| Owns | `MainWindow`, `Window`, overlays, the button window Lua | `Connection`, `StellarService`, plugins and their Lua |
| Talks to | the player | the MUD socket |

They communicate over AIDL binder. **The two legs are not symmetric, and this
is the single most misleading thing in the codebase:**

- **UI → service** is *synchronous*. `PluginXCallS` → `Connection.pluginXcallS`
  → `Plugin.xcallS` runs the plugin's Lua **on the calling thread**. Slow Lua on
  that path freezes the UI directly.
- **service → UI** is *queued*. `WindowXCallB` only posts a message to
  `ConnectionHandler` and returns in a few ms. The payload reaches the UI when
  the service main thread gets round to that message.

The consequence bit us for real: `SaveSettings` also only posts, to the *same*
handler. Anything posted before a payload delays that payload by its own full
duration. A button set switch took 1.1s because a settings save was sitting in
front of it in the queue. See `git log --grep="forty-five exceptions"`.

**When something is slow, ask which queue it is waiting in before you look for
slow code.**

### Thread ownership rules that are enforced, not just intended

- **`Window.mBuffer` may only be touched by the UI thread.** `onDraw` walks the
  line list three times per frame and only the first walk is guarded, so a
  mutation from elsewhere is a crash, not a glitch. `Window.warnIfNotUiThread`
  logs a stack trace naming the culprit, once per window.
- **`Connection` legitimately mutates its own `TextTree`s off the UI thread.**
  That is why the barrier lives in `Window` and *not* in `TextTree`. Do not
  "fix" this by adding locks to `TextTree`.
- There is no lock around the buffer on purpose. A lock would pay every frame
  for a race that does not exist.

### The settings tree has two writers with overlapping reach

`ConnectionSettingsIO.buildSettingsPage` nests the main window's `SettingsGroup`
into the root options, and `nestExtraTextUnderWindow` nests the extra-text group
into the window group. That is good for the Options menu and confusing for
serialisation, because both writers walk recursively:

- `WindowTokenParser` owns window keys and writes them inside `<window>`.
- `ConnectionSetttingsParser` owns connection keys and writes them in `<options>`.

Each reaches keys the other owns. They now **skip** foreign keys
(`isWindowOptionKey` / `isConnectionOptionKey`, guarded by
`SettingsOptionKeyOwnershipTest`). They used to throw on every one, which cost
about 45 exceptions and 1.1s per save. If you add an option key, add it to the
right enum and the test will tell you if the two sets collide.

**Not everything with a `SettingsGroup` is persisted.** Extra-text
`WindowToken`s are rebuilt by `ensureSlots()` and never reach
`settings.getWindows()`, so their settings are *not* serialised. Durable
per-slot state belongs in the slot JSON (`ExtraTextSlot`), which is why scroll
speed lives there and not on the token.

### Where errors go

- `BlowTorchLogger.logThrowable` → the error log file the player reads after a
  crash. For failures a player could hit.
- `BlowTorchLogger.logMinor` → logcat only. For routine, locally-visible ones.
- Protocol chatter (GMCP/MCP traces) must **not** go to the error log. It has
  been removed twice.
- `util/AtomicFiles` is the one place for durable writes. Do not hand-roll a
  file write for anything the player cannot reconstruct.

### The Lua layer

Plugins and the button window are Lua, loaded from
`BT_Free/assets/share/lua/5.1/`. `buttonserver.lua` runs in the service,
`buttonwindow.lua` in the UI process. Syntax-check before building — the app
will not tell you:

```sh
luac5.1 -p BT_Free/assets/share/lua/5.1/*.lua
```

Lua gotchas that have already caused bugs here: `1 ~= "1"` (values from the
settings XML arrive as strings), and `WhiteSpace extends Text` in `TextTree`, so
`instanceof Text` catches whitespace too.

### Where the bugs actually come from

818 commits: 487 from the original (2010–2018), then eight years of silence,
then the 2026 revival. **Every serious stability bug was inherited** — the ANR
loop, `wait(5)` in `onDraw`, unbounded `join()`, the settings-save-over-live-file.
The 2026 work introduced bugs *in new features* (mapper, extra-text windows),
not regressions in old code.

The real 2026 signature is subtler: new code copied the surrounding style
including its faults — `printStackTrace` everywhere, protocol traces in the
error log. **Match the surrounding code's idiom, not its mistakes.**

---

## Part 2 — How to work on it

### 1. Measure before you touch

This is the rule the others hang off. In this project, careful code reading has
produced a confident, wrong hypothesis **three times**:

- Settings that "didn't work live" — two rounds of reading gave two wrong
  causes. Logcat probes along the whole path found it in one pass.
- The button-set delay — the obvious suspect (Lua recompiling `button.lua` on
  every switch) measured at 1–8ms and was innocent. The real cause was an
  exception storm in an unrelated settings save.
- The scroll "hot spot" — a `wait(5)` retry loop that looked catastrophic turned
  out to be dead code that had probably never fired.

Reading the code tells you what *could* be slow. Only the device tells you what
*is*. If you are about to optimise something you have not measured, stop.

### 2. Probes are a commit, and they come back out

Put instrumentation in its **own commit**, clearly marked TEMPORARY, so it
reverts cleanly without taking real fixes with it. Then revert it once you have
the numbers.

Careful: if a probe commit also contains a real fix, `git revert` will take the
fix too. Remove probes surgically in that case and verify the fixes survived.

Use `SystemClock.uptimeMillis()`, not `os.clock()` — the latter is CPU time and
hides exactly the blocking I/O you are usually hunting. It is system-wide, so
spans from both processes line up on one logcat timeline.

### 3. Leave the number behind

When a measurement clears something, **write the number into a comment at the
code that looks suspicious.** `BLEED_SEARCH_MAX_LINES = 1000` reads alarming;
the comment saying "measured 9 lines, 2ms over 300 frames on a real MUD" is what
stops the next person spending a session on it.

A measurement that only lives in a commit message will be re-taken.

### 4. Silence is not evidence

A threshold probe that logs nothing is indistinguishable from a probe that never
ran. Emit a heartbeat with a rolling worst case, so "nothing to report" is a
positive result rather than an absence.

### 5. The maintainer runs the device tests

There is one physical phone and it is not yours. So:

- Say exactly what to do: which gesture, which screen, how many times.
- Say what a failure looks like, so it gets reported rather than shrugged off.
- Say which log command to run afterwards.
- Batch it. One build, then one round of testing, beats three.

Do not report something as working when what you mean is that it compiled.

### 6. Correct wrong facts loudly, in place

When a note or a claim turns out to be wrong, do not quietly edit it. Name the
old claim, say it was wrong, say what disproved it. A durable note carrying a
plausible falsehood is worse than no note — the next reader has no reason to
doubt it.

There are two such corrections in the notes already. Both were load-bearing.

### 7. Do not guess mechanisms

If you measured that something costs 3ms, that is a fact. If you then explain
*why* it is only 3ms without checking, that explanation is a guess and it will
be read as fact later. Record the number, mark the mechanism as unverified, or
go and verify it.

### 8. Fix the cause, not the symptom

- Remove the throw; do not just downgrade the log line.
- A wider `catch` in a draw loop turns a real bug into silently dropped frames.
  That is exactly how the mysterious `wait(5)` retry loop came to exist, and
  nobody could explain it eight years later.
- If you are about to make an error quieter, ask whether you are moving the
  symptom away from the cause.

### 9. "Behaviour-preserving" needs an argument

Do not assert it — show it. Good: *"`valueOf` threw before anything was
emitted, so the keys this now skips were never written on that branch; the XML
output is byte-identical."* That is checkable. "This should be safe" is not.

### 10. Prefer barriers to fixes

The leverage is not the next bug fixed, it is the class of bug prevented at the
point of cause. `warnIfNotUiThread`, the `logThrowable`/`logMinor` split,
`AtomicFiles`, `SettingsOptionKeyOwnershipTest`. When you fix something that
could plausibly come back, ask what would have caught it and whether that is
cheap to add.

The maintainer asked for these explicitly, in these words: *safeguards in the
code so the AI does not break it.*

### 11. Know where the repair boundary is

**Fix what has a credible path to a player-visible failure. Leave what does
not — and say that you are leaving it.**

Deliberately not fixed: ~150 `printStackTrace` in dialogs and parsers (the error
is local and visible to whoever triggered it), 162 do-nothing
`catch (RemoteException)`, file streams without try-with-resources on read
paths. This is a decision, not a backlog.

### 12. Stay in scope

Do the task asked. If you find something else, say so and let the maintainer
decide. This project has a long list of known-open items; adding to it
unprompted is not help.

### 13. Tests exist, and they run without Android

`unitTests.returnDefaultValues = true` in `BTLib/build.gradle`, so plain classes
can be instantiated in a JVM test. Tests sit in the same package and can see
`protected` fields. **Check `BTLib/src/test/` before reasoning from the source
alone** — this was discovered late and would have saved time.

```sh
./gradlew :BTLib:testDebugUnitTest --tests '*SomeTest*'
```

---

## Build, install, test

Work on **`staging`**, never directly on `main`.

```sh
# unit tests
./gradlew :BTLib:testDebugUnitTest

# the flavour the maintainer actually runs
./gradlew :BT_Free:assembleBtTestDebug
# -> BT_Free/build/outputs/apk/btTest/debug/BT_Free-btTest-debug.apk

# adb is not on PATH
~/Android/Sdk/platform-tools/adb -s <serial> install -r <apk>
```

The phone is often attached over both USB and wifi at once, so `adb` sees two
devices — **always pass `-s`**. The wifi port changes between sessions; check
`adb devices` rather than trusting a remembered one.

Flavours: `production` = `com.resurrection.blowtorch2`,
`btTest` = `com.resurrection.blowtorch2.test`. They can be installed together.

---

## Questions already answered — do not re-derive these

| Question | Answer |
|---|---|
| Is the `onDraw` bleed scan a performance problem? | No. 9 lines, 2ms worst case over 300 frames on a real MUD. The 1000-line limit only bites on a buffer with no colour at all. |
| Is `wait(5)` in `Window.onDraw` a real hot spot? | No — it was dead code from a threading model that no longer exists. Removed. |
| Does reloading `button.lua` per set switch cost much? | No, 1–8ms measured. |
| Why did Options → Window do nothing until restart? | `SettingsGroup.recursiveListenerUpdate` cleared the listener map on every descent into a subgroup. Fixed. |
| Are extra-text `WindowToken` settings persisted? | No. Use the slot JSON. |
| Does the mapper already parse GMCP `coord`? | Yes, several shapes, behind `mapper_gmcp_use_coords` (default off) with a Chebyshev ≤1 guard. |
| Are the stability bugs from the 2026 AI work? | No. All inherited from 2010–2018. |

---

## A note on using an AI here

The maintainer's terms, and they work: the AI investigates and proposes, the
maintainer runs it on the phone and decides what ships. Reports from the device
are treated as the source of truth over anything derived from reading code.

Two habits carry most of the value. **Ask for a measurement instead of
accepting a plausible story**, and **make the assistant say which parts it did
not verify.** Most bad AI output here has not been wrong code — it has been a
confident explanation of a mechanism nobody checked.
