# Working on BlowTorch 2

For whoever works on this next: an AI assistant, or a human with no AI at all.

The expensive knowledge in this project is not the code. It is a few dozen
facts that each cost a physical phone, a logcat, and usually two wrong guesses
to establish. This file is those facts, and the working method that produced
them.

---

## Part 0 — The ten rules

If you read nothing else, read this. Everything after it is elaboration.

1. **Measure before you touch.** Reading this code has produced a confident,
   wrong hypothesis at least six times. The device is the authority.
2. **Never `adb uninstall`.** Always `install -r`. Uninstalling destroys the
   maintainer's server list and profiles.
3. **The maintainer runs the device tests.** Say the exact gesture, what a
   failure looks like, and which log command to run. Never report "works" when
   you mean "compiles".
4. **Say what you did not verify.** Every time. A confident sentence about
   something unchecked is the most expensive thing you can produce here.
5. **Instrumentation goes in its own commit, comes back out, and leaves its
   number behind in a code comment.** Otherwise it gets re-measured.
6. **Do not guess mechanisms.** A measurement is a fact. The explanation for it
   is a guess until you check it, and a plausible wrong explanation in a durable
   place is worse than no explanation.
7. **Fix the cause, not the symptom.** Removing a throw beats downgrading a log
   line. Widening a `catch` moves the symptom away from the cause.
8. **"Behaviour-preserving" needs an argument, not an assertion.** Show why the
   output is identical. "This should be safe" is not that.
9. **Prefer barriers to fixes.** The leverage is the class of bug prevented at
   the point of cause, not the next bug fixed.
10. **Stay in scope.** Do the task asked. Report what else you found and let the
    maintainer decide.

Work on branch **`staging`**. Never commit directly to `main`.

---

## Part 1 — What this project is

BlowTorch was an Android MUD client by Daniel Block / Offset Null Entertainment,
developed 2010–2018, then abandoned. This is a fork that makes it run on modern
Android. MIT, same as the original.

One maintainer. Most code written by an LLM, all of it tested on a real phone by
a human who decides what ships.

### Where the bugs come from

818 commits: **487 from 2010–2018**, eight years of silence, then **331 in 2026**.

**Every serious stability bug was inherited.** The ANR loop, `wait(5)` in
`onDraw`, unbounded `join()`, settings saved over the live file, the recursive
listener-map clear — all 2012–2013. The 2026 work introduced bugs in *new
features* (mapper, extra text windows), not regressions in old code.

The real signature of the 2026 work is subtler: **new code copied the
surrounding style including its faults** — `printStackTrace` everywhere,
protocol traces dumped into the error log. Match the surrounding code's idiom,
not its mistakes.

### The repair boundary

**Fix what has a credible path to a player-visible failure. Leave what does
not — and say you are leaving it.**

Deliberately not fixed: ~150 `printStackTrace` in dialogs and parsers (the error
is local and visible to whoever triggered it), 162 do-nothing
`catch (RemoteException)`, file streams without try-with-resources on read
paths. That is a decision, not a backlog.

---

## Part 2 — How this codebase is actually shaped

For the full module / package / data-flow map, see
[`architecture.md`](architecture.md) (dated with the architecture snapshot).
This part keeps the **expensive** facts: process asymmetry, thread ownership,
settings writers, and traps that reading the code alone will not teach you.

Facts that are costly to rediscover and easy to get confidently wrong.

### It is two processes

| | UI process | Service process (`:stellar`) |
|---|---|---|
| Owns | `MainWindow`, `Window`, overlays, the button window Lua | `Connection`, `StellarService`, plugins and their Lua |
| Talks to | the player | the MUD socket |

**The two binder legs are not symmetric. This is the single most misleading
thing here.**

- **UI → service is synchronous.** `PluginXCallS` → `Connection.pluginXcallS` →
  `Plugin.xcallS` runs the plugin's Lua **on the calling thread**. Slow Lua
  there freezes the UI directly.
- **service → UI is queued.** `WindowXCallB` posts a message to
  `ConnectionHandler` and returns in a few ms.

`SaveSettings` also only posts, to the **same** handler. Whatever is queued
first delays everything behind it. A button set switch took 1.1s because a
settings save was in front of it in that queue.

**When something is slow, ask which queue it is waiting in before you look for
slow code.**

