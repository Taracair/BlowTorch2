#!/usr/bin/env bash
# Claude Code Stop adapter.
#
# Cursor's stop hook cannot talk back to the model. Claude Code's can. This
# fires once when a wrap-up (deploy / APK / review / tests) omitted "what I did
# not verify", then gets out of the way. Q&A and already-honest wrap-ups pass.
# stop_hook_active is honoured so a block cannot loop.
#
# Wiring is in the parent staging folder's .claude/settings.json (not in git).
# This file is the versioned behaviour.

set -uo pipefail

payload="$(cat)"

printf '%s' "$payload" | python3 -c '
import json, re, sys

try:
    d = json.load(sys.stdin)
except Exception:
    raise SystemExit(0)

if d.get("stop_hook_active"):
    raise SystemExit(0)

msg = d.get("last_assistant_message") or ""
if isinstance(msg, dict):
    msg = json.dumps(msg)
msg = str(msg)
if not msg.strip():
    raise SystemExit(0)

verified = re.search(
    r"(did not verify|not verified|nie sprawdzi|nie zweryfik|unverified)",
    msg,
    re.I,
)
wrap = re.search(
    r"(deploy\.sh|review clean|tests pass|\bAPK\b|zainstalow|installed)",
    msg,
    re.I,
)
if verified or not wrap:
    raise SystemExit(0)

print(json.dumps({
    "decision": "block",
    "reason": (
        "Before you stop: name what you did not verify "
        "(device, logcat, a path you did not read). "
        "Then you may stop. Do not invent a passing device test."
    ),
}))
'
