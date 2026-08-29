# Options (session)

In-game **Options** dialog groups (Program Settings). A search field at the
bottom of Options filters as you type; tap a result to jump to that page.

| Group | Purpose |
|-------|---------|
| **Display** | Orientation, keep screen on, fullscreen, NAWS width/height, terminal size tip |
| **Window** | Per-window text: font, buffer, word wrap, **Light theme?** (`.light`; paper and dark ink), **Light paper shade (1–5)** (`.light 1–5`; 2 is the original warm grey), **Dim repeated lines?**, **Remember how many lines?**, **Dim strength (%)**, **Scroll dates?** (`.when`; day/time to the left of ⋮ while in history), **Scroll date opacity (%)**, **Newest text at top?**, **Top padding (px)**, **Bottom padding (px)**, **Bottom padding with keyboard (px)**, **Keep text still with keyboard?**, **Show Edit button?**, **Show Send button?**, **Scroll sensitivity**, **Use OSC 8?** (words the game marks; independent of regex linkify; `.osc8 on|off`), hyperlinks (`http(s)://`, `www.`, optional bare domains like `example.com`; **Link bare domains?** and **Extra TLDs (CSV)** for short endings such as `ai,to`), ANSI color; nested **Extra text windows**; nested **Widgets** |
| **Input** | Input box / editor behavior (history size, keep last, **Grow Input Bar?** / `.wrap`, **Lowercase start of sent commands**, …) |
| **Service** | Encoding, background service & **game output** logging (`Log Session to File?`, `Session Log Directory`); **Battery optimization…**; nested **Protocols** (Use GMCP? / Use MCP? / Use MXP?), **GMCP**, **MCP**, **Telnet** |
| **Chat** | Unread mark on ⋮, a line in the game window, Android notifications, keep-at-most-N messages |
| **Bell** | Bell character reactions |
| **Miscellaneous** | Default settings directory, manage storage access, **Export / Import / Reset Settings**, persistent connection, **overflow button appearance** (opacity / background / ring) |
| **Mapper** | Built-in room map: enable, float/fullscreen default, opacity, recording defaults, follow, path auto-send, Use GMCP Room, **Configure Room Sync…**, match-by-num / absolute coords / create exits, auto reverse links, toolbar actions CSV, Capture Title/Exits Regex |

Plugins add their own pages, which appear only while that plugin is loaded:
**Button** (`button_window`) and **Starter Tutorial** (`starter_tutorial`) in the
Free build — both below.

## Extra text windows

Under **Options → Window → Extra text windows**:

| Option | Notes |
|--------|--------|
| **Enable Extra Text Windows?** | Master switch for overlays (slot definitions kept when off) |
| **Manage windows…** | List / add / delete / edit name, title, mode (`drawer_top` or `float`), height, opacity, **scroll speed**, **show title bar**, **show resize grip ◢** and **close button ✕** (floating only, all on by default; hiding the bar or the grip keeps the window draggable and resizable), visibility, **GMCP modules** (checkboxes + advanced CSV). Warns if Use GMCP? is off. |
| **Windows JSON** | Advanced: raw JSON array of slots (prefer Manage windows…) |

Slot **name** is the public id shared with gag/replace retarget, Lua
(`CreateTextWindow`, `NoteToWindow`, `AppendLineToWindow(windowName, line)`),
and `.window`. Max 8 slots; reserved names: `main`, `mainDisplay`, `button_window`.

**Scroll speed** is per window. The first choice, *Same as main window*, is the
default and follows **Options → Window → Scroll sensitivity** — so that one
setting still steers every extra window at once, and a slot only breaks away
when you set it to something specific. Changing it applies immediately; you do
not have to reopen the window.

**GMCP routes:** in Manage windows, pick modules (or advanced CSV e.g. `Char.Vitals, Comm.*`).
Inbound packets for those modules appear as `[GMCP] …` in that pane and are
**not** also echoed into main when **Show GMCP in game window?** is on. Optional
custom formatting still uses a `%Module` literal trigger + `NoteToWindow`
(see user-manual). In-band MUD text is separate — gag it if needed.

## Widgets

Under **Options → Window → Widgets**:

