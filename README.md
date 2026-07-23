# BlowTorch 2

Hello folks and beware! This is my first public repo and first bigger github work.

I present to you - revamped MUD client for Android. Fork of classic [BlowTorch](https://github.com/blockda/BlowTorch) by Daniel Block / Offset Null Entertainment (MIT).

The old client still works as a MUD app, this fork is mainly about running on current Android, fixing storage/backups, and a few extras I wanted while playing.

**Package:** `com.resurrection.blowtorch2`  
**Repo:** [Taracair/BlowTorch2](https://github.com/Taracair/BlowTorch2)  
**Commands:** [`docs/user-manual.md`](docs/user-manual.md) (also in-app **Help**)

I use LLM for writing code or docs. I still test and decide what ships.

---

## Credit

BlowTorch itself is Daniel Block and Offset Null Entertainment, LLC (2010–2018). The MUD core, Lua plugins, triggers, and so on are theirs. This fork exists so that stuff keeps working when the old APK does not. Same MIT license — see [`LICENSE`](LICENSE).

One maintainer. Bug reports with steps to reproduce help.

---

## What’s different from classic

Same basics: connections, ANSI, triggers, aliases, timers, buttons, Lua, GMCP.

**Android**
- SDK 36, min Android 9 (API 28)
- Own package id (can sit next to an old BlowTorch install)
- Optional test APK beside production
- In-app crash/log viewer

**Storage**
Classic assumed open shared storage. That broke on newer Android.

- Shared folder `/BlowTorch/` (`settings/`, `backups/`, `launcher/`, `session_logs/`, `logs/`) when you allow it
- “All files access” is optional — only if you want that folder visible in a file manager. Without it, files stay in app storage; import/export still works via the system picker
- Launcher: import/export server list, zip backup/restore
- Session: export/import settings

More detail: [`docs/FDROID_README.md`](docs/FDROID_README.md).

**Input / chrome**
- Edit / Send; Edit opens select/cut/copy/paste and arrow keys
- Growable input bar (Options → Input, or `.wrap`)
- Overflow menu (⋮): Edit buttons, Crash report, About, Help, disconnect/reconnect, import/export
- Dialogs use Cancel on the left, Done on the right

**Buttons**
- Swipe up/down/left/right → different commands
- Optional hold command
- Accordion (one button expands into more)
- Optional on-screen gesture hints
- Edit mode: ⋮ → Edit buttons, or long-press ⋮

**Session**
- Optional session log to file
- Connection time on the notification and launcher rows
- Wi‑Fi / wake lock; battery-optimization helper
- Alert sounds: system default, bundled, or pick a file

**Commands**
| Area | Examples |
|------|----------|
| Input | `.wrap`, `.kb` |
| Scrollback | `.search` |
| Mapper | `.map open`, `.map record`, `.map dirs`, … |
| GMCP | `.gmcp status`, sniff, sniff tail, … |
| Classic | `.run`, `.timer`, `.loadset`, `.switch`, … |

Full list: [`docs/user-manual.md`](docs/user-manual.md). Options: [`docs/options-guide.md`](docs/options-guide.md).

**Other**
- Larger editors; account notes on launcher rows (plain text on device — leave blank if you don’t want passwords stored)
- Lua plugins have the same power as classic: they run with the app’s privileges. Only load ones you trust. See [`docs/FDROID_README.md`](docs/FDROID_README.md).

---

## Permissions (short)

Local client. Connects only to MUDs you add. No ads, no analytics, no project cloud.

| Permission | Need it to play? |
|------------|------------------|
| Internet (+ foreground service) | Yes, for a live session |
| Notifications (Android 13+) | Useful for connection / alerts |
| All files access | No — only for a visible `/BlowTorch/` folder |

Full table: [`docs/FDROID_README.md`](docs/FDROID_README.md).

---

## Migrating from classic

Different package id — Android will not move settings for you.

1. Export from the old client (or copy the XML if you still have it)
2. Install BlowTorch 2
3. Import server list / settings / restore backup
4. Grant All files access only if you want `/BlowTorch/` in a file manager

---

## Dot commands (short list)

Period commands are on by default. `..` alone toggles that. Prefix `..` to send a leading `.` to the game.

| Command | Role |
|---------|------|
| `.wrap [on\|off]` | Growable input bar |
| `.kb` / `.keyboard` | Select, clipboard, cursor |
| `.search …` | Scrollback search |
| `.gmcp …` | GMCP helpers |
| `.run <dirs>` | Speedwalk |
| `.timer …` | Named timers |
| `.loadset` / `.clearbuttons` | Button sets |
| `.switch <name>` | Switch open connection |
| `.disconnect` / `.reconnect` | Local notices (use the menu to really disconnect) |

---

## Build notes

| Flavor | Application id |
|--------|----------------|
| `production` | `com.resurrection.blowtorch2` |
| `btTest` | `com.resurrection.blowtorch2.test` |

Needs Android SDK (compileSdk 36), NDK r26+, JDK 17, `gcc` / `make`.

```bash
export ANDROID_HOME=/path/to/Android/Sdk
export NDK_HOME=$ANDROID_HOME/ndk/<version>
./build_ndk_libraries.sh
./gradlew :BT_Free:assembleProductionDebug
BT_LOCAL_SIGN=1 ./gradlew :BT_Free:assembleProductionRelease
```

Release APKs are unsigned by default (F-Droid/CI sign their own). Output: `BT_Free/build/outputs/apk/`.

| Path | Role |
|------|------|
| `BTLib/` | Shared library |
| `BT_Free/` | App module |
| `LuaJIT-2.0.5/` | Native LuaJIT |
| `fastlane/metadata/` | Store / F-Droid text |
| `docs/` | Public guides: [`user-manual.md`](docs/user-manual.md), [`options-guide.md`](docs/options-guide.md), F-Droid notes |
| `metadata/` | Example F-Droid recipe |

F-Droid: production flavor only. See [`docs/fdroid.md`](docs/fdroid.md).

MIT — Offset Null Entertainment, LLC 2010–2018; fork changes under the same license.

Issues: GitHub Issues. Include Android version, steps, and a log or Crash report if you can.
