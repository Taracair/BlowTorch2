# BlowTorch 2

A MUD client for Android that still works on a modern phone.

BlowTorch was a genuinely good Android MUD client — triggers, aliases, timers,
on-screen buttons, a real Lua plugin engine. Then it stopped being updated in
2018, and Android moved on without it: storage rules changed, the old APK
stopped installing cleanly, and the thing quietly became unusable.

This is a fork that fixes that. Same client, running on current Android, with
the storage and backup situation sorted out and some things added that I wanted
while playing.

**Package:** `com.resurrection.blowtorch2` · **Repo:**
[Taracair/BlowTorch2](https://github.com/Taracair/BlowTorch2) · **Commands:**
[`docs/user-manual.md`](docs/user-manual.md), also in-app under **Help**

---

## Honestly, about how this is made

One person, and a lot of AI. I use an LLM to write most of the code and docs. I
test everything on a real phone, I decide what ships, and I am the one who reads
the bug reports.

That is not a disclaimer, it is a description of the process — and the process
is written down. [`docs/ORCHESTRATION.md`](docs/ORCHESTRATION.md) is the working
agreement: measure on the device before changing anything, instrumentation goes
in its own commit and comes back out, say plainly what was not verified. It
exists so the next assistant, or the next human, does not have to rediscover how
this codebase actually behaves.

This is my first public repo of any size. Bug reports with steps to reproduce
are worth a great deal.

---

## Credit

BlowTorch is Daniel Block and Offset Null Entertainment, LLC (2010–2018). The
MUD core, the Lua plugin system, triggers, buttons — all theirs. This fork
exists so that work keeps running. Same MIT license, see [`LICENSE`](LICENSE).

For what it is worth: when I went looking for where the bugs came from, every
serious stability problem I found was inherited, and every one of them had
survived eight years of people playing on it. The original held up well.

---

## What you get

Everything classic had: connections, ANSI colour, triggers, aliases, timers,
on-screen buttons, Lua plugins, GMCP.

**Runs on a current phone.** SDK 36, Android 9 and up. Its own package id, so it
can sit beside an old BlowTorch install. Optional test build alongside the real
one.

**Storage that behaves.** Classic assumed it could write anywhere, which newer
Android does not allow. Now there is a `/BlowTorch/` folder (`settings/`,
`backups/`, `launcher/`, `session_logs/`, `logs/`) if you grant access — and if
you do not, everything still works from app storage, with import and export
through the system picker. "All files access" is only about whether you can see
the folder in a file manager. Settings saves are atomic, so a crash mid-write
cannot leave you with half a profile.

**Buttons that do more.** Swipe a button up, down, left or right for different
commands. Hold for another. Accordion buttons expand into a cluster. Optional
on-screen hints so you can see what a tile is bound to.

**A mapper.** Draws as you walk, follows GMCP room data where a world provides
it, handles floors, one-way exits and exits that do not lead where the direction
says. Experimental and openly so — it is the newest part of the app.

**Extra text windows.** Route GMCP modules or trigger output into their own
floating or drawer panes, each with its own size, opacity and scroll speed.

**Session comforts.** Optional session log, connection time on the notification
and launcher rows, wifi and wake locks, alert sounds, a growable input bar, and
an in-app crash log viewer so a bug report can include something useful.

---

## Commands

| Area | Examples |
|------|----------|
| Input | `.wrap`, `.kb` |
| Scrollback | `.search` |
| Mapper | `.map open`, `.map record`, `.map dirs`, … |
| GMCP | `.gmcp status`, `.gmcp sniff`, … |
| Classic | `.run`, `.timer`, `.loadset`, `.switch`, … |

Dot commands are on by default; `..` on its own toggles that, and prefixing `..`
sends a literal leading `.` to the game.

Full list in [`docs/user-manual.md`](docs/user-manual.md); every setting is
described in [`docs/options-guide.md`](docs/options-guide.md).

---

## Privacy and permissions

A local client. It connects to the MUDs you add and nothing else. No ads, no
analytics, no accounts, no server of mine anywhere in the picture.

| Permission | Needed to play? |
|------------|-----------------|
| Internet (+ foreground service) | Yes, for a live session |
| Notifications (Android 13+) | Useful, for connection state and alerts |
| All files access | No — only to see `/BlowTorch/` in a file manager |

Two things worth knowing: account notes on launcher rows are stored as plain
text on the device, so leave them blank if you would rather not keep passwords
there; and Lua plugins run with the app's privileges, exactly as in classic, so
only load ones you trust. Details in
[`docs/FDROID_README.md`](docs/FDROID_README.md).

---

## Coming from classic BlowTorch

The package id is different, so Android will not migrate anything for you.

1. Export from the old client, or keep a copy of its XML
2. Install BlowTorch 2
3. Import the server list or settings, or restore a backup
4. Grant All files access only if you want the folder visible

---

## Building

| Flavor | Application id |
|--------|----------------|
| `production` | `com.resurrection.blowtorch2` |
| `btTest` | `com.resurrection.blowtorch2.test` |

Needs the Android SDK (compileSdk 36), NDK r26+, JDK 17, and `gcc` / `make` for
the native LuaJIT build.

```bash
export ANDROID_HOME=/path/to/Android/Sdk
export NDK_HOME=$ANDROID_HOME/ndk/<version>
./build_ndk_libraries.sh
./gradlew :BT_Free:assembleProductionDebug
BT_LOCAL_SIGN=1 ./gradlew :BT_Free:assembleProductionRelease
```

Release APKs are unsigned by default — F-Droid and CI sign their own. Output
lands in `BT_Free/build/outputs/apk/`.

| Path | What it is |
|------|------------|
| `BTLib/` | Shared library, where nearly all the code lives |
| `BT_Free/` | App module and the Lua plugins in `assets/` |
| `LuaJIT-2.0.5/` | Native LuaJIT |
| `docs/` | Guides, and [`ORCHESTRATION.md`](docs/ORCHESTRATION.md) if you are working on the code |
| `fastlane/`, `metadata/` | Store and F-Droid text |

F-Droid builds the production flavor only — see [`docs/fdroid.md`](docs/fdroid.md).

**If you are about to change something**, read
[`docs/ORCHESTRATION.md`](docs/ORCHESTRATION.md) first. It is short, and it will
save you rediscovering things like which binder calls block and which queue.

---

MIT — Offset Null Entertainment, LLC 2010–2018; fork changes under the same
license. Issues on GitHub: Android version, steps to reproduce, and a log or
crash report if you can get one.
