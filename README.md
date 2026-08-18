# BlowTorch 2

An Android client for MUDs — text worlds you play over the network. You pick
a host, connect, type commands, and read what the game sends back: rooms,
combat, chat, the lot. No graphics engine; the screen is the game's text,
plus whatever you put on it (buttons, a map, an HP bar).

This is a fork of BlowTorch (2010–2018). The original stopped getting
updates, Android moved on, and it quietly became uninstallable. Same client
underneath, running again, with a lot built for a phone on top. The
[user guide](docs/user-manual.md) is the full picture (also in-app under
**Help**). Settings: [`docs/options-guide.md`](docs/options-guide.md). Lua
plugins: [`docs/plugin-authoring.md`](docs/plugin-authoring.md).

**Work in progress.** A lot has been tested on a real phone, but I do not
promise it is perfect. If you hit a bug, a report with steps is the most
useful thing you can send.

---

## On the phone

**Buttons.** Tap for one command; swipe in any of eight directions for eight
more; hold for another. Accordion tiles fan out a cluster (pin existing
buttons onto MORE). Sets switch with `.loadset`, including from a button, so
a movement pad becomes a combat pad on one tap.

**Gauges.** A small HP / mana / cooldown on the game window (`.widget` /
`.gauge`). Point it at GMCP vitals, a score-line regex, or a timer.

**Tap the text.** Worlds that mark exits and items (MXP, OSC 8, or your own
Tappable Word triggers) send a command on a tap. Hold where several words
sit close together and a loupe lets you pick the one you meant.

**A map that draws as you walk.** Follows GMCP room info where the world
sends it, or records your movement where it does not. Several floors,
one-way exits, crooked exits drawn honestly. Pathfind and walk there. Newest
and most experimental part of the app — marked as such in the UI.

**Extra windows.** Chat in one pane, vitals in another. Each has its own
size, opacity, and scrollback that keeps filling while the pane is closed.

**Phone comfort.** Growable input bar, scrollback search, copy, session
logging, connection time on the notification, TLS when the world offers it.

## Automating play

**Triggers, aliases, timers.** A trigger can colour a line, gag it, replace
text, notify you, set a variable, send commands, or push output into another
window — several at once. Triggers and timers can wait on a condition (*is
that trigger on*, *does a variable equal this*) and live in groups you arm
or disarm together. That is a state machine: combat scripts turn on when you
fight and off when you flee. Trigger scripts are real Lua.

**The rest of the classic toolkit.** Speedwalks, per-world settings, and the
Lua plugin engine the original was built around.

### From the input bar

Dot commands, typed like anything else. A short list; `.help` has every one.

| Command | |
|---------|--|
| `.help` | Every dot command this session. `.help suggest` filters. |
| `.options` | Options screen, same as ⋮. Put it on a button. |
| `.suggest on` | Word chips from recent game text. |
| `.widget add hp ring` | Overlay an HP bar. `.gauge` is the same. Then `.widget source …` |
| `.protocols` | What this world offered vs what you have on. |
| `.loadset combat` | Switch the button pad (also from a button). |
| `.map open` | Show or hide the map. `.map goto <room>` walks a route. |
| `.search <text>` | Jump to that text in scrollback. |
| `.switch <name>` | Another open connection. Bare `.switch` lists them. |
| `.reconnect` | Drop and connect again. |
| `#5 north` | Send the rest of the line five times (`##5 north` = a literal `#`). |
| `.run 3n2ew` | Speedwalk. |
| `.trigger group off combat` | Disable a whole trigger group. |
| `.alias toggle kk` | One alias on or off. |
| `.timer play heal` | Start a named timer (`pause` / `reset` / `stop`). |

Dot commands are on by default. `..` toggles them. A line starting `..`
sends a literal leading `.` to the game.

Full list, arguments, and the Lua API:
[`docs/user-manual.md`](docs/user-manual.md) (in-app **Help**).

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
