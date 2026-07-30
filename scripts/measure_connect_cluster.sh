#!/usr/bin/env bash
# Measure connect-cluster StrictMode stalls (HANDOFF item 6).
#
# Usage:
#   ./scripts/measure_connect_cluster.sh <adb-serial>
#   ./scripts/measure_connect_cluster.sh <adb-serial> --wait 45
#
# Interactive: clears logcat, you tap a world, press Enter when connected.
# --wait N:    clears logcat, waits N seconds (tap a world during the window), then dumps.
#
# Requires btTest flavour (StrictMode penaltyLog). Never adb uninstall.
set -euo pipefail

SERIAL="${1:?adb serial required (e.g. 10.0.0.2:46133)}"
shift || true

WAIT_SECS=""
while [[ $# -gt 0 ]]; do
	case "$1" in
	--wait)
		WAIT_SECS="${2:?--wait needs seconds}"
		shift 2
		;;
	*)
		echo "Unknown arg: $1" >&2
		exit 1
		;;
	esac
done

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ADB:-$HOME/Android/Sdk/platform-tools/adb}"
OUT="${OUT:-/tmp/connect-logcat-${SERIAL//[:.]/_}.txt}"

echo "Clearing logcat on $SERIAL …"
"$ADB" -s "$SERIAL" logcat -c

if [[ -n "$WAIT_SECS" ]]; then
	echo "Tap a world on the phone within the next ${WAIT_SECS}s …"
	sleep "$WAIT_SECS"
else
	echo "Tap a world NOW. Press Enter when connected and the window is usable."
	read -r _
fi

"$ADB" -s "$SERIAL" logcat -d > "$OUT"
echo "Wrote $OUT"
echo ""
echo "=== Top stalls (quote blocked, not summed duration) ==="
python3 "$ROOT/scripts/strictmode_report.py" "$OUT" | head -40
echo ""
echo "---- BasePluginParser ----"
python3 "$ROOT/scripts/strictmode_report.py" "$OUT" BasePluginParser || true
echo ""
echo "---- ConnectionSettingsIO ----"
python3 "$ROOT/scripts/strictmode_report.py" "$OUT" ConnectionSettingsIO || true
echo ""
echo "---- listItemClicked ----"
python3 "$ROOT/scripts/strictmode_report.py" "$OUT" listItemClicked || true
