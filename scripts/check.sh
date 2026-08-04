#!/usr/bin/env bash
# Everything that can be checked without a device, in one command.
#
#   scripts/check.sh
#
# This is what CI runs (.github/workflows/tests.yml), so a green run here means
# a green run there. It does NOT build an APK: the prebuilt LuaJIT .so files
# under BTLib/libs are not in git, so no clone can assemble one.
#
# Stages 5 to 8 are rules that used to be sentences in a document. A sentence is
# advice that a model may or may not still be attending to on turn forty. A
# failing stage is not.
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
bash scripts/guards/lua-syntax.sh || fail=1

# luac -p cannot see this class: inside a bare module(...) an unimported name is
# nil, not an error, and it only bites on the branch that uses it.
stage "Lua unbound names in module(...) files"
PY="$(command -v python3 || command -v python)"
if [ -z "$PY" ]; then
  echo "python3 not installed, cannot run the unbound-name check"
  fail=1
else
  "$PY" scripts/lua_unbound.py || fail=1
fi

stage "Lua tests (BT_Free/src/test/lua)"
LUA="$(command -v lua5.1 || command -v luajit || command -v lua)"
if [ -z "$LUA" ]; then
  echo "lua5.1 not installed, cannot run the Lua tests"
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

# --- guards -----------------------------------------------------------------

stage "Launcher component is where pinned icons expect it"
bash scripts/guards/launcher-component.sh || fail=1

stage "Every responder type survives the binder"
bash scripts/guards/responder-parcel-cases.sh || fail=1

stage "docs/ contains only allowlisted files"
tracked_docs="$(git ls-files 'docs/*' || true)"
if [ -n "$tracked_docs" ]; then
  # shellcheck disable=SC2086
  bash scripts/guards/docs-allowlist.sh $tracked_docs || fail=1
fi

stage "StrictMode never penaltyDeath"
if git grep -n 'penaltyDeath' -- '*.java' '*.kt' >/dev/null 2>&1; then
  echo "penaltyDeath found. btTest is the flavour the maintainer plays on."
  git grep -n 'penaltyDeath' -- '*.java' '*.kt'
  fail=1
else
  echo "ok"
fi

stage "No instrumentation left in tracked code"
# Rule 5: probes go in their own commit and come back out. If one survived a
# revert, this is where it surfaces, before a release rather than after.
if git grep -n 'BTPROF' -- '*.java' '*.kt' '*.lua' >/dev/null 2>&1; then
  echo "BTPROF probes still in tracked code. Revert the probe commit, or"
  echo "leave the measured number in a comment and drop the log line."
  git grep -n 'BTPROF' -- '*.java' '*.kt' '*.lua'
  fail=1
else
  echo "ok"
fi

stage "Working rules have not drifted between CLAUDE.md and .cursor/rules"
# The ten rules used to live in three files and diverged. Now the short list is
# in two (one per tool, both always loaded) and this stage fails if they differ.
a="$(grep -oE '^[0-9]+\. \*\*[^*]+\*\*' CLAUDE.md 2>/dev/null | sed 's/^[0-9]*\. //' | sort)"
b="$(grep -oE '^[0-9]+\. \*\*[^*]+\*\*' .cursor/rules/orchestration.mdc 2>/dev/null | sed 's/^[0-9]*\. //' | sort)"
if [ -z "$a" ] || [ -z "$b" ]; then
  echo "could not extract the rule list from one of the files"
  fail=1
elif [ "$a" != "$b" ]; then
  echo "CLAUDE.md and .cursor/rules/orchestration.mdc disagree:"
  diff <(printf '%s\n' "$a") <(printf '%s\n' "$b") || true
  fail=1
else
  echo "ok"
fi

if [ "$fail" -ne 0 ]; then
  printf '\ncheck FAILED\n'
  exit 1
fi
printf '\ncheck OK\n'
