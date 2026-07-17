# Options (session)

In-game **Options** dialog groups (Program Settings):

| Group | Purpose |
|-------|---------|
| *(root)* | Encoding, orientation, keep screen on, fullscreen |
| **Window** | Font, buffer, word wrap, **Grow Input Bar?** (`.wrap`), etc. (window token settings) |
| **Input** | Input box / editor behavior (history size, keep last, …) |
| **Service** | Background service & **game output** logging (`Log Session to File?`, `Session Log Directory`) |
| **GMCP Options** | nested under Service |
| **Bell** | Bell character reactions |
| **Miscellaneous** | Default settings directory (for import/export), manage storage access |

## Session log

- Enable: **Options → Service → Log Session to File?**
- Custom folder: **Options → Service → Session Log Directory** (blank = app private `files/session_logs/`)
- Logs incremental plain text of **incoming game output** (ANSI stripped), not keyboard input.

## Storage

- **Options → Miscellaneous → Manage Storage Access** requests/refreshes storage permission and shows the effective BlowTorch storage root.
- The old overflow item **SDCard Permissions** was removed in favor of this setting.
- **Default Settings Directory** (Miscellaneous): preferred folder for session **Import/Export Settings**. Blank = shared-storage BlowTorch export folder when storage permission is granted, otherwise the app external-files directory.
- Session overflow **Export Settings** / **Import Settings**: SAF pickers plus “default directory” actions; no longer crash on empty names or missing cache/external dirs.

## Dot commands

Full list: in-app **Help** and `docs/user-manual.md` (keep in sync with
`BTLib/res/raw/user_manual.txt` and `Connection` / plugin `RegisterSpecialCommand`).

## Input bar growth

- **Options → Window → Grow Input Bar?** (default on) — when off, the input field stays a single non-growing line.
- Dot command: `.wrap on` / `.wrap off` (no args prints status). Distinct from **Word Wrap?** (game text wrapping).
- **Send** sits to the right of **Edit/Hide**; when grow is off and the input is tall, Send stacks under Edit/Hide.
