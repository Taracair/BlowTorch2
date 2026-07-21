package com.resurrection.blowtorch2.lib.service.plugin;

import java.io.File;

import org.keplerproject.luajava.JavaFunction;
import org.keplerproject.luajava.LuaException;
import org.keplerproject.luajava.LuaState;

import android.os.Environment;

import com.resurrection.blowtorch2.lib.service.Connection;

/*! \page page1 Lua Functions
 	* \section common Common Functions
\subsection GetActionBarHeight GetActionBarHeight
Executes a script that has been loaded during the plugin parsing phase.

\par Full Signature
\luacode
GetActionBarHeight()
\endluacode
\param none
\returns \b number the height of the ActionBar, 0 if running on Android 2.3 or lower.
\par Example 
\luacode
barHeight = GetActionBarHeight()
\endluacode
*/
class GetActionBarHeightFunction extends JavaFunction {
	Plugin plugin;


	public GetActionBarHeightFunction(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
		// TODO Auto-generated constructor stub
	}

	@Override
	public int execute() throws LuaException {
		// TODO Auto-generated method stub
		L.pushString(Integer.toString(((int)plugin.parent.getTitleBarHeight())));
		return 1;
	}
	
}

/*! \page page1 Lua Functions
\subsection GetDisplayDensity GetDisplayDensity
Executes a script that has been loaded during the plugin parsing phase.

\par Full Signature
\luacode
GetActionBarHeight()
\endluacode
\param none
\returns \b number the height of the ActionBar, 0 if running on Android 2.3 or lower.
\par Example 
\luacode
barHeight = GetActionBarHeight()
\endluacode
*/
class GetDisplayDensityFunction extends JavaFunction {
	Plugin plugin;


	public GetDisplayDensityFunction(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
		
	}

	@Override
	public int execute() throws LuaException {
		float density = plugin.mContext.getResources().getDisplayMetrics().density;
		//if((Window.this.getContext().getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_XLARGE) {
		//	density = density * 1.5f;
		//}
		//Log.e("WINODW","PUSHING DENSITY:"+Float.toString(density));
		L.pushNumber(density);
		return 1;
	}
	
}

/*! \page page1 Lua Functions
\subsection GetExternalStorageDirectory GetExternalStorageDirectory
Get the current external storage volume directory. Checks if the volume exists, but \b not if it is write protected.

\par Full Signature
\luacode
GetExternalStorageDirectory()
\endluacode
\param none
\returns \b string the absolute path to the root of the external storage directory.
\returns \b nil if there is no current external storage volume available.
\par Example 
\luacode
path = GetExternalStorageDirectory()
\endluacode
\note Equivalent Lua code
\luacode
Environment = luajava.bindClass("android.os.Environment")
local path = nil
if(Environment:getExternalStorageState() == Environment.MEDIA_MOUNTED) then
path = Environment:getExternalStorageDirectory():getAbsolutePath()
else
if(Environment:getExternalStorageState() == Environment.MEDIA_MOUNTED_READ_ONLY) then
	path = Environment:getExternalStorageDirectory():getAbsolutePath()
	Note("alert: external storage is read only!")
end
end

\endluacode
*/
class GetExternalStorageDirectoryFunction extends JavaFunction {
	Plugin plugin;


	public GetExternalStorageDirectoryFunction(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
		// TODO Auto-generated constructor stub
	}

	@Override
	public int execute() throws LuaException {
		//Log.e("PLUGIN","Get External storage state:"+Environment)
		if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			L.pushString(Environment.getExternalStorageDirectory().getAbsolutePath());
		} else {
			L.pushString("/mnt/sdcard/");
		}
		return 1;
	}
	
}

/*! \page page1 Lua Functions
\subsection GetPluginID GetPluginID
Gets the plugin ID associated with this plugin.

\par Full Signature
\luacode
GetPluginID()
\endluacode
\param none
\returns \b string the id that has been assigned to this plugin.
\par Example 
\luacode
id = GetPluginID()
\endluacode
*/
class GetPluginIdFunction extends JavaFunction {
	Plugin plugin;

	public GetPluginIdFunction(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
	}
	
