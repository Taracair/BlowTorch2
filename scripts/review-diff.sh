#!/usr/bin/env bash
# Review index plus paged hunks. Cursor Shell results omit the middle of
# ~40k+ stdout (measured: reviewers 2026-08-31, 2026-09-02, 2026-09-03), so
# hunks go to a new directory under .scratch/review-diff/ each run. Stdout
# is the index. Earlier runs are left in place.
#
#   scripts/review-diff.sh           # staged + unstaged vs HEAD, plus untracked
#   scripts/review-diff.sh HEAD      # the last commit (after commit)
#   scripts/review-diff.sh <rev>     # git diff <rev>
#
# Then Read each page path the index prints. Do not dump whole-tree git diff.
# Untracked files are new-file diffs in the pages. Context stays -U3.
# A file bigger than one page is split; the index prints its byte size.
#
# REVIEW_DIFF_OUT     directory to write. Default: a new
#                     .scratch/review-diff/run-* directory
# REVIEW_DIFF_PAGE    bytes per page file (default 20000)
# REVIEW_DIFF_INLINE  if one page is this size or smaller, also print it
# REVIEW_DIFF_ROOT    repository to diff (default: this repo)

set -uo pipefail
export LC_ALL=C

ROOT="${REVIEW_DIFF_ROOT:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT"

PAGE="${REVIEW_DIFF_PAGE:-20000}"
INLINE="${REVIEW_DIFF_INLINE:-12000}"

if [ -n "${REVIEW_DIFF_OUT:-}" ]; then
  OUTDIR="$REVIEW_DIFF_OUT"
  mkdir -p "$OUTDIR"
else
  mkdir -p "$ROOT/.scratch/review-diff"
  OUTDIR="$(mktemp -d "$ROOT/.scratch/review-diff/run-XXXXXX")"
fi
# Clear this run's files only. Never remove the parent or a sibling run.
rm -f "$OUTDIR"/page-*.txt "$OUTDIR"/status.txt "$OUTDIR"/stat.txt \
  "$OUTDIR"/names.txt "$OUTDIR"/untracked.txt "$OUTDIR"/INDEX.txt

mode="uncommitted"
range=""
if [ "${1:-}" = "HEAD" ]; then
  mode="HEAD"
elif [ -n "${1:-}" ]; then
  mode="range"
  range="$1"
fi

diff_for() {
  local path="$1"
  local u="${2:-3}"
  case "$mode" in
    uncommitted)
      if grep -Fxq -- "$path" "$OUTDIR/untracked.txt"; then
        git diff --no-index --unified="$u" -- /dev/null "$path" || true
      else
        git diff -U"$u" HEAD -- "$path"
      fi
      ;;
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
    if [ -s "$OUTDIR/untracked.txt" ]; then
      cat "$OUTDIR/untracked.txt" >>"$OUTDIR/names.txt"
    fi
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
declare -A FILE_BYTES=()
declare -A FILE_NOTED=()
large=()

new_page() {
  page_n=$((page_n + 1))
  page_file="$OUTDIR/page-$(printf '%02d' "$page_n").txt"
  PAGE_PATHS+=("")
  {
    echo "=== review-diff page ${page_n} ($mode) ==="
    echo
  } >"$page_file"
  page_used=$(wc -c <"$page_file" | tr -d ' ')
}

append_text() {
  local text="$1"
  local n
  n=$(printf '%s' "$text" | wc -c | tr -d ' ')
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
  local label="$path"
  if [ -z "${FILE_NOTED[$path]:-}" ]; then
    label="$path (${FILE_BYTES[$path]:-0} bytes)"
    FILE_NOTED[$path]=1
  else
    label="$path (continued)"
  fi
  PAGE_PATHS[$i]="${PAGE_PATHS[$i]}${label}"$'\n'
}

add_section() {
  local path="$1"
  local body="$2"
  local header continued line ln n
  header="=== ${path} ==="$'\n'
  continued="=== ${path} (continued) ==="$'\n'

  if [ "$page_n" -eq 0 ]; then
    new_page
  fi

  # Small enough to keep on one page (or start a new one).
  n=$(printf '%s' "$header$body"$'\n' | wc -c | tr -d ' ')
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
    n=$(printf '%s' "$ln" | wc -c | tr -d ' ')
    if [ "$page_used" -gt 200 ] && [ $((page_used + n)) -gt "$PAGE" ]; then
      new_page
      append_text "$continued"
      note_path "$path"
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
    if [ -z "$body" ]; then
      body="(empty diff)"
    fi
    n=$(printf '%s' "$body" | wc -c | tr -d ' ')
    FILE_BYTES["$path"]=$n
    if [ "$n" -gt "$PAGE" ]; then
      large+=("$n bytes  $path")
    fi
    add_section "$path" "$body"
  done
fi

{
  echo "=== review-diff ($mode) repo=$ROOT ==="
  echo "run=$OUTDIR"
  echo "pages=$OUTDIR page=${PAGE} inline=${INLINE}"
  echo "This run only. Earlier run directories are left in place."
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
    echo "=== untracked (new-file diffs are in the pages) ==="
    cat "$OUTDIR/untracked.txt"
  fi
  if [ "${#skipped_binary[@]}" -gt 0 ]; then
    echo
    echo "=== skipped binary ==="
    printf '%s\n' "${skipped_binary[@]}"
  fi
  if [ "${#large[@]}" -gt 0 ]; then
    echo
    echo "=== larger than one page (context kept) ==="
    printf '%s\n' "${large[@]}"
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
      sz=$(wc -c <"$f" | tr -d ' ')
      echo "Read $f ($sz bytes)"
      printf '%s' "${PAGE_PATHS[$((i - 1))]}" | sed '/^$/d' | sed 's/^/  /'
      i=$((i + 1))
    done
    only="$OUTDIR/page-01.txt"
    only_sz=$(wc -c <"$only" | tr -d ' ')
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
