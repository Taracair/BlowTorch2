#!/usr/bin/env python3
"""Cursor afterFileEdit hook.

IMPORTANT, read before relying on this: afterFileEdit is a notification hook.
Cursor accepts no response from it, so it CANNOT block an edit and CANNOT hand
a message back to the agent. Everything here is for the human: it writes to
stderr, which shows up in Cursor's Hooks output channel, and it records failures
in .scratch/guard-status so the git pre-commit hook can report them later.

The blocking enforcement for these same rules is the git pre-commit hook
(scripts/hooks/pre-commit) and scripts/check.sh. That is deliberate: an edit
that is briefly wrong on disk is harmless, a commit that is wrong is not.

Payload: {"file_path": str, "edits": [...], "workspace_roots": [str],
          "hook_event_name": "afterFileEdit"}
"""

import json
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
GUARDS = REPO / "scripts" / "guards"
STATUS = REPO / ".scratch" / "guard-status"


def note(line: str):
    print(line, file=sys.stderr)
    try:
        STATUS.parent.mkdir(parents=True, exist_ok=True)
        with STATUS.open("a") as fh:
            fh.write(line + "\n")
    except Exception:
        pass


def run(script: str, *args) -> tuple[int, str]:
    path = GUARDS / script
    if not path.exists():
        return 0, ""
    try:
        r = subprocess.run(
            ["bash", str(path), *args],
            capture_output=True, text=True, timeout=30, cwd=str(REPO),
        )
        return r.returncode, (r.stdout or "") + (r.stderr or "")
    except Exception as exc:
        return 0, str(exc)


def main():
    try:
        payload = json.load(sys.stdin)
    except Exception:
        return

    file_path = payload.get("file_path") or ""
    if not file_path:
        return

    if file_path.endswith(".lua"):
        code, out = run("lua-syntax.sh", file_path)
        if code != 0:
            note(f"[guard] {out.strip()}")

    if "/docs/" in file_path.replace("\\", "/"):
        code, out = run("docs-allowlist.sh", file_path)
        if code != 0:
            note(f"[guard] docs/ allowlist: {out.strip().splitlines()[0]}")


if __name__ == "__main__":
    main()
