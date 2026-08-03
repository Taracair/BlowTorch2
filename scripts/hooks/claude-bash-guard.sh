#!/usr/bin/env bash
# Claude Code PreToolUse adapter for the Bash tool.
#
# Claude Code speaks a different protocol from Cursor: JSON on stdin, and a
# non-zero exit with stderr text is what blocks the call. The rules themselves
# are the same file both tools read: scripts/guards/shell-guard.sh

set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
GUARD="$ROOT/scripts/guards/shell-guard.sh"

payload="$(cat)"

cmd="$(printf '%s' "$payload" | python3 -c \
  'import json,sys
try:
    d = json.load(sys.stdin)
except Exception:
    print(""); raise SystemExit
print(d.get("tool_input", {}).get("command", ""))' 2>/dev/null)"

[ -z "$cmd" ] && exit 0
[ -x "$GUARD" ] || [ -f "$GUARD" ] || exit 0

if out="$(bash "$GUARD" "$cmd")"; then
  exit 0
else
  code=$?
  if [ "$code" -eq 2 ]; then
    printf '%s\n' "$out" >&2
    exit 2   # exit 2 = block the tool call and show stderr to Claude
  fi
  exit 0
fi
