# BlowTorch Media Studio

Terminal UI for recording short phone demos (scrcpy → MP4 + GIF) for README and
docs. English interface; maintainer-side tool only — not part of the Android app.

## Quick start

```sh
tools/media-studio/run.sh
```

First run creates a local venv under `tools/media-studio/.venv/` (gitignored).

## Requirements

On `PATH` (user-local installs are fine):

| Tool | Purpose |
|------|---------|
| `adb` | Phone connection |
| `scrcpy` | Screen capture |
| `ffmpeg` | Trim / scale / MP4 |
| `gifski` | GIF export |

Optional: `scripts/adb-device.sh` in the repo root (gitignored locally) for
wifi/USB device discovery. Without it, the TUI falls back to the first `adb
devices` entry.

## Screens

- **Status** — tool checks, connected device, quick start
- **Record** — clip name, duration, mirror toggle, live log
- **Library** — list `docs/media/`, copy README markdown, re-encode raw, delete

### Keys (Library)

| Key | Action |
|-----|--------|
| `c` | Copy `![name](docs/media/name.gif)` to clipboard |
| `e` | Re-encode selected raw MP4 → MP4 + GIF |
| `d` | Delete selected file |
| `o` | Open `docs/media/` in the file manager |
| `F5` | Reload file list |

## Git safety

**Safe to commit**

- Everything under `tools/media-studio/` (this tool)
- `docs/media/README.md` and `.gitkeep`

**Never commit**

- `docs/media/*.mp4` and `*.gif` — may show game/session content (gitignored)
- `scripts/adb-device.sh` — machine-specific IPs and device lab config
- `.venv/` — local Python environment

Recorded pixels can contain character names, chat, or world text even in a
“harmless” demo. Treat outputs like screenshots from a live session.

## CLI alternative

```sh
scripts/demo-record.sh buttons 8 --window
```

The TUI calls that script for recording; encoding settings match
`scripts/demo-record.sh`.
