#!/usr/bin/env bash
# Launchers key pinned home screen icons on the component name. Moving the
# MAIN/LAUNCHER intent filter to another component killed the maintainer's home
# screen icon while the app was still installed.
#
# This pins the fact so the next agent cannot undo it by reasoning.
#
# If the entry point genuinely has to move, update EXPECTED below in its own
# commit, with the maintainer's agreement.

set -uo pipefail
# Run from the repo root wherever the caller happens to be. The original pair
# of cd lines appended /.. to the toplevel, landing in the parent staging
# folder, where git ls-files matches nothing and the guard silently passed.
cd "$(dirname "$0")/../.." || exit 0
root="$(git rev-parse --show-toplevel 2>/dev/null || true)"
[ -n "$root" ] && cd "$root"

EXPECTED="FreeLauncher"

manifests="$(git ls-files '*AndroidManifest.xml' || true)"
[ -z "$manifests" ] && exit 0

found=0
for m in $manifests; do
  # Find the activity block that carries android.intent.category.LAUNCHER and
  # report which component name it belongs to.
  owner="$(python3 - "$m" <<'PY'
import re, sys, xml.etree.ElementTree as ET
ns = "{http://schemas.android.com/apk/res/android}"
try:
    root = ET.parse(sys.argv[1]).getroot()
except Exception:
    sys.exit(0)
for act in root.iter():
    if not act.tag.endswith("activity") and not act.tag.endswith("activity-alias"):
        continue
    for f in act.findall("intent-filter"):
        cats = {c.get(ns + "name") for c in f.findall("category")}
        acts = {a.get(ns + "name") for a in f.findall("action")}
        if "android.intent.category.LAUNCHER" in cats and "android.intent.action.MAIN" in acts:
            print(act.get(ns + "name") or "")
PY
)"
  [ -z "$owner" ] && continue
  found=1
  case "$owner" in
    *"$EXPECTED"*) ;;
    *)
      echo "BLOCKED: the MAIN/LAUNCHER filter is on '$owner', expected '$EXPECTED' ($m)."
      echo "Pinned home screen icons are keyed on the component name. Moving it"
      echo "breaks the maintainer's launcher icon while the app is still installed."
      echo "If a trampoline flashes, theme it with BlowTorch.Invisible instead."
      exit 2
      ;;
  esac
done

exit 0
