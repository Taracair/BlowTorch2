#!/usr/bin/env bash
# Catch a call to a global whose name is another function's name with something
# glued to it — "newrefreshRect" next to a real "refreshRect".
#
# That is what a careless text replace produces: renaming
# "b:updateRect(statusoffset)" to "refreshRect(b)" also matched the tail of
# "newb:updateRect(statusoffset)" and left "newrefreshRect(b)". luac -p cannot
# see it (the call is valid Lua), the module(...) sweep does not cover these
# files, and the Lua tests cannot load them off-device. It reached the phone as
# "attempt to call global 'newrefreshRect'" and took the app down when the
# player added a button.
#
# Deliberately narrow: only names that contain a *defined* function name and are
# themselves never defined. That is close to zero false positives, and it needs
# no list of the globals Java registers.

set -uo pipefail
cd "$(dirname "$0")/../.." || exit 0

PY="$(command -v python3 || command -v python || true)"
if [ -z "$PY" ]; then
  echo "lua-glued-names: python3 not installed, cannot check"
  exit 1
fi

"$PY" - "$@" <<'PY'
import glob
import re
import sys

files = sys.argv[1:] or sorted(glob.glob("BT_Free/assets/share/lua/5.1/*.lua"))
if not files:
    sys.exit(0)

sources = {}
for path in files:
    try:
        with open(path, encoding="utf-8", errors="replace") as handle:
            sources[path] = handle.read()
    except OSError:
        pass

def strip_quotes(line):
    """A quoted word( is not a call. Long [[...]] strings are left alone on
    purpose: swallowing them with a lazy regex ate whole files and the guard
    went quiet, which is worse than a false positive. Prose survives the
    length rules below instead."""
    line = re.sub(r'"(?:\\.|[^"\\])*"', ' ', line)
    line = re.sub(r"'(?:\\.|[^'\\])*'", ' ', line)
    return line

# Anything bound to a name counts as defined: functions, proxies, locals. The
# question here is only "does this name exist at all".
defined = set()
define_re = re.compile(r"^\s*(?:local\s+)?function\s+([A-Za-z_][A-Za-z0-9_.:]*)\s*\(", re.M)
bound_re = re.compile(r"^\s*(?:local\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=", re.M)
local_re = re.compile(r"^\s*local\s+([A-Za-z_][A-Za-z0-9_]*)", re.M)
for text in sources.values():
    for name in define_re.findall(text):
        defined.add(name.split(".")[-1].split(":")[-1])
    defined.update(bound_re.findall(text))
    defined.update(local_re.findall(text))

call_re = re.compile(r"(?<![\w.:])([A-Za-z_][A-Za-z0-9_]*)\s*\(")
bad = []
for path, text in sources.items():
    for line_no, line in enumerate(text.split("\n"), 1):
        stripped = strip_quotes(line.split("--", 1)[0])
        for name in call_re.findall(stripped):
            if name in defined:
                continue
            for known in defined:
                # A real function name with a short scrap stuck to it. Both
                # limits matter: without the first, "setmetatable" reads as
                # "table" with glue; without the second, every honest
                # setNameExists / drawButtonsNoSelected is reported. What is
                # left is the shape a careless replace leaves behind.
                if name == known or len(known) < 8:
                    continue
                if len(name) - len(known) > 4:
                    continue
                if name.endswith(known) or name.startswith(known):
                    bad.append((path, line_no, name, known, line.strip()))
                    break

if bad:
    print("BLOCKED: call to a global that looks like a glued-together name:")
    for path, line_no, name, known, line in bad:
        print("  %s:%d  %s  (there is a function %s)" % (path, line_no, name, known))
        print("      %s" % line)
    print("This is what a text replace across a file does. Check the rename.")
    sys.exit(2)
PY
