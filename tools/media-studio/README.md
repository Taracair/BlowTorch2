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
| `ffmpeg` | Trim / crop / scale / MP4 |
| `gifski` | GIF export |

Optional: `scripts/adb-device.sh` in the repo root (gitignored locally) for
wifi/USB device discovery. Without it, the TUI falls back to the first `adb
devices` entry.

## Screens

- **Status** — tool checks, connected device, quick start
- **Record** — clip name, duration, mirror toggle, live log
- **Library** — list `docs/media/`, copy README markdown, open Crop, re-encode, delete
- **Crop** — presets or custom X/Y/W/H, trim start/duration, preview frames, encode

### Keys (Library)

| Key | Action |
|-----|--------|
| `x` | Open Crop for the selected clip (needs a `*-raw-*.mp4`) |
| `c` | Copy `![name](docs/media/name.gif)` to clipboard |
| `e` | Re-encode selected raw MP4 → MP4 + GIF (full frame) |
| `d` | Delete selected file |
| `o` | Open `docs/media/` in the file manager |
| `F5` | Reload file list |

### Crop workflow

1. Record the **full** phone screen (do not try to crop while recording).
2. Library → select the raw (or any file from that clip) → **Crop…** / `x`.
3. Pick a preset (e.g. *Bottom 60%* for button pads) or type custom X/Y/W/H.
4. **Open full frame** — measure coordinates in an image viewer.
5. **Preview box** — red rectangle on the full frame; **Preview crop** — only the cut.
6. Set trim start / duration if needed → **Encode MP4+GIF**.
7. Raw stays untouched; `NAME.mp4` / `NAME.gif` are overwritten.

Coordinates are pixels from the **top-left** of the raw frame (same size ffmpeg
reports, e.g. 480×1080 after scrcpy `-m 1080`).

## Git safety

**Committed in this repo**

- Everything under `tools/media-studio/` (this tool)
- `scripts/demo-record.sh`
- `docs/media/README.md` and `.gitkeep`

**Never commit**

- `docs/media/*.mp4`, `*.gif`, `*-preview.png` — may show game/session content
- `scripts/adb-device.sh` — machine-specific IPs and device lab config
- `.venv/` — local Python environment

Recorded pixels can contain character names, chat, or world text even in a
“harmless” demo. Treat outputs like screenshots from a live session.

## CLI alternative

```sh
scripts/demo-record.sh buttons 8 --window
```

The TUI calls that script for recording; Crop re-encodes through the same
ffmpeg/gifski pipeline with an optional crop filter.