### Static state is per process

Both processes load the same classes. A `static` field exists **twice**, and
they never see each other's writes.

This caused a real bug: a cache in `SDCardUtils` was invalidated explicitly from
the UI, which did nothing for the service — and settings import/export runs in
the service. **If you cache something static, make it self-correcting (check a
cheap source of truth) rather than relying on being told to clear it.**

### Thread ownership

- **`Window.mBuffer` is UI-thread only.** `onDraw` walks the line list three
  times a frame and only the first walk is guarded, so a mutation from elsewhere
  is a crash, not a glitch. `Window.warnIfNotUiThread` logs a stack trace naming
  the culprit, once per window.
- **`Connection` legitimately mutates its own `TextTree`s off the UI thread.**
  That is why the barrier lives in `Window`, not `TextTree`. Do not "fix" this
  by putting locks in `TextTree`.
- There is deliberately no lock around the buffer. A lock would pay every frame
  for a race that does not exist.
- **Responders run on two different threads**: triggers on the connection
  thread, timers on a timer thread. Anything they share must be local. One
  shared `Matcher` and `StringBuffer` used to sit in responder instance fields.

### The settings tree has two writers with overlapping reach

`ConnectionSettingsIO.buildSettingsPage` nests the main window's `SettingsGroup`
into root options, and `nestExtraTextUnderWindow` nests the extra-text group
into the window group. Good for the Options menu, confusing for serialisation,
because both writers walk recursively:

- `WindowTokenParser` owns window keys, writes them inside `<window>`.
- `ConnectionSetttingsParser` owns connection keys, writes them in `<options>`.

Each reaches keys the other owns. They **skip** foreign keys now
(`isWindowOptionKey` / `isConnectionOptionKey`, guarded by
`SettingsOptionKeyOwnershipTest`). They used to throw on every one: ~45
exceptions and 1.1s per save.

**Not everything with a `SettingsGroup` is persisted.** Extra-text
`WindowToken`s are rebuilt by `ensureSlots()` and never reach
`settings.getWindows()`, so their settings are not serialised. Durable per-slot
state belongs in the slot JSON (`ExtraTextSlot`).

### What is per world and what is global

Getting this wrong is a recurring bug shape.

- **Per world**: maps (`openMapForHost`, keyed on `hostHint`), mapper overlay
  visibility and float geometry, per-connection settings.
- **App-wide**: the update check. It lives in `SharedPreferences`, not a
  connection profile, because whether the app looks for its own updates is a
  property of the install.

Ask "is this about this MUD, or about this app?" before choosing where a
setting lives.

### Where errors go

- `BlowTorchLogger.logThrowable` → the error log file the player reads after a
  crash. For failures a player could hit.
- `BlowTorchLogger.logMinor` → logcat only. Routine, locally-visible failures.
- `BlowTorchLogger.logGmcpTrace` → `logs/gmcp.log`, its own file. Protocol
  chatter must **never** go in the error log; it has been removed from there
  twice, because it rolls the crash history away.
- `util/AtomicFiles` is the one place for durable writes. Do not hand-roll a
  file write for anything a player cannot reconstruct.

### The Lua layer

Plugins and the button window are Lua under `BT_Free/assets/share/lua/5.1/`.
`buttonserver.lua` runs in the service, `buttonwindow.lua` in the UI process.
**The build does not check Lua syntax.** Always:

```sh
luac5.1 -p BT_Free/assets/share/lua/5.1/*.lua
```

Lua traps that have already caused bugs here:

- `1 ~= "1"` — values from the settings XML arrive as strings.
- **"nothing selected" is spelled `{}`, not `nil`**, in `buttonwindow.lua`. A
  `== nil` check passes an empty table straight through. This crashed the touch
  handler on every cancelled gesture.
- `WhiteSpace extends Text` in `TextTree`, so `instanceof Text` catches
  whitespace too.

### Alias and trigger text substitution

Three separate mechanisms, easy to confuse:

- `$1` in a **trigger** action comes from the MUD's output line.
- `$1` in an **alias** comes from what the player typed.
- `${name}` in either comes from a session variable (`SetVariable` / Lua), which
  is how the two worlds connect.

An alias substitutes differently depending on its anchors — see
`AliasExpansion.Mode`. That rule was implicit in a branch inside a 150-line
method for years.

