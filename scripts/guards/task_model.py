#!/usr/bin/env python3
"""Rewrite Cursor Task inputs so BlowTorch reviews do not run as Composer 2.5.

Cursor's `bugbot` subagent type is pinned to Composer 2.5 and ignores `model`.
Passing `cursor-grok-4.6-xhigh` on a bugbot Task does not change what the UI
shows. This module is the rule; the hook only translates JSON.

Run `python3 scripts/guards/task_model.py --self-test` (also a check.sh stage).
"""

from __future__ import annotations

import json
import sys

REQUIRED_MODEL = "cursor-grok-4.6-xhigh"
REQUIRED_TYPE = "generalPurpose"
PINNED_TYPES = frozenset({"bugbot"})
REVIEW_DESCRIPTIONS = frozenset({"bugbot"})

REVIEW_DIFF_NOTE = (
    "[BlowTorch] Do not dump whole-tree `git diff`. In Full Repository Path "
    "run `scripts/review-diff.sh` (uncommitted) or `scripts/review-diff.sh HEAD` "
    "(already committed). For TOO LARGE or remaining files: "
    "`git diff -- <one path>` or `git diff -U0 -- <path>`.\n\n"
)

# Cursor pins the `bugbot` *type* to Composer 2.5 and ignores `model`. We do
# not choose that pin; we refuse the type. Only prepend this when rewriting
# a leftover bugbot launch, not on every Grok reviewer.
COMPOSER_TYPE_NOTE = (
    "[BlowTorch] Cursor pins subagent_type=bugbot to Composer 2.5 and ignores "
    "model; this launch was rewritten to generalPurpose.\n\n"
)

PROMPT_NOTE = REVIEW_DIFF_NOTE


def _norm(value) -> str:
    return str(value or "").strip()


def is_review_task(inp: dict) -> bool:
    if not isinstance(inp, dict):
        return False
    stype = _norm(inp.get("subagent_type")).lower()
    desc = _norm(inp.get("description")).lower()
    return stype in PINNED_TYPES or desc in REVIEW_DESCRIPTIONS


def _model_needs_replace(model: str) -> bool:
    m = model.lower()
    if m in ("", "inherit"):
        return True
    if m == REQUIRED_MODEL.lower():
        return False
    if m.startswith("composer-2.5") or m.endswith("-fast"):
        return True
    return m != REQUIRED_MODEL.lower()


def rewrite_task_input(inp: dict) -> tuple[dict | None, str]:
    """Return (new_input, reason) or (None, '') if the call can stand."""
    if not isinstance(inp, dict) or not is_review_task(inp):
        return None, ""

    out = dict(inp)
    reasons: list[str] = []

    stype = _norm(out.get("subagent_type"))
    rewritten_from_bugbot = stype.lower() in PINNED_TYPES
    if rewritten_from_bugbot:
        out["subagent_type"] = REQUIRED_TYPE
        reasons.append("subagent_type bugbot -> generalPurpose")

    model = _norm(out.get("model"))
    if _model_needs_replace(model):
        out["model"] = REQUIRED_MODEL
        reasons.append(f"model {model or 'omitted'} -> {REQUIRED_MODEL}")

    prompt = out.get("prompt") if isinstance(out.get("prompt"), str) else ""
    prefix = ""
    if rewritten_from_bugbot and COMPOSER_TYPE_NOTE.strip() not in prompt:
        prefix += COMPOSER_TYPE_NOTE
        reasons.append("prefixed composer-type note")
    if REVIEW_DIFF_NOTE.strip() not in prompt:
        prefix += REVIEW_DIFF_NOTE
        reasons.append("prefixed review-diff recipe")
    if prefix:
        out["prompt"] = prefix + prompt

    if not reasons:
        return None, ""
    return out, "; ".join(reasons)


def tool_name(payload: dict) -> str:
    for key in ("tool_name", "toolName"):
        value = payload.get(key)
        if isinstance(value, str):
            return value
    tool = payload.get("tool")
    if isinstance(tool, str):
        return tool
    if isinstance(tool, dict) and isinstance(tool.get("name"), str):
        return tool["name"]
    return ""


