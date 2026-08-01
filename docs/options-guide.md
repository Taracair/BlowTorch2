# Options (session)

In-game **Options** dialog groups (Program Settings):

| Group | Purpose |
|-------|---------|
| **Display** | Orientation, keep screen on, fullscreen, NAWS width/height, terminal size tip |
| **Window** | Per-window text: font, buffer, word wrap, **Newest text at top?**, **Top padding (px)**, **Bottom padding (px)**, **Bottom padding with keyboard (px)**, **Keep text still with keyboard?**, **Scroll sensitivity**, hyperlinks (`http(s)://`, `www.`, bare domains like `example.com`), ANSI color; nested **Extra text windows** |
| **Input** | Input box / editor behavior (history size, keep last, **Grow Input Bar?** / `.wrap`, …) |
| **Service** | Encoding, background service & **game output** logging (`Log Session to File?`, `Session Log Directory`); **Battery optimization…**; nested **GMCP Options**, **MCP Options**, **MUD Protocols** |
| **Bell** | Bell character reactions |
| **Miscellaneous** | Default settings directory, manage storage access, **Export / Import Settings**, persistent connection, **overflow button appearance** (opacity / background / ring) |
| **Mapper** | Built-in room map: enable, float/fullscreen default, opacity, recording defaults, follow, path auto-send, Use GMCP Room, **Configure Room Sync…**, match-by-num / absolute coords / create exits, auto reverse links, toolbar actions CSV, Capture Title/Exits Regex |

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
(see user-manual). In-band MUD text is separate — gag it if needed.## Shared storage layout (`/BlowTorch/`)

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
is on by default for new profiles. Use **Manage modules…** to pick what goes in
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

## MCP Options

Mud Client Protocol — in-band `#$#` messages, used by a number of MOOs.
**Not** the same as GMCP. Under **Options → Service → MCP Options**. All advanced
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

## MUD Protocols (optional)

Separate from GMCP, under **Options → Service → MUD Protocols**. All **off** by
default — enable only if a MUD needs them, then reconnect:

| Option | What it does |
|--------|----------------|
| **Use MTTS?** | TTYPE always follows [MTTS](https://mudstandards.org/mud/mtts): name → `ANSI` → `MTTS <bits>`. On = bits **13** (ANSI+UTF-8+256); off = bits **1** (ANSI only). Reconnect after change. |
| **Use MSDP?** | Out-of-band variables (option 69). Two-way, unlike MSSP: most servers send nothing until you ask, so use `.msdp list`, then `.msdp send <var>` or `.msdp report <var>`. `.msdp` alone dumps the cache |
| **Use MSSP?** | Server listing/status (option 70); dump with `.mssp` |

When off, BlowTorch answers `DONT` so the server should not send those channels.
Parse errors never disconnect — the packet is ignored.

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

## Miscellaneous

- **Overflow button opacity (%)** / **Overflow button background?** /
  **Overflow button ring?** — how the gameplay **⋮** in the bottom corner is
  drawn. Fade it down when it sits over text you want to read, drop the disc to
  uncover what is behind it, or keep a ring with no fill. Opacity stops at 15%
  on purpose: the button keeps its whole 48dp tap area however faint it looks,
  and an invisible ⋮ is a corner of the screen that quietly eats taps.
- **Persistent Connection?** — ride out brief network loss without the
  disconnect dialog.
- **Export Settings** / **Import Settings** — moved here from the ⋮ menu. They
  are setup and migration jobs rather than things you reach for mid-session, and
  they sit beside the storage settings they depend on.

## Storage

- **Manage Storage Access** grants All files access and creates the `/BlowTorch/` tree.
- **Default Settings Directory** (Miscellaneous): blank = `/BlowTorch/settings/`.
- Session overflow **Export Settings** / **Import Settings**: SAF pickers plus default-directory actions.
- Launcher **Export Server List** / **Backup All Settings** use `/BlowTorch/launcher/` and `/BlowTorch/backups/`, with SAF **Choose location…** as an alternative.

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

## Dot commands

Full list: in-app **Help** and `docs/user-manual.md` (keep in sync with
`BTLib/res/raw/user_manual.txt` and `Connection` / plugin `RegisterSpecialCommand`).

## Input bar growth

- **Options → Input → Grow Input Bar?** (default on) — when off, the input field stays a single non-growing line.
- Dot command: `.wrap on` / `.wrap off` (no args prints status). Distinct from **Word Wrap?** (game text wrapping).
- **Edit / Send:** side-by-side on one line; when the input wraps to more lines, **Edit** stacks above **Send** at the bottom-right (input field keeps full height so it stays tappable).

## Notification responders

Trigger/timer notification responders can use the system default sound, five
bundled presets (soft chime/tap, mid ping/pluck, loud alert), files under
`/BlowTorch/` on shared storage, or **Pick from storage…** (SAF content URI).