---

## Part 3 — The working method, in full

Every rule here is present because breaking it already cost a wrong diagnosis in
this repo.

### 3.1 Measure before you touch

Code reading has produced confident, wrong hypotheses repeatedly:

- Settings that "didn't work live" — two rounds of reading, two wrong causes.
  Logcat probes along the whole path found it in one pass.
- The button-set delay — the obvious suspect (Lua recompiling `button.lua` every
  switch) measured at 1–8ms and was innocent. The real cause was an exception
  storm in an unrelated settings save.
- The `onDraw` "hot spot" — a `wait(5)` retry loop that looked catastrophic was
  dead code that had probably never fired.
- Direction label placement — **three** wrong fixes before the real cause (both
  directions of a link competing for one midpoint).

Reading tells you what *could* be slow or wrong. Only the device tells you what
*is*. If you are about to optimise something you have not measured, stop.

### 3.2 Probes are a commit, and they come back out

Instrumentation goes in its **own commit**, marked TEMPORARY, so it reverts
cleanly. Then revert it once you have numbers.

If a probe commit also contains a real fix, `git revert` takes the fix too.
Remove probes surgically in that case and verify the fixes survived.

Use `SystemClock.uptimeMillis()`, not `os.clock()` — the latter is CPU time and
hides exactly the blocking I/O you are usually hunting. It is system-wide, so
spans from both processes line up on one logcat timeline.

### 3.3 Leave the number behind

When a measurement clears something, **write the number into a comment at the
code that looks suspicious**. `BLEED_SEARCH_MAX_LINES = 1000` reads alarming;
the comment saying "measured 9 lines, 2ms over 300 frames on a real MUD" is what
stops the next person spending a session on it.

A measurement that lives only in a commit message will be re-taken.

### 3.4 Silence is not evidence

A threshold probe that logs nothing is indistinguishable from a probe that never
ran. Emit a heartbeat with a rolling worst case so "nothing to report" is a
positive result.

The same error in a different costume: **absence of data is not data**. The
mapper's welcome dialog treated "the map snapshot has not arrived yet" as "the
map is empty", and greeted people who had a full map every time they opened it.

### 3.5 The maintainer runs the device tests

One physical phone, and it is not yours.

- Say exactly what to do: which gesture, which screen, how many times.
- Say what a failure looks like, so it gets reported rather than shrugged off.
- Say which log command to run afterwards.
- Batch it. One build then one round of testing beats three.

### 3.6 Correct wrong facts loudly, in place

When a note or claim turns out to be wrong, do not quietly edit it. Name the old
claim, say it was wrong, say what disproved it. A durable note carrying a
plausible falsehood is worse than no note.

There are several such corrections in the git history. All were load-bearing.

### 3.7 Do not guess mechanisms

If you measured 3ms, that is a fact. If you then explain *why* it is only 3ms
without checking, that is a guess and will be read as fact later. Record the
number, mark the mechanism unverified, or go and verify it.

### 3.8 Evidence has a provenance

**Text pasted from the game window is not a network capture.** A payload pasted
by hand appeared to show a server sending malformed JSON. A whole fix was built
on it, and a public claim made that GMCP was broken on that world. The live
logcat showed perfectly well-formed JSON — the mangling happened in the copy.

For what a server really sent, use `logs/gmcp.log` (Options → Service → GMCP →
Log GMCP?) or logcat. Not a paste, not a screenshot.

### 3.9 Fix the cause, not the symptom

- Remove the throw; do not just downgrade the log line.
- A wider `catch` in a draw loop turns a real bug into silently dropped frames.
  That is exactly how the mysterious `wait(5)` retry loop came to exist, and
  nobody could explain it eight years later.
- If you are about to make an error quieter, ask whether you are moving the
  symptom away from the cause.

### 3.10 "Behaviour-preserving" needs an argument

Do not assert it — show it. Good: *"`valueOf` threw before anything was emitted,
so the keys this now skips were never written on that branch; the XML output is
byte-identical."* That is checkable.

Better still: extract the logic, write tests that pass against the **old**
behaviour, then swap the implementation. If the tests pass first try, that is
evidence.

### 3.11 Prefer barriers to fixes

The leverage is the class of bug prevented at the point of cause:

