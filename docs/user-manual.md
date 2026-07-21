# BlowTorch User Manual

Source of truth for in-app **Help**. Keep this file in sync with
`BTLib/res/raw/user_manual.txt` (packaged into the Help dialog).

## Dot commands

Lines that start with a single `.` are handled by BlowTorch when “process period”
is enabled (default). Type `..` alone to toggle that processing on or off.
Prefix a server command with `..` to send a leading `.` to the game without
running a client command (e.g. `..look` sends `.look`).

Aliases that share a simple name with a command win when you type `.name newtext`
(that changes the alias’s replacement text). Plugins may register additional
commands via `RegisterSpecialCommand`; those appear only while the plugin is
loaded.

Registrations live in `Connection` (built-ins) and Lua
`RegisterSpecialCommand(...)` (plugins).

## Aliases and triggers (patterns / `$1`)

BlowTorch does not use TinTin-style `%1` wildcards. Patterns are **regular
expressions** (Java regex). Captured pieces go into `$1`, `$2`, … in the
replacement or trigger action.

### Aliases

Open the alias editor from the session Options / editors list.

- **Replace** = pattern to match what you type  
- **With** = text to send (can include `$1`, `$2`, …)  
- Optional **start of line (^)** / **end of line ($)** checkboxes

Examples:

| Replace | With | You type | Sent |
|---------|------|----------|------|
| `c` | `cast` | `c fireball` | (word alias; see below) |
| `^cast (.+)$` | `c $1` | `cast fireball` | `c fireball` |
| `^kill (.+)$` | `k $1` | `kill goblin` | `k goblin` |

Notes:

- Without `^`/`$`, BlowTorch wraps the pattern in word boundaries and uses
  normal regex `$n` groups in **With**.
- With **both** `^` and `$`, `$1` is the first `(…)` group in the pattern
  (example above).
- With **only** `^` (no trailing `$`), the line is split on spaces: `$0` is
  the first word, `$1` the next, and so on.

**Changing an alias from the input bar:** for a simple word name (letters,
digits, `_`), type:

```
.name new replacement text
```

That updates the **With** field only. Example: alias key `c`, then
`.c cast 'fireball'` sets the replacement. Patterns with spaces or regex
(like `^cast (.+)$`) must be edited in the alias dialog — the `.name …`
shortcut only works for simple `\w+` keys.

### Triggers

In the trigger editor:

- **Literal?** on → match the pattern as plain text (no regex)  
- **Literal?** off → pattern is a regular expression  
- **Group** → optional label (e.g. `combat`); blank = ungrouped. Use the
  **Group** dropdown (existing names) or type a new name below it. The Triggers
  list shows `[group]`, sorts by group, and has **Plugin** / **Group** spinners
  under search (All / Main / plugins, and All groups / (default) / named).
  Options (=) is Enable/Disable all for the current filter.
- **Conditions** → extra gate after the pattern matches — not a substitute
  for the pattern. Optional AND/OR list checked before responders. Empty =
  always fire. Types: Trigger enabled/disabled (pick another trigger;
  `plugin:name` ok), Variable equals/exists. Set vars with the **Set
  Variable** responder or Lua `SetVariable` / `GetVariable` /
  `UnsetVariable` (session only, not persisted).

In regex mode you can capture with `(…)` and use `$1`, `$2`, … in Ack,
Replace, Toast, Notification, Set Variable text, and similar actions.

Examples (Literal off):

| Pattern | Action text | Meaning |
|---------|-------------|---------|
| `You hit (.+) for (\d+)` | `emote crushed $1 ($2 dmg)` | Name → `$1`, damage → `$2` |
| `A (.+) appears` | `kill $1` | Auto-target the thing that appeared |

**Sample — fire only if another trigger is enabled:** create trigger
`combat_mode` (any pattern; leave it disabled until you want the mode on). On
a second trigger, under **Conditions**, Add → Trigger enabled → pick
`combat_mode`. Responders on the second trigger run only while `combat_mode`
is enabled (`.trigger on combat_mode`).

**GMCP note:** a **literal** trigger whose pattern starts with `%` (default
GMCP character) is a GMCP hook (`%module.path`), not a line wildcard. See
GMCP below.

Options → Service → **Regular Expression Warning?** controls the reminder
dialog when you turn Literal off.

### Aliases and Timers list filters

Triggers, Aliases, and Timers lists show **Plugin** (All / Main / plugins) next
to search. Triggers and Timers also show **Group** (All groups / (default) /
named). Changing a spinner rebuilds the list and shows a short toast.
Enable/Disable all (Triggers/Aliases) stays under Options (`=`).

