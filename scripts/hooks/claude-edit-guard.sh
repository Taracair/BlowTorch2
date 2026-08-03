#!/usr/bin/env bash
# Claude Code PostToolUse adapter for Edit / Write / MultiEdit.
#
# Unlike Cursor's afterFileEdit, this one CAN talk back: exit 2 with stderr
# feeds the message to the model, which then fixes the file in the same turn.
# That is the one place where Claude Code is genuinely stronger here, and it is
# why the Lua syntax check is worth wiring on both sides.

set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
GUARDS="$ROOT/scripts/guards"

payload="$(cat)"

path="$(printf '%s' "$payload" | python3 -c \
  'import json,sys
try:
    d = json.load(sys.stdin)
except Exception:
    print(""); raise SystemExit
ti = d.get("tool_input", {})
print(ti.get("file_path") or ti.get("path") or "")' 2>/dev/null)"

[ -z "$path" ] && exit 0

rc=0
msg=""

case "$path" in
  *.lua)
    if ! out="$(bash "$GUARDS/lua-syntax.sh" "$path" 2>&1)"; then
      msg="$out"
      rc=2
    fi
    ;;
esac

case "$path" in
  */docs/*|docs/*)
    if ! out="$(bash "$GUARDS/docs-allowlist.sh" "$path" 2>&1)"; then
      msg="${msg}${msg:+$'\n'}$out"
      rc=2
    fi
    ;;
esac

if [ "$rc" -ne 0 ]; then
  printf '%s\n' "$msg" >&2
  exit 2
fi
exit 0
