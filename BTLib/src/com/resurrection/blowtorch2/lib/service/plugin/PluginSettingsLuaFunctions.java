package com.resurrection.blowtorch2.lib.service.plugin;

import org.keplerproject.luajava.JavaFunction;
import org.keplerproject.luajava.LuaException;
import org.keplerproject.luajava.LuaObject;
import org.keplerproject.luajava.LuaState;

import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import com.resurrection.blowtorch2.lib.script.ScriptData;
import com.resurrection.blowtorch2.lib.service.Connection;

/*! \page page1
\subsection ExecuteScript ExecuteScript
Executes a script that has been loaded during the plugin parsing phase.
 
\par Full Signature
\luacode
ExecuteScript(name)
\endluacode
\param name \b name of the script to run, this is configured in the plugin settings.
\returns none
\par Example 
\luacode
ExecuteScript("parseInventory")
\endluacode
*/	
  class ExecuteScriptFunction extends JavaFunction {
	Plugin plugin;


		public ExecuteScriptFunction(LuaState L, Plugin plugin) {
			super(L);
			this.plugin = plugin;
			// TODO Auto-generated constructor stub
		}

		@Override
		public int execute() throws LuaException {
			// TODO Auto-generated method stub
			String pName = this.getParam(2).getString();
			ScriptData d = plugin.getSettings().getScripts().get(pName);
			String body = d.getData();
			if(body != null) {
				L.LdoString(body);
			} else {
				//error
				//L.pushString(bytes)
				//L.error();
			}
			return 0;
		}
		
	}

/*! \page page1

\subsection GetPluginSettings GetPluginSettings
Gets the raw \b com.resurrection.blowtorch2.lib.service.settings.SettingsGroup settings, this is to allow direct manipulation.

\par Full Signature
\luacode
InvalidateWindowText(name)
\endluacode
\param none
\returns a \b com.resurrection.blowtorch2.lib.service.settings.SettingsGroup object that can be directly manipulated.
\par Example 
\luacode
settings = GetPluginSettings()
\endluacode
*/
class GetPluginSettingsFunction extends JavaFunction {
	Plugin plugin;

	public GetPluginSettingsFunction(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
	}
	
	@Override
	public int execute() throws LuaException {
		//if(this.getParam(2).isNil()) { return 0; }
		this.L.pushJavaObject(plugin.getSettings().getOptions());
		
		//Log.e("LUAWINDOW","script is sending:"+this.getParam(2).getString()+" to server.");
		//plugin.parent.handler.sendMessage(plugin.parent.handler.obtainMessage(Connection.MESSAGE_SENDDATA_STRING,this.getParam(2).getString()));
		return 1;
	}
	
}

/*! \page page1
\subsection ReloadSettings ReloadSettings
Causes the BlowTorch core to dump the current settings and reload them from the source files.

\par Full Signature
\luacode
ReloadSettings()
\endluacode
\param none
\returns none
\par Example 
\luacode
ReloadSettings()
\endluacode
*/
class ReloadSettingsFunction extends JavaFunction {
	Plugin plugin;

	public ReloadSettingsFunction(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
	}
	
	@Override
	public int execute() throws LuaException {
		//if(this.getParam(2).isNil()) { return 0; }
		//this.L.pushNumber(plugin.getSettings().getId());
		
		//Log.e("LUAWINDOW","script is sending:"+this.getParam(2).getString()+" to server.");
		plugin.mHandler.sendMessage(plugin.mHandler.obtainMessage(Connection.MESSAGE_RELOADSETTINGS));
		return 0;
	}
	
}

/*! \page page1
\subsection RegisterSpecialCommand RegisterSpecialCommand
Adds and entry into the "special command" processor, or .nameyouwant and it will call the specified global function

\par Full Signature
\luacode
RegisterSpecialCommand(sortName,callbackName)
\endluacode
\param shortName \b string short name that will be searched for
\param callbackName \b string the name of a global function to call when this is called.
\returns none
\par Example 
\luacode
function goHome(args)
SendToServer("enter portal")
end
RegisterSpecialCommand("home","goHome")
\endluacode
\note The callback, called when the command is processed, gives the arguments as a single string to the callback function.
*/
class RegisterFunctionCallback extends JavaFunction {
	Plugin plugin;


	public RegisterFunctionCallback(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
		
	}

	@Override
	public int execute() throws LuaException {
		LuaObject name = this.getParam(2);
		LuaObject function = this.getParam(3);
		
		if(name == null) {
			return 0;
		}
		
		if(function == null) {
			return 0;
		}
		
		if(!name.isString()) {
			return 0;
		}
		
		if(!function.isString()) {
			return 0;
		}
		//function.tos
		String funcstring = function.getString();
		Log.e("PLUGIN","SENDING FUNCTION:" + name + "("+funcstring+"): for inclusion into the global .command processor.");
		
		Message msg = plugin.mHandler.obtainMessage(Connection.MESSAGE_ADDFUNCTIONCALLBACK);
		Bundle b = msg.getData();
		b.putString("ID", plugin.getSettings().getName());
		b.putString("COMMAND", name.getString());
		b.putString("CALLBACK", funcstring);
		msg.setData(b);
		plugin.mHandler.sendMessage(msg);
		return 0;
	}
	
	
	
}

/*! \page page1
\subsection SaveSettings SaveSettings
Initiates the saving of the whole settings wad for the currently open connection.
 
\par Full Signature
\luacode
SaveSettings()
\endluacode
\param none
\returns none
\par Example 
\luacode
SaveSettings()
\endluacode
\note the callback that is called when the command is processed will give the arguments as a single string to the callback function.
*/
class SaveSettingsFunction extends JavaFunction {
	Plugin plugin;

	
	public SaveSettingsFunction(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
	}

	@Override
	public int execute() throws LuaException {
		plugin.getSettings().setDirty(true);
		plugin.mHandler.sendMessage(plugin.mHandler.obtainMessage(Connection.MESSAGE_SAVESETTINGS,plugin.getName()));
		return 0;
	}
}

