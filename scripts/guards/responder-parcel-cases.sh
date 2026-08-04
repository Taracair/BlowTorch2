#!/usr/bin/env bash
# Every responder type must be listed in TriggerData.readFromParcel.
#
# A trigger edited in the UI travels to :stellar as a Parcelable, and that is
# the only copy the service saves. The switch there rebuilds responders by type;
# a type it does not name falls into default, which reads the parcelable purely
# to stay in sync and then throws it away. The responder is silently dropped —
# the editor shows it, the file never gets it, and nothing logs anything.
#
# That is exactly what happened to the tap action (added 57ab5ce0, noticed by
# the maintainer four days later: "the option does not save").
#
# So: for every RESPONDER_TYPE_* constant declared in TriggerResponder, there
# must be a matching case in TriggerData.

set -uo pipefail
cd "$(dirname "$0")/../.." || exit 0
root="$(git rev-parse --show-toplevel 2>/dev/null || true)"
[ -n "$root" ] && cd "$root"

types="BTLib/src/com/resurrection/blowtorch2/lib/responder/TriggerResponder.java"
data="BTLib/src/com/resurrection/blowtorch2/lib/trigger/TriggerData.java"
[ -f "$types" ] || exit 0
[ -f "$data" ] || exit 0

missing=""
for name in $(grep -oE 'RESPONDER_TYPE_[A-Z_]+' "$types" | sort -u); do
  # The enum's own int-value field is not a responder type.
  [ "$name" = "RESPONDER_TYPE_" ] && continue
  if ! grep -q "TriggerResponder\.$name" "$data"; then
    missing="$missing $name"
  fi
done

if [ -n "$missing" ]; then
  echo "BLOCKED: responder type(s) with no case in TriggerData.readFromParcel:$missing"
  echo "Without a case the responder is dropped when the edited trigger crosses"
  echo "the binder, so it never reaches the file the service saves."
  exit 2
fi

exit 0
