---
name: device-test
description: Writes a Polish on-device test card for the maintainer after a player-visible change. Use when finishing a feature that needs phone verification, after scripts/deploy.sh, or when asking them to tap, type, rotate, or read logcat.
---

# Device test card

One physical phone, and it is not yours. `scripts/deploy.sh` installs; after that it is their hands. Status to the maintainer is Polish. The card is what they will actually do, not a description of the diff.

## After deploy

One build, then **one** round of tests. Do not send three cards for three guesses.

The card must include:

1. Exact gesture / command / screen, and how many times.
2. What success looks like, as a worked example (`kk goblin` → game receives `kill goblin`).
3. What failure looks like, so it gets reported rather than shrugged off.
4. The logcat (or file) to capture **after** the round, if a log is needed. Resolve the serial with `scripts/adb-device.sh` in the command; do not ask for the port.

They play on **btTest** (`com.resurrection.blowtorch2.test`). Crash log: `/sdcard/BlowTorch/logs/blowtorch2.log`. Protocol: `/sdcard/BlowTorch/logs/gmcp.log`.

## Card template

```markdown
## Do sprawdzenia (jedna runda)

1. [gdzie] [gest / komenda].
   Sukces: [co widać / co gra dostaje].
   Porażka: [co widać zamiast tego].

2. …

Po rundzie (tylko jeśli potrzebny log):
`ADB=~/Android/Sdk/platform-tools/adb; SERIAL=$(scripts/adb-device.sh); $ADB -s "$SERIAL" logcat -d -s BTPROF`
```

## Do not

- Say "works". Report "installed", then what **they** still have to try.
- Invent a passing device test.
- Name a live MUD, guild, character, or profile filename.
---
