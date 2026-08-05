# Demo media (local outputs)

Short screen recordings for README and docs. **Generated files are gitignored**
because they may show game text, character names, or other session content.

## Record

```sh
tools/media-studio/run.sh
```

Or without the TUI:

```sh
scripts/demo-record.sh buttons 8 --window
```

## What gets committed

- Tool source under `tools/media-studio/`
- This README and `.gitkeep` only

## What stays local

- `*.mp4`, `*.gif` in this folder (see root `.gitignore`)

## Publishing one clip

A clip is only visible on GitHub once it is tracked, and the ignore rule keeps
that from happening by accident. Watch the clip first, then opt it in by hand:

```sh
git add -f docs/media/buttons.gif
```
