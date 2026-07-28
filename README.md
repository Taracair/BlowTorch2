# BlowTorch 2

A MUD client for Android that still works on a modern phone.

BlowTorch was a genuinely good Android MUD client. Then it stopped being
updated in 2018, Android moved on without it, and it quietly became
uninstallable. This fork brings it back: same client, current Android, storage
and backups sorted out, and a fair amount added.

**Commands:** [`docs/user-manual.md`](docs/user-manual.md), also in-app under
**Help** · **Every setting:** [`docs/options-guide.md`](docs/options-guide.md)

---

## What it does

**Triggers that can turn each other on and off.** Triggers live in named groups,
and any trigger can enable or disable a whole group — so a combat set can switch
itself on when you engage and off when you flee, and a quest chain can arm the
next step as each one fires. A trigger's script action is real Lua with access
to `EnableTrigger`, `EnableTriggerGroup`, `SetVariable`/`GetVariable` and the
ability to create or delete triggers at runtime. This is the part people build
whole play styles on.

Triggers can also colour a line, gag it, replace text in it, fire a
notification, raise a toast, set a variable, send commands, or push output into
a separate window — several at once, in order.

**On-screen buttons that hold more than one command.** Swipe a button up, down,
left or right for four more bindings. Hold for another. Accordion buttons expand
into a cluster of related ones. Buttons come in sets you can switch between, and
a button can switch the set itself — so a movement pad becomes a combat pad on
one tap. Optional on-screen hints show what a tile is bound to.

**A mapper that draws itself as you walk.** Follows GMCP `Room.Info` where a
world sends it, or records your movement where it does not. Handles multiple
floors, one-way exits, and exits that do not go where the direction implies —
those get drawn honestly rather than straightened out into a lie. Pathfind to
any room and walk there. It is the newest and most experimental part of the app,
and it is marked as such in the UI.

**Extra text windows.** Route GMCP modules or trigger output into their own
floating or drawer panes — chat in one, vitals in another — each with its own
size, opacity, scroll speed, and its own scrollback that keeps filling while the
window is closed.

**The classic toolkit, intact.** Aliases, named timers with play/pause/reset,
speedwalks, per-connection settings, and the full Lua plugin engine the original
was built around.

**Comfort on a phone.** Growable input bar, scrollback search, text selection
and clipboard, session logging, connection time on the notification, wifi and
wake locks, alert sounds, and an in-app crash log viewer so a bug report can
carry something useful.

### Commands

| Area | Examples |
|------|----------|
| Input | `.wrap`, `.kb` |
| Scrollback | `.search` |
| Mapper | `.map open`, `.map record`, `.map dirs`, … |
| GMCP | `.gmcp status`, `.gmcp sniff`, … |
| Classic | `.run`, `.timer`, `.loadset`, `.switch`, … |

Dot commands are on by default; `..` alone toggles them, and prefixing `..`
sends a literal leading `.` to the game.

---

## Credit

BlowTorch is Daniel Block and Offset Null Entertainment, LLC (2010–2018). The
MUD core, the Lua plugin system, triggers, buttons — all theirs. This fork
exists so that work keeps running on phones people actually own. Same MIT
license, see [`LICENSE`](LICENSE).

For what it is worth: when I went looking for where the bugs came from, every
serious stability problem was inherited, and every one had survived eight years
of people playing on it. The original held up well.

---

## How this is made

One person, and a lot of AI. I use an LLM to write most of the code and docs. I
test on a real phone, I decide what ships, and I read the bug reports.

The process is written down rather than implied.
[`docs/ORCHESTRATION.md`](docs/ORCHESTRATION.md) is the working agreement:
measure on the device before changing anything, instrumentation goes in its own
commit and comes back out, leave the measured number in a comment so nobody
re-measures it, say plainly what was not verified. It also records how the
codebase actually behaves — which binder calls block and which queue, who owns
the window buffer — so the next assistant, or the next human, does not rediscover
it the hard way.

This is my first public repo of any size. Bug reports with steps to reproduce are
worth a great deal.

---

## Privacy and permissions

A local client. It connects to the MUDs you add and nothing else. No ads, no
analytics, no accounts, no server of mine anywhere.

| Permission | Needed to play? |
|------------|-----------------|
| Internet (+ foreground service) | Yes, for a live session |
| Notifications (Android 13+) | Useful, for connection state and alerts |
| All files access | No — only to see `/BlowTorch/` in a file manager |

Two things worth knowing: account notes on launcher rows are stored as plain
text on the device, so leave them blank if you would rather not keep passwords
there; and Lua plugins run with the app's privileges, exactly as in classic, so
only load ones you trust.

**Storage.** Classic assumed it could write anywhere, which newer Android does
not allow. There is now a `/BlowTorch/` folder (`settings/`, `backups/`,
`launcher/`, `session_logs/`, `logs/`) if you grant access, and everything still
works from app storage if you do not, with import and export through the system
picker. Settings saves are atomic, so a crash mid-write cannot leave you with
half a profile. Details in [`docs/FDROID_README.md`](docs/FDROID_README.md).

---

## Coming from classic BlowTorch

Different package id, so Android will not migrate anything for you.

1. Export from the old client, or keep a copy of its XML
2. Install BlowTorch 2
3. Import the server list or settings, or restore a backup
4. Grant All files access only if you want the folder visible

It can sit alongside an old BlowTorch install — the package ids differ.

---

## Building

| Flavor | Application id |
|--------|----------------|
| `production` | `com.resurrection.blowtorch2` |
| `btTest` | `com.resurrection.blowtorch2.test` |

Needs the Android SDK (compileSdk 36, min API 28), NDK r26+, JDK 17, and
`gcc` / `make` for the native LuaJIT build.

```bash
export ANDROID_HOME=/path/to/Android/Sdk
export NDK_HOME=$ANDROID_HOME/ndk/<version>
./build_ndk_libraries.sh
./gradlew :BT_Free:assembleProductionDebug
BT_LOCAL_SIGN=1 ./gradlew :BT_Free:assembleProductionRelease
```

Release APKs are unsigned by default — F-Droid and CI sign their own. Output in
`BT_Free/build/outputs/apk/`.

| Path | What it is |
|------|------------|
| `BTLib/` | Shared library, where nearly all the code lives |
| `BT_Free/` | App module, and the Lua plugins under `assets/` |
| `LuaJIT-2.0.5/` | Native LuaJIT |
| `docs/` | Guides, plus [`ORCHESTRATION.md`](docs/ORCHESTRATION.md) |
| `fastlane/`, `metadata/` | Store and F-Droid text |

F-Droid builds the production flavor only — see
[`docs/fdroid.md`](docs/fdroid.md).

**If you are about to change something**, read
[`docs/ORCHESTRATION.md`](docs/ORCHESTRATION.md) first. It is short, and it will
save you a wrong guess or two.

---

MIT — Offset Null Entertainment, LLC 2010–2018; fork changes under the same
license. Issues on GitHub: Android version, steps to reproduce, and a log or
crash report if you can get one.
