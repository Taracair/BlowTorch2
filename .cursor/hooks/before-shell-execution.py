#!/usr/bin/env python3
"""Cursor beforeShellExecution hook.

Reads the hook payload on stdin, asks scripts/guards/shell-guard.sh whether the
command is allowed, and answers Cursor with allow or deny.

All the actual rules live in the guard script, not here. This file only
translates between Cursor's JSON protocol and an exit code, so the same rules
can be reused by Claude Code and by the git pre-commit hook.

Payload:  {"command": str, "workspace_roots": [str], "hook_event_name": str}
Response: {"permission": "allow"|"deny"|"ask", "agentMessage"?: str, "userMessage"?: str}
"""

import json
import subprocess
import sys
from pathlib import Path

# .cursor/hooks/before-shell-execution.py -> repo root is two levels up.
REPO = Path(__file__).resolve().parents[2]
GUARD = REPO / "scripts" / "guards" / "shell-guard.sh"


def allow():
    print(json.dumps({"permission": "allow"}))
    sys.exit(0)


def main():
    try:
        payload = json.load(sys.stdin)
    except Exception:
        # A hook that crashes must not become a hook that blocks all work.
        allow()

    command = payload.get("command") or ""
    if not command or not GUARD.exists():
        allow()

    try:
        result = subprocess.run(
            ["bash", str(GUARD), command],
            capture_output=True,
            text=True,
            timeout=10,
            cwd=str(REPO),
        )
    except Exception:
        allow()

    if result.returncode == 2:
        reason = (result.stdout or "").strip() or "Blocked by scripts/guards/shell-guard.sh"
        print(json.dumps({
            "permission": "deny",
            "agentMessage": reason,
            "userMessage": reason,
        }))
        sys.exit(0)

    allow()


if __name__ == "__main__":
    main()