- `Window.warnIfNotUiThread` — logs the culprit's stack trace.
- `logThrowable` vs `logMinor` — one decision about where an error goes.
- `util/AtomicFiles` — one place for durable writes.
- `SettingsOptionKeyOwnershipTest` — two key sets can never silently collide.
- StrictMode on the test build — the device reports UI-thread disk and network
  itself, before anyone complains.

The maintainer asked for these explicitly, in these words: *safeguards in the
code so the AI does not break it.*

### 3.12 Extract, then test, then rewire

Much of this codebase cannot be tested because pure logic lives inside classes
that need Lua and Android to construct. The way in:

1. Find logic with no Android and no Lua in it.
2. Move it to its own class, unchanged.
3. Write tests. **They should pass first try** — that is the proof you did not
   change behaviour.
4. Delegate from the original.

Done so far: `AliasPattern`, `AliasExpansion`, `CaptureSubstitution`,
`VariableSubstitution`, `AnchoredAliasCaptures`. The alias replacement loop is
the standing example of code too tangled to touch safely — chip at it this way.

### 3.13 Read the code that runs before recommending a change to it

A recommendation to add trigger-style conditions to aliases was made from the
data model, which looked symmetric. Reading the code that applies aliases showed
a joined regex over every enabled alias, a second recursive pass, two places
resolving which alias matched, and `doTail`/`eatTail` threaded through both.
The recommendation was withdrawn and something smaller shipped instead.

### 3.14 Explain in examples, not architecture

When the maintainer asks what a change means, answer with a worked example of
what they will see. "Unanchored aliases do not substitute captures" is a
sentence about implementation. "You type `kk goblin` and the game receives
`kill $1` instead of `kill goblin`" is the same fact, usable.

### 3.15 The second attempt is the signal

Fixing the same failure in the same place twice means the first fix was a
guess. **Stop guessing at the third.** Do not open another "maybe this layout /
estimator / flag" commit. Read what the API actually requires — soft-input mode
vs insets, window-manager stacking, the `LayoutParams` type a parent demands,
what a callback may not do — and then write **one** informed fix.

Two commit storms in this repo were exactly successive guesses:

- Three commits in 45 minutes against `editoroptionsdialog.lua` on 30 July
  (`b99bd711`, `23b03263`, `fb8e9bda`) — layout params that neither `luac5.1 -p`
  nor Gradle can type-check.
- Four commits in a row on Mode A "above the keyboard"
  (`ad9f250e`, `65aa3d3f`, `8b2acee3`, `1b6c2ebe`) — each another height
  estimator on `getWindowVisibleDisplayFrame()`. The activity uses
  `windowSoftInputMode="adjustNothing"`, so that frame never shrinks and every
  layer returned 0 by construction. `15fedc99` finally treated IME insets as the
  only authority; `7fe6675f` needed `TYPE_APPLICATION_OVERLAY` because the
  window manager stacks the IME above every application window. Four guesses
  before reading the constraint is this rule failing.

**If the informed fix still fails, stop and ask the maintainer** before a
seventh approach. Check two things out loud:

1. **Do they want what they said?** The request may be impossible under a hard
   platform constraint, or it may conflict with another choice already in the
   app. Say the constraint in plain language; ask whether to change the goal,
   accept degraded behaviour, or pick an approach they approve.
2. **Is what they said what you understood?** Restate the desired player-visible
   behaviour in one worked example. Misread requirements produce the same
   commit storm as misread APIs.

Do not silently redefine the goal to something easier to implement. The
maintainer decides.

---

## Part 4 — The device lab

### Hardware and connection

- **Pixel 9a, GrapheneOS.** `adb` is **not** on PATH:
  `~/Android/Sdk/platform-tools/adb`
- **The wifi ADB port changes constantly.** It has been 5555, 42135, 41721,
  35055. When a device shows `offline`, `adb connect` on the old port will not
  fix it — `adb disconnect` first. **Do not ask the maintainer for the port;
  find it.** The phone advertises host and port over mDNS:

  ```sh
  avahi-browse -rpt _adb-tls-connect._tcp   # =;…;<host>;<address>;<port>;"serial=…"
  adb mdns services                         # adb's own discovery; often empty
  ```

  If mDNS is quiet (phone asleep, different subnet), scan for it:
  `nmap -Pn -T4 --min-rate 2000 --max-retries 1 --open -p 5555,30000-49999 <ip>`
  — about two seconds over the LAN. `scripts/adb-device.sh` does all of this and
  prints a ready serial on stdout, so `-s "$(scripts/adb-device.sh)"` just works;
  it is a local lab tool and is **not** in git, so recreate it if it is missing.
