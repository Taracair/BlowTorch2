# Options (session)

In-game **Options** dialog groups (Program Settings):

| Group | Purpose |
|-------|---------|
| *(root)* | Encoding, orientation, keep screen on, fullscreen |
| **Window** | Font, buffer, word wrap, **Grow Input Bar?** (`.wrap`), etc. (window token settings) |
| **Input** | Input box / editor behavior (history size, keep last, …) |
| **Service** | Background service & **game output** logging (`Log Session to File?`, `Session Log Directory`); **Battery optimization…** |
| **GMCP Options** | nested under Service (`Use GMCP?`, Supports String, `Log GMCP?`) |
| **Bell** | Bell character reactions |
| **Miscellaneous** | Default settings directory (for import/export), manage storage access |

## GMCP

GMCP is an optional structured out-of-band channel (telnet option 201). Enable
**Use GMCP?** and set **Supports String** for modules your MUD expects (servers
differ; common starters: `"char 1"`, `"room 1"`). **Log GMCP?** writes handshake
and packets to the app error log (and to the session log when that is enabled).
Dot helpers: `.gmcp` (see Help / user-manual).

## Session log

- Enable: **Options → Service → Log Session to File?**
- Custom folder: **Options → Service → Session Log Directory** (blank = app private `files/session_logs/`)
- Logs incremental plain text of **incoming game output** (ANSI stripped), not keyboard input.

## Background connection / battery

- **Keep Wifi Alive?** (Service) holds a Wi‑Fi lock while connected.
- The service also takes a partial CPU wake lock while any connection is up.
- **Battery optimization…** opens the system exemption flow; a one-shot dialog
  also appears when you are connected if BlowTorch is still battery-optimized.
- Connection duration is shown on the ongoing notification and launcher rows.

## Storage

- **Options → Miscellaneous → Manage Storage Access** requests/refreshes storage permission and shows the effective BlowTorch storage root.
- The old overflow item **SDCard Permissions** was removed in favor of this setting.
- **Default Settings Directory** (Miscellaneous): preferred folder for session **Import/Export Settings**. Blank = shared-storage BlowTorch export folder when that path is actually writable; otherwise the app external-files directory (scoped storage usually cannot create `/storage/emulated/0/BlowTorch` without all-files access).
- Session overflow **Export Settings** / **Import Settings**: SAF pickers plus “default directory” actions; no longer crash on empty names or missing cache/external dirs.
- Launcher **Export Server List** / **Backup All Settings** use the same writable-root rule (`…/launcher/`, `…/backups/` under that root), with SAF **Choose location…** as an alternative.

## Launcher (server list)

Toolbar **⋮** menu (About moved here; bottom **New** only):

| Menu item | What it does |
|-----------|----------------|
| **Import Server List** | Load launcher connections XML (SAF **Pick file…** or default `…/launcher/`) |
| **Export Server List** | Save launcher connections XML (default dir or SAF **Choose location…**) |
| **Backup All Settings** | Zip all private session `*.xml` settings (default `…/backups/` or SAF) |
| **Restore Settings Backup** | Restore that zip (or a scanned backup folder) into private files — restart after |
| **Copy Settings to Storage** | Copy private settings files to `…/recovered/` (salvage/debug; not the same as restore) |
| **About** | About / donate dialog |

**Account notes** on New/Edit connection: optional login/password/mail slots per server. Informational only (not auto-login). Stored in the launcher list XML on device — **plaintext**; see warning in the dialog and `docs/backup-encryption-plan.md`.

Encrypted backup format: planned, not shipped yet — `docs/backup-encryption-plan.md`.

## Dot commands

Full list: in-app **Help** and `docs/user-manual.md` (keep in sync with
`BTLib/res/raw/user_manual.txt` and `Connection` / plugin `RegisterSpecialCommand`).

## Input bar growth

- **Options → Window → Grow Input Bar?** (default on) — when off, the input field stays a single non-growing line.
- Dot command: `.wrap on` / `.wrap off` (no args prints status). Distinct from **Word Wrap?** (game text wrapping).
- **Send** sits to the right of **Edit/Hide**; when grow is off and the input is tall, Send stacks under Edit/Hide.

## Notification responders

Trigger/timer notification responders can use the system default sound, five
bundled presets (soft chime/tap, mid ping/pluck, loud alert), files under
`/BlowTorch/` on shared storage, or **Pick from storage…** (SAF content URI).
