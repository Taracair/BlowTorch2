#!/usr/bin/env bash
# Review index plus paged hunks. Cursor Shell results omit the middle of
# ~40k+ stdout (measured: reviewers 2026-08-31, 2026-09-02, 2026-09-03), so
# hunks go to .scratch/review-diff/page-NN.txt. Stdout is the index.
#
#   scripts/review-diff.sh           # staged + unstaged vs HEAD
#   scripts/review-diff.sh HEAD      # the last commit (use this after commit)
#   scripts/review-diff.sh <rev>     # git diff <rev> (HEAD~1, origin/staging…)
#
# Then Read each listed page. Do not dump whole-tree git diff.
# Untracked files are listed only — Read the source, do not cat dumps.
#
# REVIEW_DIFF_PAGE    bytes per page file (default 20000)
# REVIEW_DIFF_INLINE  if one page is this size or smaller, also print it

set -uo pipefail
export LC_ALL=C

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PAGE="${REVIEW_DIFF_PAGE:-20000}"
INLINE="${REVIEW_DIFF_INLINE:-12000}"
OUTDIR="$ROOT/.scratch/review-diff"

mode="uncommitted"
range=""
if [ "${1:-}" = "HEAD" ]; then
  mode="HEAD"
elif [ -n "${1:-}" ]; then
  mode="range"
  range="$1"
fi

rm -rf "$OUTDIR"
mkdir -p "$OUTDIR"

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
    git status -sb >"$OUTDIR/status.txt"
    git diff HEAD --stat >"$OUTDIR/stat.txt"
    git diff HEAD --name-only >"$OUTDIR/names.txt"
    git ls-files --others --exclude-standard >"$OUTDIR/untracked.txt"
    while IFS= read -r line; do
      [ -n "$line" ] && names+=("$line")
    done <"$OUTDIR/names.txt"
    ;;
  HEAD)
    git log -1 --oneline >"$OUTDIR/status.txt"
    git show --stat --format= HEAD >"$OUTDIR/stat.txt"
    git diff-tree --no-commit-id --name-only -r HEAD >"$OUTDIR/names.txt"
    : >"$OUTDIR/untracked.txt"
    while IFS= read -r line; do
      [ -n "$line" ] && names+=("$line")
    done <"$OUTDIR/names.txt"
    ;;
  range)
    echo "range $range" >"$OUTDIR/status.txt"
    git diff --stat "$range" >"$OUTDIR/stat.txt"
    git diff --name-only "$range" >"$OUTDIR/names.txt"
    : >"$OUTDIR/untracked.txt"
    while IFS= read -r line; do
      [ -n "$line" ] && names+=("$line")
    done <"$OUTDIR/names.txt"
    ;;
esac

page_n=0
page_used=0
page_file=""
declare -a PAGE_PATHS=()

new_page() {
  page_n=$((page_n + 1))
  page_file="$OUTDIR/page-$(printf '%02d' "$page_n").txt"
  PAGE_PATHS+=("")
  {
    echo "=== review-diff page ${page_n} ($mode) ==="
    echo
  } >"$page_file"
  page_used=$(wc -c <"$page_file")
}

append_text() {
  local text="$1"
  local n
  n=$(printf '%s' "$text" | wc -c)
  if [ "$page_n" -eq 0 ]; then
    new_page
  elif [ "$page_used" -gt 200 ] && [ $((page_used + n)) -gt "$PAGE" ]; then
    new_page
  fi
  printf '%s' "$text" >>"$page_file"
  page_used=$((page_used + n))
}

note_path() {
  local path="$1"
  local i=$((page_n - 1))
  PAGE_PATHS[$i]="${PAGE_PATHS[$i]}${path}"$'\n'
}

