package com.resurrection.blowtorch2.lib.service.plugin;

import java.util.HashMap;

import org.keplerproject.luajava.JavaFunction;
import org.keplerproject.luajava.LuaException;
import org.keplerproject.luajava.LuaState;

import com.resurrection.blowtorch2.lib.alias.AliasData;
import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.timer.TimerData;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;

/**
 * Read-only views of what the player has built, for Lua.
 *
 * <p>The Starter Tutorial needs these to check a lesson: it is one thing to
 * describe a trigger and another to look at the one the player actually made and
 * tell them the pattern is right but nothing is attached to it. A plugin's
 * {@code triggers} global is that plugin's own set, which cannot answer the
 * question.
 *
 * <p>Everything comes back as tab separated lines, one record per line. Lua has
 * no structured return worth the plumbing here, and this is easy to read in
 * either language. Nothing here can change anything: teaching material should
 * not be able to edit the player's work by accident.
 */
final class PluginInspectLuaFunctions {

	private PluginInspectLuaFunctions() {
	}

	/** Tabs and newlines would break the record format; spaces are harmless. */
	static String flat(final String raw) {
		if (raw == null) {
			return "";
		}
		return raw.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
	}

	static void registerAll(final LuaState l, final Plugin plugin) throws LuaException {
		new PlayerTriggersFunction(l, plugin).register("GetPlayerTriggers");
		new PlayerAliasesFunction(l, plugin).register("GetPlayerAliases");
		new PlayerTimersFunction(l, plugin).register("GetPlayerTimers");
	}
}

/**
 * {@code GetPlayerTriggers()} → one line per trigger:
 * {@code name<TAB>pattern<TAB>regex<TAB>enabled<TAB>responder,responder}.
 */
class PlayerTriggersFunction extends JavaFunction {
	private final Plugin plugin;

	PlayerTriggersFunction(LuaState l, Plugin p) {
		super(l);
		this.plugin = p;
	}

	@Override
	public int execute() throws LuaException {
		StringBuilder out = new StringBuilder();
		try {
			HashMap<String, TriggerData> all = plugin.getParentCallback().getTriggers();
			if (all != null) {
				for (TriggerData t : all.values()) {
					if (t == null) {
						continue;
					}
					out.append(PluginInspectLuaFunctions.flat(t.getName())).append('\t')
							.append(PluginInspectLuaFunctions.flat(t.getPattern())).append('\t')
							.append(t.isInterpretAsRegex()).append('\t')
							.append(t.isEnabled()).append('\t');
					java.util.List<TriggerResponder> responders = t.getResponders();
					if (responders != null) {
						for (int i = 0; i < responders.size(); i++) {
							if (responders.get(i) == null) {
								continue;
							}
							if (i > 0) {
								out.append(',');
							}
							// The class name is what a lesson wants to know:
							// "you made the pattern but attached nothing".
							out.append(responders.get(i).getClass().getSimpleName());
						}
					}
					out.append('\n');
				}
			}
		} catch (Exception e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
					"GetPlayerTriggers", e);
		}
		this.L.pushString(out.toString());
		return 1;
	}
}

/** {@code GetPlayerAliases()} → {@code pre<TAB>post<TAB>enabled<TAB>localEcho} per line. */
class PlayerAliasesFunction extends JavaFunction {
	private final Plugin plugin;

	PlayerAliasesFunction(LuaState l, Plugin p) {
		super(l);
		this.plugin = p;
	}

	@Override
	public int execute() throws LuaException {
		StringBuilder out = new StringBuilder();
		try {
			HashMap<String, AliasData> all = plugin.getParentCallback().getAliases();
			if (all != null) {
				for (AliasData a : all.values()) {
					if (a == null) {
						continue;
					}
					out.append(PluginInspectLuaFunctions.flat(a.getPre())).append('\t')
							.append(PluginInspectLuaFunctions.flat(a.getPost())).append('\t')
							.append(a.isEnabled()).append('\t')
							.append(a.getLocalEcho().toInspectToken()).append('\n');
				}
			}
		} catch (Exception e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
					"GetPlayerAliases", e);
		}
		this.L.pushString(out.toString());
		return 1;
	}
}

/** {@code GetPlayerTimers()} → {@code name<TAB>seconds<TAB>repeat<TAB>playing} per line. */
class PlayerTimersFunction extends JavaFunction {
	private final Plugin plugin;

	PlayerTimersFunction(LuaState l, Plugin p) {
		super(l);
		this.plugin = p;
	}

	@Override
	public int execute() throws LuaException {
		StringBuilder out = new StringBuilder();
		try {
			HashMap<String, TimerData> all = plugin.getParentCallback().getTimers();
			if (all != null) {
				for (TimerData t : all.values()) {
					if (t == null) {
						continue;
					}
					Integer seconds = t.getSeconds();
					out.append(PluginInspectLuaFunctions.flat(t.getName())).append('\t')
							.append(seconds == null ? 0 : seconds.intValue()).append('\t')
							.append(t.isRepeat()).append('\t')
							.append(t.isPlaying()).append('\n');
				}
			}
		} catch (Exception e) {
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
					"GetPlayerTimers", e);
		}
		this.L.pushString(out.toString());
		return 1;
	}
}
