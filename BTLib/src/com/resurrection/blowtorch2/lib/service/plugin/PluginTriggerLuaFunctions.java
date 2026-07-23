package com.resurrection.blowtorch2.lib.service.plugin;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;

import org.keplerproject.luajava.JavaFunction;
import org.keplerproject.luajava.LuaException;
import org.keplerproject.luajava.LuaObject;
import org.keplerproject.luajava.LuaState;

import android.os.RemoteException;
import android.util.Log;

import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.responder.TriggerResponder.FIRE_WHEN;
import com.resurrection.blowtorch2.lib.responder.ack.AckResponder;
import com.resurrection.blowtorch2.lib.responder.color.ColorAction;
import com.resurrection.blowtorch2.lib.responder.gag.GagAction;
import com.resurrection.blowtorch2.lib.responder.notification.NotificationResponder;
import com.resurrection.blowtorch2.lib.responder.replace.ReplaceResponder;
import com.resurrection.blowtorch2.lib.responder.script.ScriptResponder;
import com.resurrection.blowtorch2.lib.responder.toast.ToastResponder;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;

/*! \page page1
\subsection DeleteTrigger DeleteTrigger
Deletes a trigger.

\par Full Signature
\luacode
DeleteTrigger(name)
\endluacode
\param name \b string the the trigger to delete
\returns none
\par Example 
\luacode
DeleteTrigger("mob_alert_1")
\endluacode
*/
class DeleteTriggerFunction extends JavaFunction {
	Plugin plugin;

	public DeleteTriggerFunction(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
	}

	@Override
	public int execute() throws LuaException {
		
		String name = this.getParam(2).getString();
		if(name != null) {
			if(plugin.getSettings().getTriggers().containsKey(name)) {
				plugin.getSettings().getTriggers().remove(name);
			}
		}
		plugin.parent.setTriggersDirty();
		return 0;
	}
}

/*! \page page1
\subsection DeleteTriggerGroup DeleteTriggerGroup
Deletes an entire group of triggers.

\par Full Signature
\luacode
DeleteTriggerGroup(name)
\endluacode
\param name \b string the name of the trigger group to delete.
\returns none
\par Example 
\luacode
DeleteTriggerGroup("campaign_targets")
\endluacode
*/
class DeleteTriggerGroupFunction extends JavaFunction {
	Plugin plugin;

	public DeleteTriggerGroupFunction(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
	}

	@Override
	public int execute() throws LuaException, RemoteException,
			UnsupportedEncodingException {
		String group = this.L.LcheckString(2);
		
		if(group == null) return 0;
		
		ArrayList<String> tmp = new ArrayList<String>();
		for(TriggerData t : plugin.getSettings().getTriggers().values()) {
			if(t.getGroup().equals(group)) {
				tmp.add(t.getName());
			}
			//tmp.add(t.getName());
		}
		
		for(String name : tmp) {
			plugin.getSettings().getTriggers().remove(name);
		}
		
		plugin.parent.setTriggersDirty();
		
		return 0;
	}
}

/*! \page page1
\subsection EnableTriggerGroup EnableTriggerGroup
Sets the enabled state for all triggers in a group.

\par Full Signature
\luacode
EnableTriggerGroup(name,state)
\endluacode
\param name \b string Name of the trigger group to manipulate.
\param state \b boolean Enabled state for the group's triggers.
\returns none
\par Example 
\luacode
EnableTriggerGroup("auto_hunt")
\endluacode
*/
class EnableTriggerGroupFunction extends JavaFunction {
	Plugin plugin;

	public EnableTriggerGroupFunction(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
	}

	@Override
	public int execute() throws LuaException {
		String group = this.getParam(2).getString();
		boolean state = this.getParam(3).getBoolean();
		for(TriggerData t : plugin.getSettings().getTriggers().values()) {
			if(t.getGroup().equals(group)) {
				t.setEnabled(state);
			}
		}
		plugin.parent.setTriggersDirty();
		return 0;
	}
}

