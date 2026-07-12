#!/usr/bin/env python3
"""Remove ForgeMap plugin block and MAP button from a profile XML (main/production safe)."""
import re
import sys
from pathlib import Path

FORGEMAP_PLUGIN_RE = re.compile(
    r"\s*<plugin name=\"forgemap\" id=\"11\">.*?</plugin>\s*",
    re.DOTALL,
)

MAP_BUTTON_RE = re.compile(
    r'\{ \["flipLabel"\] = "", \["x"\] = 1090, \["label"\] = "MAP".*?\}, ',
)


def strip_forgemap(text: str) -> str:
    text = FORGEMAP_PLUGIN_RE.sub("\n", text)
    text = MAP_BUTTON_RE.sub("", text)
    text = text.replace('command"] = ".map"', 'command"] = ".clearbuttons"')  # safety noop if lone
    text = text.replace('"label"] = "MAP"', '"label"] = "CLEAR"')  # should not trigger after re
    return text


def main() -> None:
    paths = sys.argv[1:] or [
        str(Path(__file__).resolve().parents[1] / "samples" / "samsaramoo.xml"),
    ]
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
