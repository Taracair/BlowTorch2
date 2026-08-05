#!/usr/bin/env bash
# Record a short BlowTorch phone demo → MP4 + GIF for README / docs.
#
# Requires (user-local ok):
#   scrcpy  (~/.local/bin)   gifski (~/.cargo/bin)   ffmpeg   adb
#   Device: scripts/adb-device.sh
#
# Usage:
#   scripts/demo-record.sh                 # 8s → docs/media/demo-<stamp>.{mp4,gif}
#   scripts/demo-record.sh buttons 10      # name + seconds
#   scripts/demo-record.sh buttons 10 --window   # also mirror on PC while recording
#
# Workflow:
#   1. Open BlowTorch on the phone, set up the scene (font big, DND on).
#   2. Run this script; act on the phone during the countdown.
#   3. Drop the GIF into README:  ![](docs/media/demo-buttons.gif)
#   Prefer GIF in README (autoplays). Keep MP4 for Releases / Discord (much smaller).

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=/dev/null
PATH="${HOME}/.local/bin:${HOME}/.cargo/bin:${PATH}"

NAME="${1:-demo}"
SECS="${2:-8}"
WINDOW=0
if [[ "${3:-}" == "--window" ]] || [[ "${1:-}" == "--window" ]]; then
  WINDOW=1
fi
# allow: scripts/demo-record.sh --window
if [[ "${1:-}" == "--window" ]]; then
  NAME=demo
  SECS=8
fi

MEDIA="${ROOT}/docs/media"
mkdir -p "${MEDIA}"
STAMP="$(date +%Y%m%d-%H%M%S)"
RAW="${MEDIA}/${NAME}-raw-${STAMP}.mp4"
OUT_MP4="${MEDIA}/${NAME}.mp4"
OUT_GIF="${MEDIA}/${NAME}.gif"
FRAMES="$(mktemp -d /tmp/bt-demo-frames.XXXXXX)"
cleanup() { rm -rf "${FRAMES}"; }
trap cleanup EXIT

SERIAL="$("${ROOT}/scripts/adb-device.sh")"
echo "Device: ${SERIAL}"
echo "Recording ${SECS}s → ${RAW}"
echo "Do the demo on the phone now…"

SCRCPY_ARGS=(
  -s "${SERIAL}"
  --no-audio
  -m 1080
  -b 8M
  --time-limit="${SECS}"
  --record="${RAW}"
)
if [[ "${WINDOW}" -eq 0 ]]; then
  SCRCPY_ARGS+=(--no-playback)
fi

scrcpy "${SCRCPY_ARGS[@]}"

echo "Encoding compact MP4…"
ffmpeg -y -hide_banner -loglevel error \
  -i "${RAW}" -ss 0.2 -t "${SECS}" \
  -vf "scale=720:-2:flags=lanczos" \
  -c:v libx264 -crf 23 -preset medium -an -movflags +faststart \
  "${OUT_MP4}"

echo "Extracting frames + gifski…"
ffmpeg -y -hide_banner -loglevel error \
  -i "${RAW}" -ss 0.2 -t "${SECS}" \
  -vf "fps=12,scale=540:-1:flags=lanczos" \
  "${FRAMES}/%04d.png"

gifski -o "${OUT_GIF}" --width 540 --fps 12 "${FRAMES}"/*.png

echo
ls -lh "${RAW}" "${OUT_MP4}" "${OUT_GIF}"
echo
echo "README:"
echo "  ![${NAME}](docs/media/${NAME}.gif)"
echo "Raw kept at ${RAW} (delete when happy)."
