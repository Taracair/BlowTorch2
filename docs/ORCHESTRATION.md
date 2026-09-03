# Working on BlowTorch 2

For whoever works on this next: an AI assistant, or a human with no AI at all.

The expensive knowledge in this project is not the code. It is a few dozen facts
that each cost a physical phone, a logcat, and usually two wrong guesses. Those
facts are in `CODEBASE-TRAPS.md`. **This file is the method that produced them.**

What is mechanically enforced is in `GUARDRAILS.md`, and is deliberately not
repeated here.

---

## What this project is

BlowTorch was an Android MUD client by Daniel Block and Offset Null
Entertainment, developed 2010 to 2018, then abandoned. This is a fork that makes
it run on modern Android. MIT, same as the original.

One maintainer. Most code written by an LLM, all of it tested on a real phone by
a human who decides what ships.

### Where the bugs come from

818 commits at the time of writing: **487 from 2010 to 2018**, eight years of
silence, then **331 in 2026**.

**Every serious stability bug was inherited.** The ANR loop, `wait(5)` in
`onDraw`, unbounded `join()`, settings saved over the live file, the recursive
listener-map clear: all 2012 to 2013. The 2026 work introduced bugs in *new
features* (mapper, extra text windows), not regressions in old code.

The real signature of the 2026 work is subtler: **new code copied the surrounding
style including its faults**, `printStackTrace` everywhere, protocol traces
dumped into the error log. Match the surrounding code's idiom, not its mistakes.

### The repair boundary

**Fix what has a credible path to a player-visible failure. Leave what does not,
and say you are leaving it.**

Deliberately not fixed: about 150 `printStackTrace` in dialogs and parsers (the
error is local and visible to whoever triggered it), 162 do-nothing
`catch (RemoteException)`, file streams without try-with-resources on read paths.
That is a decision, not a backlog.

---

## The method

Every rule here is present because breaking it already cost a wrong diagnosis in
this repo. The short list is in `CLAUDE.md`; this is the elaboration.

### Measure before you touch

Code reading has produced confident, wrong hypotheses repeatedly:

- Settings that "didn't work live": two rounds of reading, two wrong causes.
  Logcat probes along the whole path found it in one pass.
- The button-set delay: the obvious suspect (Lua recompiling `button.lua` every
  switch) measured at 1 to 8ms and was innocent. The real cause was an exception
  storm in an unrelated settings save.
- The `onDraw` "hot spot": a `wait(5)` retry loop that looked catastrophic was
  dead code that had probably never fired.
- Direction label placement: **three** wrong fixes before the real cause, both
  directions of a link competing for one midpoint.

Reading tells you what *could* be slow or wrong. Only the device tells you what
*is*. If you are about to optimise something you have not measured, stop.

### Probes are a commit, and they come back out

Instrumentation goes in its **own commit**, marked TEMPORARY, so it reverts
cleanly. Then revert it once you have numbers. The pre-commit hook will stop a
probe riding along inside a real fix; if the commit really is the probe, say so
in the message.

If a probe commit also contains a real fix, `git revert` takes the fix too.
Remove probes surgically in that case and verify the fixes survived.

Use `SystemClock.uptimeMillis()`, not `os.clock()`. The latter is CPU time and
hides exactly the blocking I/O you are usually hunting. It is system-wide, so
spans from both processes line up on one logcat timeline.

### Leave the number behind

When a measurement clears something, **write the number into a comment at the
code that looks suspicious**. `BLEED_SEARCH_MAX_LINES = 1000` reads alarming; the
comment saying "measured 9 lines, 2ms over 300 frames on a real MUD" is what
stops the next person spending a session on it.

A measurement that lives only in a commit message will be re-taken.

### Comments are facts, not essays

Default is no comment. A useful comment is 1–2 lines that will still be true
after the next edit: a measured number, an API quirk, a compatibility
constraint, or "not measured". Narrating the implementation, restating the
function name, and "this used to do X" are how a wrong hypothesis becomes
durable (rule 3).

When editing, condense only comments on the code you are already changing.
Do not sweep the tree unless asked. Stale `TODO`/`FIXME` markers are a size
signal, not a backlog: do not delete them in passing unless you have checked
they are false.

### Silence is not evidence

