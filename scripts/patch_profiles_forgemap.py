#!/usr/bin/env python3
"""Inject ForgeMap plugin and MAP button into BlowTorch **test** profile XML only."""
from pathlib import Path

FORGEMAP_PLUGIN = """
\t<plugin name="forgemap" id="11">
\t\t<windows>
\t\t\t<window name="forgemap_window" id="10204" script="forgemapwindow">
\t\t\t\t<layoutGroup target="normal">
\t\t\t\t\t<layout orientation="landscape" width="fill_parent" height="148" above="40"/>
\t\t\t\t\t<layout orientation="portrait" width="fill_parent" height="148" above="40"/>
\t\t\t\t</layoutGroup>
\t\t\t</window>
\t\t</windows>
\t\t<options title="ForgeMap" summary="Manual exploration map — tap adjacent ? tiles to build the map room by room.">
\t\t\t<boolean title="Auto-open map panel" key="auto_open_map" summary="Show map strip when connecting.">true</boolean>
\t\t\t<integer title="Tile size (dp)" key="tile_size_dp" summary="Base tile size on screen.">36</integer>
\t\t\t<boolean title="Show tile labels" key="show_labels" summary="Draw room names on explored tiles.">true</boolean>
\t\t</options>
\t\t<triggers/>
\t\t<aliases/>
\t\t<script name="bootstrap" execute="true"><![CDATA[require("forgemapserver")]]></script>
\t\t<script name="forgemapwindow"><![CDATA[require("forgemapwindow")]]></script>
\t</plugin>
"""

MAP_BUTTON = (
	'{ ["flipLabel"] = "", ["x"] = 1090, ["label"] = "MAP", ["y"] = 462, '
	'["command"] = ".map", ["flipCommand"] = "", ["name"] = "", ["labelSize"] = 12 }, '
)

BUTTON_WINDOW_ABOVE_FORGEMAP = (
	'<window name="button_window" id="10203" script="buttonwindow">\n'
	'\t\t\t<layoutGroup target="normal">\n'
	'\t\t\t\t<layout orientation="landscape" above="10204" width="fill_parent" height="fill_parent" />\n'
	'\t\t\t\t<layout orientation="portrait" above="10204" width="fill_parent" height="fill_parent" />'
)


def patch_test_profile(path: Path) -> None:
	text = path.read_text(encoding="utf-8")
	changed = False
	if 'name="forgemap"' not in text:
		if "  </plugins>" not in text:
			raise SystemExit(f"{path}: missing </plugins>")
		text = text.replace("  </plugins>", FORGEMAP_PLUGIN + "  </plugins>", 1)
		changed = True
	if ".map" not in text and ".clearbuttons" in text:
		text = text.replace(
			'{ ["flipLabel"] = "", ["x"] = 1146, ["label"] = "CLEAR"',
			MAP_BUTTON + '{ ["flipLabel"] = "", ["x"] = 1146, ["label"] = "CLEAR"',
			1,
		)
		changed = True
	if 'name="button_window" id="10203"' in text and 'above="10204"' not in text:
		text = text.replace(
			'<window name="button_window" id="10203" script="buttonwindow">\n'
			'\t\t\t<layoutGroup target="normal">\n'
			'\t\t\t\t<layout orientation="landscape" above="40" width="fill_parent" height="fill_parent" />',
			BUTTON_WINDOW_ABOVE_FORGEMAP,
			1,
		)
		text = text.replace(
			'<layout orientation="portrait" above="40" width="fill_parent" height="fill_parent" />\n'
			'\t\t\t</layoutGroup>\n'
			'\t\t</window>\n'
			'\t</windows>\n'
			'\t<options title="Button"',
			'<layout orientation="portrait" above="10204" width="fill_parent" height="fill_parent" />\n'
			'\t\t\t</layoutGroup>\n'
			'\t\t</window>\n'
			'\t</windows>\n'
			'\t<options title="Button"',
			1,
		)
		changed = True
	if changed:
		path.write_text(text, encoding="utf-8")
		print(f"Patched {path}")
	else:
		print(f"No changes needed for {path}")


def main() -> None:
	root = Path(__file__).resolve().parents[1]
	patch_test_profile(root / "samples" / "test.xml")


if __name__ == "__main__":
	main()
