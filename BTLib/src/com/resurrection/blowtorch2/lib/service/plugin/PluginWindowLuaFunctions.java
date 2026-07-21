package com.resurrection.blowtorch2.lib.service.plugin;

import org.keplerproject.luajava.JavaFunction;
import org.keplerproject.luajava.LuaException;
import org.keplerproject.luajava.LuaObject;
import org.keplerproject.luajava.LuaState;

import android.os.Message;
import android.util.Log;

import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.WindowToken;
import com.resurrection.blowtorch2.lib.window.TextTree;

/*! \page page1
\section service Service Functions
\subsection AppendLineToWindow AppendLineToWindow
Sends a packet of GMCP data to the server
 
\par Full Signature
\luacode
AppendLineToWindow(line,windowName)
\endluacode
\param line \b com.resurrection.blowtorch2.lib.window.TextTree$Line the line to append, this usually comes from a trigger callback
\returns none
\par Example 
\luacode
function calledFromTrigger(line,number,map)
AppendLineToWindow(line,GetPluginID().."_chat_window")
end
\endluacode
*/
class AppendLineToWindowFunction extends JavaFunction {
	Plugin plugin;


	public AppendLineToWindowFunction(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
	}

	@Override
	public int execute() throws LuaException {
		//the only arguments should be a TextTree.Line copied from the one passed to it from a trigger script action
		//and the window id to send it to.
		LuaObject o = this.getParam(3);
		
		TextTree.Line line = null;
		String windowId = (this.getParam(2).getString());
		
		if(o.isJavaObject()) {
			//test if it is a real line.
			Object tmp = o.getObject();
			if(tmp instanceof TextTree.Line) {
				line = (TextTree.Line)tmp;
				
				//now abuse the lineToWindow handler from the replace action to ferry this bad boy across
				Message m = plugin.mHandler.obtainMessage(Connection.MESSAGE_LINETOWINDOW,line);
				m.getData().putString("TARGET", windowId);
				plugin.mHandler.sendMessage(m);
			} else {
				//error is java object but not a TextTree.Line
			}
		} else if(o.isString()) {
			//construct a new line and append it.
			Message m = plugin.mHandler.obtainMessage(Connection.MESSAGE_LINETOWINDOW,o.getString());
			m.getData().putString("TARGET", windowId);
			plugin.mHandler.sendMessage(m);
		} else {
			//error bad argument
		}
		
		//TextTree.Line line = (TextTree.Line)(this.getParam(3)).getObject();
		
		
		
		return 0;
	}
	
}

/*! \page page1
\subsection AppendWindowSettings AppendWindowSettings
Attaches a settings group from a window to the plugin settings block. This is to allow a plugin writer to include window settings in the main options dialog block at their discretion.

\par Full Signature
\luacode
AppendWindowSettings(name)
\endluacode
\param name \b string the line to append, this usually comes from a trigger callback
\returns none
\par Example 
\luacode
function OnBackgroundStartup()
AppendWindowSettings(GetPluginID().."_chat_window")
end
\endluacode
*/
	
class AppendWindowSettingsFunction extends JavaFunction {
	Plugin plugin;


	public AppendWindowSettingsFunction(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
		
	}

	@Override
	public int execute() throws LuaException {
		//float density = plugin.parent.getContext().getResources().getDisplayMetrics().density;
		//if((Window.this.getContext().getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_XLARGE) {
		//	density = density * 1.5f;
		//}
		//Log.e("WINODW","PUSHING DENSITY:"+Float.toString(density));
		//L.pushNumber(density);
		String name = this.getParam(2).getString();
		WindowToken w = plugin.getSettings().getWindows().get(name);
		if(w != null) {
			//mWindows.get(0).getSettings().setListener(new WindowSettingsChangedListener(mWindows.get(0).getName()));
			plugin.parent.attatchWindowSettingsChangedListener(w);
			w.getSettings().setSkipForPluginSave(true);
			plugin.getSettings().getOptions().addOption(w.getSettings());
			//optionSkipSaveList.add(plugin.getSettings().getOptions().getOptions().size()-1);
		}
		return 0;
	}
	
}

/*! \page page1
\subsection GetWindowTokenByName GetWindowTokenByName
Gets the raw /c com.resurrection.blowtorch2.lib.service.WindowToken object that is being held by the background service. This is to allow direct manipulation of the buffer.

\par Full Signature
\luacode
GetWindowTokenByName(name)
\endluacode
\param name \b string the id of the window to get.
\returns none
\par Example 
\luacode
window = GetWindowTokenByName(GetPluginID().."_chat_window")
\endluacode
*/	
class GetWindowFunction extends JavaFunction {
	Plugin plugin;


	public GetWindowFunction(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
		// TODO Auto-generated constructor stub
	}

