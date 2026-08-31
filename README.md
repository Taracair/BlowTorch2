# BlowTorch 2

An Android client for MUDs — text worlds you play over the network.
MUD stands for Multi-User Dungeon. They are an old kind of game people
still play: immersive text rooms, meeting other players, fighting and
talking, from before the graphical MMOs. The classic ones are fantasy
role-play — a class, races, monsters, skills. The client does not care;
it talks to whatever host you add. To find one, search
[MudStats](https://www.mudstats.com/),
[MudVerse](https://www.mudverse.com/), or
[The Mud Connector](https://www.mudconnect.com/).

You pick a host, connect, type commands, and read what the game sends back: rooms,
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

## The game screen

After you connect, you read the game in the big text window. Nothing below
is required to play. This is what you can add around that text.

**Buttons.** Probably the most important part of the client. 
Shown as a grid on the screen. Tap N and the game receives `north`.
Swipe that same tile in any of eight directions (including the corners)
for eight more commands; hold for another. One tile can run
`.loadset combat` and the whole pad swaps from walking to fighting.
A MORE tile fans out extra buttons if you pin them onto it.

**Gauges.** A small HP bar, ring, or countdown you put on the game window
(`.widget` / `.gauge` — same command). You add it empty, then point it at
the world's vitals, a line of score text, a variable, or a timer. The
world does not draw it for you.

**Tap the text.** A word the world marked, or one your own trigger marked,
can send a command when you tap it. If several tappable words sit close
together, hold and pick the one you meant from a small loupe.

**Extra windows.** Optional panes beside the main text (a channel dump,
combat, whatever you send there). Hide one and it still collects; destroy
it and it does not. Chat with a reply box is a different thing: the chat
drawer (⋮ → **Chat**, or `.chat`).

**Input and scrollback.** The input field can grow to more than one line
(on by default). Search the buffer. Copy is two fingers, not a long-press.
Log the session if you turn that on. The notification shows how long you
have been connected.

**A map.** Follows room info the world sends, or records your steps when
you turn recording on. Find a room; `.map go` walks you there. Newest
part of the app — the first time you open it, a dialog calls it
experimental.

## Triggers, aliases, and timers

**Triggers, aliases, timers.** A trigger watches the game's text and does
what you set: colour a line, hide it, notify you, send a command, set a
variable, push the line into another window — several at once. An alias
turns a short word into a longer command (`kk goblin` → the game receives
`kill goblin`). A timer fires on a clock. Triggers can live in a group
you arm or disarm together (`.trigger group off combat`). Aliases and
timers are one by one. Trigger scripts are real Lua.

**The rest of the classic toolkit.** Speedwalks, per-world settings, and the
Lua plugin engine the original was built around.

### `.commands` — bar or button

Type a leading `.` in the input bar, or put that same line on a button.
That is the point: during play you tap `.options`, `.font +2`, or
`.loadset combat` instead of digging through the client's menus. `.help`
and `.commands` are the same command; `.help` lists every registered one.
`.help suggest` keeps names containing "suggest".

A short list of the ones people put on buttons:

`.help` — every dot command (same as `.commands`).
`.options` — Options, the same screen as ⋮ → Options.
`.font +2` — bigger game text, no trip through Options.
`.loadset combat` — switch the button pad (a set named combat).
`.clearbuttons` — hide the pad; one BACK tile brings it back (not the
phone's Back key).
`.map open` — show the map (does not hide it; `.map close` does).
`.map go` plus a room title — walk there. `.map goto` only sends if Path
auto-send is on.
`.widget add hp ring` — an empty ring named hp. Then you point it at
numbers. `.gauge` is the same command.
`.window list` — extra text panes. `.window show` / `.window hide` need
a slot name; bare `.window` is help.
`.search goblin` — find that text in scrollback.
`.switch` — list open sessions; add the exact display name to jump.
`.reconnect` — drop and connect again.
`.kb popup` — show the keyboard. Words after it go into the bar (and
replace what was there). Bare `.kb popup` clears the bar, then shows
the keyboard.
`.run 3n2ew` — speedwalk. `.rev 3n2ew` walks those letters backwards.
`#5 north` — send `north` five times at once. `##5 north` reaches the
game as `#5 north`.
`.trigger group off combat` — disable that trigger group.
`.alias toggle kk` — that alias on or off.
`.timer play heal` — start that timer (`pause` / `reset` / `stop` / `info` / `dump`).
`.suggest on` — word chips from recent game text.

Dot commands are on by default. `..` alone toggles them. `..look` sends
`.look` to the game.

Full list, arguments, and the Lua API:
[`docs/user-manual.md`](docs/user-manual.md) (in-app **Help**).

---

## Credit

BlowTorch is Daniel Block and Offset Null Entertainment, LLC (2010–2018). The
MUD core, the Lua plugin system, triggers, buttons — all theirs. This fork is
the natural inheritance of that work — my personal gratitude for what they
built, and a slightly desperate attempt to keep it running on newer Android
phones. Without that MIT release there would be nothing to keep alive. Same
license, see [`LICENSE`](LICENSE).

---

## How this is made

One person, and a lot of AI. I use an LLM to write most of the code and docs. I
test on a real phone, I decide what ships, and I read the bug reports.
I definitely listen to your feedback too.
After all, the client is here for you, not only me.

This is my first public repo of any size. Bug reports with steps to reproduce are
worth a great deal. How the work is actually done lives under **Building**, for
anyone about to change the code.

---

## Privacy and permissions

A local client. No ads, no analytics, no accounts, no server of mine anywhere.

The only request a GitHub install makes on its own, besides the worlds you
added, is an update check against GitHub, on by default, at most once a day.
That is a GET of GitHub's latest-release API (`User-Agent: BlowTorch2`) — no
account, no game text, nothing about you in the body. The F-Droid build of
the same source defaults that check **off** (`-Pblowtorch.fdroid`); F-Droid
updates you already. If a world then asks the client to fetch a sound or a
picture, that fetch is the world talking, not a tracker of mine. The check
is in
[`UpdateChecker.java`](BTLib/src/com/resurrection/blowtorch2/lib/util/UpdateChecker.java)
if you would rather verify than take my word. Turn it off under the
launcher's **⋮ → Check for updates** and the app stops phoning GitHub.
App-wide, not per world. Test builds never check on their own; **Check for
updates now** still talks to GitHub if you tap it.

Internet is needed to play. A foreground service is how the session can stay
up when the screen is off. Notifications on Android 13+ are useful (connection
state, alerts), not required to type. All files access is not needed to play
— it is so `/BlowTorch/` is visible in a file manager (settings, backups,
maps, logs). Without it, everything still runs from app storage, with import
and export through the system picker (the picker does not first ask for All
files access). Display over other apps is not needed
either, unless you float a button or a gauge over the soft keyboard. The
full list is in [`docs/FDROID_README.md`](docs/FDROID_README.md).

**Speaking out loud.** A trigger or a timer can read a line aloud (the **Speak
Out Loud** action). That is not a permission — it uses the phone's own speech
engine, so BlowTorch carries no voices and cannot listen to anything. Android 11
hides the engine unless the app declares an interest in TTS; that declaration
grants no microphone and no data. Output only. If nothing is spoken, the **?**
in the Speak action's editor walks through what to check and opens Android's
own text-to-speech settings, where voices live.

Two things worth knowing: account notes on launcher rows are stored as plain
text on the device, so leave them blank if you would rather not keep passwords
there; and Lua plugins run with the app's privileges, exactly as in classic, so
only load ones you trust.

**Storage.** Classic BlowTorch assumed it could write anywhere, which newer Android does
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

The `production` flavor is `com.resurrection.blowtorch2`. The `btTest`
flavor is `com.resurrection.blowtorch2.test`.

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

Most of the code is in `BTLib/`, the shared library. `BT_Free/` is the app
module, with the Lua plugins under `assets/`. Native LuaJIT lives in
`LuaJIT-2.0.5/` (32-bit ABI) and `LuaJIT-2.1/` (GC64, 64-bit).
`scripts/check.sh` runs everything checkable without a device, in one
command — the same thing CI runs. Guides are under `docs/`: architecture in
[`architecture.md`](docs/architecture.md), plugins in
[`plugin-authoring.md`](docs/plugin-authoring.md), working rules in
[`ORCHESTRATION.md`](docs/ORCHESTRATION.md). Store and F-Droid text is in
`fastlane/` and `metadata/`.

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
