#!/usr/bin/env bash
# Hermetic checks for scripts/review-diff.sh. Not a device test.
# Untracked files land in the pages, -U3 context survives a split, and a
# second run does not delete the first run's directory.

set -euo pipefail
export LC_ALL=C

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT="$ROOT/scripts/review-diff.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

repo="$tmp/repo"
mkdir -p "$repo"
git -C "$repo" init -q
pad="$(printf 'x%.0s' {1..80})"
{
  i=1
  while [ "$i" -le 40 ]; do
    printf 'pad-%02d %s\n' "$i" "$pad"
    i=$((i + 1))
  done
} >"$repo/tracked.txt"
git -C "$repo" add tracked.txt
git -C "$repo" -c user.name=review-diff-test -c user.email=review-diff-test@example.org \
  commit -q -m init
# One changed line in the middle. Neighbours must survive paging.
awk 'NR==20 { sub(/pad-20/, "CHANGED-NEW"); } { print }' "$repo/tracked.txt" >"$repo/tracked.txt.new"
mv "$repo/tracked.txt.new" "$repo/tracked.txt"
printf 'untracked-marker\n' >"$repo/fresh.txt"

bytes="$(git -C "$repo" diff -U3 HEAD -- tracked.txt | wc -c | tr -d ' ')"
page=$((bytes / 2))
if [ "$page" -le 200 ]; then
  echo "fixture diff is ${bytes} bytes; too small to prove context is kept" >&2
  exit 1
fi

out1="$tmp/out1"
mkdir -p "$out1"
REVIEW_DIFF_ROOT="$repo" REVIEW_DIFF_OUT="$out1" REVIEW_DIFF_PAGE="$page" \
  REVIEW_DIFF_INLINE=0 \
  "$SCRIPT" >"$tmp/index1.txt"

pages="$(cat "$out1"/page-*.txt)"
# pad-17 and pad-23 are three lines from the edit. A -U1 diff still has pad-19 and pad-21.
for needle in 'untracked-marker' 'pad-17' 'pad-23' 'CHANGED-NEW'; do
  if ! printf '%s\n' "$pages" | grep -q "$needle"; then
    echo "missing $needle in $out1 pages" >&2
    exit 1
  fi
done
if ! grep -q 'larger than one page (context kept)' "$tmp/index1.txt"; then
  echo "index did not report the oversized file" >&2
  exit 1
fi
if ! grep -q 'new-file diffs are in the pages' "$tmp/index1.txt"; then
  echo "index did not name the untracked file as part of the pages" >&2
  exit 1
fi
if ! grep -Fxq "run=$out1" "$tmp/index1.txt"; then
  echo "index did not name its run directory" >&2
  exit 1
fi

out2="$tmp/out2"
mkdir -p "$out2"
REVIEW_DIFF_ROOT="$repo" REVIEW_DIFF_OUT="$out2" REVIEW_DIFF_PAGE=20000 \
  REVIEW_DIFF_INLINE=0 \
  "$SCRIPT" >/dev/null
if ! grep -q 'untracked-marker' "$out1"/page-*.txt; then
  echo "second run removed the first run's pages" >&2
  exit 1
fi

run_a="$(REVIEW_DIFF_ROOT="$repo" REVIEW_DIFF_PAGE=20000 REVIEW_DIFF_INLINE=0 \
  "$SCRIPT" | sed -n 's/^run=//p')"
run_b="$(REVIEW_DIFF_ROOT="$repo" REVIEW_DIFF_PAGE=20000 REVIEW_DIFF_INLINE=0 \
  "$SCRIPT" | sed -n 's/^run=//p')"
if [ -z "$run_a" ] || [ -z "$run_b" ] || [ "$run_a" = "$run_b" ]; then
  echo "default runs did not get distinct directories (a=$run_a b=$run_b)" >&2
  exit 1
fi
if [ ! -s "$run_a/page-01.txt" ] || [ ! -s "$run_b/page-01.txt" ]; then
  echo "a later default run removed an earlier one" >&2
  exit 1
fi

echo ok
