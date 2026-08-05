#!/usr/bin/env bash
# Launch BlowTorch Media Studio (Textual TUI).
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
VENV="$HERE/.venv"

export PATH="${HOME}/.local/bin:${HOME}/.cargo/bin:${PATH}"

if [ ! -d "$VENV" ]; then
  echo "Creating venv in tools/media-studio/.venv …" >&2
  python3 -m venv "$VENV"
  "$VENV/bin/pip" install -q -r "$HERE/requirements.txt"
fi

cd "$HERE"
exec "$VENV/bin/python" -m media_studio --repo "$REPO"