Timers support an optional **Group** in the timer editor (same idea as
triggers): list subtitle `[group]`, sort by group, XML `group` attribute.

Timers also support **Conditions** in the timer editor — an extra gate when
the timer fires (same AND/OR types as triggers). Empty = always fire
responders. Types: Trigger enabled/disabled, Variable equals/exists. Set
vars with the **Set Variable** responder or Lua `SetVariable` /
`GetVariable` / `UnsetVariable` (session only).

## Built-in commands

| Command | Description |
|---------|-------------|
| `.colordebug <0\|1\|2\|3>` | ANSI color debug: `0` normal; `1` color on + codes; `2` color off + codes; `3` color off, no codes |
| `.closewindow` | Dirty-exit the game window |
| `.note <text>` | Client-only echo to the game window; never sent to the MUD. Useful for button tips and debugging |
| `.trigger …` | Enable/disable triggers (`on`/`off`/`toggle`/`status`/`group`/`all`/`plugin`; main + plugins); see below |
| `.timer <action> <name> [silent]` | Timer control: `play`, `pause`, `reset`, `stop`, `info`. Optional third token suppresses toasts (not `info`) |
| `.dobell` | Fire configured bell reaction |
| `.togglefullscreen` | Toggle fullscreen preference |
| `.wrap [on\|off]` | Input bar growth (default on); also Options → Input → Grow Input Bar? |
| `.gmcp …` | GMCP helpers (status / sniff / version / supports / dump / send); see below |
| `.mcp …` | MCP helpers (Mud Client Protocol `#$#`); see below |
| `.keyboard` / `.kb` | Input-bar control — see `.kb` section below |
| `.disconnect` | Disconnect the current session (same as overflow **Disconnect**) |
| `.reconnect` | Reconnect the current session (same as overflow **Reconnect**) |
| `.run <directions>` | Speedwalk; mapping from **Speedwalk Directions**; commas insert free-text commands |
| `.loadset <setname>` | Built-in stub; `button_window` overrides to load a button set |
| `.clearbuttons` | Clear on-screen buttons (`button_window` may re-register) |
| `.switch <connection>` | Switch foreground UI to another open connection by display name |
| `.search …` | Scrollback search; see forms below |
| `.map …` | Built-in Mapper (record/draw/links/find/path/maps); see Mapper |

### `.trigger` forms

```
.trigger
.trigger on <name|plugin:name>
.trigger off <name|plugin:name>
.trigger toggle <name|plugin:name>
.trigger status [name]
.trigger group on <group>
.trigger group off <group>
.trigger group toggle <group>
.trigger all on
.trigger all off
.trigger plugin <plugin> all on|off
```

Unqualified names resolve **main settings** first, then a unique plugin
match; use `plugin:name` when names collide. Names and groups may contain
spaces (rest of line after the action). `status` with no name prints
main + plugin counts. Empty group name matches the default group (exact
string match, same as Lua `EnableTriggerGroup`). Group commands apply to
**main + all plugins**. `.trigger all` affects main only; use
`.trigger plugin <plugin> all on|off` for one plugin.

### `.search` forms

```
.search phrase
.search 'phrase with spaces'
.search "phrase"
.search next | n
.search prev | previous | p
.search close | hide | clear
```

Empty argument opens the search UI. Buttons may also use `/search 'phrase'`.

## Mapper

Built-in room map (not the legacy ForgeMap plugin). Open from overflow **Map**,
or `.map` / `.map open` / `.map close` / `.map toggle`. Prefer floating or
fullscreen with `.map mode float|fullscreen` (also **Options → Mapper**).
Floating windows can be dragged and resized; opacity is in Options. The overlay
stays under the ⋮ chrome so overflow remains reachable.

### Concepts

| Idea | Meaning |
|------|---------|
| **Tile** | One room cell on a grid (x, y) plus a **level** |
| **Exit** | Edge from tile A to B via a walk command (`n`, `go west`, `out`, …) |
| **Current** | Where the mapper thinks you are (green highlight) |
| **Selected** | Tile you tapped (yellow outline); used by Edit / Here / Links |
| **Follow** | Camera keeps current centered; pan/pinch turns Follow off until you Center |
| **Level nest** | A floor anchored on a door/stairs tile (not one global stack) |

Maps are JSON files under `/BlowTorch/maps/` (autosave ~2s after changes; **Save**
in **File** or `.map export` / `.map save` forces a write). With a path,
`.map export|save <path>` writes JSON there (absolute or BlowTorch-relative).
`.map import <path|name>` loads JSON from an absolute/BlowTorch-relative path
or a maps-dir name, then copies it into `/BlowTorch/maps/`.

