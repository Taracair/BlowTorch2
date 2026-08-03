#!/usr/bin/env bash
# Build btTest debug and install it on the maintainer's phone. One command.
#
#   scripts/deploy.sh
#
# Replaces a five-line recipe that appeared, slightly differently, in three
# documents. There is now one copy and it is executable, so it cannot drift.
#
# Reports exactly what happened. Says "installed", never "works": whether it
# works is the maintainer's call after touching the device.

set -uo pipefail
cd "$(dirname "$0")/.."

ADB="${ADB:-$HOME/Android/Sdk/platform-tools/adb}"
APK="BT_Free/build/outputs/apk/btTest/debug/BT_Free-btTest-debug.apk"

step() { printf '\n=== %s ===\n' "$1"; }

step "JVM unit tests"
./gradlew --console=plain :BTLib:testDebugUnitTest || exit 1

step "Lua syntax (the Gradle build does not do this)"
bash scripts/guards/lua-syntax.sh || exit 1

step "Assemble btTest debug"
./gradlew --console=plain :BT_Free:assembleBtTestDebug || exit 1

step "Refresh the fixed-name copy in the parent folder"
cp -f "$APK" ../BlowTorch2-btTest-debug.apk || exit 1

step "Resolve device"
SERIAL="$(scripts/adb-device.sh)" || {
  echo "Built and copied, NOT installed: no device."
  echo "APK: $APK"
  exit 1
}
echo "serial: $SERIAL"

step "Install"
# Never uninstall. install -r re-registers a changed manifest and keeps the
# maintainer's server list and profiles.
"$ADB" -s "$SERIAL" install -r "$APK" || exit 1

printf '\nAPK installed on %s. Not tested: device behaviour is the maintainer'"'"'s call.\n' "$SERIAL"
