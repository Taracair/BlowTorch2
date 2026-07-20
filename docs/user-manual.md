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

In regex mode you can capture with `(…)` and use `$1`, `$2`, … in Ack,
Replace, Toast, Notification text, and similar actions.

Examples (Literal off):

| Pattern | Action text | Meaning |
|---------|-------------|---------|
| `You hit (.+) for (\d+)` | `emote crushed $1 ($2 dmg)` | Name → `$1`, damage → `$2` |
| `A (.+) appears` | `kill $1` | Auto-target the thing that appeared |

**GMCP note:** a **literal** trigger whose pattern starts with `%` (default
GMCP character) is a GMCP hook (`%module.path`), not a line wildcard. See
GMCP below.

Options → Service → **Regular Expression Warning?** controls the reminder
dialog when you turn Literal off.

## Built-in commands

| Command | Description |
|---------|-------------|
| `.colordebug <0\|1\|2\|3>` | ANSI color debug: `0` normal; `1` color on + codes; `2` color off + codes; `3` color off, no codes |
| `.closewindow` | Dirty-exit the game window |
| `.timer <action> <name> [silent]` | Timer control: `play`, `pause`, `reset`, `stop`, `info`. Optional third token suppresses toasts (not `info`) |
| `.dobell` | Fire configured bell reaction |
| `.togglefullscreen` | Toggle fullscreen preference |
| `.wrap [on\|off]` | Input bar growth (default on); also Options → Input → Grow Input Bar? |
| `.gmcp …` | GMCP helpers (status / sniff / version / supports / dump / send); see below |
| `.keyboard` / `.kb` | Input-bar control — see `.kb` section below |
| `.disconnect` | Local “Disconnected.” notice (use overflow **Disconnect** for a real disconnect) |
| `.reconnect` | Local “Reconnecting . . .” notice (use overflow **Reconnect** to reconnect) |
| `.run <directions>` | Speedwalk; mapping from **Speedwalk Directions**; commas insert free-text commands |
| `.loadset <setname>` | Built-in stub; `button_window` overrides to load a button set |
| `.clearbuttons` | Clear on-screen buttons (`button_window` may re-register) |
| `.switch <connection>` | Switch foreground UI to another open connection by display name |
| `.search …` | Scrollback search; see forms below |

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

- **Double-tap** anywhere on the game text to open the selection / copy widget.
- Or **long-press** (system long-press timing) and hold still enough for the widget to appear.
- Drag the cursors, then use the widget’s copy control. The widget is raised above on-screen buttons while selecting.

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

## Session overflow menu

1. **Edit buttons** — enter button layout edit mode  
2. **Crash report** — Show log / Share log  
3. **About**  
4. **Help** — This manual  

Connection duration appears on the ongoing notification and launcher row.

## Related docs

- [`options-guide.md`](options-guide.md) — Options / storage layout  
- [`FDROID_README.md`](FDROID_README.md) — permissions for F-Droid  