/*! \page page1
\subsection NewTrigger NewTrigger
Makes a new trigger with the given parameters

\par Full Signature
\luacode
NewTrigger(name,pattern,config[,action,...])
\endluacode
\param name \b string name of the new trigger.
\param pattern \b string the pattern to use
\param config \b table Configuration of the trigger options like, regex, fireOnce, etc. see example.
\param action \b table representing response actions to perform when triggered. May have as many as desired, see example.
\returns none
\par Example
\luacode
--configuration parameters is a table that can have the following properties defined:
--regex = [boolean] indicates use of regular expression or literal text.
--group = [string] group name to enroll this trigger into.
--fireOnce = [boolean] indicates whether this trigger is a one shot trigger.
--notification action table configuration can have the following properties
--{
--	type="notification", (must be set for the action type)
--	title = [string], title of the notification
--	message = [string], long message of the notification
--	soundpath = [string/boolean/nil], absolute path to the sound file to play for this notification. 
--	if nil/not set it will play the default sound. if set to false it won't play sound.
--	vibrate = [number], vibrate pattern to use, 0=short, 1=medium, 2=long, 3=super long
--	spawnNew = [boolean], indicates whether to spawn a new notification.
--} 		

--toast action table configuration
--{
--	type = "toast", (must be set for the action type)
--	message = [string], message to display
--	duration = [number], 0 = short duration, 1 = long duration.
--}

--send action table configuratio
--{
--	type = "send", (must be set for the action type)
--	text = [string] text to send to the server.
--}

--gag action table configuration
--{
--	type = "gag"
--	output=[boolean], gag from output
--	log = [boolean], gag from log
--	retarget = [string] window to send this gagged line to.
--}

--replace action table configuration
--{
--	type = "replace", (must be set for the action type).
--	text = [string] text to replace matched text with.
--	retarget = [string] optional window/slot to send the replaced line to.
--}

--color action table configuration
--{
--	type = "color", (must be set for the action type).
--	foreground = [number], 1-256 indicating the foreground xterm256 color to use.
--	background = [number], 1-256 indicating the background xterm256 color to use.
--}
--simple notification
NewTrigger("tmp", "^foo\.$", 
{ regex = true,group = "test", fireOnce = false }, 
{ type = "notification", title="custom title", message="custom message", vibrate = 2 })
--literal trigger with colorize and response to the server.
NewTrigger("tmp2", "fox", { regex = false },
{ type = "color", foreground = 36, background = 75},
{ type = "send", text = "listen fox" })
\endluacode
*/
class NewTriggerFunction extends JavaFunction {
	Plugin plugin;

	
	public NewTriggerFunction(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
	}
	
