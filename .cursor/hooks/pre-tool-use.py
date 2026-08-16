#!/usr/bin/env python3
"""Cursor preToolUse / subagentStart hook.

Reads the hook payload on stdin, asks scripts/guards/task_model.py whether a
Task/subagent call should be rewritten or denied, and answers Cursor.

All the actual rules live in the guard module, not here. This file only
translates between Cursor's JSON protocol and that module.

preToolUse: rewrite leftover `bugbot` / Composer 2.5 reviewer Tasks to
generalPurpose + cursor-grok-4.6-xhigh.

subagentStart: deny `bugbot` if a launch still arrives with that type (the
type is pinned to Composer 2.5 and ignores `model`).
"""

import json
import sys
from pathlib import Path

# .cursor/hooks/pre-tool-use.py -> repo root is two levels up.
REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "scripts" / "guards"))

try:
    from task_model import handle_payload
except Exception:
    handle_payload = None


def allow():
    print(json.dumps({"permission": "allow"}))
    sys.exit(0)


def main():
    try:
        payload = json.load(sys.stdin)
    except Exception:
        allow()

    if handle_payload is None:
        allow()

    try:
        print(json.dumps(handle_payload(payload)))
    except Exception:
        allow()
    sys.exit(0)


if __name__ == "__main__":
    main()
