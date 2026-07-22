package com.resurrection.blowtorch2.lib.service.function;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.os.Message;

import com.resurrection.blowtorch2.lib.mapper.MapDirections;
import com.resurrection.blowtorch2.lib.mapper.MapMoveEffect;
import com.resurrection.blowtorch2.lib.mapper.MapTile;
import com.resurrection.blowtorch2.lib.mapper.MapperController;
import com.resurrection.blowtorch2.lib.mapper.MudMap;
import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * Mapper special command.
 *
 * <pre>
 * .map / .map help
 * .map open|close|toggle
 * .map record on|off|toggle
 * .map follow on|off
 * .map level list|prev|next|set &lt;name&gt;|delete &lt;id|name&gt;
 * .map find|path|goto &lt;query&gt;
 * .map title|note &lt;text&gt;
 * .map link|unlink …
 * .map maps | load | new | import | export | undo | center | zoom | mode …
 * .map capture preview|apply
 * .map conflict[s] [list [all]|resolve|ignore …]
 * </pre>
 */
public class MapCommand extends SpecialCommand {

	public MapCommand() {
		this.commandName = "map";
	}

	@Override
	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : o.toString().trim();
		MapperController mapper = c.getMapper();
		if (mapper == null) {
			c.sendDataToWindow(Colorizer.getRedColor()
					+ "\nMapper unavailable.\n" + Colorizer.getWhiteColor());
			return null;
		}

		if (arg.length() == 0 || arg.equalsIgnoreCase("help") || arg.equals("?")) {
			c.sendDataToWindow(helpText(mapper));
			return null;
		}

		String[] parts = arg.split("\\s+", 2);
		String sub = parts[0].toLowerCase(Locale.US);
		String rest = parts.length > 1 ? parts[1].trim() : "";

