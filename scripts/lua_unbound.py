#!/usr/bin/env python3
"""Flag names that are unbound inside a bare `module(...)` Lua file.

Why this exists
---------------
`module("x")` without `package.seeall` replaces the file's environment with the
module table, so nothing from `_G` is in scope — not even base functions like
`error` or `pairs`. A name that was never imported is therefore not a parse
error and not a load error: it is `nil`, and it only bites on the branch that
uses it. `luac -p` structurally cannot see this, and the Lua tests cannot
either, because these files bind Android classes and will not load off-device.

That class has been found by hand twice (buttoneditor.lua's `view` and
`error`). This closes it at the point of cause.

What it does NOT do
-------------------
This is a lexical sweep, not a parser. It strips comments and strings with a
small scanner and then approximates scope by treating every name bound
*anywhere* in the file as bound *everywhere* in it — deliberately, to keep
false positives near zero. Consequences:

  * a name used before its `local` is declared is not reported;
  * a name only ever reached through a table (`t[k]()`) or from inside a
    string passed to `loadstring` is invisible;
  * a shadowed local hides a real problem.

So a clean run here is weaker evidence than a clean `luac -p`. It catches the
one specific mistake it was written for: an identifier used bare that the file
never imports, declares, or defines.

Usage: scripts/lua_unbound.py [files...]   (defaults to the button plugin assets)
Exit status 1 if anything is reported.
"""

import re
import sys
import os

KEYWORDS = {
    "and", "break", "do", "else", "elseif", "end", "false", "for", "function",
    "if", "in", "local", "nil", "not", "or", "repeat", "return", "then",
    "true", "until", "while",
}

# `self` is implicit in `function a:b()`; module(...) itself defines these.
ALWAYS_BOUND = {"self", "_M", "_NAME", "_PACKAGE"}

IDENT = r"[A-Za-z_][A-Za-z0-9_]*"


def strip_comments_and_strings(src):
    """Blank out comments and string literals, preserving offsets and newlines."""
    out = []
    i = 0
    n = len(src)
    while i < n:
        c = src[i]
        # long bracket, as a comment or as a string
        m = re.match(r"(--)?\[(=*)\[", src[i:])
        if m and (m.group(1) or c == "["):
            level = m.group(2)
            close = "]" + level + "]"
            end = src.find(close, i + m.end())
            end = n if end == -1 else end + len(close)
            chunk = src[i:end]
            out.append("".join(ch if ch == "\n" else " " for ch in chunk))
            i = end
            continue
        if src.startswith("--", i):
            end = src.find("\n", i)
            end = n if end == -1 else end
            out.append(" " * (end - i))
            i = end
            continue
        if c in "'\"":
            j = i + 1
            while j < n and src[j] != c:
                if src[j] == "\\":
                    j += 1
                if src[j:j + 1] == "\n":
                    break
                j += 1
            j = min(j + 1, n)
            chunk = src[i:j]
            out.append("".join(ch if ch == "\n" else " " for ch in chunk))
            i = j
            continue
        out.append(c)
        i += 1
    return "".join(out)


def bound_names(code):
    """Every name the file binds, by any means, anywhere."""
    names = set(ALWAYS_BOUND)

    # local function f()
    for m in re.finditer(r"\blocal\s+function\s+(" + IDENT + r")", code):
        names.add(m.group(1))

    # local a, b, c [= ...]
    for m in re.finditer(r"\blocal\s+((?:" + IDENT + r"\s*,\s*)*" + IDENT + r")", code):
        for part in m.group(1).split(","):
            names.add(part.strip())

    # function name(...) / function a.b.c(...) / function a:b(...)
    for m in re.finditer(r"\bfunction\s+(" + IDENT + r")([.:]" + IDENT + r")*\s*\(([^)]*)\)", code):
        names.add(m.group(1))
        for p in m.group(3).split(","):
            p = p.strip()
            if re.fullmatch(IDENT, p):
                names.add(p)

    # anonymous function(...)
    for m in re.finditer(r"\bfunction\s*\(([^)]*)\)", code):
        for p in m.group(1).split(","):
            p = p.strip()
            if re.fullmatch(IDENT, p):
                names.add(p)

    # for i = ... / for k, v in ...
    for m in re.finditer(r"\bfor\s+((?:" + IDENT + r"\s*,\s*)*" + IDENT + r")\s*(=|\bin\b)", code):
        for part in m.group(1).split(","):
            names.add(part.strip())

    # assignment targets: `a = ...`, `a, b = ...`. Also catches table-constructor
    # keys (`{ foo = 1 }`), which over-binds rather than under-binds. Skips
    # `==`, `<=`, `>=`, `~=`.
    for m in re.finditer(r"(?<![=~<>])\b((?:" + IDENT + r"\s*,\s*)*" + IDENT + r")\s*=(?!=)", code):
        for part in m.group(1).split(","):
            names.add(part.strip())

    return names


def used_names(code, from_line):
    """Bare identifier uses at or after from_line, with line numbers.

    Everything above the `module(...)` call still runs with the normal
    environment — that is where the `local x = _G["x"]` header lives — so uses
    there are not evidence of anything.
    """
    uses = {}
    for m in re.finditer(r"(\.|:)?\b(" + IDENT + r")\b", code):
        line = code.count("\n", 0, m.start(2)) + 1
        if line <= from_line:
            continue
        if m.group(1):          # field or method access — not a bare name
            continue
        name = m.group(2)
        if name in KEYWORDS:
            continue
        # `function foo(` — foo is a definition, handled by bound_names
        before = code[max(0, m.start(2) - 10):m.start(2)]
        if re.search(r"\bfunction\s*$", before):
            continue
        uses.setdefault(name, code.count("\n", 0, m.start(2)) + 1)
    return uses


def check(path):
    with open(path, encoding="utf-8", errors="replace") as fh:
        src = fh.read()
    code = strip_comments_and_strings(src)
    # `^[ \t]*`, not `^\s*`: with re.M a `\s*` would swallow preceding blank
    # lines and put the call one line too early.
    m = re.search(r"^[ \t]*(module)\s*\(\s*\.\.\.\s*\)[ \t]*$", code, re.M)
    if not m:
        return []          # not a bare module(...) file — _G is in scope
    if re.search(r"module\s*\([^)]*package\.seeall", code):
        return []
    module_line = code.count("\n", 0, m.start(1)) + 1
    bound = bound_names(code)
    return [(path, line, name)
            for name, line in sorted(used_names(code, module_line).items(),
                                     key=lambda kv: kv[1])
            if name not in bound]


def main(argv):
    paths = argv[1:]
    if not paths:
        here = os.path.dirname(os.path.abspath(__file__))
        assets = os.path.join(here, os.pardir, "BT_Free", "assets", "share", "lua", "5.1")
        paths = sorted(os.path.join(assets, f)
                       for f in os.listdir(assets) if f.endswith(".lua"))
    findings = []
    for p in paths:
        findings.extend(check(p))
    for path, line, name in findings:
        rel = os.path.relpath(path)
        print("%s:%d: '%s' is not imported, not local and not a module member — "
              "it is nil inside module(...)" % (rel, line, name))
    if findings:
        print("\n%d unbound name(s). Import it at the top "
              "(local %s = _G[\"%s\"]) or fix the call."
              % (len(findings), findings[0][2], findings[0][2]))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