A threshold probe that logs nothing is indistinguishable from a probe that never
ran. Emit a heartbeat with a rolling worst case so "nothing to report" is a
positive result.

The same error in a different costume: **absence of data is not data**. The
mapper's welcome dialog treated "the map snapshot has not arrived yet" as "the
map is empty", and greeted people who had a full map every time they opened it.

### The maintainer runs the device tests

One physical phone, and it is not yours. `scripts/deploy.sh` builds and installs;
after that it is the maintainer's hands.

- Say exactly what to do: which gesture, which screen, how many times.
- Say what a failure looks like, so it gets reported rather than shrugged off.
- Say which log command to run afterwards.
- Batch it. One build then one round of testing beats three.

### Correct wrong facts loudly, in place

When a note or claim turns out to be wrong, do not quietly edit it. Name the old
claim, say it was wrong, say what disproved it. A durable note carrying a
plausible falsehood is worse than no note. There are several such corrections in
the git history and all were load-bearing.

### Evidence has a provenance

Text pasted from the game window is not a network capture. The full story is in
`CODEBASE-TRAPS.md`; the short version is that a paste artefact once produced a
whole fix and a public claim about a bug that did not exist.

### Extract, then test, then rewire

Much of this codebase cannot be tested because pure logic lives inside classes
that need Lua and Android to construct. The way in:

1. Find logic with no Android and no Lua in it.
2. Move it to its own class, unchanged.
3. Write tests. **They should pass first try**, which is the proof you did not
   change behaviour.
4. Delegate from the original.

Done so far: `AliasPattern`, `AliasExpansion`, `CaptureSubstitution`,
`VariableSubstitution`, `AnchoredAliasCaptures`. The alias replacement loop is the
standing example of code too tangled to touch safely. Chip at it this way.

This is also the honest answer to "should we refactor the god classes". Not
directly, and not for tidiness. Extract what is already pure, test it, and let
the seams appear.

### Read the code that runs before recommending a change to it

A recommendation to add trigger-style conditions to aliases was made from the
data model, which looked symmetric. Reading the code that applies aliases showed
a joined regex over every enabled alias, a second recursive pass, two places
resolving which alias matched, and `doTail`/`eatTail` threaded through both. The
recommendation was withdrawn and something smaller shipped instead.

### The second attempt is the signal

Fixing the same failure in the same place twice means the first fix was a guess.
**Stop guessing at the third.** Do not open another "maybe this layout /
estimator / flag" commit. Read what the API actually requires: soft-input mode
versus insets, window-manager stacking, the `LayoutParams` type a parent demands,
what a callback may not do. Then write **one** informed fix.

Two commit storms in this repo were exactly successive guesses:

- Three commits in 45 minutes against `editoroptionsdialog.lua` on 30 July
  (`b99bd711`, `23b03263`, `fb8e9bda`), layout params that neither `luac5.1 -p`
  nor Gradle can type-check.
- Four commits in a row on Mode A "above the keyboard" (`ad9f250e`, `65aa3d3f`,
  `8b2acee3`, `1b6c2ebe`), each another height estimator on
  `getWindowVisibleDisplayFrame()`. The activity uses
  `windowSoftInputMode="adjustNothing"`, so that frame never shrinks and every
  layer returned 0 by construction. `15fedc99` finally treated IME insets as the
  only authority; `7fe6675f` needed `TYPE_APPLICATION_OVERLAY` because the window
  manager stacks the IME above every application window.

**If the informed fix still fails, stop and ask the maintainer** before a seventh
approach. Check two things out loud:

1. **Do they want what they said?** The request may be impossible under a hard
   platform constraint, or conflict with another choice already in the app. Say
   the constraint in plain language; ask whether to change the goal, accept
   degraded behaviour, or pick an approach they approve.
2. **Is what they said what you understood?** Restate the desired player-visible
   behaviour in one worked example.

Do not silently redefine the goal to something easier to implement.

### Explain in examples, not architecture

"Unanchored aliases do not substitute captures" is a sentence about
implementation. "You type `kk goblin` and the game receives `kill $1` instead of
`kill goblin`" is the same fact, usable by someone holding a phone.

---

## Tests

`unitTests.returnDefaultValues = true` in `BTLib/build.gradle`, so plain classes
can be instantiated in a JVM test. Tests live in the same package and can see
`protected` fields.

