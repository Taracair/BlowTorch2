# BlowTorch 2

**Work in progress.** The whole project is still WIP — I do not promise that
everything works perfectly, though I am trying hard. A lot of features and code
have been fixed and put through extensive testing, but a stray bug may still
lurk somewhere. If you hit one, reporting it is the most valuable thing you
can do for me as a player.

BlowTorch was a genuinely good Android MUD client. Then it stopped being
updated in 2018, Android moved on without it, and it quietly became
uninstallable. This fork brings it back: same client, but with many new
features. The [user guide](docs/user-manual.md) is strongly recommended if you
want to understand them.

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

**On-screen buttons that hold more than one command.** Tap for one command;
swipe in any of eight directions (up, down, left, right, and the four corners)
for eight more; hold for a tenth. Accordion buttons expand
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
| `.help` | Lists every dot command in this session. `.help suggest` filters. |
| `.suggest on` | Word chips from recent game text. `.suggest where off` hides the bar; ghost + `.suggest 1`..`8` still work. |
| `.prompt on` | Pins the world's unfinished prompt above the input. |
| `.loadset combat` | Switch the on-screen button set (also from a button). |
| `.clearbuttons` | Hide the button pad until the next `.loadset` (leaves a BACK tile). |
| `.switch <name>` | Jump to another open connection. Bare `.switch` lists names. |
| `.search <text>` | Search scrollback and jump to the hit. |
| `.font +2` / `.width toggle` | Font size and text canvas width without opening Options. |
| `.trigger group off combat` | Disable a whole trigger group (e.g. leave combat scripts). |
| `.alias toggle kk` | Turn one alias on or off. |
| `.timer play heal` | Start a named timer (`pause` / `reset` / `stop` / `info` too). |
| `.run 3n2ew` | Speedwalk with pauses between steps. |
| `.map open` | Show or hide the map. `.map goto <room>` walks a route. |
| `.reconnect` | Drop and open the connection again. |
| `#5 north` | Send the rest of the line five times (`##5 north` = literal `#`). |
| `.kb insert troll` | Put text at the cursor (no send). Useful from a tappable as `.kb insert $word`. |
| `.editbutton` | Show or hide the Edit button (Options → Window). |
| `.editpanel` | Toggle the Edit tools strip (Sel/Cut/… pad). |
| `.sendbutton` | Show or hide the Send button (Options → Window). |
| `.widget add hp ring` | Overlay HP/mana/cooldown on the game window. `.gauge` is the same. |
| `.protocols` | What this world offered vs what you have switched on. |
| `.options` | Open Options, same as the ⋮ menu. |

Dot commands are on by default. `..` on its own turns them off and on, and
prefixing a line with `..` sends a literal leading `.` to the game, for worlds
that use one.

**Everything else** — every command, every argument, and the Lua plugin API —
is in [`docs/user-manual.md`](docs/user-manual.md), which is also available
in-app under **Help**.

---

## Credit

BlowTorch is Daniel Block and Offset Null Entertainment, LLC (2010–2018). The
MUD core, the Lua plugin system, triggers, buttons — all theirs. This fork is
the natural inheritance of that work — my personal gratitude for what they
built, and a slightly desperate attempt to keep it running on newer Android
phones. Same MIT license, see [`LICENSE`](LICENSE).

Huge thanks to Daniel and Offset Null for building this and releasing it under
MIT. Without that work there would be nothing to keep alive. I am grateful they
made it, and that they left it where someone else could pick it up.

---

## How this is made

One person, and a lot of AI. I use an LLM to write most of the code and docs. I
test on a real phone, I decide what ships, and I read the bug reports. 
I definitely listen to your feedback too. 
After all, the client is here for you, not only me.

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
word. Turn it off under the launcher's **⋮ → Check for updates** and the app
talks to nothing but your MUDs. It is app-wide, not per world — which is why it
moved out of a world's Options. If you installed from F-Droid, turn it off:
F-Droid updates you already. Test builds never check, whatever the setting says.

| Permission | Needed to play? |
|------------|-----------------|
| Internet (+ foreground service) | Yes, for a live session |
| Notifications (Android 13+) | Useful, for connection state and alerts |
| All files access | No — only to see `/BlowTorch/` in a file manager |
| Display over other apps | No — only if you float a button over the soft keyboard |

**Speaking out loud.** A trigger or a timer can read a line aloud (the **Speak
Out Loud** action). That is not a permission — it uses the phone's own speech
engine, so BlowTorch carries no voices and gains no ability to listen to
anything. What it does need is one line in the manifest declaring that it wants
to talk to a speech engine at all:

```xml
<queries>
    <intent><action android:name="android.intent.action.TTS_SERVICE" /></intent>
</queries>
```

Since Android 11 an app cannot even see a package it has not declared an
interest in, so without that the engine is invisible and speech fails with
nothing in the log but a line from the package manager. It grants no access to
your data and none to the microphone — this is output only. If nothing is
spoken, the **?** button in the Speak action's editor walks through what to
check and opens Android's own text-to-speech settings, where voices live.

Two things worth knowing: account notes on launcher rows are stored as plain
text on the device, so leave them blank if you would rather not keep passwords
there; and Lua plugins run with the app's privileges, exactly as in classic, so
only load ones you trust.

**Storage.** Classic assumed it could write anywhere, which newer Android does
not allow. There is now a `/BlowTorch/` folder (`settings/`, `backups/`,
`launcher/`, `maps/`, `session_logs/`, `logs/`) if you grant access, and everything still
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
| `LuaJIT-2.0.5/`, `LuaJIT-2.1/` | Native LuaJIT — 2.0.5 for the 32-bit ABI, 2.1 (GC64) for 64-bit |
| `scripts/check.sh` | Everything checkable without a device, in one command — the same thing CI runs |
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