- **The phone is often on USB and wifi at once**, showing two entries. Every
  `adb` command then needs `-s <serial>`, or it fails with "more than one
  device".

### Deployment

```sh
./gradlew :BTLib:testDebugUnitTest            # JVM tests, no device
./gradlew :BT_Free:assembleBtTestDebug        # the flavour actually tested
luac5.1 -p BT_Free/assets/share/lua/5.1/*.lua # the build does NOT do this
cp -f BT_Free/build/outputs/apk/btTest/debug/BT_Free-btTest-debug.apk \
  ../BlowTorch2-btTest-debug.apk

~/Android/Sdk/platform-tools/adb -s <serial> install -r \
  BT_Free/build/outputs/apk/btTest/debug/BT_Free-btTest-debug.apk
```

After a **production release** build (`assembleProductionRelease`), also copy:

```sh
cp -f BT_Free/build/outputs/apk/production/release/BT_Free-production-release.apk \
  ../BlowTorch2-production-release.apk
```

The parent folder (`../` relative to the repo) holds only these two fixed-name
APKs — refresh them; do not leave older builds there.

**Never `adb uninstall`.** `install -r` re-registers a changed manifest just as
well and keeps the data.

Flavours: `production` = `com.resurrection.blowtorch2`,
`btTest` = `com.resurrection.blowtorch2.test`. They install side by side. The
maintainer plays on **btTest**.

### Reading the device

```sh
adb -s <serial> logcat -c                     # clear before a test run
adb -s <serial> logcat -d -s BTPROF           # your own probes
adb -s <serial> logcat -d | grep -A 15 "StrictMode policy violation"
adb -s <serial> logcat -d | grep -E "^.*(BlowTorch|GMCP):"
```

Aggregate StrictMode hits to your own code:

```sh
adb -s <serial> logcat -d | grep -E "at com\.resurrection" \
  | sed 's/.*lib\.//;s/(.*//' | sort | uniq -c | sort -rn
```

Files on the device that survive reinstall:

```
/sdcard/BlowTorch/{settings,backups,launcher,maps,session_logs,logs}/
```

`logs/blowtorch2.log` is the crash log. `logs/gmcp.log` is the protocol trace.

### Things that break the phone experience

- **Do not change which component holds the `MAIN`/`LAUNCHER` intent filter.**
  Launchers key pinned icons on the component name. Moving the entry point from
  `FreeLauncher` to `Launcher` made the maintainer's home screen icon stop
  working while the app was still installed. If a trampoline activity flashes,
  give it `BlowTorch.Invisible` (`windowNoDisplay`) instead of moving it.
- StrictMode is on for the **test flavour only**, `penaltyLog` only. Never
  `penaltyDeath` — that build is the one being played.
- The update check never runs automatically on the test flavour, whatever the
  setting says.

---

## Part 5 — Tests

`unitTests.returnDefaultValues = true` in `BTLib/build.gradle`, so plain classes
can be instantiated in a JVM test. Tests live in the same package and can see
`protected` fields.

```sh
./gradlew :BTLib:testDebugUnitTest --tests '*SomeTest*'
```

**Check `BTLib/src/test/` before reasoning from source alone.** This was
discovered late and would have saved time.

Around 205 tests. They cluster on the mapper, `TextTree`, settings key
ownership, and the alias/responder substitution chain. `Connection`,
`MainWindow`, `Window` and the mapper controllers are effectively uncovered —
which is exactly why Part 3.12 exists.

A test is worth writing when it pins something a human cannot easily see: group
index arithmetic, a chunk boundary, a regex that silently produces the wrong
alternative. It is not worth writing to assert that a getter returns what was
set.

---

## Part 6 — Mistakes already made here

Written down so they are not made again. Each cost real time or real data.