def tool_input(payload: dict) -> dict:
    for key in ("tool_input", "toolInput", "arguments", "input", "tool_args"):
        value = payload.get(key)
        if isinstance(value, dict):
            return value
    tool = payload.get("tool")
    if isinstance(tool, dict):
        for key in ("input", "arguments", "params"):
            value = tool.get(key)
            if isinstance(value, dict):
                return value
    return {}


def event_name(payload: dict) -> str:
    return _norm(
        payload.get("hook_event_name")
        or payload.get("event_name")
        or payload.get("event")
    )


def subagent_type_from_payload(payload: dict) -> str:
    for key in ("subagent_type", "subagentType", "type"):
        value = payload.get(key)
        if isinstance(value, str) and value.strip():
            return value.strip()
    inp = tool_input(payload)
    return _norm(inp.get("subagent_type"))


def handle_payload(payload: dict) -> dict:
    """Map a Cursor hook payload to a response object. Fail-open on junk."""
    if not isinstance(payload, dict):
        return {"permission": "allow"}

    event = event_name(payload).lower().replace("_", "")
    if event in {"subagentstart"}:
        if subagent_type_from_payload(payload).lower() in PINNED_TYPES:
            msg = (
                "BlowTorch: the bugbot subagent type is pinned to Composer 2.5. "
                f"Relaunch as generalPurpose with model {REQUIRED_MODEL}."
            )
            return {
                "permission": "deny",
                "agent_message": msg,
                "agentMessage": msg,
                "user_message": msg,
                "userMessage": msg,
            }
        return {"permission": "allow"}

    name = tool_name(payload)
    if name and name not in {"Task", "task"}:
        return {"permission": "allow"}

    new, reason = rewrite_task_input(tool_input(payload))
    if not new:
        return {"permission": "allow"}

    msg = f"BlowTorch: rewrote reviewer Task ({reason})."
    return {
        "permission": "allow",
        "updated_input": new,
        "agent_message": msg,
        "agentMessage": msg,
    }