Title bar shows a breadcrumb when you are on a nested floor, e.g.
`map · L-1 ← Hallway` (map name · level · door you entered from). **[REC]** means
recording is on. The title also has a **Browse | Edit** segment (**Browse** is
default). **Edit** is required to create nests, use **Draw** / **Links**, and
delete levels. Long-press the title opens the **Floors** radial.

### Category radials (top chips)

Under the title: **Nav** · **Floors** · **Build** · **File**. Each opens an
in-map pie menu (no system status-bar flash).

| Chip | Actions |
|------|---------|
| **Nav** | Rec, Follow, Center, Find, Undo, Close |
| **Floors** | List, ↑, ↓, Root, Door, Delete (**Delete** = **Edit** only) |
| **Build** | Draw, Links, Paths, Here, Edit (tile) |
| **File** | Save, Maps, New, Export |

**Floors → List**: tap a floor to view; long-press = Go Here. In **Edit**, the
browser also offers **Delete…** (confirm) — removes that floor and all its tiles.
You cannot delete the last level.

**Draw** (Edit): grid on; tap empty cell to place; long-press empty = place + Here.
**Links** (Edit): tap FROM then TO, then enter a walk verb (or unlink).
**Paths** / **Pack**: Paths spaces tiles for arrows; Pack compresses neighbors.
**Here**: set current to the selected tile. Tile **Edit** dialog: title / notes /
level / exits. **Save** / **Export**: write map file now. **Full** / **Float** /
**✕**: presentation mode / close.

Long-press a tile and drag to move it on the grid (release without moving opens the
tile menu). **Double-tap** a tile = **Set as Here**. Double-tap empty map = center on
current.

Exits with a known destination draw as **arrows** between tiles with walk-word labels
(`go west`, `n`, …). Use **Paths** so arrows/labels fit between tiles. If more than
two commands share the same edge, the label shows `cmd1 · cmd2 +N` — tap it to open
the full list (and optionally unlink). Cross-level exits show **▲** / **▼** / **◆**
badges on the tile; tap a badge to jump to that floor (browse only).

### Levels (tile-anchored)

Floors nest **per Here tile**: each stairs/door can have its own basement or attic.
**↑** / **↓** (Floors radial) follow an existing nest from Here or return to the
anchor door when leaving (**Browse**). Creating a nest needs **Edit**.

**Browse floors:** **Floors → List** (name, tile count, “via …” for nests; tap =
view; long-press = Go Here), or tap ▲/▼/◆ badges. **Root** / **Door** jump to the
root floor or the nest’s anchor door.

