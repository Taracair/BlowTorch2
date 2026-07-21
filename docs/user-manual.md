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
| `.map …` | Built-in Mapper (open/record/find/path/maps/capture); see Mapper |

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

Built-in room map (not ForgeMap). Open from overflow **Map**, or `.map` /
`.map open` / `.map close` / `.map toggle`. Prefer floating or fullscreen
with `.map mode float|fullscreen` (also Options → Mapper). Floating windows
can be dragged and resized; opacity is in Options. The overlay stays under
the ⋮ chrome so overflow remains reachable.

| Command | Description |
|---------|-------------|
| `.map` | Help / status |
| `.map open\|close\|toggle` | Show or hide the map UI |
| `.map mode fullscreen\|float` | Presentation mode |
| `.map record on\|off\|toggle` | Record movement into tiles/exits |
| `.map follow on\|off` | Keep the view centered on you |
| `.map level list\|prev\|next\|set <name>` | Levels |
| `.map find <query>` | Search rooms |
| `.map path <query>` | Show path commands (no send) |
| `.map goto <query>` | Path; send only if **Path auto-send** is on |
| `.map center` | Center on current room |
| `.map undo` | Undo last graph change |
| `.map maps` / `.map load <name>` / `.map new <name>` | Multiple maps |
| `.map export` | Save now (`/BlowTorch/maps/`) |
| `.map title` / `.map note` / `.map link` / `.map unlink` | Edit current tile |
| `.map conflict list` | Conflict inbox |
| `.map capture preview` | Test capture patterns on a pasted sample (or recent buffer lines when available) |
| `.map capture apply` | Apply capture to the current tile |

**Options → Mapper:** enable module, float/fullscreen default, opacity,
recording defaults, follow, path auto-send, Use GMCP Room, toolbar actions,
capture profile.

**GMCP Room:** with GMCP and **Use GMCP Room** on, `Room.*` can sync title /
position / exits. Without GMCP, use capture patterns + preview/apply.

### `.run` defaults

Direction letters (editable in Speedwalk Directions): `n e s w u d`,
`h`=nw, `j`=ne, `k`=sw, `l`=se. Prefix with a count. Examples:
`.run 3desw2n`, `.run 3ds,open door,3w`.

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
