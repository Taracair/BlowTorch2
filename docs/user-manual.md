# BlowTorch User Manual

Source of truth for in-app **Help**. Keep this file in sync with
`BTLib/res/raw/user_manual.txt` (packaged into the Help dialog).

## Dot commands

Lines that start with a single `.` are handled by BlowTorch when “process period”
is enabled (default). Type `..` alone to toggle that processing on or off.
Prefix a server command with `..` to send a leading `.` to the game without
running a client command (e.g. `..look` sends `.look`).

Aliases that share a name with a command win when you type `.name newtext`
(that updates the alias). Plugins may register additional commands via
`RegisterSpecialCommand`; those appear only while the plugin is loaded.

Registrations live in `Connection` (built-ins) and Lua
`RegisterSpecialCommand(...)` (plugins).

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
| `stepu` / `stepd` | Caret one line up / down |

Examples: `.kb popup reply`, `.kb sel`, `.kb cut`, `.kb start`, `.kb end`, `.kb stepf`, `.kb stepb`.

**Edit** on the input bar expands Sel/Cut/Copy/Paste plus a compact **← ↑ ↓ →** pad (hidden again with **Hide** so the screen stays clear).

## On-screen buttons: swipe + accordion

**Edit layout:** long-press the **⋮** (three dots) next to Edit/Send. Cancel is on the left, Done on the right; the gear opens set options.

The default `button_window` plugin supports more than tap:

- **Swipe up / down / left / right** — each direction can run a different command (edit button → Swipe). Overrides classic Flip. Drag roughly a finger-width off the tile.
- **Hold** — optional command after press-and-hold.
- **Accordion** — up to five child buttons expand from a parent (direction + tap/hold/swipe trigger). Ideal for packing macros into one tile. Editor badges: **T** tap, **H** hold, **S** swipe. Options can draw gesture hint arrows (uncheck to hide U/D/L/R and Hold markers).

## GMCP (short)

Enable under **Options → Service → GMCP Options**. Helpers:

```
.gmcp                 — help
.gmcp status          — flags
.gmcp sniff [on|off]  — log handshake/packets
.gmcp sniff tail [N]  — last N GMCP lines in-game (0–100, default 40)
.gmcp version         — client hello / syntax notes
.gmcp supports […]   — show or set supports modules
.gmcp dump [path]     — dump cached GMCP table
.gmcp send <payload>  — queue a GMCP packet
```

## Plugin commands (when loaded)

### `button_window` (default Free build)

| Command | Description |
|---------|-------------|
| `.loadset <name>` | Load named button set |
| `.clearbuttons` | Clear via button window |

### `forgemap` (test / optional)

| Command | Description |
|---------|-------------|
| `.map` | Toggle map window |
| `.fmhere [label]` | Mark current position (default `"Here"`) |
| `.fmwalk <dir>` | Record a manual move |
| `.fmgo <tileId>` | Pathfind and `.run` toward tile |
| `.fmnote <text>` | Set note on current tile |
| `.fmflag <flag>` | Set flag on current tile |

## Session overflow menu

1. **Crash report** — Show log / Share log  
2. **About/donate**  
3. **Help** — This manual  

Connection duration appears on the ongoing notification and launcher row.

## Related docs

- [`options-guide.md`](options-guide.md) — Options / storage layout  
- [`FDROID_README.md`](FDROID_README.md) — permissions for F-Droid  