Recording still maps `up`/`down` while you walk. Use **↑**/**↓** when you need a
pocket floor that is not a simple vertical stack.

### Movement lexicon

Planar grid: **+x = east**, **+y = south** (north decreases y).

| Commands | Effect |
|----------|--------|
| `n`/`north`, `s`/`south`, `e`/`east`, `w`/`west` (and `go`/`walk`/`move` prefixes) | Neighbor on the same level |
| `ne`/`nw`/`se`/`sw` | Diagonal |
| `u`/`up`/`climb`/`ascend` | Level +1 (while Recording) |
| `d`/`down`/`descend` | Level −1 (while Recording) |
| `in`/`enter`, `out`/`leave`/`exit`, other text | Special exit (nearby cell, not a compass step) |

Recording prefers this built-in compass lexicon before Speedwalk key bindings
(default `.run`: `h`=nw, `j`=ne, `k`=sw, `l`=se), so typing `go se` still places a
southeast neighbor. Custom Speedwalk commands still apply for non-compass verbs.
Print the summary with `.map dirs`.

### Commands

| Command | Description |
|---------|-------------|
| `.map` | Help / status |
| `.map open\|close\|toggle` | Show or hide the map UI |
| `.map mode fullscreen\|float` | Presentation mode |
| `.map mode browse\|edit\|toggle` | Map interaction mode (Browse default; Edit for nests/Draw/Links/delete) |
| `.map record on\|off\|toggle` | Record movement into tiles/exits (`rec` alias) |
| `.map follow on\|off\|toggle` | Keep the view centered on you |
| `.map level list\|prev\|next\|set <name>` | List / nest down (prev) / nest up (next) / jump by name |
| `.map level delete <id\|name>` | Delete a floor and all its tiles (Edit; cannot delete last) |
| `.map level move <tileId> <level>` | Move a tile onto another level |
| `.map find <query>` | Search rooms (`search` alias) |
| `.map path <query>` | Show path commands (no send) |
| `.map goto <query>` | Path; send only if **Path auto-send** is on (`go` alias) |
| `.map center` | Center on current room |
| `.map undo` | Undo last graph change |
| `.map dirs` | Movement lexicon / grid offsets |
| `.map maps` / `.map load <name>` / `.map new <name>` | Multiple maps |
| `.map export` / `.map save` | Save now (`/BlowTorch/maps/`) |
| `.map export\|save <path>` | Write JSON to that path |
| `.map import <path\|name>` | Import JSON (path or maps-dir name); copy into maps |
| `.map zoom in\|out\|reset` | Zoom the open map UI |
| `.map zoom <factor>` | Zoom by scale factor (map UI must be open) |
| `.map add [x y] [title] [here]` | Place a tile (optional title; `here` sets current) |
| `.map here [id]` | Mark current position |
| `.map delete [id]` | Remove a tile (and links to it) |
| `.map neighbor <cmd> [from <id>]` | Create/link a neighbor by walk verb |
| `.map move [id] <x> <y>` | Reposition a tile on the grid |
| `.map title` / `.map note` | Edit current tile text |
| `.map title for <id> <text>` / `.map note for <id> <text>` | Edit a specific tile |
| `.map link <cmd> [from <id>] to <tileId>` | Manual link |
| `.map unlink <cmd> [from <id>]` | Remove an exit |
| `.map conflict` / `list` / `list all` | List open conflicts (or all, including resolved) |
| `.map conflict resolve\|ignore <id\|n>` | Mark one conflict resolved |
| `.map conflict resolve\|ignore all` | Mark all open conflicts resolved |
| `.map conflict purge` | Remove resolved conflicts (open ones kept) |
| `.map capture preview` | Match Options title/exits regex on recent buffer |
| `.map capture apply` | Apply last preview to the current tile |

**Options → Mapper:** enable module, float/fullscreen default, opacity,
recording defaults, follow, path auto-send, Use GMCP Room, auto reverse links,
legacy toolbar CSV (UI uses **Nav/Floors/Build/File** chips),
**Capture Title Regex** / **Capture Exits Regex** (keys
`mapper_capture_title_regex` / `mapper_capture_exits_regex`; used by
`.map capture`).

**GMCP Room:** with GMCP and **Use GMCP Room** on, `Room.*` syncs the current
room title (and related hints) and creates missing neighbors from Room exits
(compass / level / special), without deleting exits not in the GMCP list.
Without GMCP (typical on many MOOs), use **Rec** while walking, switch to
**Edit** for **Draw** / **Links**, and/or `.map capture`.

### Typical workflows

1. **Record while exploring:** `.map new mymap` → open map → **Nav → Rec** → walk → Rec again to stop → **File → Save**.
2. **Draw by hand:** title **Edit** → **Build → Draw** → tap empty cells → **Links** → **Here** on your room.
3. **Fix layout:** long-press a tile and drag (or **Move…**). Use **Paths** to see arrows.

### `.run` defaults

Direction letters (editable in Speedwalk Directions): `n e s w u d`,
`h`=nw, `j`=ne, `k`=sw, `l`=se. Prefix with a count. Examples:
`.run 3desw2n`, `.run 3ds,open door,3w`.
Mapper recording still treats `se`/`sw`/… as compass diagonals even when those
letters are Speedwalk *keys*.

### `.keyboard` / `.kb`

| Token | Action |
|-------|--------|
| *(no args)* | Print help |
| `add` / `popup` + text | Set or append input; `popup` also shows the IME |
| `flush` | Send current input |
| `close` / `clear` | Hide IME / clear text |
| `sel` / `selectall` | Select all |
| `cut` / `copy` / `paste` | Clipboard |
| `start` / `cursorstart` | Caret to start |
| `end` / `cursorend` | Caret to end |
| `stepf` / `stepr` | Caret one character right |
| `stepb` / `stepl` | Caret one character left |
| `stepu` / `stepd` | Command history (↑ older / ↓ newer), like keyboard arrows; within multiline text, move one line first |


Examples: `.kb popup reply`, `.kb sel`, `.kb cut`, `.kb start`, `.kb end`, `.kb stepf`, `.kb stepb`.

**Edit** on the input bar expands Sel/Cut/Copy/Paste plus a compact **← ↑ ↓ →** pad (hidden again with **Hide**). ↑/↓ recall previous commands (same as keyboard up/down); ←/→ move the caret.

## Copy text from the game window

- **First finger** — touch where selection should start (marks the start).
- **Second finger** — tap to open the selection / copy widget.
- One-finger long-press alone does not open copy.
- Drag the cursors, then use the widget’s copy control. On-screen buttons may hide while selecting so the widget stays usable.

## Font size

New profiles start around font size **20** (readable on phones). Change under
Options → Window → Font Size.

## On-screen buttons: swipe + accordion

**Edit layout:** open **⋮ → Edit buttons**, or long-press the **⋮** next to Edit/Send. In edit mode ⋮ is hidden — use the strip icons: gear (set options), **Cancel** left, **Done** right.

The default `button_window` plugin supports more than tap:

- **Swipe up / down / left / right** — each direction can run a different command (edit button → Swipe). Overrides classic Flip. Drag roughly a finger-width off the tile.
- **Hold** — optional command after press-and-hold.
- **Accordion** — up to five child buttons expand from a parent (direction + tap/hold/swipe trigger). Handy when you want several macros on one tile. Editor badges: **T** tap, **H** hold, **S** swipe. Options can draw gesture hint arrows (uncheck to hide U/D/L/R and Hold markers).

## GMCP (short)

Enable under **Options → Service → GMCP Options**. Prefer **Manage modules…**
over editing the raw Supports String. Helpers:

```
.gmcp                 — help
.gmcp ask|handshake   — Hello / enabled / native / seen (honest)
.gmcp modules         — enabled vs seen this session
.gmcp enable|disable  — toggle modules (+ live Add/Remove)
.gmcp renegotiate     — re-send Hello + Supports.Set
.gmcp status          — flags
.gmcp sniff [on|off]  — log handshake/packets
.gmcp sniff tail [N]  — last N GMCP lines in-game (0–100, default 40)
.gmcp feed [on|off]   — live IN/OUT GMCP in the mud window
.gmcp version         — client hello / syntax notes
.gmcp supports […]   — show or set supports modules
.gmcp dump [path]     — dump cached GMCP table
.gmcp send <payload>  — queue a GMCP packet
```

## MCP (short)

Mud Client Protocol ([MCP 2.1](https://www.moo.mud.org/mcp/)) — in-band `#$#…`
(not GMCP). Off by default. **Options → Service → MCP Options**. Prefer **Manage packages…**.

Native packages (when enabled): hellmoo-status vitals, simpleedit editor,
displayurl (browser), ping auto-reply, mcp-cord, vmoo-client info.

```
.mcp                  — help
.mcp ask|status       — handshake / flags
.mcp packages         — enabled vs seen
.mcp enable|disable   — toggle packages
.mcp renegotiate      — re-send mcp-negotiate-can
.mcp sniff|feed|dump|vitals|send|ping|client
.mcp cord open|close|send|list
```

Lua: `Send_MCP_Packet(s)`, `Get_MCP_Status()`, literal triggers `@message-name`
(same idea as GMCP `%module`).

Optional protocols (off by default; Options → Service → **MUD Protocols**):

```
.mssp   — dump MSSP cache (enable Use MSSP? first, reconnect)
.msdp   — dump MSDP cache (enable Use MSDP? first, reconnect)
```

## Plugin commands (when loaded)

### `button_window` (default Free build)

| Command | Description |
|---------|-------------|
| `.loadset <name>` | Load named button set |
| `.clearbuttons` | Clear via button window |

### `starter_tutorial` (loaded by default)

| Command | Description |
|---------|-------------|
| `.tutorial …` | Starter Tutorial: `help` / `start` / `next` / `prev` / `skip` / `done` / `topics` / `<topic>` |

On the default button set, tap **HELP** to run `.tutorial start`. The launcher
lists a built-in **Starter Tutorial** row first (offline — no MUD). Disable the
welcome note on normal MUDs via **Options → Starter Tutorial → Show on connect**,
or type `.tutorial done`. Toggle `starter_tutorial` off under **Plugins**, or unload
it entirely to remove the plugin.

## Session overflow menu

1. **Map** — open / toggle the built-in Mapper (also `.map open|toggle`)  
2. **Edit buttons** — enter button layout edit mode  
3. **Crash report** — Show log / Share log  
4. **About**  
5. **Help** — This manual  

Connection duration appears on the ongoing notification and launcher row.

**Persistent Connection?** (Options → Miscellaneous): after brief network loss
(VPN/Wi-Fi flaps), keep retrying longer without the disconnect dialog and wait
for connectivity before reconnecting. Cannot keep a dead TCP socket — the MUD
session is re-established when the network returns.

## Related docs

- [`options-guide.md`](options-guide.md) — Options / storage layout  
- [`FDROID_README.md`](FDROID_README.md) — permissions for F-Droid  
