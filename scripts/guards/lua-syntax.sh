#!/usr/bin/env bash
# Lua syntax check. The Gradle build compiles no Lua at all, so a syntax error
# ships and shows up on the device as a dead button.
#
#   scripts/guards/lua-syntax.sh                  # all shipped Lua
#   scripts/guards/lua-syntax.sh path/to/file.lua # one file
#
# Exit 0 = clean. Exit 1 = syntax error, printed on stdout.

set -uo pipefail
cd "$(dirname "$0")/../.."

LUAC="$(command -v luac5.1 || command -v luac || true)"
if [ -z "$LUAC" ]; then
  echo "lua-syntax: luac5.1 not installed, cannot check"
  exit 1
fi

if [ "$#" -gt 0 ]; then
  targets=("$@")
else
  targets=(BT_Free/assets/share/lua/5.1/*.lua)
fi

fail=0
for f in "${targets[@]}"; do
  case "$f" in
    *.lua) ;;
    *) continue ;;
  esac
  [ -f "$f" ] || continue
  if ! out="$("$LUAC" -p "$f" 2>&1)"; then
    echo "LUA SYNTAX ERROR in $f"
    echo "$out"
    fail=1
  fi
done

exit "$fail"