	@Override
	public int execute() throws LuaException {
		//biiig function.
		TriggerData t = new TriggerData();
		String name = this.getParam(2).getString();
		String pattern = this.getParam(3).getString();
		
		if(plugin.getSettings().getTriggers().containsKey(name)) {
			this.L.pushString("0");
			return 1;
		}
		
		t.setName(name);
		t.setPattern(pattern);
		
		Log.e("LUA","NEW TRIGGER: " + name + " PATTERN: " + pattern);
		
		//now comes the hard part.
		LuaObject options = this.getParam(4);
		if(options.isTable()) {
			this.L.pushNil();
			Log.e("LUA","DUMPING TRIGGER OPTION TABLE");
			while(this.L.next(4) != 0) {
				String key = this.L.getLuaObject(-2).getString();
				LuaObject obj = this.L.getLuaObject(-1);
				int type = obj.type();
				String value = null;
				//int bssfd = LuaState.LUA_TBOOLEAN;
				switch(type) {
				case Plugin.LUA_TBOOLEAN:
					value = Boolean.toString(obj.getBoolean()); 
					break;
				}
				
				if(key.equals("enabled")) {
					t.setEnabled(obj.getBoolean());
				} else if(key.equals("once")) {
					t.setFireOnce(obj.getBoolean());
				} else if(key.equals("regex")) {
					t.setInterpretAsRegex(obj.getBoolean());
				} else if(key.equals("group")) {
					t.setGroup(obj.getString());
				}
				Log.e("LUA","KEY: " + key + " VALUE: " + value + " TYPE: "+type);
				this.L.pop(1);
			}
			Log.e("LUA","\n\n");
		} else if(options.isNil()) {
			//assume default options
		} else {
			//error?
		}
		
		//start reading out the possible limitless responders.
		ArrayList<HashMap<String,Object>> responders = new ArrayList<HashMap<String,Object>>();
		
		int top = this.L.getTop();
		for(int i=5;i<=top;i++) {
			LuaObject tmp = this.getParam(i);
			if(tmp.isTable()) {
				HashMap<String,Object> data = new HashMap<String,Object>();
				//pump and dump the table
				this.L.pushNil();
				Log.e("LUA","DUMPING RESPONDER TABLE ARGUMENT: "+i);
				while(this.L.next(i) != 0) {
					String key = this.L.getLuaObject(-2).getString();
					LuaObject obj = this.L.getLuaObject(-1);
					int type = this.L.type(-1);
					Object value = null;
					switch(type) {
					case Plugin.LUA_TNIL:
						break;
					case Plugin.LUA_TNUMBER:
						value = new Double(obj.getNumber());
						break;
					case Plugin.LUA_TBOOLEAN:
						value = new Boolean(obj.getBoolean());
						break;
					case Plugin.LUA_TSTRING:
						value = obj.getString();
						break;
					}
					data.put(key, value);
					Log.e("LUA","KEY: " + key + " VALUE: " + value + " TYPE: "+type);
					this.L.pop(1);
				}
				
				if(data.size() > 0) {
					responders.add(data);
				}
				Log.e("LUA","\n\n");
			}
		}
		
		//ok now that we are done we can actually make the new trigger.
		for(int i=0;i<responders.size();i++) {
			TriggerResponder r = null;
			HashMap<String,Object> data = responders.get(i);
			String type = (String)data.get("type");
			boolean valid = true;
			if(type.equals("notification")) {
				NotificationResponder tmp = new NotificationResponder();
				Object title = data.get("title");
				Object message = data.get("message");
				Object soundpath = data.get("soundPath");
				Object vibrate = data.get("vibrate");
				Object light = data.get("light");
				Object spawnNew = data.get("spawnNew");
				
				if(title == null) {
					title = new String("Custom title!");
				}
				
				if(message == null) {
					message = new String("Custom message.");
				}
				
				if(soundpath == null) {
					soundpath = new Boolean(false);
				}
				
				if(vibrate == null) {
					vibrate = new Boolean(false);
				}
				
				if(light == null) {
					light = new Boolean(false);
				}
				
				if(spawnNew == null) {
					spawnNew = new Boolean(false);
				}
				
				tmp.setTitle((String)title);
				tmp.setMessage((String)message);
				if(soundpath instanceof String) {
					tmp.setSoundPath((String)soundpath);
					tmp.setUseDefaultSound(true);
				} else if(soundpath instanceof Boolean) {
					boolean b = (Boolean)soundpath;
					if(b) {
						tmp.setSoundPath("");
						tmp.setUseDefaultSound(true);
					} else {
						tmp.setSoundPath("");
						tmp.setUseDefaultSound(false);
					}
				}
				
				if(vibrate instanceof Boolean) {
					boolean b = (Boolean)vibrate;
					if(b) {
						tmp.setUseDefaultVibrate(true);
						tmp.setVibrateLength(0);
					} else {
						tmp.setUseDefaultVibrate(false);
						tmp.setVibrateLength(0);
					}
				} else if(vibrate instanceof Double) {
					tmp.setUseDefaultLight(true);
					int v = ((Double)vibrate).intValue();
					tmp.setVibrateLength((int)v);
				}
				
				if(light instanceof Boolean) {
					boolean b= (Boolean)light;
					if(b) {
						tmp.setUseDefaultLight(true);
						tmp.setColorToUse(0);
					} else {
						tmp.setUseDefaultLight(false);
						tmp.setColorToUse(0);
					}
				} else if(light instanceof Double) {
					tmp.setUseDefaultLight(true);
					int l = ((Double)light).intValue();
					tmp.setColorToUse(l);
				}
				
				if(spawnNew instanceof Boolean) {
					boolean b = (Boolean)spawnNew;
					tmp.setSpawnNewNotification(b);
				}
				
				r = tmp;
			} else if(type.equals("send")) {
				AckResponder tmp = new AckResponder();
				Object text = data.get("text");
				if(text == null) {
					text = "";
				} else if(text instanceof Double) {
					text = Double.toString((Double)text);
				}
				tmp.setAckWith((String)text);
				
				r = tmp;
			} else if(type.equals("toast")) {
				ToastResponder tmp = new ToastResponder();
				Object message = data.get("message");
				Object duration = data.get("duration");
				if(message == null) {
					message = "";
				}
				
				if(duration == null || !(duration instanceof Double)) {
					duration = Double.valueOf(0);
				}
				
				tmp.setMessage((String)message);
				tmp.setDelay(((Double)duration).intValue());
				
				r = tmp;
			} else if(type.equals("gag")) {
				GagAction tmp = new GagAction();
				Object output = data.get("output");
				Object log = data.get("log");
				Object retarget = data.get("retarget");
				if(output == null || !(output instanceof Boolean)) {
					output = true;
				}
				
				if(log == null || !(log instanceof Boolean)) {
					log = true;
				}
				
				if(retarget != null && !(retarget instanceof String)) {
					retarget = null;
				}
				if(retarget instanceof String && ((String) retarget).trim().length() == 0) {
					retarget = null;
				}
				
				tmp.setGagOutput((Boolean)output);
				tmp.setGagLog((Boolean)log);
				tmp.setRetarget(retarget == null ? null : (String) retarget);
				
				r = tmp;
			} else if(type.equals("replace")) {
				ReplaceResponder tmp = new ReplaceResponder();
				Object text = data.get("text");
				Object retarget = data.get("retarget");
				if(text == null || !(text instanceof String)) {
					text = "";
				}
				if(retarget != null && !(retarget instanceof String)) {
					retarget = null;
				}
				if(retarget instanceof String && ((String) retarget).trim().length() == 0) {
					retarget = null;
				}
				tmp.setWith((String)text);
				tmp.setRetarget(retarget == null ? null : (String) retarget);
				r = tmp;
			} else if(type.equals("color")) {
				ColorAction tmp = new ColorAction();
				Object foreground = data.get("foreground");
				Object background = data.get("background");
				if(foreground == null || !(foreground instanceof Double)) {
					foreground = new Double(256);
				}
				
				if(background == null || !(background instanceof Double)) {
					background = new Double(232);
				}
				tmp.setColor(((Double)foreground).intValue());
				tmp.setBackgroundColor(((Double)background).intValue());
				r = tmp;
			} else if(type.equals("script")) {
				ScriptResponder tmp = new ScriptResponder();
				Object function = data.get("function");
				if(function == null || !(function instanceof String)) {
					function = "";
				}
				tmp.setFunction((String)function);
				r = tmp;
			} else {
				//invalid.
				valid = false;
			}
			
			if(valid) {
			//handle fire type.
				String fire = (String)data.get("fire");
				
				if(fire == null) {
					r.setFireType(FIRE_WHEN.WINDOW_BOTH);
				} else {
					if(fire.equals("always")) {
						r.setFireType(FIRE_WHEN.WINDOW_BOTH);
					} else if(fire.equals("never")) {
						r.setFireType(FIRE_WHEN.WINDOW_NEVER);
					} else if(fire.equals("windowOpen")) {
						r.setFireType(FIRE_WHEN.WINDOW_OPEN);
					} else if(fire.equals("windowClosed")) {
						r.setFireType(FIRE_WHEN.WINDOW_CLOSED);
					}
				}
			}
			t.getResponders().add(r);
		}
		
		plugin.addTrigger(t);
		return 0;
	}
	
}

