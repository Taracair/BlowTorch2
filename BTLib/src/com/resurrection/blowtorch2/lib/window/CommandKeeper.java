package com.resurrection.blowtorch2.lib.window;

import java.util.LinkedList;

import android.content.Context;
import android.content.SharedPreferences;

public class CommandKeeper {
	int max;
	LinkedList<String> commands;
	
	int index = 0;
	int selected = 0;
	
	static enum STATE {
		FORWARD,
		PREV,
		NONE
	}
	
	STATE direction = STATE.NONE;
	
	public CommandKeeper(int maxCommands) {
		max = Math.max(10, Math.min(100, maxCommands));
		commands = new LinkedList<String>();
	}

	public void setMax(int maxCommands) {
		max = Math.max(10, Math.min(100, maxCommands));
		while (commands.size() > max) {
			commands.removeLast();
		}
	}

	public int getMax() {
		return max;
	}
	
	public void addCommand(String cmd) {
		
		if(commands.size() > 0) {
			String test = commands.getFirst();
			if(test.equals(cmd)) {
				selected = 0;
				direction = STATE.NONE;
				return;
			}
		}
		
		if(cmd.equals("")) {
			return;
		}
		
		while (commands.size() >= max) {
			commands.removeLast();
		}

		
		commands.addFirst(cmd);
		selected = 0;
		direction = STATE.NONE;
		
	}
	
	public String getNext() {
		
		if (commands.size() == 0) {
			return "";
		}
		
		switch(direction) {
		case NONE:
			//at the 0'th command, have not gone forward or back. and set the state.
			selected = 0;
			direction = STATE.FORWARD;
			break;
		case FORWARD:
			//increase one unit
			selected = selected + 1;
			if(selected > commands.size() -1) {
				selected = 0;
			}
			break;
		case PREV:
			//increase by two units and set the state to forward.
			selected = selected + 2;
			direction = STATE.FORWARD;
			if(selected > commands.size() -1) {
				selected = 0;
			}
			break;
		}
		
		String get_current = commands.get(selected);
		return get_current;
		
	}
	
	public String getPrev() {
		
		if(commands.size() ==0) {
			return "";
		}
		
		switch(direction) {
		case FORWARD:
			selected = selected - 1;
			if(selected < 0) {
				selected = 0;
				return "";
			}
			direction = STATE.PREV;
			break;
		case PREV:
			selected = selected - 1;
			if(selected < 0) {
				//selected = commands.size() -1;
				selected = 0;
				direction = STATE.NONE;
				return "";
			}
			break;
		case NONE:
			selected = selected - 1;
			if(selected < 0) {
				selected = 0;
				return "";
			}
			direction = STATE.PREV;
			break;
		}
		
		String select = commands.get(selected);
		
		return select;
		

	}

	public void save(Context context, String profile) {
		if (context == null || profile == null) {
			return;
		}
		SharedPreferences.Editor edit = context.getSharedPreferences(prefsName(profile), Context.MODE_PRIVATE).edit();
		edit.clear();
		edit.putInt("count", commands.size());
		edit.putInt("max", max);
		int i = 0;
		for (String cmd : commands) {
			edit.putString("c" + i, cmd);
			i++;
		}
		edit.apply();
	}

	public void load(Context context, String profile) {
		if (context == null || profile == null) {
			return;
		}
		SharedPreferences prefs = context.getSharedPreferences(prefsName(profile), Context.MODE_PRIVATE);
		int count = prefs.getInt("count", 0);
		int storedMax = prefs.getInt("max", max);
		setMax(storedMax);
		commands.clear();
		for (int i = 0; i < count && i < max; i++) {
			String cmd = prefs.getString("c" + i, null);
			if (cmd != null && cmd.length() > 0) {
				commands.addLast(cmd);
			}
		}
		selected = 0;
		direction = STATE.NONE;
	}

	private static String prefsName(String profile) {
		return "INPUT_HISTORY_" + profile.replaceAll("[^A-Za-z0-9._-]+", "_");
	}
	
}