| What happened | What to do instead |
|---|---|
| `adb uninstall` to "re-register the manifest" — destroyed the maintainer's profiles | `install -r`; it does the same job |
| Moved the `MAIN`/`LAUNCHER` filter to another component — the home screen icon died while the app was installed | Leave the component alone; theme the trampoline instead |
| Treated pasted game-window text as proof of what a server sent; built a fix and announced a bug that did not exist | `logs/gmcp.log` or logcat, never a paste |
| Cached a value in a `static` and invalidated it explicitly from one process — the other process kept the stale value | Make the cache check a cheap source of truth |
| Guarded a Lua sentinel with `== nil` when it is `{}` — crashed every cancelled gesture | Test for the fields a real object has |
| Three wrong fixes to label placement, each tuning the collision search | The two labels were competing for one spot; find the cause |
| Recommended a large change to the alias loop without reading it | Read the code that runs before recommending changes to it |
| Explained a fix in terms of implementation, twice, and lost the maintainer | Worked example of what they will see |
| Wrote a plausible mechanism for a measured number without checking it | Record the number; mark the mechanism unverified |
| Four commits guessing Mode A IME height on `getWindowVisibleDisplayFrame` under `adjustNothing` (always 0); a fifth and sixth finally read the API | Second failed attempt → read the API; if that still fails, ask the maintainer whether the goal is what they want and what you understood |

---

## Part 7 — Questions already answered

Do not re-derive these.

| Question | Answer |
|---|---|
| Is the `onDraw` bleed scan a performance problem? | No. 9 lines, 2ms worst case over 300 frames on a real MUD. The 1000-line limit only bites on a buffer with no colour at all. |
| Is `wait(5)` in `Window.onDraw` a real hot spot? | No — dead code from a threading model that no longer exists. Removed. |
| Does reloading `button.lua` per set switch cost much? | No, 1–8ms measured. |
| What made button set switching take 1.1s? | A settings save queued ahead of the payload on the same handler, slow because it threw ~45 exceptions. |
| Why did Options → Window do nothing until restart? | `SettingsGroup.recursiveListenerUpdate` cleared the listener map on every descent into a subgroup. |
| Are extra-text `WindowToken` settings persisted? | No. Use the slot JSON. |
| Does the mapper parse GMCP `coord`? | Yes, several shapes, behind `mapper_gmcp_use_coords` (default off) with a Chebyshev ≤1 guard. |
| Are MSSP and MTTS implemented properly? | Yes, both complete. MSSP is one-way by design; MTTS is the full three-reply TTYPE cycle. |
| Was MSDP complete? | No — transport was correct, but the client could never *ask*. `LIST`/`SEND`/`REPORT`/`UNREPORT`/`RESET` added later. |
| Are the stability bugs from the 2026 AI work? | No. All inherited from 2010–2018. |
| Did that MUD send malformed GMCP? | No. That was a paste artefact. |

---

## Part 8 — If you are a human without an AI

Everything above still applies; the method is not AI-specific. A few
orientation notes:

- **`BTLib/` is the whole app.** `BT_Free/` is a thin module plus the Lua
  plugins in `assets/`.
- **Five classes are over 4000 lines**: `Connection`, `Window`, `MainWindow`,
  `MapperController`, `MapperOverlayController`. They are god classes and known
  to be. Splitting them is *not* recommended as a first task: coverage on them
  is near zero, and a refactor without tests is how the next unexplainable
  `wait(5)` gets written. Follow Part 3.12 instead — extract what is already
  pure, test it, and let the seams appear.
- `Connection` already delegates to `ConnectionAliases`, `ConnectionTimers`,
  `ConnectionExtraText`, `ConnectionSettingsIO`. That pattern works and is the
  cheapest way to continue.
- The mapper is the newest and least-exercised subsystem, and is marked
  experimental in the UI.

---

## A note on running this with an AI

The maintainer's terms, and they work: **the AI investigates and proposes, the
maintainer runs it on the phone and decides what ships.** Reports from the
device are the source of truth over anything derived from reading code.

Three habits carry most of the value:

- **Ask for a measurement instead of accepting a plausible story.**
- **Make the assistant say which parts it did not verify.**
- **After two failed attempts at the same behaviour, demand an API reading —
  and if that still fails, a restatement of the goal before more code**
  (Part 3.15). Successive "Mode A above the keyboard" commits were the cost
  of skipping that.

Most bad AI output here has not been wrong code. It has been a confident
explanation of a mechanism nobody checked, or a third guess at a constraint
nobody read.