	@Override
	public int execute() throws LuaException {
		/*int x = (int) this.getParam(2).getNumber();
		int y = (int) this.getParam(3).getNumber();
		int width = (int) this.getParam(4).getNumber();
		int height = (int) this.getParam(5).getNumber();
		
		Message msg = plugin.mHandler.obtainMessage(Connection.MESSAGE_MODMAINWINDOW);
		Bundle b = msg.getData();
		b.putInt("X", x);
		b.putInt("Y", y);
		b.putInt("WIDTH", width);
		b.putInt("HEIGHT", height);
		msg.setData(b);
		plugin.mHandler.sendMessage(msg);*/
		String desired = this.getParam(2).getString();
		WindowToken t = plugin.parent.getWindowByName(desired);
		if(t == null) {
			//check our local window that haven't been loaded into the main window group.
			for(WindowToken tmp : plugin.getSettings().getWindows().values()) {
				if(tmp.getName().equals(desired)) {
					t = tmp;
				}					
			}
		}
		L.pushJavaObject(t);
		
		return 1;
	}
	
}

/*! \page page1

\subsection InvalidateWindowText InvalidateWindowText
Invalidates a foreground windows text and forces it to redraw.

\par Full Signature
\luacode
InvalidateWindowText(name)
\endluacode
\param name \b string the name of the window to redraw
\returns none
\par Example 
\luacode
InvalidateWindowText(GetPluginID().."_chat_window")
\endluacode
*/
class InvalidateWindowTextFunction extends JavaFunction {
	Plugin plugin;


	public InvalidateWindowTextFunction(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
		// TODO Auto-generated constructor stub
	}

	@Override
	public int execute() throws LuaException {
		String name = this.getParam(2).getString();
		plugin.mHandler.sendMessage(plugin.mHandler.obtainMessage(Connection.MESSAGE_INVALIDATEWINDOWTEXT,name));
		return 0;
	}
	
}

/*! \page page1
\subsection NewWindow NewWindow
Makes a new window with the given paramters.

\par Full Signature
\luacode
NewWindow(name,x,y,width,height,script)
\endluacode
\param name \b string name or id of the window
\param x \b number the x coordinate of the window
\param y \b number the y coordinate of the window
\param width \b number the width of the window
\param height \b number the height of the window
\param script \b string the named script to load into the window's Lua state.
\returns none
\par Example 
\luacode
NewWindow("chat_window",0,0,400,400,"chat_script")
\endluacode
\note Windows don't work like this anymore. I'm pretty sure this won't work as intended or it will work but the size arguments will all be ignored.
*/
class WindowFunction extends JavaFunction {
	Plugin plugin;


	public WindowFunction(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
		// TODO Auto-generated constructor stub
	}

	@Override
	public int execute() throws LuaException {
		String name = null;
		int x = 0;
		int y = 0;
		int width = 0;
		int height = 0;
		String scriptName = null;
		
		LuaObject pName = this.getParam(2);
		LuaObject pX = this.getParam(3);
		LuaObject pY = this.getParam(4);
		LuaObject pWidth = this.getParam(5);
		LuaObject pHeight = this.getParam(6);
		LuaObject pScriptName = this.getParam(7);
		
		if(pName.isString()) {
			name = pName.getString();
		} else {
			//error
		}
		
		if(pX.isNil() || pY.isNil() || pWidth.isNil() || pHeight.isNil()) {
			//errror
		}
		
		if(!pX.isNumber() || !pY.isNumber() || !pWidth.isNumber() || !pHeight.isNumber()) {
			//error
		}
		
		x = (int) pX.getNumber();
		y = (int) pY.getNumber();
		width = (int) pWidth.getNumber();
		height = (int) pHeight.getNumber();
		
		if(!pScriptName.isNil() && pScriptName.isString()) {
			scriptName = pScriptName.getString();
		}
		
		WindowToken tok = null;
		if(pScriptName.isNil()) {
			tok = new WindowToken(name,null,null,plugin.parent.getDisplayName());
			plugin.mHandler.sendMessage(plugin.mHandler.obtainMessage(Connection.MESSAGE_NEWWINDOW, tok));
		} else {
			tok = new WindowToken(name,scriptName,plugin.getSettings().getName(),plugin.parent.getDisplayName());
			plugin.mHandler.sendMessage(plugin.mHandler.obtainMessage(Connection.MESSAGE_NEWWINDOW, tok));
		}
		
		L.pushJavaObject(tok);
		
		return 1;
	}
	
}

/*! \page page1
\subsection WindowBuffer WindowBuffer
Instructs a named window to either start or stop buffering incoming text

\par Full Signature
\luacode
WindowBuffer(name,state)
\endluacode
\param name \b string the name of the window to affect
\param state \b boolean the state of the buffering desired
\returns nothing
\par Example 
\luacode
WindowBuffer(GetPluginID().."_chat_window",false)
\endluacode
*/
class WindowBufferFunction extends JavaFunction {
	Plugin plugin;


