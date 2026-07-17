# Options (session)

In-game **Options** dialog groups (Program Settings):

| Group | Purpose |
|-------|---------|
| *(root)* | Encoding, orientation, keep screen on, fullscreen |
| **Window** | Font, buffer, word wrap, etc. (window token settings) |
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
- **Default Settings Directory** (Miscellaneous) is the preferred default folder for session Import/Export (used by upcoming file-picker flow).

## Dot commands

(Documented in Help — keep in sync when adding `.wrap`, etc.)
