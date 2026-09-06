#!/usr/bin/env bash
# Post a status-bar notification on the phone over adb (USB or wifi).
#
#   scripts/notify-device.sh                       # "BlowTorch deployed"
#   scripts/notify-device.sh "Do sprawdzenia" $'1. …\n2. …'
#   scripts/notify-device.sh "Do sprawdzenia" < card.txt
#
# Same tag every time, so a later card replaces the install ping.
# Does not fail the caller if the phone is gone or notifications are off.

set -uo pipefail
cd "$(dirname "$0")/.."

ADB="${ADB:-$HOME/Android/Sdk/platform-tools/adb}"
TAG="bt-deploy"
TITLE="${1:-BlowTorch deployed}"
MAX_CHARS=4000

if [ $# -ge 2 ]; then
	BODY="$2"
elif [ ! -t 0 ]; then
	BODY="$(cat)"
else
	BODY="APK zainstalowany."
fi

if [ "${#BODY}" -gt "$MAX_CHARS" ]; then
	BODY="${BODY:0:$MAX_CHARS}"$'\n… (obcięte)'
fi

SERIAL="${NOTIFY_SERIAL:-}"
if [ -z "$SERIAL" ]; then
	SERIAL="$(scripts/adb-device.sh)" || {
		echo "notify-device: no device, skipped." >&2
		exit 0
	}
fi

# Remote sh splits on spaces unless the whole command is one quoted string.
qtitle=${TITLE//\'/\'\\\'\'}
qbody=${BODY//\'/\'\\\'\'}

if ! "$ADB" -s "$SERIAL" shell \
	"cmd notification post -S bigtext -t '$qtitle' '$TAG' '$qbody'" \
	>/dev/null; then
	echo "notify-device: post failed on $SERIAL (APK still installed if that already ran)." >&2
	exit 0
fi
echo "notify-device: posted on $SERIAL"
