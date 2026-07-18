# Options (session)

In-game **Options** dialog groups (Program Settings):

| Group | Purpose |
|-------|---------|
| *(root)* | Encoding, orientation, keep screen on, fullscreen |
| **Window** | Font, buffer, word wrap, etc. (window token settings) |
| **Input** | Input box / editor behavior (history size, keep last, **Grow Input Bar?** / `.wrap`, …) |
| **Service** | Background service & **game output** logging (`Log Session to File?`, `Session Log Directory`); **Battery optimization…** |
| **GMCP Options** | nested under Service (`Use GMCP?`, Supports String, `Log GMCP?`) |
| **Bell** | Bell character reactions |
| **Miscellaneous** | Default settings directory (for import/export), manage storage access |

## Shared storage layout (`/BlowTorch/`)

Default for import/export, backups, launcher lists, session logs, and app/GMCP logs is **outside** `Android/data`:

```text
/storage/emulated/0/BlowTorch/
  settings/       # session Import/Export Settings (default)
  backups/        # launcher Backup All Settings
  launcher/       # server list export/import
  session_logs/   # incremental game .txt logs
  logs/           # blowtorch2.log (errors + GMCP when Log GMCP? is on)
```

On Android 11+ this needs **All files access** once: **Options → Miscellaneous → Manage Storage Access** (opens the system permission screen). Without it the app falls back to `Android/data/…/files/BlowTorch/` with the same subfolders.

## GMCP

GMCP is an optional structured out-of-band channel (telnet option 201). Enable
**Use GMCP?** and set **Supports String** for modules your MUD expects (servers
differ; common starters: `"char 1"`, `"room 1"`). **Log GMCP?** writes handshake
and packets to `/BlowTorch/logs/blowtorch2.log` (and to the session log when that
is enabled). `.gmcp sniff on` prints the absolute path in-game; Overflow → Crash
report → Show log to view. Dot helpers: `.gmcp` (see Help / user-manual).

## Session log

- Enable: **Options → Service → Log Session to File?**
- Blank directory = `/BlowTorch/session_logs/`. Use **Browse…** for SAF or an absolute path.
- Incremental plain text of **incoming game output** (ANSI stripped), not keyboard input.
- Files are named `{profile}_{yyyy-MM-dd_HH-mm-ss}.txt`.

## Background connection / battery

- **Keep Wifi Alive?** (Service) holds a Wi‑Fi lock while connected.
- The service also takes a partial CPU wake lock while any connection is up.
- **Battery optimization…** opens the system exemption flow; a one-shot dialog
  also appears when you are connected if BlowTorch is still battery-optimized.
- Connection duration is shown on the ongoing notification and launcher rows.

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
| **About** | About dialog |

Removed: legacy **Copy Settings to Storage** / Recover (raw dump to `…/recovered/`). Use **Backup All Settings** instead.

**Account notes** on New/Edit connection: optional login/password/mail per server. Notes only — not auto-login. Stored as plain text in the launcher list on this device; see the warning in the dialog.

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
