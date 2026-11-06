---
name: extract-then-test
description: Extracts Android-free, Lua-free logic into its own class, pins current behaviour with JVM tests that must pass on the first run, then delegates. Use when covering Connection, Window, MainWindow, mapper, alias/trigger substitution, or when the user asks to extract then test / behaviour-preserving refactor.
---

# Extract, then test, then rewire

God classes cannot be unit-tested because construction needs Lua and Android. Do not split them for tidiness. Pull out logic that is already pure, pin it, then delegate.

Check `BTLib/src/test/` **before** reasoning from source. A test that already pins the behaviour is the advisor; do not extract a second copy.

## Two jobs

**Move, same output.** Follow the steps below. The tests pass on the first run, and that pass is the proof the move changed nothing.

**Change behaviour.** Name the player-visible slice, not the whole class. Take the expected result from the requirement or from something observed, not from the return value of the method about to be edited. Green before the edit means the old code already does that. Red on the old code is the bug; leave the assertion alone.

Do not construct `Connection`, `Window`, or `MainWindow` in a JVM test. Pull the pure slice, or test a helper that already exists.

## Steps

1. Find logic with **no** Android and **no** Lua in it. If it touches `Context`, binder, LuaState, or views, stop — this skill does not apply.
2. Move it to its own class **unchanged** (same package is fine; tests live in `BTLib/src/test/` and can see `protected`).
3. Write tests against that class. They must pass **first try**. That is the proof you did not change behaviour. `unitTests.returnDefaultValues = true` is already on in `BTLib/build.gradle`.

```sh
./gradlew :BTLib:testDebugUnitTest --tests '*TheNewClass*'
```

4. Only then: one-line (or few-line) delegate from the original. That commit is separate from the extract if the extract is large enough to revert on its own.

Standing example: `AliasPattern` (and `AliasExpansion`, `CaptureSubstitution`, `VariableSubstitution`, `AnchoredAliasCaptures`) pulled out of plugin/alias code so group-index arithmetic could be tested. The alias replacement loop is still too tangled to extract — chip at a pure piece, do not boil the loop.

## Do not

- Rewrite while extracting. A behaviour change is a later edit, after the move is green.
- Copy the current return value into an assertion when changing behaviour. Take the expected result from the requirement or from something observed. Red on the old code is the bug.
- Claim "behaviour-preserving" without the first-try pass (or without showing why the output is identical).
- Extract because a change "touches 3+ files". File count is not a trigger here.
---
