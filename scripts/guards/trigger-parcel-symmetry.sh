#!/usr/bin/env bash
# TriggerData's parcel is a positional format: writeToParcel and readFromParcel
# have to agree on the order and the count of the fields before the responder
# loop. There is no key, so a field added to one side and not the other does not
# fail loudly -- it shifts every field after it, and a trigger arrives in the
# other process with its group in its pattern.
#
# This came up when the resolved pattern (an alias's text pasted into a trigger
# pattern) had to start crossing the binder: the UI process builds the
# tappable-word rules from the trigger it is handed, and while the resolved form
# stayed behind in the service the word never lit up.
#
# So: count the scalar writes before the responder count is written and the
# scalar reads before it is read, and insist they match. That catches a field
# added to one side only, which is the mistake that actually happens.
# Parcelables are not counted -- they are matched by type in
# responder-parcel-cases.sh -- and a pair swapped within the run is not caught
# by a count, only by reading the two methods side by side, which is why they
# are next to each other in the file.

set -uo pipefail
cd "$(dirname "$0")/../.." || exit 0
root="$(git rev-parse --show-toplevel 2>/dev/null || true)"
[ -n "$root" ] && cd "$root"

data="BTLib/src/com/resurrection/blowtorch2/lib/trigger/TriggerData.java"
[ -f "$data" ] || exit 0

writes="$(awk '
  /public void writeToParcel/ { on = 1; next }
  on && /out\.writeInt\(responders\.size\(\)\)/ { exit }
  on && /out\.write(String|Int|Long|Float|Double)\(/ { n++ }
  END { print n + 0 }
' "$data")"

reads="$(awk '
  /public void readFromParcel/ { on = 1; next }
  on && /int numresponders = in\.readInt\(\)/ { exit }
  on && /in\.read(String|Int|Long|Float|Double)\(/ { n++ }
  END { print n + 0 }
' "$data")"

if [ "$writes" != "$reads" ]; then
  echo "BLOCKED: TriggerData parcel is lopsided: $writes field(s) written," \
       "$reads read before the responders."
  echo "The format is positional. One side missing a field shifts every field"
  echo "after it, and the trigger arrives in the other process scrambled."
  echo "Fix writeToParcel and readFromParcel together, in the same order."
  exit 2
fi

exit 0
