#!/usr/bin/env python3
"""Inject ForgeMap plugin and MAP button into BlowTorch profile XML files."""
import sys
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
\t\t<options title="ForgeMap" summary="Exploration map with tile notes, quick actions, and pathfinding.">
\t\t\t<boolean title="Auto-open map panel" key="auto_open_map" summary="Show map strip when connecting.">true</boolean>
\t\t\t<integer title="Tile size (dp)" key="tile_size_dp" summary="Base tile size on screen.">44</integer>
\t\t\t<boolean title="Show tile labels" key="show_labels" summary="Draw room names on tiles in full map mode.">true</boolean>
\t\t</options>
\t\t<triggers>
\t\t\t<trigger title="forgemap_gmcp_room" pattern="%room.info" interpretLiteral="false" enabled="true" sequence="1">
\t\t\t\t<script function="got_gmcp_room" fireWhen="always"/>
\t\t\t</trigger>
\t\t\t<trigger title="forgemap_gmcp_area" pattern="%room.area" interpretLiteral="false" enabled="true" sequence="1">
\t\t\t\t<script function="update_gmcp_area" fireWhen="always"/>
\t\t\t</trigger>
\t\t\t<trigger title="forgemap_room_title" pattern="^=+\\s*(.+?)\\s*=+$" interpretLiteral="false" enabled="true" sequence="50">
\t\t\t\t<script function="forgemap_room_title" fireWhen="always"/>
\t\t\t</trigger>
\t\t\t<trigger title="forgemap_enter" pattern="^[Yy]ou (?:are in|enter|arrive (?:at|in)|walk into|step into) (.+?)[\\.!]?$" interpretLiteral="false" enabled="true" sequence="50">
\t\t\t\t<script function="forgemap_enter_room" fireWhen="always"/>
\t\t\t</trigger>
\t\t\t<trigger title="forgemap_exits" pattern="^[Ee]xits?:?\\s+(.+)$" interpretLiteral="false" enabled="true" sequence="51">
\t\t\t\t<script function="forgemap_room_exits" fireWhen="always"/>
\t\t\t</trigger>
\t\t</triggers>
\t\t<aliases>
\t\t\t<alias pre="^n$" post=".fmwalk n" enabled="true" />
\t\t\t<alias pre="^s$" post=".fmwalk s" enabled="true" />
\t\t\t<alias pre="^e$" post=".fmwalk e" enabled="true" />
\t\t\t<alias pre="^w$" post=".fmwalk w" enabled="true" />
\t\t\t<alias pre="^u$" post=".fmwalk u" enabled="true" />
\t\t\t<alias pre="^d$" post=".fmwalk d" enabled="true" />
\t\t</aliases>
\t\t<script name="bootstrap" execute="true"><![CDATA[require("forgemapserver")]]></script>
\t\t<script name="forgemapwindow"><![CDATA[require("forgemapwindow")]]></script>
\t</plugin>
"""

MAP_BUTTON = (
	'{ ["flipLabel"] = "", ["x"] = 1090, ["label"] = "MAP", ["y"] = 462, '
	'["command"] = ".map", ["flipCommand"] = "", ["name"] = "", ["labelSize"] = 12 }, '
)


def patch(path: Path) -> None:
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
	if changed:
		path.write_text(text, encoding="utf-8")
		print(f"Patched {path}")
	else:
		print(f"No changes needed for {path}")


def main() -> None:
	root = Path(__file__).resolve().parents[1]
	targets = [root / "samples" / "samsaramoo.xml", root / "samples" / "test.xml"]
	for t in targets:
		if not t.exists():
			print(f"Skip missing {t}")
			continue
		patch(t)


if __name__ == "__main__":
	main()