| Option | Notes |
|--------|--------|
| **Manage widgets…** | List / add / delete overlay gauges (HP, mana, cooldown). Same ids as `.widget` / `.gauge` (lowercase `a-z` `0-9` `_`, max 12). Shape, colour, source (GMCP / MCP / variable / regex / timer / manual), tap and eight-way swipe, size, IME (stay / hide / overlay). Long-press (~½ s) edits (move/resize); tap leaves edit. Definitions stay if you hide one. |

`.widget` (and `.gauge`) is the typed command list — see the user manual.
Typical first pair: `.widget add hp ring` then `.widget source hp gmcp Char.Vitals.hp Char.Vitals.maxhp`.
No GMCP? `.widget source hp mcp hp maxhp`, Set Variable then `.widget source hp var hp maxhp`, or `.widget source hp regex "HP: (\\d+)/(\\d+)"`.
GMCP sources need **Use GMCP?** on (off for new worlds). MXP `<GAUGE>` does not create a widget.

## Chat

Under **Options → Chat** (before Bell). Own lines from Send are never announced.

| Option | Default | Notes |
|--------|---------|--------|
| **Unread mark on ⋮** | on | Disc on the overflow button while a conversation has unread messages and the drawer is closed |
| **New-message line in the game window** | Off | Off, every new line, or a digest. Digest waits the interval, then the cyan line is how many arrived (not only the first) |
| **Digest interval (seconds)** | 60 | Wait between digest lines. Also used for Android notification sound when the line mode is Off |
| **Android notification for new chat** | off | Master switch. Each conversation picks Tells / Channels / Auction / Other in chat ⚙ (four system channels, not one per name). Tune sound in Android Settings. Chat used to share the alerts channel with the bell — re-tune after this update. Tap opens that conversation. Count updates on every new line; sound follows Every, or once per digest window |
| **Keep at most this many chat messages** | 4000 | Oldest lines drop first, across every conversation. 0 = no practical limit (hard stop 50000) |

## Shared storage layout (`/BlowTorch/`)

Default for import/export, backups, launcher lists, session logs, maps, and app/GMCP logs is **outside** `Android/data`:

```text
/storage/emulated/0/BlowTorch/
  settings/       # session Import/Export Settings (default)
  backups/        # launcher Backup All Settings
  launcher/       # server list export/import
  maps/           # Mapper JSON maps (.map export / autosave)
  session_logs/   # incremental game .txt logs
  logs/           # blowtorch2.log (errors), gmcp.log (when Log GMCP? is on)
```

`gmcp.log` is a separate file on purpose: a busy world sends GMCP constantly,
and mixing it into `blowtorch2.log` used to push the crash history out of the
file you actually want after a crash. Both rotate at 2 MB.

On Android 11+ this needs **All files access** once: **Options → Miscellaneous → Manage Storage Access** (opens the system permission screen). Without it the app falls back to `Android/data/…/files/BlowTorch/` with the same subfolders.

## GMCP

GMCP is an optional structured out-of-band channel (telnet option 201). **Use GMCP?**
starts **off** for new worlds (existing profiles that never saved the key keep it on).
Enable it under Options → Service → Protocols. Use **Manage modules…** (under Options → Service → GMCP) to pick what goes in
`Core.Supports.Set` (built-in, seen this session, catalog). Nothing auto-enables
from traffic. **Supports String (advanced)** is the raw list if you prefer editing
it by hand. **Log GMCP?** writes the handshake and every packet to
`/BlowTorch/logs/gmcp.log` (and to the session log when that is enabled). This
is the reliable way to find out what a world really sends — text copied out of
the game window can pick up escaping on the way and is not proof of what was on
the wire. Passwords in `Char.Login.Credentials` are redacted before writing.
**Suggest modules when seen?** (off by default) can toast when the server sends a
module you have not enabled. **Show GMCP in game window?** (or `.gmcp feed on`)
echoes live IN/OUT packets in the mud window — noisy, but the fastest way to see
what your MUD actually sends. `.gmcp sniff on` prints the absolute path in-game;
Overflow → Crash report → Show log to view. Dot helpers: `.gmcp ask`,
`.gmcp enable|disable`, `.gmcp renegotiate`, `.gmcp feed` (see Help / user-manual).