add_section() {
  local path="$1"
  local body="$2"
  local header continued line ln
  header="=== ${path} ==="$'\n'
  continued="=== ${path} (continued) ==="$'\n'

  if [ "$page_n" -eq 0 ]; then
    new_page
  fi

  # Small enough to keep on one page (or start a new one).
  local n
  n=$(printf '%s' "$header$body"$'\n' | wc -c)
  if [ "$n" -le "$PAGE" ]; then
    if [ "$page_used" -gt 200 ] && [ $((page_used + n)) -gt "$PAGE" ]; then
      new_page
    fi
    append_text "$header$body"$'\n\n'
    note_path "$path"
    return
  fi

  if [ "$page_used" -gt 200 ]; then
    new_page
  fi
  append_text "$header"
  note_path "$path"
  while IFS= read -r line || [ -n "$line" ]; do
    ln="$line"$'\n'
    n=$(printf '%s' "$ln" | wc -c)
    if [ "$page_used" -gt 200 ] && [ $((page_used + n)) -gt "$PAGE" ]; then
      new_page
      append_text "$continued"
      note_path "$path (continued)"
    fi
    append_text "$ln"
  done < <(printf '%s\n' "$body")
  append_text $'\n'
}

skipped_binary=()

if [ "${#names[@]}" -gt 0 ]; then
  for path in "${names[@]}"; do
    case "$path" in
      *.apk|*.so|*.png|*.jpg|*.jpeg|*.webp|*.jar|*.zip)
        skipped_binary+=("$path")
        continue
        ;;
    esac

    body="$(diff_for "$path" 3 || true)"
    n=$(printf '%s' "$body" | wc -c)
    if [ "$n" -gt "$PAGE" ]; then
      body="$(diff_for "$path" 1 || true)"
      n=$(printf '%s' "$body" | wc -c)
    fi
    if [ "$n" -gt "$PAGE" ]; then
      body="$(diff_for "$path" 0 || true)"
    fi
    if [ -z "$body" ]; then
      body="(empty diff)"
    fi
    add_section "$path" "$body"
  done
fi

{
  echo "=== review-diff ($mode) repo=$ROOT ==="
  echo "pages=$OUTDIR page=${PAGE} inline=${INLINE}"
  echo "Cursor truncates the middle of large Shell stdout. Read the pages."
  echo "Do not dump whole-tree git diff. Do not Read a 4000-line class hunting for the hunk."
  echo
  cat "$OUTDIR/status.txt"
  echo
  echo "=== stat ==="
  cat "$OUTDIR/stat.txt"
  echo
  echo "=== names ==="
  cat "$OUTDIR/names.txt"
  if [ -s "$OUTDIR/untracked.txt" ]; then
    echo
    echo "=== untracked (not in pages; Read the source, do not cat dumps) ==="
    cat "$OUTDIR/untracked.txt"
  fi
  if [ "${#skipped_binary[@]}" -gt 0 ]; then
    echo
    echo "=== skipped binary ==="
    printf '%s\n' "${skipped_binary[@]}"
  fi
  echo
  if [ "$page_n" -eq 0 ]; then
    echo "=== pages ==="
    echo "(none)"
  else
    echo "=== pages (${page_n}) ==="
    i=1
    while [ "$i" -le "$page_n" ]; do
      f="$OUTDIR/page-$(printf '%02d' "$i").txt"
      sz=$(wc -c <"$f")
      echo "Read $f ($sz bytes)"
      printf '%s' "${PAGE_PATHS[$((i - 1))]}" | sed '/^$/d' | sed 's/^/  /'
      i=$((i + 1))
    done
    only="$OUTDIR/page-01.txt"
    only_sz=$(wc -c <"$only")
    if [ "$page_n" -eq 1 ] && [ "$only_sz" -le "$INLINE" ]; then
      echo
      echo "=== page-01 (inline; fits stdout) ==="
      cat "$only"
    else
      echo
      echo "Read every page listed above. Hunks are not on stdout."
    fi
  fi
} | tee "$OUTDIR/INDEX.txt"
