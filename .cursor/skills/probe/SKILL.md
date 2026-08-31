---
name: probe
description: Adds device logcat instrumentation (BTPROF, TEMPORARY PROBE, SystemClock) so a suspected slow path or wrong mechanism is measured before code changes. Use when adding a probe, timing a path, reading logcat for a hypothesis, or when the user asks to measure before touching code.
---

# Probe

A measurement is a fact. The explanation is a guess until the device has spoken. Do not change the behaviour you are investigating in the same commit as the probe.

## Steps

1. One probe commit, nothing else. Message must contain `probe` or `temporary` (git `commit-msg` rejects `BTPROF` / `TEMPORARY PROBE` inside a real fix).
2. Tag logs `BTPROF`. Mark the site `TEMPORARY PROBE`.
3. Time with `SystemClock.uptimeMillis()`, not `os.clock()` (CPU time; hides blocking I/O). Both processes share that clock, so UI and `:stellar` line up on one logcat.
4. Heartbeat: a threshold probe that prints nothing is indistinguishable from a probe that never ran. Log a rolling worst case so silence is a positive result.
5. `scripts/deploy.sh`. Ask the maintainer to reproduce **once**. Then:

```sh
ADB=~/Android/Sdk/platform-tools/adb
SERIAL=$(scripts/adb-device.sh)
$ADB -s "$SERIAL" logcat -d -s BTPROF
```

Do not ask for the wifi port. Do not cache a serial from an earlier session.

6. Write the number into a comment at the code that looked suspicious. A number that lives only in a commit message will be re-taken.
7. Revert the probe commit. `check.sh` fails if `BTPROF` remains in tracked `*.java` / `*.kt` / `*.lua`. If the probe commit also contains a fix, revert surgically and confirm the fix survived.

## Do not

- Guess the mechanism from the number and ship a fix on that guess.
- Name a live world, host, or profile in the probe or the comment (`world-a`, `a live world`).
- Treat a paste from the game window as a network capture (`logs/gmcp.log` or logcat).
---