def _self_test() -> int:
    cases = [
        (
            {"subagent_type": "bugbot", "model": REQUIRED_MODEL, "prompt": "Full Repository Path: /x\nDiff: uncommitted changes"},
            True,
            REQUIRED_TYPE,
            REQUIRED_MODEL,
        ),
        (
            {"subagent_type": "bugbot", "description": "Bugbot"},
            True,
            REQUIRED_TYPE,
            REQUIRED_MODEL,
        ),
        (
            {"subagent_type": "bugbot", "model": "composer-2.5", "description": "Bugbot"},
            True,
            REQUIRED_TYPE,
            REQUIRED_MODEL,
        ),
        (
            {"subagent_type": "generalPurpose", "description": "Bugbot", "model": "composer-2.5-fast"},
            True,
            REQUIRED_TYPE,
            REQUIRED_MODEL,
        ),
        (
            {"subagent_type": "generalPurpose", "description": "Bugbot"},
            True,
            REQUIRED_TYPE,
            REQUIRED_MODEL,
        ),
        (
            {"subagent_type": "generalPurpose", "description": "Bugbot", "model": REQUIRED_MODEL},
            True,
            REQUIRED_TYPE,
            REQUIRED_MODEL,
        ),
        (
            {
                "subagent_type": "generalPurpose",
                "description": "Bugbot",
                "model": REQUIRED_MODEL,
                "prompt": PROMPT_NOTE + "Full Repository Path: /x\nDiff: uncommitted changes",
            },
            False,
            REQUIRED_TYPE,
            REQUIRED_MODEL,
        ),
        (
            {"subagent_type": "explore", "description": "Find files"},
            False,
            "explore",
            "",
        ),
        (
            {"subagent_type": "shell", "model": "composer-2.5"},
            False,
            "shell",
            "composer-2.5",
        ),
    ]
    failed = 0
    for inp, expect_rewrite, expect_type, expect_model in cases:
        new, reason = rewrite_task_input(inp)
        got_rewrite = new is not None
        if got_rewrite != expect_rewrite:
            print(f"FAIL rewrite flag: {inp!r} -> {got_rewrite} ({reason})", file=sys.stderr)
            failed += 1
            continue
        result = new or inp
        if _norm(result.get("subagent_type")) != expect_type:
            print(f"FAIL type: {inp!r} -> {result.get('subagent_type')}", file=sys.stderr)
            failed += 1
        if expect_rewrite and expect_model and _norm(result.get("model")) != expect_model:
            print(f"FAIL model: {inp!r} -> {result.get('model')}", file=sys.stderr)
            failed += 1
        if expect_rewrite:
            prompt = result.get("prompt") or ""
            if "review-diff.sh" not in prompt:
                print(f"FAIL missing review-diff recipe: {inp!r}", file=sys.stderr)
                failed += 1
            if inp.get("subagent_type") == "bugbot" and "Composer 2.5" not in prompt:
                print(f"FAIL missing composer-type note: {inp!r}", file=sys.stderr)
                failed += 1
            if inp.get("subagent_type") != "bugbot" and "Composer 2.5" in prompt:
                print(f"FAIL composer note on a Grok reviewer: {inp!r}", file=sys.stderr)
                failed += 1

    hook = handle_payload({
        "hook_event_name": "preToolUse",
        "tool_name": "Task",
        "tool_input": {
            "description": "Bugbot",
            "subagent_type": "bugbot",
            "model": "composer-2.5",
            "prompt": "Diff: uncommitted changes",
        },
    })
    if hook.get("permission") != "allow" or not isinstance(hook.get("updated_input"), dict):
        print(f"FAIL hook rewrite: {hook!r}", file=sys.stderr)
        failed += 1
    elif hook["updated_input"].get("model") != REQUIRED_MODEL:
        print(f"FAIL hook model: {hook!r}", file=sys.stderr)
        failed += 1
    elif hook["updated_input"].get("subagent_type") != REQUIRED_TYPE:
        print(f"FAIL hook type: {hook!r}", file=sys.stderr)
        failed += 1

    deny = handle_payload({
        "hook_event_name": "subagentStart",
        "subagent_type": "bugbot",
    })
    if deny.get("permission") != "deny":
        print(f"FAIL subagentStart deny: {deny!r}", file=sys.stderr)
        failed += 1

    allow_explore = handle_payload({
        "hook_event_name": "preToolUse",
        "tool_name": "Task",
        "tool_input": {"subagent_type": "explore", "description": "search"},
    })
    if allow_explore.get("permission") != "allow" or "updated_input" in allow_explore:
        print(f"FAIL explore untouched: {allow_explore!r}", file=sys.stderr)
        failed += 1

    inject = handle_payload({
        "hook_event_name": "preToolUse",
        "tool_name": "Task",
        "tool_input": {
            "description": "Bugbot",
            "subagent_type": "generalPurpose",
            "model": REQUIRED_MODEL,
            "prompt": "Diff: uncommitted changes",
        },
    })
    inj = inject.get("updated_input") if isinstance(inject.get("updated_input"), dict) else {}
    if inject.get("permission") != "allow" or "review-diff.sh" not in (inj.get("prompt") or ""):
        print(f"FAIL recipe inject on correct-model Bugbot: {inject!r}", file=sys.stderr)
        failed += 1

    if failed:
        print(f"{failed} self-test failure(s)", file=sys.stderr)
        return 1
    print("ok")
    return 0


def main(argv: list[str]) -> int:
    if argv[1:] == ["--self-test"]:
        return _self_test()
    try:
        payload = json.load(sys.stdin)
    except Exception:
        print(json.dumps({"permission": "allow"}))
        return 0
    try:
        print(json.dumps(handle_payload(payload)))
    except Exception:
        print(json.dumps({"permission": "allow"}))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
