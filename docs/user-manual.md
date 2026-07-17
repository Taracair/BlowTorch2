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
| `.keyboard` / `.kb` | Input-bar control (`add`, `popup`, `flush`, `close`, `clear`, `selectall`, `copy`, `paste`, `cursorstart`, `cursorend`); no args prints help |
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

### `.keyboard` / `.kb` examples

- `.kb popup reply` — set text and show IME  
- `.kb add foo` — append without popup  
- `.kb flush` — send current input  
- `.kb clear` / `.kb close`

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

Order at the bottom of the expandable menu:

1. **Crash report** — Show log / Share log (app error log under BlowTorchLogger)  
2. **About/donate** — Version, description, donate placeholder + short PL legal note  
3. **Help** — This manual (nearly full-screen)

## Related docs

- `docs/options-guide.md` — Options groups (Service session log, Miscellaneous storage, …)  
- `docs/smoke-test-feedback.md` — items 8–10 (Help / Crash report / About·donate)
