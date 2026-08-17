package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * {@code .tutorial} and {@code .tips} — Java names so a world that never grew
 * the Lua plugin still recognises the command instead of printing "not a
 * recognized alias or command". The body lives in startertutorial.lua.
 */
public class TutorialCommand extends SpecialCommand {

	private final String luaCallback;

	public TutorialCommand(final String commandName, final String luaCallback) {
		this.commandName = commandName;
		this.luaCallback = luaCallback;
	}

	@Override
	public Object execute(final Object o, final Connection c) {
		String args = o == null ? "" : (String) o;
		c.runStarterTutorialCommand(luaCallback, args);
		return null;
	}
}
