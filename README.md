# BlowTorch 2

A MUD client for Android that still works on a modern phone.

BlowTorch was a genuinely good Android MUD client. Then it stopped being
updated in 2018, Android moved on without it, and it quietly became
uninstallable. This fork brings it back: same client, current Android, storage
and backups sorted out, and a fair amount added.

**Commands:** [`docs/user-manual.md`](docs/user-manual.md), also in-app under
**Help** · **Every setting:** [`docs/options-guide.md`](docs/options-guide.md) ·
**Write plugins:** [`docs/plugin-authoring.md`](docs/plugin-authoring.md)

---

## What it does

**Triggers and timers with conditions.** A trigger or a timer can carry a
condition that decides whether it fires at all: *is this other trigger enabled*,
*is that one disabled*, *does a variable equal something*, *does a variable
exist* — combined with and/or. Triggers also live in named groups, and any
trigger can enable or disable a whole group.

Together that is a state machine. A combat set arms itself when you engage and
disarms when you flee. A quest chain arms the next step as each one fires. A
heal timer only fires while a `fighting` variable is set. A trigger's script
action is real Lua as well, with `EnableTrigger`, `EnableTriggerGroup`,
`SetVariable`/`GetVariable`, and creating or deleting triggers at runtime. This
is the part people build whole play styles on.

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

### Commands you will actually use

Typed into the input bar like anything else. These are the ones worth knowing:

| Command | What it does |
|---------|--------------|
| `.run 3n2ew` | Speedwalk. Walks three north, two east, one west, pausing between steps. |
| `.map open` | Show or hide the map overlay. |
| `.map record` | Draw the map from your movement, for worlds that send no room data. |
| `.map find <text>` | Find a room by name and highlight it. |
| `.map goto <room>` | Walk you there along a route the mapper works out. |
| `.timer 30 quaff health` | Run a command after thirty seconds. Name it and you can pause, reset or stop it later. |
| `.loadset combat` | Switch the on-screen button set. Works from a button, so one tap can change the whole pad. |
| `.switch <name>` | Jump to another open connection without disconnecting this one. |
| `.search <text>` | Search the scrollback and jump to the hit. |
| `.gmcp status` | What the world is sending, and which modules you subscribed to. |
| `.gmcp sniff on` | Show every GMCP packet in the window. Useful when a world's data is not doing what you expect. |
| `.wrap` | Let the input bar grow to several lines, for long lines and pasted text. |
| `.kb` | Selection, clipboard and cursor keys for the input bar. |

Dot commands are on by default. `..` on its own turns them off and on, and
prefixing a line with `..` sends a literal leading `.` to the game, for worlds
that use one.

**Everything else** — every command, every argument, and the Lua plugin API —
is in [`docs/user-manual.md`](docs/user-manual.md), which is also available
in-app under **Help**.

---

## Credit

BlowTorch is Daniel Block and Offset Null Entertainment, LLC (2010–2018). The
MUD core, the Lua plugin system, triggers, buttons — all theirs. This fork
exists so that work keeps running on phones people actually own. Same MIT
license, see [`LICENSE`](LICENSE).

Huge thanks to Daniel and Offset Null for building this and releasing it under
MIT. Without that work there would be nothing to keep alive. I am grateful they
made it, and that they left it where someone else could pick it up.

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

A local client. No ads, no analytics, no accounts, no server of mine anywhere.

It makes exactly one connection that is not a MUD you added: an update check
against GitHub, on by default, at most once a day. It is a plain GET of the
public releases page — no identifier, no telemetry, nothing about you in the
request:

```java
conn = (HttpURLConnection) new URL(API_URL).openConnection();
conn.setRequestMethod("GET");
conn.setRequestProperty("Accept", "application/vnd.github+json");
conn.setRequestProperty("User-Agent", "BlowTorch2");
```

The whole of it is in
[`BTLib/src/com/resurrection/blowtorch2/lib/util/UpdateChecker.java`](BTLib/src/com/resurrection/blowtorch2/lib/util/UpdateChecker.java)
— about two hundred lines, worth a read if you would rather verify than take my
word. Turn it off under **Options → Miscellaneous → Check for updates?** and the
app talks to nothing but your MUDs. If you installed from F-Droid, turn it off:
F-Droid updates you already. Test builds never check, whatever the setting says.

| Permission | Needed to play? |
|------------|-----------------|
| Internet (+ foreground service) | Yes, for a live session |
| Notifications (Android 13+) | Useful, for connection state and alerts |
| All files access | No — only to see `/BlowTorch/` in a file manager |
| Display over other apps | No — only if you float a button over the soft keyboard |

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
| `docs/` | Guides; architecture in [`architecture.md`](docs/architecture.md); plugins in [`plugin-authoring.md`](docs/plugin-authoring.md); working rules in [`ORCHESTRATION.md`](docs/ORCHESTRATION.md) |
| `fastlane/`, `metadata/` | Store and F-Droid text |

F-Droid builds the production flavor only — see
[`docs/fdroid.md`](docs/fdroid.md).

**If you are new to the codebase**, start with
[`docs/architecture.md`](docs/architecture.md) (how it is built). **If you are
about to change something**, read [`docs/ORCHESTRATION.md`](docs/ORCHESTRATION.md)
first — it will save you a wrong guess or two.

---

MIT — Offset Null Entertainment, LLC 2010–2018; fork changes under the same
license. Issues on GitHub: Android version, steps to reproduce, and a log or
crash report if you can get one.
