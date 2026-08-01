#!/usr/bin/env bash
# Everything that can be checked without a device, in one command.
#
#   scripts/check.sh
#
# This is what CI runs (.github/workflows/tests.yml), so a green run here means
# a green run there. It does NOT build an APK: the prebuilt LuaJIT .so files
# under BTLib/libs are not in git, so no clone can assemble one.
#
# Exits non-zero on the first failing stage.

set -uo pipefail
cd "$(dirname "$0")/.."

fail=0
stage() { printf '\n=== %s ===\n' "$1"; }

stage "JVM unit tests (:BTLib:testDebugUnitTest)"
./gradlew --console=plain :BTLib:testDebugUnitTest || fail=1

# The Gradle build compiles no Lua at all, so a syntax error in a script ships
# and only shows up as a dead button on the device.
stage "Lua syntax (luac -p)"
LUAC="$(command -v luac5.1 || command -v luac)"
if [ -z "$LUAC" ]; then
  echo "luac5.1 not installed — cannot check Lua syntax"
  fail=1
else
  "$LUAC" -p BT_Free/assets/share/lua/5.1/*.lua || fail=1
fi

# luac -p cannot see this class: inside a bare module(...) an unimported name is
# nil, not an error, and it only bites on the branch that uses it.
stage "Lua unbound names in module(...) files"
PY="$(command -v python3 || command -v python)"
if [ -z "$PY" ]; then
  echo "python3 not installed — cannot run the unbound-name check"
  fail=1
else
  "$PY" scripts/lua_unbound.py || fail=1
fi

stage "Lua tests (BT_Free/src/test/lua)"
LUA="$(command -v lua5.1 || command -v luajit || command -v lua)"
if [ -z "$LUA" ]; then
  echo "lua5.1 not installed — cannot run the Lua tests"
  fail=1
else
  for t in BT_Free/src/test/lua/*.lua; do
    if "$LUA" "$t" >/dev/null; then
      echo "ok   $t"
    else
      echo "FAIL $t"
      "$LUA" "$t" 2>&1 | tail -20
      fail=1
    fi
  done
fi

if [ "$fail" -ne 0 ]; then
  printf '\ncheck FAILED\n'
  exit 1
fi
printf '\ncheck OK\n'