	public WindowBufferFunction(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
		// TODO Auto-generated constructor stub
	}

	@Override
	public int execute() throws LuaException {
		String win = this.getParam(2).getString();
		boolean set = this.getParam(3).getBoolean();
		Log.e("PLUGIN","MODDING WINDOW("+win+") Buffer:"+set);
		plugin.mHandler.sendMessage(plugin.mHandler.obtainMessage(Connection.MESSAGE_WINDOWBUFFER, set ? 1 : 0, 0, win));
		
		return 0;
	}
	
}

/*! \page page1
\subsection WindowXCallB WindowXCallB
Sends a message to a foreground window that it should run a specified callback with the desired argument data.

\par Full Signature
\luacode
WindowXCallB(name,data)
\endluacode
\param name \b string the global callback in the window to call
\param data \b string the data to send
\returns nothing
\par Example 
\luacode
WindowXCallB(GetPluginID().."_chat_window",42)
\endluacode
\note Semantically this is the same as WindowXCallS, only great care has been taken to ensure that the data that is ferried across the AIDL bridge and delivered to the foreground window's Lua state as an array of \b bytes without having any intervention by the DalvikVM host converting it through a Java string to avoid corruption. This is largely to support large data tables being serialized with libmarshal or any other binary serialization format.
*/
class WindowXCallBFunction extends JavaFunction {
	Plugin plugin;


	public WindowXCallBFunction(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
		// TODO Auto-generated constructor stub
	}

	@Override
	public int execute() throws LuaException {
		String token = this.getParam(2).getString();
		String function = this.getParam(3).getString();
		byte[] foo = null;
		//try {
			foo = this.getParam(4).getBytes();
		//} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		//}
		//L.L
		Message msg = plugin.mHandler.obtainMessage(Connection.MESSAGE_WINDOWXCALLB,foo);
		//String str = "";
		//try {
		//	str = plugin.parent.windowXCallS(token, function, foo.getString());
		//} catch (RemoteException e) {
			// TODO Auto-generated catch block
		//	e.printStackTrace();
		//}
		msg.getData().putString("TOKEN",token);
		msg.getData().putString("FUNCTION", function);
		//L.pushString(str);
		plugin.mHandler.sendMessage(msg);
		//android.R.style.TextAppearanceM
		return 0;
	}
	
}

/*! \page page1
\subsection WindowXCallS WindowXCallS
Sends a message to a foreground window that it should run a specified callback with the desired argument data.

\par Full Signature
\luacode
WindowXCallS(name,data)
\endluacode
\param name \b string the global callback in the window to call
\param data \b string the data to send
\returns nothing
\par Example 
\luacode
WindowXCallS(GetPluginID().."_chat_window",42)
\endluacode
\note Semantically this is the same as WindowXCallB, only the data is cross converted to a DalvikVM string through the AIDL bridge. This can cause some problems with binary data. For very large serialized tables, or any kind of binary data, see WindowXCallB.
*/
class WindowXCallSFunction extends JavaFunction {
	Plugin plugin;

	//HashMap<String,String> 
	public WindowXCallSFunction(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
		// TODO Auto-generated constructor stub
	}

	@Override
	public int execute() throws LuaException {
		String token = this.getParam(2).getString();
		String function = this.getParam(3).getString();
		LuaObject foo = this.getParam(4);
		
		
		//--if(foo.isTable()) {
		//-	Log.e("DEBUG","ARGUMENT IS TABLE");
		//}
		//HashMap<String,Object> dump = dumpTable("t",4);
		//
		/*L.pushNil();
		while(L.next(2) != 0) {
			
			String id = L.toString(-2);
			LuaObject l = L.getLuaObject(-1);
			if(l.isTable()) {
				//need to dump more tables
			} else {
				
			}
		}*/
		//plugin.mHandler.sendMessage(plugin.mHandler.obtainMessage(MESSAGE_X, obj))
		Message msg = null;
		
		if(foo.isNil()) {
			msg = plugin.mHandler.obtainMessage(Connection.MESSAGE_WINDOWXCALLS);
		} else {
			msg = plugin.mHandler.obtainMessage(Connection.MESSAGE_WINDOWXCALLS,foo.getString());
		}
		
		
		//String str = "";
		//try {
		//	str = plugin.parent.windowXCallS(token, function, foo.getString());
		//} catch (RemoteException e) {
			// TODO Auto-generated catch block
		//	e.printStackTrace();
		//}
		msg.getData().putString("TOKEN",token);
		msg.getData().putString("FUNCTION", function);
		//L.pushString(str);
		plugin.mHandler.sendMessage(msg);
		// TODO Auto-generated method stub
		return 1;
	}
}