		switch (sub) {
		case "open":
			return doOpen(c, mapper);
		case "close":
			return doClose(c, mapper);
		case "toggle":
			return doToggle(c, mapper);
		case "record":
		case "rec":
			return doRecord(c, mapper, rest);
		case "follow":
			return doFollow(c, mapper, rest);
		case "level":
		case "lvl":
			return doLevel(c, mapper, rest);
		case "find":
		case "search":
			return doFind(c, mapper, rest);
		case "path":
			return doPath(c, mapper, rest, false, false);
		case "goto":
			return doPath(c, mapper, rest, true, false);
		case "go":
		case "walkto":
			return doPath(c, mapper, rest, true, true);
		case "title":
			if (!requireEdit(c, mapper)) {
				return null;
			}
			return doTitleOrNotes(c, mapper, rest, true);
		case "note":
		case "notes":
			if (!requireEdit(c, mapper)) {
				return null;
			}
			return doTitleOrNotes(c, mapper, rest, false);
		case "link":
			if (!requireEdit(c, mapper)) {
				return null;
			}
			return doLink(c, mapper, rest);
		case "unlink":
			if (!requireEdit(c, mapper)) {
				return null;
			}
			return doUnlink(c, mapper, rest);
		case "add":
		case "place":
			if (!requireEdit(c, mapper)) {
				return null;
			}
			return doAdd(c, mapper, rest);
		case "here":
			note(c, mapper.setHere(rest));
			return null;
		case "dirs":
		case "directions":
		case "lexicon":
			return doDirs(c, mapper);
		case "moves":
		case "moveeffects":
			return doMoves(c, mapper, rest);
		case "delete":
		case "del":
		case "rm":
			if (!requireEdit(c, mapper)) {
				return null;
			}
			note(c, mapper.deleteTile(rest));
			return null;
		case "neighbor":
		case "nb":
			if (!requireEdit(c, mapper)) {
				return null;
			}
			return doNeighbor(c, mapper, rest);
		case "portal":
		case "linkmap":
			if (!requireEdit(c, mapper)) {
				return null;
			}
			return doPortal(c, mapper, rest);
		case "opacity":
			return doOpacity(c, mapper, rest);
		case "move":
			if (!requireEdit(c, mapper)) {
				return null;
			}
			return doMoveTile(c, mapper, rest);
		case "conflict":
		case "conflicts":
			return doConflicts(c, mapper, rest);
		case "export":
		case "save":
			note(c, mapper.exportMap(rest));
			return null;
		case "import":
			if (!requireEdit(c, mapper)) {
				return null;
			}
			note(c, mapper.importMap(rest));
			return null;
		case "center":
			mapper.centerUi();
			note(c, "Mapper: center.");
			return null;
		case "zoom":
			note(c, mapper.zoom(rest));
			return null;
		case "mode":
			return doMode(c, mapper, rest);
		case "oneway":
		case "one-way":
			return doOneWay(c, mapper, rest);
		case "maps":
			return doMaps(c, mapper);
		case "load":
		case "openmap":
			note(c, mapper.openMap(rest));
			return null;
		case "new":
			if (!requireEdit(c, mapper)) {
				return null;
			}
			note(c, mapper.newMap(rest));
			return null;
		case "undo":
			if (!requireEdit(c, mapper)) {
				return null;
			}
			note(c, mapper.undoStatus());
			return null;
		case "capture":
			return doCapture(c, mapper, rest);
		case "status":
			c.sendDataToWindow(helpText(mapper));
			return null;
		default:
			c.sendDataToWindow(getErrorMessage("Map usage",
					"Unknown subcommand '" + sub + "'.\nTry .map help"));
			return null;
		}
	}

	private Object doOpen(Connection c, MapperController mapper) {
		c.requestMapperUi(1);
		note(c, "Mapper: open.");
		return null;
	}

	/**
	 * {@code .map title|note <text>} on current, or
	 * {@code .map title|note for <tileId> <text>} on a specific tile.
	 */
	private Object doTitleOrNotes(Connection c, MapperController mapper, String rest,
			boolean title) {
		String tileId = null;
		String text = rest;
		String lower = rest.toLowerCase(Locale.US);
		if (lower.startsWith("for ")) {
			String after = rest.substring(4).trim();
			int sp = after.indexOf(' ');
			if (sp <= 0) {
				note(c, title
						? "Usage: .map title for <tileId> <text>"
						: "Usage: .map note for <tileId> <text>");
				return null;
			}
			tileId = after.substring(0, sp).trim();
			text = after.substring(sp + 1);
		}
		if (title) {
			note(c, mapper.setTitle(tileId, text));
		} else {
			note(c, mapper.setNotes(tileId, text));
		}
		return null;
	}

	private Object doClose(Connection c, MapperController mapper) {
		c.requestMapperUi(2);
		note(c, "Mapper: close.");
		return null;
	}

	private Object doToggle(Connection c, MapperController mapper) {
		c.requestMapperUi(3);
		note(c, "Mapper: toggle.");
		return null;
	}

	private Object doRecord(Connection c, MapperController mapper, String rest) {
		String a = rest.toLowerCase(Locale.US);
		if (a.equals("on") || a.equals("1") || a.equals("true")) {
			note(c, mapper.setRecordingStatus(true));
		} else if (a.equals("off") || a.equals("0") || a.equals("false")) {
			note(c, mapper.setRecordingStatus(false));
		} else if (a.equals("toggle") || a.length() == 0) {
			if (!mapper.isEditMode() && !mapper.isRecording()) {
				note(c, mapper.setRecordingStatus(true));
			} else {
				note(c, mapper.setRecordingStatus(!mapper.isRecording()));
			}
		} else {
			note(c, "Usage: .map record on|off|toggle");
			return null;
		}
		return null;
	}

	private Object doFollow(Connection c, MapperController mapper, String rest) {
		String a = rest.toLowerCase(Locale.US);
		if (a.equals("on") || a.equals("1") || a.equals("true")) {
			mapper.setFollow(true);
		} else if (a.equals("off") || a.equals("0") || a.equals("false")) {
			mapper.setFollow(false);
		} else if (a.equals("toggle") || a.length() == 0) {
			if (a.equals("toggle")) {
				mapper.setFollow(!mapper.isFollowPlayer());
			} else {
				note(c, "Mapper follow: " + (mapper.isFollowPlayer() ? "on" : "off"));
				return null;
			}
		} else {
			note(c, "Usage: .map follow on|off|toggle");
			return null;
		}
		note(c, "Mapper follow: " + (mapper.isFollowPlayer() ? "on" : "off"));
		return null;
	}

	private Object doLevel(Connection c, MapperController mapper, String rest) {
		if (rest.length() == 0 || rest.equalsIgnoreCase("list")) {
			note(c, mapper.levelList());
			return null;
		}
		String[] p = rest.split("\\s+", 2);
		String sub = p[0].toLowerCase(Locale.US);
		String name = p.length > 1 ? p[1].trim() : "";
		if (sub.equals("prev") || sub.equals("-")) {
			note(c, mapper.levelPrev());
		} else if (sub.equals("next") || sub.equals("+")) {
			note(c, mapper.levelNext());
		} else if (sub.equals("set")) {
			note(c, mapper.levelSet(name));
		} else if (sub.equals("delete") || sub.equals("del") || sub.equals("rm")) {
			if (!requireEdit(c, mapper)) {
				return null;
			}
			if (name.length() == 0) {
				note(c, "Usage: .map level delete <id|name>");
			} else {
				note(c, mapper.deleteLevel(name));
			}
		} else if (sub.equals("move")) {
			if (!requireEdit(c, mapper)) {
				return null;
			}
			String[] mp = name.split("\\s+", 2);
			if (mp.length < 2) {
				note(c, "Usage: .map level move <tileId> <levelName>");
			} else {
				note(c, mapper.moveTileLevel(mp[0], mp[1]));
			}
		} else {
			// treat whole rest as level name
			note(c, mapper.levelSet(rest));
		}
		return null;
	}

	private Object doFind(Connection c, MapperController mapper, String query) {
		if (query.length() == 0) {
			note(c, "Usage: .map find <query>");
			return null;
		}
		List<MapTile> hits = mapper.search(query);
		if (hits.isEmpty()) {
			note(c, "Mapper: no tiles matching \"" + query + "\".");
			return null;
		}
		StringBuilder sb = new StringBuilder();
		sb.append("Mapper find \"").append(query).append("\" (").append(hits.size()).append("):\n");
		int n = Math.min(hits.size(), 20);
		for (int i = 0; i < n; i++) {
			MapTile t = hits.get(i);
			sb.append("  ").append(shortId(t.getId())).append("  ");
			sb.append(t.getTitle() != null ? t.getTitle() : "(untitled)");
			sb.append("  @").append(t.getGridX()).append(",").append(t.getGridY());
			sb.append("\n");
		}
		if (hits.size() > n) {
			sb.append("  …\n");
		}
		note(c, sb.toString());
		return null;
	}

	private Object doPath(Connection c, MapperController mapper, String query,
			boolean gotoMode, boolean forceSend) {
		if (query.length() == 0) {
			note(c, "Usage: .map " + (forceSend ? "go" : (gotoMode ? "goto" : "path"))
					+ " <query|tileId>");
			return null;
		}
		List<String> cmds = mapper.findPathToTile(query.trim());
		if (cmds == null || cmds.isEmpty()) {
			cmds = mapper.findPath(query);
		}
		if (cmds == null || cmds.isEmpty()) {
			List<MapTile> hits = mapper.search(query);
			if (hits.isEmpty() && mapper.getMap() != null
					&& mapper.getMap().findTile(query.trim()) == null) {
				note(c, "Mapper: no match for \"" + query + "\".");
			} else {
				note(c, "Mapper: no path to \"" + query + "\" (or already there).");
			}
			return null;
		}
		StringBuilder path = new StringBuilder();
		for (int i = 0; i < cmds.size(); i++) {
			if (i > 0) {
				path.append("; ");
			}
			path.append(cmds.get(i));
		}
		note(c, "Mapper path (" + cmds.size() + "): " + path);

		boolean send = forceSend || (gotoMode && mapper.isPathAutoSend());
		if (send) {
			final List<String> toSend = new ArrayList<String>(cmds);
			c.getHandler().post(new Runnable() {
				@Override
				public void run() {
					mapper.setSuppressRecord(true);
					try {
						for (int i = 0; i < toSend.size(); i++) {
							String step = toSend.get(i);
							if (step == null) {
								continue;
							}
							step = step.trim();
							if (step.length() == 0) {
								continue;
							}
							// Callback-based Handler: handleMessage() is a no-op —
							// must dispatchMessage (or sendMessage) to run ConnectionHandler.
							Message m = c.getHandler().obtainMessage(
									Connection.MESSAGE_SENDDATA_STRING, step);
							c.getHandler().dispatchMessage(m);
						}
					} catch (Exception e) {
						note(c, "Mapper: failed to send path.");
					} finally {
						mapper.setSuppressRecord(false);
					}
				}
			});
			note(c, forceSend
					? "Mapper: path sent (" + cmds.size() + " steps)."
					: "Mapper: path sent (mapper_path_auto_send, "
							+ cmds.size() + " steps).");
		} else if (gotoMode) {
			note(c, "Mapper: path_auto_send off — path printed only. Use .map go to send.");
		}
		return null;
	}

	private Object doLink(Connection c, MapperController mapper, String rest) {
		if (rest.length() == 0) {
			note(c, "Usage: .map link <cmd> [from <id>] to <tileId>");
			return null;
		}
		String cmd;
		String toId = null;
		String fromId = null;
		String work = rest.trim();
		String lower = work.toLowerCase(Locale.US);
		int fromIdx = lower.indexOf(" from ");
		int toIdx = lower.indexOf(" to ");
		if (fromIdx >= 0 && toIdx > fromIdx) {
			cmd = work.substring(0, fromIdx).trim();
			fromId = work.substring(fromIdx + 6, toIdx).trim();
			toId = work.substring(toIdx + 4).trim();
			note(c, mapper.linkBetween(fromId, cmd, toId));
			return null;
		}
		if (toIdx >= 0) {
			cmd = work.substring(0, toIdx).trim();
			toId = work.substring(toIdx + 4).trim();
		} else {
			String[] p = work.split("\\s+", 2);
			cmd = p[0];
			if (p.length > 1) {
				toId = p[1].trim();
			}
		}
		note(c, mapper.link(cmd, toId));
		return null;
	}

	private Object doUnlink(Connection c, MapperController mapper, String rest) {
		if (rest.length() == 0) {
			note(c, "Usage: .map unlink <cmd> [from <tileId>]");
			return null;
		}
		String work = rest.trim();
		String lower = work.toLowerCase(Locale.US);
		int fromIdx = lower.indexOf(" from ");
		if (fromIdx >= 0) {
			String cmd = work.substring(0, fromIdx).trim();
			String fromId = work.substring(fromIdx + 6).trim();
			note(c, mapper.unlinkBetween(fromId, cmd));
		} else {
			note(c, mapper.unlink(work));
		}
		return null;
	}

	private Object doDirs(Connection c, MapperController mapper) {
		StringBuilder sb = new StringBuilder();
		sb.append("Mapper movement lexicon (effective):\n");
		if (mapper != null) {
			String table = mapper.getCombinedMoveEffectsDisplay();
			if (table != null && table.length() > 0) {
				sb.append(table).append('\n');
			}
		}
		sb.append("\nBuilt-in notes:\n");
		for (String line : MapDirections.lexiconSummary()) {
			sb.append(line).append('\n');
		}
		note(c, sb.toString());
		return null;
	}

	private Object doMoves(Connection c, MapperController mapper, String rest) {
		String work = rest != null ? rest.trim() : "";
		if (work.length() == 0 || work.equalsIgnoreCase("list")
				|| work.equalsIgnoreCase("show")) {
			StringBuilder sb = new StringBuilder();
			sb.append("Mapper moves (edit: File → Moves, or Options → Mapper):\n");
			sb.append(mapper.getCombinedMoveEffectsDisplay());
			sb.append("\nUsage: .map moves [list|reset|set <cmd> …|unset <cmd>|apply <table>]\n");
			note(c, sb.toString());
			return null;
		}
		String lower = work.toLowerCase(Locale.US);
		if (lower.equals("reset") || lower.equals("default")
				|| lower.equals("defaults")) {
			note(c, mapper.resetMoveEffects());
			return null;
		}
		if (lower.startsWith("apply ") || lower.startsWith("replace ")
				|| lower.startsWith("load ")) {
			int sp = work.indexOf(' ');
			String body = sp >= 0 ? work.substring(sp + 1).trim() : "";
			note(c, mapper.applyCombinedMoveEffects(body));
			return null;
		}
		if (lower.startsWith("unset ") || lower.startsWith("rm ")
				|| lower.startsWith("delete ") || lower.startsWith("del ")) {
			int sp = work.indexOf(' ');
			String cmd = sp >= 0 ? work.substring(sp + 1).trim() : "";
			note(c, mapper.unsetOneMoveEffect(cmd));
			return null;
		}
		if (lower.startsWith("set ")) {
			String[] p = work.substring(4).trim().split("\\s+");
			if (p.length < 2) {
				note(c, "Usage: .map moves set <cmd> grid <dx> <dy>|level <n>|special");
				return null;
			}
			String cmd = p[0];
			String kind = p[1].toLowerCase(Locale.US);
			MapMoveEffect fx = null;
			if (kind.equals("special") || kind.equals("spec") || kind.equals("s")) {
				fx = MapMoveEffect.special();
			} else if (kind.equals("level") || kind.equals("lvl") || kind.equals("l")) {
				if (p.length < 3) {
					note(c, "Usage: .map moves set <cmd> level <+1|-1|n>");
					return null;
				}
				try {
					fx = MapMoveEffect.level(Integer.parseInt(p[2]));
				} catch (NumberFormatException e) {
					note(c, "Mapper: level delta must be an integer.");
					return null;
				}
			} else if (kind.equals("grid") || kind.equals("g") || kind.equals("planar")) {
				if (p.length < 4) {
					note(c, "Usage: .map moves set <cmd> grid <dx> <dy>");
					return null;
				}
				try {
					fx = MapMoveEffect.grid(Integer.parseInt(p[2]),
							Integer.parseInt(p[3]));
				} catch (NumberFormatException e) {
					note(c, "Mapper: grid dx/dy must be integers.");
					return null;
				}
			} else if (kind.equals("up")) {
				fx = MapMoveEffect.level(1);
			} else if (kind.equals("down")) {
				fx = MapMoveEffect.level(-1);
			} else {
				note(c, "Usage: .map moves set <cmd> grid <dx> <dy>|level <n>|special");
				return null;
			}
			note(c, mapper.setOneMoveEffect(cmd, fx));
			return null;
		}
		note(c, "Usage: .map moves [list|reset|set …|unset …|apply …]");
		return null;
	}

	private Object doAdd(Connection c, MapperController mapper, String rest) {
		Integer x = null;
		Integer y = null;
		String title = null;
		boolean makeHere = false;
		if (rest.length() > 0) {
			String work = rest.trim();
			String lower = work.toLowerCase(Locale.US);
			if (lower.endsWith(" here")) {
				makeHere = true;
				work = work.substring(0, work.length() - 5).trim();
			} else if (lower.equals("here")) {
				makeHere = true;
				work = "";
			}
			if (work.length() > 0) {
				String[] p = work.split("\\s+");
				if (p.length >= 2) {
					try {
						x = Integer.valueOf(p[0]);
						y = Integer.valueOf(p[1]);
						if (p.length > 2) {
							StringBuilder sb = new StringBuilder();
							for (int i = 2; i < p.length; i++) {
								if (i > 2) {
									sb.append(' ');
								}
								sb.append(p[i]);
							}
							title = sb.toString();
						}
					} catch (NumberFormatException e) {
						title = work;
					}
				} else {
					title = work;
				}
			}
		}
		note(c, mapper.placeTile(x, y, title, makeHere));
		return null;
	}

	private Object doNeighbor(Connection c, MapperController mapper, String rest) {
		if (rest.length() == 0) {
			note(c, "Usage: .map neighbor <cmd> [from <tileId>]");
			return null;
		}
		String work = rest.trim();
		String lower = work.toLowerCase(Locale.US);
		int fromIdx = lower.indexOf(" from ");
		if (fromIdx >= 0) {
			String cmd = work.substring(0, fromIdx).trim();
			String fromId = work.substring(fromIdx + 6).trim();
			note(c, mapper.addNeighbor(fromId, cmd));
		} else {
			note(c, mapper.addNeighbor(null, work));
		}
		return null;
	}

	/**
	 * {@code .map portal <cmd> map <mapName> [from <tileId>]}
	 * or {@code .map portal <cmd> <mapName> [from <tileId>]}.
	 */
	private Object doPortal(Connection c, MapperController mapper, String rest) {
		if (rest.length() == 0) {
			note(c, "Usage: .map portal <cmd> map <mapName> [from <tileId>]");
			return null;
		}
		String work = rest.trim();
		String lower = work.toLowerCase(Locale.US);
		String fromId = null;
		int fromIdx = lower.indexOf(" from ");
		if (fromIdx >= 0) {
			fromId = work.substring(fromIdx + 6).trim();
			work = work.substring(0, fromIdx).trim();
			lower = work.toLowerCase(Locale.US);
		}
		String cmd;
		String mapName;
		int mapIdx = lower.indexOf(" map ");
		if (mapIdx >= 0) {
			cmd = work.substring(0, mapIdx).trim();
			mapName = work.substring(mapIdx + 5).trim();
		} else {
			int sp = work.indexOf(' ');
			if (sp <= 0) {
				note(c, "Usage: .map portal <cmd> map <mapName> [from <tileId>]");
				return null;
			}
			cmd = work.substring(0, sp).trim();
			mapName = work.substring(sp + 1).trim();
		}
		note(c, mapper.linkMapPortal(fromId, cmd, mapName));
		return null;
	}

	private Object doOpacity(Connection c, MapperController mapper, String rest) {
		if (rest.length() == 0) {
			note(c, "Mapper: opacity " + mapper.getOpacity() + "%");
			return null;
		}
		try {
			int pct = Integer.parseInt(rest.trim());
			mapper.setOpacity(pct);
			note(c, "Mapper: opacity " + mapper.getOpacity() + "%");
		} catch (NumberFormatException e) {
			note(c, "Usage: .map opacity [40-100]");
		}
		return null;
	}

	private Object doMoveTile(Connection c, MapperController mapper, String rest) {
		String[] p = rest.split("\\s+");
		if (p.length < 2) {
			note(c, "Usage: .map move <x> <y>  OR  .map move <tileId> <x> <y>");
			return null;
		}
		try {
			if (p.length >= 3) {
				note(c, mapper.moveTileOnGrid(p[0],
						Integer.parseInt(p[1]), Integer.parseInt(p[2])));
			} else {
				note(c, mapper.moveTileOnGrid(null,
						Integer.parseInt(p[0]), Integer.parseInt(p[1])));
			}
		} catch (NumberFormatException e) {
			note(c, "Usage: .map move <x> <y>  OR  .map move <tileId> <x> <y>");
		}
		return null;
	}

	private Object doConflicts(Connection c, MapperController mapper, String rest) {
		String a = rest.trim();
		if (a.length() == 0 || a.equalsIgnoreCase("list")) {
			note(c, mapper.conflictList(false));
			return null;
		}
		String[] p = a.split("\\s+", 2);
		String sub = p[0].toLowerCase(Locale.US);
		String arg = p.length > 1 ? p[1].trim() : "";
		if (sub.equals("list")) {
			boolean all = arg.equalsIgnoreCase("all") || arg.equalsIgnoreCase("resolved");
			note(c, mapper.conflictList(all));
		} else if (sub.equals("resolve") || sub.equals("ignore")) {
			if (arg.equalsIgnoreCase("all")) {
				note(c, sub.equals("ignore")
						? mapper.ignoreAllConflicts()
						: mapper.resolveAllConflicts());
			} else if (arg.length() == 0) {
				note(c, "Usage: .map conflict " + sub + " <id|n>|all");
			} else {
				note(c, sub.equals("ignore")
						? mapper.ignoreConflict(arg)
						: mapper.resolveConflict(arg));
			}
		} else if (sub.equals("purge")) {
			note(c, mapper.purgeResolved());
		} else {
			note(c, "Usage: .map conflict [list [all]|resolve|ignore <id|n>|all|purge]");
		}
		return null;
	}

	private Object doMode(Connection c, MapperController mapper, String rest) {
		String a = rest.toLowerCase(Locale.US);
		if (a.equals("browse") || a.equals("view") || a.equals("nav")) {
			mapper.setEditMode(false);
			note(c, "Mapper mode: Browse");
		} else if (a.equals("edit")) {
			mapper.setEditMode(true);
			note(c, "Mapper mode: Edit");
		} else if (a.equals("toggle") || a.equals("swap")) {
			boolean edit = mapper.toggleEditMode();
			note(c, "Mapper mode: " + (edit ? "Edit" : "Browse"));
		} else if (a.equals("fullscreen") || a.equals("full") || a.equals("fs")) {
			mapper.setModeFullscreen(true);
			note(c, "Mapper display: fullscreen");
		} else if (a.equals("float") || a.equals("floating") || a.equals("window")) {
			mapper.setModeFullscreen(false);
			note(c, "Mapper display: float");
		} else {
			note(c, "Usage: .map mode browse|edit|toggle | fullscreen|float");
		}
		return null;
	}

	private Object doOneWay(Connection c, MapperController mapper, String rest) {
		String a = rest.toLowerCase(Locale.US);
		if (a.equals("on") || a.equals("1") || a.equals("true") || a.equals("accept")) {
			mapper.setAcceptOneWaySpecials(true);
		} else if (a.equals("off") || a.equals("0") || a.equals("false")
				|| a.equals("smart") || a.equals("close")) {
			mapper.setAcceptOneWaySpecials(false);
		} else if (a.equals("toggle") || a.length() == 0) {
			mapper.toggleAcceptOneWaySpecials();
		} else {
			note(c, "Usage: .map oneway on|off|toggle");
			return null;
		}
		note(c, "Mapper accept one-way specials: "
				+ (mapper.isAcceptOneWaySpecials() ? "on" : "off"));
		return null;
	}

	private Object doMaps(Connection c, MapperController mapper) {
		List<String> names = mapper.listMaps();
		MudMap map = mapper.getMap();
		String cur = map != null ? map.getName() : "(none)";
		StringBuilder sb = new StringBuilder();
		sb.append("Mapper maps (current: ").append(cur).append("):\n");
		if (names.isEmpty()) {
			sb.append("  (none on disk)\n");
		} else {
			for (String n : names) {
				sb.append(n != null && n.equals(cur) ? " * " : "   ");
				sb.append(n).append("\n");
			}
		}
		note(c, sb.toString());
		return null;
	}

	private Object doCapture(Connection c, MapperController mapper, String rest) {
		String a = rest.toLowerCase(Locale.US);
		if (a.startsWith("preview") || a.length() == 0) {
			note(c, mapper.capturePreview(20));
		} else if (a.startsWith("apply")) {
			if (!requireEdit(c, mapper)) {
				return null;
			}
			note(c, mapper.captureApply());
		} else {
			note(c, "Usage: .map capture preview|apply");
		}
		return null;
	}

	private static void note(Connection c, String msg) {
		if (msg == null) {
			return;
		}
		if (!msg.startsWith("\n")) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor() + msg
					+ (msg.endsWith("\n") ? "" : "\n"));
		} else {
			c.sendDataToWindow(Colorizer.getWhiteColor() + msg);
		}
	}

	private static boolean requireEdit(Connection c, MapperController mapper) {
		String deny = mapper.requireEditMode();
		if (deny != null) {
			note(c, deny);
			return false;
		}
		return true;
	}

	private static String shortId(String id) {
		if (id == null) {
			return "?";
		}
		return id.length() > 8 ? id.substring(0, 8) : id;
	}

	private static String statusLine(MapperController m) {
		MudMap map = m.getMap();
		String name = map != null && map.getName() != null ? map.getName() : "(none)";
		int tiles = map != null ? map.getTiles().size() : 0;
		return "map=" + name + " tiles=" + tiles
				+ " mode=" + (m.isEditMode() ? "edit" : "browse")
				+ " rec=" + (m.isRecording() ? "on" : "off")
				+ " follow=" + (m.isFollowPlayer() ? "on" : "off");
	}

	private static String helpText(MapperController m) {
		StringBuilder sb = new StringBuilder();
		sb.append("\n").append(Colorizer.getWhiteColor());
		sb.append("Mapper (").append(statusLine(m)).append(")\n");
		sb.append("  .map open|close|toggle\n");
		sb.append("  .map record|rec on|off|toggle\n");
		sb.append("  .map follow on|off|toggle\n");
		sb.append("  .map level list|prev|next|set <name>|delete <id|name>|move <tileId> <level>\n");
		sb.append("      (L-/L+ follow/return nests; create only in Edit mode)\n");
		sb.append("  .map find|search <query> | .map path <query>\n");
		sb.append("  .map goto <query>  (send if path_auto_send) | .map go <query|id> (always send)\n");
		sb.append("  .map title <text> | .map note|notes <text>\n");
		sb.append("  .map title for <id> <text> | .map note for <id> <text>\n");
		sb.append("  .map link <cmd> [from <id>] to <tileId> | .map unlink <cmd> [from <id>]\n");
		sb.append("  .map dirs|directions|lexicon  (current move table + notes)\n");
		sb.append("  .map moves [list|reset|set <cmd> …|unset <cmd>|apply <table>]\n");
		sb.append("  .map add|place [x y] [title] [here] | .map here [id] | .map delete|del|rm [id]\n");
		sb.append("  .map neighbor|nb <cmd> [from <id>] | .map move [id] <x> <y>\n");
		sb.append("  .map portal|linkmap <cmd> map <name> [from <id>]\n");
		sb.append("  .map opacity [40-100]\n");
		sb.append("  .map conflict[s] [list [all]|resolve|ignore <id|n>|all|purge]\n");
		sb.append("  .map export|save [path] | .map import <path|name> | .map undo | .map center\n");
		sb.append("  .map zoom in|out|reset  (or .map zoom <factor>)\n");
		sb.append("  .map mode browse|edit|toggle  (Browse = view/nav only; Edit = record+edit)\n");
		sb.append("  .map mode fullscreen|float\n");
		sb.append("  .map oneway on|off|toggle  (ON = specials spawn new tiles; OFF = close to unique inbound)\n");
		sb.append("  .map maps | .map load|openmap <name> | .map new <name> (new/import = Edit)\n");
		sb.append("  .map capture preview|apply  (apply = Edit; Options → Mapper regex)\n");
		sb.append("  Browse: no record / Draw / Links / tile edit. Follow tracks known exits while walking.\n");
		return sb.toString();
	}
}
