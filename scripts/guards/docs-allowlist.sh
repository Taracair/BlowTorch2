#!/usr/bin/env bash
# docs/ is public: player guides, packager notes, architecture, working
# agreement. Plans, audits, roadmaps and internal notes belong outside the repo
# or in .scratch/.
#
#   scripts/guards/docs-allowlist.sh <path> [<path>...]
#
# Exit 0 = every path allowed. Exit 2 = at least one is not, reason on stdout.
#
# This replaces public-docs-only.mdc, which spent 1.7 KB of every session
# describing a filename check.

set -uo pipefail

allowed=(
  "docs/user-manual.md"
  "docs/options-guide.md"
  "docs/fdroid.md"
  "docs/FDROID_README.md"
  "docs/architecture.md"
  "docs/plugin-authoring.md"
  "docs/ORCHESTRATION.md"
  "docs/CODEBASE-TRAPS.md"
  "docs/GUARDRAILS.md"
  "docs/canvas-capabilities.md"
  "docs/media/README.md"
  "docs/media/.gitkeep"
)

bad=0
root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

for raw in "$@"; do
  # Normalise to a repo-relative path.
  rel="${raw#"$root"/}"
  rel="${rel#./}"

  case "$rel" in
    docs/*) ;;
    *) continue ;;
  esac

  # A gitignored file is not in the public tree and never will be. HANDOFF.md,
  # changelog_draft.md and the audits live in docs/ on purpose and are edited
  # daily; firing on them would make the guard noise instead of a barrier.
  if git -C "$root" check-ignore -q "$rel" 2>/dev/null; then
    continue
  fi

  ok=0
  for a in "${allowed[@]}"; do
    [ "$rel" = "$a" ] && ok=1 && break
  done

  if [ "$ok" -eq 0 ]; then
    echo "NOT ALLOWED: $rel"
    bad=1
  fi
done

if [ "$bad" -ne 0 ]; then
  cat <<'EOF'
docs/ is the public tree. Only the allowlisted files belong there.
Plans, audits, roadmaps, changelog drafts and internal notes go in .scratch/
(gitignored) or outside the repo. If this file really should be public, add it
to the allowlist in scripts/guards/docs-allowlist.sh in its own commit.
EOF
  exit 2
fi

exit 0
