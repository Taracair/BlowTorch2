#!/usr/bin/env python3
"""Remove ForgeMap plugin block and MAP buttons from a BlowTorch profile XML."""
import re
import sys
from pathlib import Path

FORGEMAP_PLUGIN_RE = re.compile(
    r"\s*<plugin name=\"forgemap\"[^>]*>.*?</plugin>\s*",
    re.DOTALL | re.IGNORECASE,
)
MAP_BUTTON_XML_RE = re.compile(
    r"\s*<button[^>]*label=\"MAP\"[^>]*/>\s*",
    re.IGNORECASE,
)


def strip_forgemap(text: str) -> str:
    text = FORGEMAP_PLUGIN_RE.sub("\n", text)
    text = MAP_BUTTON_XML_RE.sub("\n", text)
    return text


def main() -> None:
    paths = sys.argv[1:]
    if not paths:
        print("Usage: strip_forgemap_from_profile.py <profile.xml>...")
        sys.exit(1)
    for p in paths:
        path = Path(p)
        original = path.read_text(encoding="utf-8")
        updated = strip_forgemap(original)
        if updated != original:
            path.write_text(updated, encoding="utf-8")
            print(f"Stripped ForgeMap from {path}")
        else:
            print(f"No ForgeMap found in {path}")


if __name__ == "__main__":
    main()