	@Override
	public int execute() throws LuaException {
		//if(this.getParam(2).isNil()) { return 0; }
		this.L.pushNumber(plugin.getSettings().getId());
		
		//Log.e("LUAWINDOW","script is sending:"+this.getParam(2).getString()+" to server.");
		//plugin.parent.handler.sendMessage(plugin.parent.handler.obtainMessage(Connection.MESSAGE_SENDDATA_STRING,this.getParam(2).getString()));
		return 1;
	}
	
}

 /*! \page page1 Lua Functions
\subsection GetPluginName GetPluginName
Gets the plugin name associated with this plugin.

\par Full Signature
\luacode
GetPluginName()
\endluacode
\param none
\returns \b string the name of this plugin.
\par Example 
\luacode
name = GetPluginName()
\endluacode
*/
class GetPluginNameFunction extends JavaFunction {
	Plugin plugin;

	public GetPluginNameFunction(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
	}
	
	@Override
	public int execute() throws LuaException {
		//if(this.getParam(2).isNil()) { return 0; }
		this.L.pushString(plugin.getSettings().getName());
		
		//Log.e("LUAWINDOW","script is sending:"+this.getParam(2).getString()+" to server.");
		//plugin.parent.handler.sendMessage(plugin.parent.handler.obtainMessage(Connection.MESSAGE_SENDDATA_STRING,this.getParam(2).getString()));
		return 1;
	}
	
}

/*! \page page1 Lua Functions
\subsection GetPluginInstallDirectory GetPluginInstallDirectory
Get the absolute path to the path that the plugin was loaded from.

\par Full Signature
\luacode
GetPluginInstallDirectory()
\endluacode
\param none
\returns \b string the absolute path to where the plugin was loaded
\par Example 
\luacode
path = GetPluginInstallDirectory()
\endluacode
\note This function should always return the path if the path no longer exists to the external volume being unmounted etc.
*/
class GetPluginInstallDirectoryFunction extends JavaFunction {
	Plugin plugin;


	public GetPluginInstallDirectoryFunction(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
		// TODO Auto-generated constructor stub
	}

	@Override
	public int execute() throws LuaException {
		//Log.e("PLUGIN","Get External storage state:"+Environment)
		/*if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			L.pushString(Environment.getExternalStorageDirectory().getAbsolutePath());
		} else {
			L.pushNil();
		}*/
		String path = plugin.getFullPath();
		File file = new File(path);
		String dir = file.getParent();
		//file.getPar
		L.pushString(dir);
		return 1;
	}
	
}

/*! \page page1 Lua Functions
\subsection GetStatusBarHeight GetStatusBarHeight
Gets the current height of the status bar.

\par Full Signature
\luacode
GetStatusBarHeight()
\endluacode
\param none
\returns \b number the size of the status bar, will always be constant regardless of the full screen state.
\par Example
\luacode
height = GetStatusBarHeight()
\endluacode
*/
class GetStatusBarHeight extends JavaFunction {
	Plugin plugin;


	public GetStatusBarHeight(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
		// TODO Auto-generated constructor stub
	}

	@Override
	public int execute() throws LuaException {
		// TODO Auto-generated method stub
		L.pushString(Integer.toString((int)plugin.parent.getStatusBarHeight()));
		return 1;
	}
	
}

/*! \page page1 Lua Functions
\subsection SendToServer SendToServer
Send the given string to the server.

\par Full Signature
\luacode
SendToServer(str)
\endluacode
\param str \b string the data to send to the server
\returns nothing
\par Example 
\luacode
SendToServer("run north;open door")
\endluacode
\note This is the same as sending data from the keyboard. The data is processed for special commands and aliases.
*/
class SendToServerFunction extends JavaFunction {
	Plugin plugin;


	public SendToServerFunction(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
		// TODO Auto-generated constructor stub
	}

	@Override
	public int execute() throws LuaException {
		if(this.getParam(2).isNil()) { return 0; }
		//Log.e("LUAWINDOW","script is sending:"+this.getParam(2).getString()+" to server.");
		plugin.mHandler.sendMessage(plugin.mHandler.obtainMessage(Connection.MESSAGE_SENDDATA_STRING,this.getParam(2).getString()));
		return 0;
	}
	
}

/*! \page page1
\subsection UserPresent UserPresent
Call to get a boolean indicating if the screen is on / user present.

\par Full Signature
\luacode
UserPresent()
\endluacode
\param none
\returns boolean true if the user is present, false if not
\par Example 
\luacode
present = UserPresent()
\endluacode
*/
class UserPresentFunction extends JavaFunction {
	Plugin plugin;


	public UserPresentFunction(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
		// TODO Auto-generated constructor stub
	}

	@Override
	public int execute() throws LuaException {
		L.pushBoolean(plugin.parent.isWindowShowing());
		return 1;
	}
	
}