```sh
scripts/check.sh                                          # everything, what CI runs
./gradlew :BTLib:testDebugUnitTest --tests '*SomeTest*'   # one area
```

**Check `BTLib/src/test/` before reasoning from source alone.** This was
discovered late and would have saved time.

Around 205 tests. They cluster on the mapper, `TextTree`, settings key ownership,
and the alias/responder substitution chain. `Connection`, `MainWindow`, `Window`
and the mapper controllers are effectively uncovered, which is exactly why
extract-then-test exists.

A test is worth writing when it pins something a human cannot easily see: group
index arithmetic, a chunk boundary, a regex that silently produces the wrong
alternative. It is not worth writing to assert that a getter returns what was set.

---

## The device lab

**Pixel 9a, GrapheneOS.** `adb` is not on PATH:
`~/Android/Sdk/platform-tools/adb`.

The wifi ADB port changes constantly and the phone is often on USB and wifi at
once. All of that is handled by `scripts/adb-device.sh`, which prints a ready
serial on stdout. **Do not ask the maintainer for the port** and do not cache one
from a previous session; run the script.

```sh
scripts/deploy.sh          # test, lua, build btTest debug, resolve serial, install -r
```

Reading the device:

```sh
ADB=~/Android/Sdk/platform-tools/adb
SERIAL=$(scripts/adb-device.sh)
$ADB -s "$SERIAL" logcat -c                      # clear before a test run
$ADB -s "$SERIAL" logcat -d -s BTPROF            # your own probes
$ADB -s "$SERIAL" logcat -d | grep -A 15 "StrictMode policy violation"
$ADB -s "$SERIAL" logcat -d | grep -E "^.*(BlowTorch|GMCP):"
```

Aggregate StrictMode hits to your own code:

```sh
$ADB -s "$SERIAL" logcat -d | grep -E "at com\.resurrection" \
  | sed 's/.*lib\.//;s/(.*//' | sort | uniq -c | sort -rn
```

Files on the device that survive reinstall:

```
/sdcard/BlowTorch/{settings,backups,launcher,maps,session_logs,logs}/
```

`logs/blowtorch2.log` is the crash log, `logs/gmcp.log` the protocol trace.

Flavours: `production` is `com.resurrection.blowtorch2`, `btTest` is
`com.resurrection.blowtorch2.test`. They install side by side. The maintainer
plays on **btTest**. StrictMode is on for the test flavour only, `penaltyLog`
only. The update check never runs automatically on the test flavour, whatever the
setting says.

---

## If you are a human without an AI

Everything above still applies; the method is not AI-specific. A few orientation
notes:

- **`BTLib/` is the whole app.** `BT_Free/` is a thin module plus the Lua plugins
  in `assets/`.
- **Five classes are over 4000 lines**: `Connection`, `Window`, `MainWindow`,
  `MapperController`, `MapperOverlayController`. They are god classes and known
  to be. Splitting them is *not* recommended as a first task: coverage on them is
  near zero, and a refactor without tests is how the next unexplainable `wait(5)`
  gets written.
- `Connection` already delegates to `ConnectionAliases`, `ConnectionTimers`,
  `ConnectionExtraText`, `ConnectionSettingsIO`. That pattern works and is the
  cheapest way to continue.
- The mapper is the newest and least-exercised subsystem, and is marked
  experimental in the UI.

---

## A note on running this with an AI

The maintainer's terms, and they work: **the AI investigates and proposes, the
maintainer runs it on the phone and decides what ships.** Reports from the device
are the source of truth over anything derived from reading code.

Three habits carry most of the value:

- **Ask for a measurement instead of accepting a plausible story.**
- **Make the assistant say which parts it did not verify.**
- **After two failed attempts at the same behaviour, demand an API reading, and
  if that still fails, a restatement of the goal before more code.**

Most bad AI output here has not been wrong code. It has been a confident
explanation of a mechanism nobody checked, or a third guess at a constraint
nobody read.

A fourth habit was added later, after the working agreement itself had grown to
about 25 KB of always-loaded rules across three files that had quietly diverged:
**anything a script can check should be a script, and must then be deleted from
the prose.** See `GUARDRAILS.md`. An instruction the model has to remember on
turn forty competes with everything else in the window. An exit code does not.