/*! \page page1
\subsection TriggerEnabled TriggerEnabled
Sets or Tests the enabled state of a trigger

\par Full Signature
\luacode
TriggerEnabled(name[,state])
\endluacode
\param name \b string the trigger to test or manipulate
\param state \b boolean the new enabled state
\returns Returns the enabled state of the trigger if state is nil. If state is set to true or false, will return true if the state was successfully changed.
\par Example 
\luacode
if(TriggerEnabled("afk")) then
TriggerEnabled("afk",false)
end
\endluacode
*/
class TriggerEnabledFunction extends JavaFunction {
	Plugin plugin;


	
	public TriggerEnabledFunction(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
		//this.L = L;
	}
	
	@Override
	public int execute() throws LuaException {
		String trigger = this.getParam(2).getString();
		
		if(plugin.getSettings().getTriggers().containsKey(trigger)) {
			//execute function
			if(this.getParam(3) == null) {
				L.pushBoolean(plugin.getSettings().getTriggers().get(trigger).isEnabled());
				return 1;
			}
			Boolean state = this.getParam(3).getBoolean();
			plugin.getSettings().getTriggers().get(trigger).setEnabled(state);
			
			plugin.markTriggersDirty();
			//this.mPlugin.buildTriggerSystem();
			L.pushBoolean(true);
			return 1;
			
		} else {
			//return error
			L.pushString("Function: TriggerEnabled(string,boolean) Error: \""+trigger+"\" does not exist");
			return 1;
		}
		
		//return 0;
	}

	
	
}

class SetVariableFunction extends JavaFunction {
	Plugin plugin;
	public SetVariableFunction(LuaState L, Plugin plugin) { super(L); this.plugin = plugin; }
	@Override public int execute() throws LuaException {
		String name = this.getParam(2) != null ? this.getParam(2).getString() : null;
		String value = this.getParam(3) != null ? this.getParam(3).getString() : "";
		if (name == null || name.length() == 0) { L.pushString("SetVariable: name required"); return 1; }
		plugin.parent.getSessionVariables().set(name, value != null ? value : "");
		return 0;
	}
}
class GetVariableFunction extends JavaFunction {
	Plugin plugin;
	public GetVariableFunction(LuaState L, Plugin plugin) { super(L); this.plugin = plugin; }
	@Override public int execute() throws LuaException {
		String name = this.getParam(2) != null ? this.getParam(2).getString() : null;
		if (name == null) { L.pushNil(); return 1; }
		String v = plugin.parent.getSessionVariables().get(name);
		if (v == null) L.pushNil(); else L.pushString(v);
		return 1;
	}
}
class UnsetVariableFunction extends JavaFunction {
	Plugin plugin;
	public UnsetVariableFunction(LuaState L, Plugin plugin) { super(L); this.plugin = plugin; }
	@Override public int execute() throws LuaException {
		String name = this.getParam(2) != null ? this.getParam(2).getString() : null;
		if (name != null) plugin.parent.getSessionVariables().unset(name);
		return 0;
	}
}
