#!/usr/bin/env bash
# Deterministic guard for shell commands an agent wants to run.
#
#   scripts/guards/shell-guard.sh "<full command string>"
#
# Exit 0 = allow (silent).
# Exit 2 = deny; a one-line reason is printed on stdout.
#
# This is the only place the rules live. Cursor, Claude Code and the git
# pre-commit hook all call this script, so there is one implementation to
# audit and one place to change.
#
# Every rule here used to be a sentence in a document. A sentence is advice.
# This is a barrier (ORCHESTRATION rule 9).

set -uo pipefail

cmd="${1:-}"
[ -z "$cmd" ] && exit 0

# Escape hatch for the maintainer. A guard that a human cannot get past is not a
# barrier, it is a wall: these rules exist to stop an agent doing something on
# autopilot, not to take the maintainer's own machine away from them.
#
#   BT_GUARD_OFF=1 git push
#
# Matched both as an environment variable (git hooks, plain shells) and as a
# prefix inside the command string, because an editor hook is handed the command
# as text and never runs it. An agent that reaches for this is working around a
# fact about the project and must say so out loud instead.
[ "${BT_GUARD_OFF:-}" = "1" ] && exit 0
case "$cmd" in
  BT_GUARD_OFF=1*|*"; BT_GUARD_OFF=1 "*) exit 0 ;;
esac

deny() { printf '%s\n' "$1"; exit 2; }

# Collapse whitespace so "adb    uninstall" and newlines do not slip past.
flat="$(printf '%s' "$cmd" | tr '\n\t' '  ' | tr -s ' ')"

# --- 1. adb uninstall destroys the maintainer's server list and profiles ----
# install -r re-registers a changed manifest just as well and keeps the data.
# Matches bare `adb`, `$ADB`, and any path prefix, with flags in any order.
# Known limit: the phrase inside a quoted string (echo "...adb uninstall...")
# is not matched. That is deliberate. Widening it far enough to catch quotes
# would block writing about the rule, and a quoted string does not uninstall
# anything. If you find a real invocation that slips through, widen it and say
# which one in the commit message.
if printf '%s' "$flat" | grep -qE '(^|[;&|`(]|[[:space:]])([^[:space:]]*/)?(adb|\$ADB|\$\{ADB\})([[:space:]]+-[^[:space:]]+([[:space:]]+[^-][^[:space:]]*)?)*[[:space:]]+uninstall([[:space:]]|;|$)'; then
  deny "BLOCKED: adb uninstall wipes the maintainer's profiles and server list. Use: adb -s <serial> install -r <apk>"
fi

# --- 2. pushing: deliberately NOT blocked -----------------------------------
# The package this came from denied `git push` on the theory that the maintainer
# pushes and the agent does not. That is wrong for this project: the maintainer
# does not use git directly, so a blocked push means work sits only on this
# laptop with no copy anywhere. The agent commits and pushes `staging`.
#
# What still needs asking is releasing: tags, `main`, GitHub releases and
# production APKs. That is judgment, not a pattern match, and it lives in
# .cursor/rules/release-workflow.mdc.

# --- 3. daily work happens on staging, never on main ------------------------
if printf '%s' "$flat" | grep -qE '(^|[;&|[:space:]])git([[:space:]]+-[^[:space:]]+)*[[:space:]]+commit([[:space:]]|$)'; then
  root="$(git rev-parse --show-toplevel 2>/dev/null || true)"
  if [ -n "$root" ]; then
    branch="$(git -C "$root" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
    if [ "$branch" = "main" ] || [ "$branch" = "master" ]; then
      deny "BLOCKED: you are on '$branch'. Daily work goes on staging. Run: git switch staging"
    fi
  fi
fi

# --- 4. history rewriting on a repo the maintainer cannot re-derive ---------
if printf '%s' "$flat" | grep -qE '(^|[;&|[:space:]])git[[:space:]]+reset[[:space:]]+--hard([[:space:]]|$)'; then
  deny "BLOCKED: git reset --hard discards uncommitted work. Use git stash, or ask the maintainer."
fi
if printf '%s' "$flat" | grep -qE '(^|[;&|[:space:]])git[[:space:]]+clean[[:space:]]+-[a-z]*f'; then
  deny "BLOCKED: git clean -f deletes untracked files, including .scratch/ probes and local APK copies."
fi

# --- 5. rm -rf outside the scratch area ------------------------------------
if printf '%s' "$flat" | grep -qE '(^|[;&|[:space:]])rm[[:space:]]+(-[a-zA-Z]*r[a-zA-Z]*f|-[a-zA-Z]*f[a-zA-Z]*r)'; then
  if ! printf '%s' "$flat" | grep -qE '\.scratch/|/build/|/tmp/'; then
    deny "BLOCKED: recursive force delete outside .scratch/, build/ or /tmp/. Name the paths explicitly or ask."
  fi
fi

# --- 6. StrictMode penaltyDeath would kill the build being played ----------
if printf '%s' "$flat" | grep -q 'penaltyDeath'; then
  deny "BLOCKED: never penaltyDeath. btTest is the flavour the maintainer plays on; penaltyLog only."
fi

exit 0
