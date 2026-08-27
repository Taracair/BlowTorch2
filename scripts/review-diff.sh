#!/usr/bin/env bash
# Review bundle that fits in one Cursor tool result.
#
# Whole-tree `git diff` is truncated on this repo (god classes, fat waves).
# Reviewers must run this instead, then `git diff -- <one path>` only for
# files marked TOO LARGE or listed under remaining.
#
#   scripts/review-diff.sh           # staged + unstaged vs HEAD
#   scripts/review-diff.sh HEAD      # the last commit (use this after commit)
#   scripts/review-diff.sh <rev>     # git diff <rev> (HEAD~1, origin/staging…)
#
# REVIEW_DIFF_BUDGET  total bytes of printed file diffs (default 80000)
# REVIEW_DIFF_FILE    per-file cap; above it, try -U1, then skip the body

set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

BUDGET="${REVIEW_DIFF_BUDGET:-80000}"
FILEMAX="${REVIEW_DIFF_FILE:-24000}"

mode="uncommitted"
range=""
if [ "${1:-}" = "HEAD" ]; then
  mode="HEAD"
elif [ -n "${1:-}" ]; then
  mode="range"
  range="$1"
fi

echo "=== review-diff ($mode) repo=$ROOT ==="
echo "budget=${BUDGET} filemax=${FILEMAX}"
echo "Do not dump whole-tree git diff. Continue oversized files with:"
echo "  git diff -U0 -- <path>"
echo

diff_for() {
  local path="$1"
  local u="${2:-3}"
  case "$mode" in
    uncommitted) git diff -U"$u" HEAD -- "$path" ;;
    HEAD)
      git diff -U"$u" HEAD~1 HEAD -- "$path" 2>/dev/null \
        || git show --pretty=format: -p -U"$u" HEAD -- "$path"
      ;;
    range) git diff -U"$u" "$range" -- "$path" ;;
  esac
}

names=()
case "$mode" in
  uncommitted)
    git status -sb
    echo
    echo "=== stat ==="
    git diff HEAD --stat
    echo
    echo "=== names ==="
    git diff HEAD --name-only
    echo
    echo "=== untracked (not in git diff; Read source files, do not cat dumps) ==="
    git ls-files --others --exclude-standard
    while IFS= read -r line; do
      [ -n "$line" ] && names+=("$line")
    done < <(git diff HEAD --name-only)
    ;;
  HEAD)
    git log -1 --oneline
    echo
    echo "=== stat ==="
    git show --stat --format= HEAD
    echo
    echo "=== names ==="
    git diff-tree --no-commit-id --name-only -r HEAD
    while IFS= read -r line; do
      [ -n "$line" ] && names+=("$line")
    done < <(git diff-tree --no-commit-id --name-only -r HEAD)
    ;;
  range)
    echo "=== stat ==="
    git diff --stat "$range"
    echo
    echo "=== names ==="
    git diff --name-only "$range"
    while IFS= read -r line; do
      [ -n "$line" ] && names+=("$line")
    done < <(git diff --name-only "$range")
    ;;
esac

echo
used=0
skipped=()

if [ "${#names[@]}" -eq 0 ]; then
  echo "=== file diffs ==="
  echo "(none)"
  echo
  echo "=== used ${used} / ${BUDGET} bytes ==="
  exit 0
fi

for path in "${names[@]}"; do
  case "$path" in
    *.apk|*.so|*.png|*.jpg|*.jpeg|*.webp|*.jar|*.zip)
      echo "=== skip binary $path ==="
      echo
      continue
      ;;
  esac

  body="$(diff_for "$path" 3 || true)"
  n=$(printf '%s' "$body" | wc -c)

  if [ "$n" -gt "$FILEMAX" ]; then
    body="$(diff_for "$path" 1 || true)"
    n=$(printf '%s' "$body" | wc -c)
  fi

  if [ "$n" -gt "$FILEMAX" ]; then
    echo "=== TOO LARGE $path ($n bytes) ==="
    echo "Continue with: git diff -U0 -- $path"
    echo "Do not Read the whole class hunting for the hunk."
    echo
    continue
  fi

  if [ $((used + n)) -gt "$BUDGET" ]; then
    skipped+=("$path")
    continue
  fi

  echo "=== $path ($n bytes) ==="
  if [ -z "$body" ]; then
    echo "(empty diff)"
  else
    printf '%s\n' "$body"
  fi
  echo
  used=$((used + n))
done

echo "=== used ${used} / ${BUDGET} bytes ==="
if [ "${#skipped[@]}" -gt 0 ]; then
  echo
  echo "=== remaining (budget full; git diff -- each path) ==="
  for path in "${skipped[@]}"; do
    echo "$path"
  done
fi