Native handlers: **Char.Login** (primary launcher account login/password; if none
stored, sends empty `Char.Login.Credentials {}` so the MUD falls back to in-band
login — required on some MUDs) and
**Client.Media** (sound/music). `Client.Media.Stop` with no name/type/tag/key
filters (including `{"fadeaway":true}` alone) stops all tracks; fade only when
`fadeaway` or an explicit `fadeout` is sent. Audio is also hard-stopped on
disconnect and when the app is swiped away from Recents (service may stay for
persistent connection).

## MCP

Mud Client Protocol — in-band `#$#` messages, used by a number of MOOs.
**Not** the same as GMCP. **Use MCP?** under **Options → Service → Protocols**. Details under **Options → Service → MCP**. All advanced
flags default off except omit-from-output and auto-negotiate (when Use MCP? is on):

| Option | Default | Notes |
|--------|---------|--------|
| **Use MCP?** | off | Handshake + package negotiate |
| **Manage packages…** | — | Checkbox UI for `mcp-negotiate-can` |
| **Packages String (advanced)** | negotiate + hellmoo + simpleedit + displayurl + ping + cord + vmoo | Raw list |
| **Log MCP?** | off | Also `.mcp sniff` |
| **Show MCP in game window?** | off | Also `.mcp feed` |
| **Omit MCP lines from output?** | on | Hide `#$#` from scrollback |
| **Auto-negotiate packages?** | on | Send can/end after `#$#mcp` |

Native handlers: **dns-org-hellmoo-status** (`.mcp vitals`), **simpleedit** (edit dialog),
**displayurl** (open browser), **ping** (auto-reply), **mcp-cord**, **vmoo-client**.
Lua: `Send_MCP_Packet`, `Get_MCP_Status`, triggers `@message-name`.
Helpers: `.mcp ask`, `.mcp cord …`, `.mcp ping`, `.mcp client`, `.mcp send`.

## Protocols and Telnet

**Use GMCP? / Use MCP? / Use MXP?** sit together under **Options → Service → Protocols**. `.protocols` reports what this world offered versus what is on; `.protocols enable` turns on offered-but-off switches.

**MTTS, MSDP, MSSP and MCCP** sit under **Options → Service → Telnet**. **MTTS and MCCP
are on by default; MSDP and MSSP are off** — enable those only if a MUD needs
them. Reconnect after changing any of these:

| Option | Default | What it does |
|--------|---------|----------------|
| **Use MTTS?** | **on** | TTYPE always follows [MTTS](https://mudstandards.org/mud/mtts): name → `ANSI-256COLOR` (or `ANSI` if off) → `MTTS <bits>`. On = bits **269** (ANSI+UTF-8+256+truecolor); off = bits **1** (ANSI only). Reconnect after changing. |
| **Use MSDP?** | off | Out-of-band variables (option 69). Two-way, unlike MSSP: most servers send nothing until you ask, so use `.msdp list`, then `.msdp send <var>` or `.msdp report <var>`. `.msdp` alone dumps the cache |
| **Use MSSP?** | off | Server listing/status (option 70); dump with `.mssp` |
| **Use MCCP?** | **on** | MUD Client Compression Protocol v2 (option 86). Saves bandwidth and is invisible when it works. If decompression fails, the client says so, drops compression for that connection and reconnects once without it — one shot, not a reconnect loop. Turn it off for a server whose compression misbehaves |
| **Use MXP?** | **on** | Under **Protocols**, not Telnet. MUD eXtension Protocol (option 91). Clickable SEND / menus / EXPIRE, colours, SOUND/MUSIC (local file or `http(s)` `U=`, same player as Client.Media). No images, no `SCRIPT`/`RELOCATE`. MXP `GAUGE` writes session variables and does not mint a `.widget`. Reconnect after changing. `.mxp on\|off`. `.probe mxp` dumps a sample. Launcher seeds Discworld / Threshold RPG / Ansalon / Midnight Sun once |
| **Log MXP?** | off | Dump MXP tags to logcat (`BlowTorch.MXP`) |
| **Show MXP in game window?** | off | Echo parsed MXP into the game window (debug) |

When one is off, BlowTorch answers `DONT` so the server should not send that
channel. Parse errors never disconnect — the packet is ignored.

## Telnet ECHO and the masked input bar

There is no option for this: a MUD asks for a password by taking echoing over
(telnet option 1). While the server holds it, the input bar masks what you type
and the text is kept out of the session log; it unmasks when the server hands
echoing back, or on a disconnect. `.echo on` / `.echo off` is the manual
override for a server that takes echo and never returns it, and the next change
from the server wins over the command.

Not the same as **Options → Service → Local Echo?**, which decides whether your
own commands are printed into the game window at all.

## Mapper

Session group **Options → Mapper** (also overflow → **Map** / `.map`):

| Option | Notes |
|--------|--------|
| **Enable mapper** | Master switch for the built-in map module |
| **Prefer floating window** | Default float vs fullscreen when opening |
| **Opacity** | Overlay transparency |
| **Recording default** | Seed for new sessions (live toggle is still Rec / `.map record`) |
| **Follow player** | Camera tracks current room |
| **Path auto-send** | If on, `.map goto` sends the path; if off, prints only |
| **Use GMCP Room** | Sync from `Room.*` when GMCP is on (independent of Record/Draw) |
| **Configure Room Sync…** | Auto-grow, match by `num`, absolute coords (off by default), create exits |
| **GMCP: Auto-grow map?** | Create rooms/exits from Room.Info; off = follow existing by number only |
| **GMCP: Match by room number?** | Prefer `num`/`id`/`vnum` as tile identity. Leave on where the world sends one — it is the only stable identity there is. With it off, or on a world that sends no room number, the mapper falls back to the tile the walk points at, which is reliable for walking but cannot recognise a room you arrive at some other way |
| **GMCP: Use absolute coordinates?** | Place at x,y only when ≤1 cell away; off = grow beside previous |
| **GMCP: Create exit neighbors?** | Create/link missing exits; vnum stubs until visited; never deletes exits |
| **Auto reverse links** | Suggest opposite exits when linking |
| **Accept One-Way Specials?** | ON = recording `out`/`enter` always makes a new tile. OFF (default) = if exactly one room already leads into Here, link the special back there. Also **Build → 1-way** |
| **Level-Up Commands (CSV)** | Recording moves that create a higher floor (`u,up,climb,ascend` default). Clear Up+Down to never auto-create levels |
| **Level-Down Commands (CSV)** | Recording moves that create a lower floor (`d,down,descend` default) |
| **Toolbar actions** | Legacy CSV (still stored); map chrome uses the **Nav / Floors / Edit / Map / View** radial chips instead of the bottom strip |
| **Capture Title Regex** | Regex for `.map capture` and the Capture dialog title field (`mapper_capture_title_regex`). Group 1 when present; else whole match. Default: `^([A-Z].*)$` |
| **Capture Exits Regex** | Regex for exits field (`mapper_capture_exits_regex`). Group 1 when present (e.g. after `Exits:`). Default: `(?i)exits?:\s*(.*)` |

Maps are stored as JSON under `/BlowTorch/maps/`. See Help → Mapper for Browse|Edit,
**Nav/Floors/Build/File** radials, Draw/Links/Paths, tile-anchored levels (List
browser, ▲/▼/◆ badges, ↑/↓ nests), the movement lexicon (`.map dirs`), and the
full `.map` command list. Capture uses **Options → Mapper** regexes via
`.map capture preview|apply`, or `.map capture` for a one-off edit of those
patterns.

## Session log

- Enable: **Options → Service → Log Session to File?**
- Blank directory = `/BlowTorch/session_logs/`. Use **Browse…** for SAF or an absolute path.
- Incremental plain text of **incoming game output** (ANSI stripped), not keyboard input.
- Files are named `{profile}_{yyyy-MM-dd_HH-mm-ss}.txt`.
- Writes are **near-live**: kept in an open stream, flushed about every **0.75s** or
  **4 KB**, plus **fsync** so other apps see growth; also flushed on disconnect.
  Not delayed until you leave the game.
- Reconnect to the same profile **continues the same file** (marker `reconnected`).
  A brand-new file is created when you first enable logging or connect with no
  active log for that profile.
- Tip: check the `connected → /path/...` marker at the top of the file so you
  open the log that is actually being written (do not edit that file in another
  app while playing).

## Background connection / battery

- **Keep Wifi Alive?** (Service) holds a Wi‑Fi lock while connected.
- The service also takes a partial CPU wake lock while any connection is up.
- **Battery optimization…** opens the system exemption flow; a one-shot dialog
  also appears when you are connected if BlowTorch is still battery-optimized.
- Connection duration is shown on the ongoing notification and launcher rows.

## Device (the phone's own sensors)

Not the same thing as button gestures: swipes, holds and chrome bindings live in
the **button editor**. This group is about hardware readings — proximity,
motion, light, charger, headphones, battery, screen, rotation — offered to triggers,
conditions and timers.

| Option | Default | Notes |
|--------|---------|--------|
| **Device state as variables** | off | Keep `device.facing`, `device.screen`, `device.headphones`, `device.charging`, `device.battery`, `device.covered`, `device.light` up to date as session variables. With it off nothing is registered and a condition testing one is *false*, never true |
| **Sensors…** | — | The list screen, built like the Alias / Trigger / Timer chooser: readings grouped under *A hand over the screen*, *Movement*, *Light* and *Headphones, charger, battery, screen and rotation*, each row one line of what it is or what already answers it. Tapping a row opens the trigger editor; **Test** opens a live probe. Readings this handset cannot provide fold away under *Not available on this phone*, still tappable, because a profile is shared with people whose phones do have them. The `?` in the button bar is what the screen is for; which chip provides a reading is `.sensor caps`, not the row |
| **Test** (on a row) | — | Watches the sensor while you do the gesture and says whether the phone saw it — the question `.sensor fire` cannot answer. Runs in the UI process and releases every listener when it closes. Near/far, which way up, the one-shot sensors and the system events get a verdict from the same shared code the real detector uses; **shake** and the two **light** readings show the raw number instead, because their thresholds are calibrated and kept by the service process. Where a reading has an enabled trigger the probe also offers **Run the actions**, which is `.sensor fire` |
| **Calibrate shake…** | — | Two measurements (shaking, then walking) and it picks a threshold between them; refuses when they overlap. Kept with the phone, never exported |
| **Calibrate light…** | — | Tap once somewhere dark and once somewhere bright. Lux is not comparable between phones or rooms, so this is the only way "dark" can mean yours |
| **Battery low threshold…** | 20 / 35 | Two percents: Low (`batterylow` fires once when charge crosses down through it) and Recover (`batteryok` once when it crosses back up). Recover must be at least Low+5 so 19–21% cannot flap. Kept with the phone, never exported |
| **Movement sensors with the screen off** | off | Off means shake / wave / face-down do nothing while the display sleeps, so a pocket cannot fire them |
| **Movement sensors while the app is in the background** | off | The same while another app is on top or BlowTorch is in Recents |

Both movement switches cover **movement** readings only. Headphone, charger,
battery, screen and rotation readings keep working regardless — muting speech when the
jack comes out has to work precisely when you are not looking at the screen.

`landscape` / `portrait` fire when the screen actually turns. The orientation
the phone already has when you bind the reading is not a fire — turn it the
other way, then back.

A sensor trigger is **not aimed at one world**: it fires in every world you have
open, so with two MUDs connected one shake sends its command twice.

From the input bar the same ground is `.sensor` (`caps`, `<reading> <command>`,
`fire <reading>`, `watch on|off`, `threshold …`) and `.probe sensors`.

## Miscellaneous

- **Overflow button opacity (%)** / **Overflow button background?** /
  **Overflow button ring?** — how the gameplay **⋮** in the bottom corner is
  drawn. Fade it down when it sits over text you want to read, drop the disc to
  uncover what is behind it, or keep a ring with no fill. Opacity stops at 15%
  on purpose: the button keeps its whole 48dp tap area however faint it looks,
  and an invisible ⋮ is a corner of the screen that quietly eats taps.
- **Persistent Connection?** — only with **Auto Reconnect** on: after a
  brief network loss wait for connectivity before retrying, and treat a
  peer close as a flap. Does not retry on its own, and does not change the
  **Auto Reconnect Tries** number.
- **Export Settings** / **Import Settings** — setup and migration jobs rather
  than things you reach for mid-session; they sit beside the storage settings
  they depend on. **Reset Settings** is here too (throws away this world's
  settings after a confirm).

## Button (plugin `button_window`)

Appears while the button plugin is loaded, which in the Free build is always —
it cannot be disabled or deleted.

| Option | Default | Notes |
|--------|---------|--------|
| **Show gesture hints** | on | Draw swipe arrows, hold (**H**) and accordion chevrons on the tiles. Off is a cleaner pad you have to remember. The same switch is in Edit mode under the gear, as **Markings on buttons**, which is where to look on a profile too old to have this row. It is the master: with it off nothing is drawn anywhere, and with it on each button may still opt out in its own Swipe tab |
| **Offer button layout wizard** | on | Show the pack/size picker once after connect on a new profile. Cleared when you finish or skip it; turn it back on to see the prompt again |
| **Load button set from wizard** | — | Opens the wizard right now, at any time. Same as `.layoutwizard`. Writes only the sets you name; removes nothing |
| **Button size** | Comfortable | Compact / Comfortable / Large / Extra large / **Fit to screen**. Picking one resizes the **current** set immediately, keeping its arrangement — tile size and grid spacing move together, so a compass rose stays a rose. Also becomes the wizard's default |
| **Layout template** | Compass | Compass / Newbie / Combat / Explorer / Social. Only chooses which pack the wizard offers first — **it installs nothing on its own** |
| **Button roundness** | 6 | Corner radius of a tile (key is `roundess`, spelled that way on disk) |
| **Haptic feedback on editor launch / on press / on flip** | — | Three separate dropdowns |

A short tap on empty grid in Edit buttons still makes a new tile. Long-press an
empty cell pastes copied buttons. The old **Automatically create buttons** /
**Automatically launch editor** rows are stripped on connect if a profile still
had them.

The Compass pack's rose sits **bottom-right** on a new layout (thumb reach).
Skipping the wizard still pins that pad to the **right**; left / center / right
in the wizard still wins.

Both dropdowns used to be free-text fields. A profile keeps whatever option
*type* it was created with, so an older profile is migrated to the dropdown on
the first connect after upgrading, carrying its previous value across.

## Starter Tutorial (plugin `starter_tutorial`)

| Option | Default | Notes |
|--------|---------|--------|
| **Show welcome on connect** | off | Print a short welcome tip when connecting to a normal MUD. The **Starter Tutorial** launcher entry always opens the full guide regardless |
| **Tips while playing?** | off | When you type a `.command` on a real MUD, print a short reminder of what it does. `.tips on` (once per command this session), `always`, or `off`. `.tutorial <topic>` still opens any lesson without this |

`.tutorial done` clears Show on connect too. The plugin can be toggled off in the Plugins
list but not deleted.

## Storage

- **Manage Storage Access** grants All files access and creates the `/BlowTorch/` tree.
- **Default Settings Directory** (Miscellaneous): blank = `/BlowTorch/settings/`.
- **Export Settings** / **Import Settings** (Miscellaneous): SAF pickers plus default-directory actions.
- Launcher **Export Server List** / **Backup All Settings** use `/BlowTorch/launcher/` and `/BlowTorch/backups/`, with SAF **Choose location…** as an alternative.
- `.settings` from the input bar is the no-cable route to the `.bak` copy kept beside this world's settings file in private app storage, refreshed on every save: `.settings` names the file and the date and size of the kept copy, `.settings backup` saves now and refreshes it, `.settings restore` puts it back and reloads. For a copy you can move off the phone, use Export or the launcher's **Backup All Settings** instead.

## Launcher (server list)

Toolbar **⋮** menu (About moved here; bottom **New** only):

| Menu item | What it does |
|-----------|----------------|
| **Import Server List** | Load launcher connections XML (SAF **Pick file…** or default `…/launcher/`) |
| **Export Server List** | Save launcher connections XML (default dir or SAF **Choose location…**) |
| **Backup All Settings** | Zip all private session `*.xml` settings (default `…/backups/` or SAF **Choose location…** — preferred way to keep a portable copy) |
| **Restore Settings Backup** | Restore that zip (or a scanned backup folder) into private files — restart after |
| **Check for updates** | Checkbox, **on by default**, app-wide. See below |
| **Check for updates now** | Ask GitHub straight away and say what it found either way |
| **About** | About dialog |

**Check for updates** — asks GitHub once a day whether a newer BlowTorch 2
release exists, when you open the launcher, with a button to the release page
and instructions for downloading the APK there. This is the only connection the
app makes to anything other than a MUD you added: a plain read of the public
releases page, with nothing about you in the request. Turn it off and the app
talks to nothing but your MUDs. If you installed from **F-Droid, turn it off** —
F-Droid updates you already. The test flavour never checks on its own, whatever
this is set to; **Check for updates now** works there, since you asked for it.
Failures are silent; "Skip this one" suppresses a version you do not want to be
reminded about. The setting used to live in a world's Options → Miscellaneous,
which made it look per-world; it was always app-wide, and your existing choice
carried over.

Removed: legacy **Copy Settings to Storage** / Recover (raw dump to `…/recovered/`). Use **Backup All Settings** instead.

**Account** on New/Edit connection: optional login/password/mail. Primary login/password
can be used for GMCP **Char.Login** when the MUD offers it; extra slots are notes only.
Stored as plain text in the launcher list on this device; see the warning in the dialog.

**Hostname** on New/Edit connection is labelled Hostname (not "Host Name"). The
field uses a URI keyboard so typing a dot does not insert a space. Leftover
spaces are stripped on save.

## Dot commands

Full list: in-app **Help** and `docs/user-manual.md` (keep in sync with
`BTLib/res/raw/user_manual.txt` and `Connection` / plugin `RegisterSpecialCommand`).

## Input bar growth

- **Options → Input → Grow Input Bar?** (default on) — when off, the input field stays a single non-growing line.
- Dot command: `.wrap on` / `.wrap off` (no args prints status). Distinct from **Word Wrap?** (game text wrapping).
- **Dim repeated lines:** `.dimrepeat on|off`, `.dimrepeat lines N` (how many recent long lines stay in memory, default 12), `.dimrepeat strength N` (10–90, default 50 = half as bright; higher is darker). Also Options → Window.
- **Light theme:** `.light on|off|toggle|1-5|shade N`. Five papers (1 grey … 5 near-white; 2 is the original). Ink darkens as the paper lightens. Game colours stay; whites and light greys are darkened. Extra-text follows. Launcher, Options, mapper, chat and ⋮ stay dark. Off by default. Also Options → Window.
- **Use OSC 8?:** `.osc8 on|off` (default on). Worlds can mark words as links even when the words are not a URL. Mudlet-style `send:` taps type a command; `prompt:` fills the input bar. Independent of **Enable Hyperlinks?** (that one is regex linkify of `http` / `www.` / bare domains). Hold where several tappable words sit close together (OSC 8, MXP, or a Tappable Word trigger) and a small loupe appears so you can slide to the one you meant. `.probe osc8` dumps a tappable sample without a MUD. Launcher row **OSC 8 links (local test)** is `127.0.0.1:4445` (needs `python3 .scratch/osc8server.py` and `adb reverse tcp:4445 tcp:4445`).
- **`.protocols`** — what this world offered vs what is on. `.protocols enable` turns on offered-but-off switches (reconnect when it says so).
- **`.options`** — opens the Options screen, same as ⋮. Put it on a button.
- **`.widget` / `.gauge`** — overlay HP/mana/timer gauges. Options → Window → Widgets. See the user manual.
- **`.buttonopacity` / `.buttonsopacity`** — force every tile opaque until `restore`. Edit buttons pauses a 0% override so the pad is visible.
- **Edit / Send:** side-by-side when both are shown (Options → Window → Show Edit/Send button?).
- **Show Edit button?** — `.editbutton on|off` · tools strip `.editpanel on|off`
- **Show Send button?** — `.sendbutton on|off` · or keyboard Send / `.kb flush`

## Lowercase start of sent commands

- **Options → Input → Lowercase start of sent commands** (default off) — for
  case-sensitive worlds: softens only the first letter of each outbound command
  (`Look` → `look`). Mid-line text is unchanged (`say Hello` stays `say Hello`).
  Passwords are never rewritten.
- **Bypass once:** prefix with `\` to keep a capital — `\Look` sends `Look`
  (backslash stripped, never reaches the MUD). With the option off, `\Look` is
  sent as typed.

## Word completion

Completes mob / player / item words the world just used, which the soft keyboard
never learns. All of it is off by default and lives under **Options → Input**;
each has a dot form on `.suggest`.

All of these live under **Options → Input → Suggestions** — one feature with
seven switches was making the Input page a wall.

- **Complete words the game used** — `.suggest on|off`. While off, incoming text
  is not even sent to the completer, so it costs nothing.
- **Completion memory (lines)** — `.suggest lines N`. Freshness is the last `N`
  lines the world sent (default 300), not a word count; `0` keeps the whole
  session. "Recent" then means on screen what it means to the completer.
- **`.suggest 1` … `.suggest 8`** picks that chip. Meant for a super button /
  alias / trigger, not the input bar: typing into the bar replaces the word being
  completed, so the strip empties. Chips are numbered to match.
- **Forgive typos in suggestions** — `.suggest loose on|off`. Only after an exact
  prefix finds nothing: letters in order with gaps, `grzld` → `grizzled`. First
  letter must match, four-letter minimum.
- **Show the rest of the word as you type** — `.suggest ghost on|off`. Draws the
  top suggestion after the cursor, dimmed, with a micro `1`. **Tap it to take
  it** — the ghost is a target, not just a hint. Drawn only, never inserted, so
  what you send is exactly what you typed.
  - A continuation shows only the missing letters: `gri` with `zzled` behind it.
  - A forgiven typo shows the whole word behind an arrow, because the letters
    have to change rather than grow: `grzld → grizzled`. Tapping replaces what
    you typed.
  - The ghost never makes the bar taller. If it does not fit the rest of the
    line it continues on the next line when the bar already has one, and is cut
    with `…` when it does not.
- **Suggestions float over the game** — `.suggest overlay on|off`. **On by
  default.** The strip below the game window takes height, so the text jumps when
  a suggestion appears; floating over the game text costs the layout nothing and
  nothing moves. Turn it off to get the in-layout strip back. Either way the
  panel now waits a moment before hiding, rather than blinking off on the first
  prefix that matches nothing.
- **Keep the suggestion bar in place** — `.suggest persist on|off`. Leaves the
  floating bar up even with nothing to suggest, so the chips change inside
  something that does not move instead of appearing under your thumb. Empty it
  shows only its grip: **tap the grip** to collapse, **long press and drag** it
  to move the bar (remembered per world and per rotation; drop it back near the
  input bar to go back to following it). `.suggest persist off` gets rid of an
  empty bar.
- **Suggestion chip opacity (%)** — `.suggest opacity N` (10–100). Fades the chip
  backing of the floating chips only; the words stay fully readable.
- The learned vocabulary is dropped on connect, so one world's names are never
  offered in another. Two worlds open at once keep separate bags.
- **Edit the bag:** `.suggest forget <word>` drops one token (session strip and
  pairings). `.suggest unpair <verb> <target>` drops one pairing.
  `.suggest weight <verb> <target> N` sets that pairing's count (`0` = unpair).
  Bare `.suggest forget` is usage, not a wipe; `.suggest clear` still throws
  everything away.

## Prompt bar

- **Prompt on its own bar** — `.prompt on|off`, **Options → Input**, off by
  default. A MUD prompt is the line the world never finishes (your HP/EN line,
  resent after every command); on, it sits in one fixed place above the input bar
  instead of repeating down the game window.
- `.prompt` with no argument also reports **prompts seen: N** for this connection.
  Zero after a while means the world sends no prompt (many MOOs do not) — the bar
  showing nothing is then correct, not broken.

## Notification responders

Trigger/timer notification responders can use the system default sound, five
bundled presets (soft chime/tap, mid ping/pluck, loud alert), files under
`/BlowTorch/` on shared storage, or **Pick from storage…** (SAF content URI).
