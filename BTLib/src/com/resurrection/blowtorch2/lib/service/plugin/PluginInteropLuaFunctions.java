package com.resurrection.blowtorch2.lib.service.plugin;

import java.io.UnsupportedEncodingException;
import java.util.Map;

import org.keplerproject.luajava.JavaFunction;
import org.keplerproject.luajava.LuaException;
import org.keplerproject.luajava.LuaState;

import android.os.RemoteException;
import android.util.Log;

import com.resurrection.blowtorch2.lib.service.Connection;

/*! \page page1
\subsection CallPlugin CallPlugin
Calls a named global function in another plugin.

\par Full Signature
\luacode
CallPlugin(name,function,arguments[,...])
\endluacode
\param name \b string the line to append, this usually comes from a trigger callback
\param function \b function the global function to call.
\param arguments a list of arguments to provide, can be more than one separated by commas. Numbers or strings.
\returns none
\par Example 
\luacode
CallPlugin("button_window","newbutton",400,800,299,894)
\endluacode
\note use PluginSupports to test to see if a plugin has a global function with the desired name. 
*/
class CallPluginFunction extends JavaFunction {
	Plugin plugin;

	public CallPluginFunction(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
	}

	@Override
	public int execute() throws LuaException {
		
		String plugin = this.getParam(2).getString();
		String function = this.getParam(3).getString();
		String data = this.getParam(4).getString();
		
		this.plugin.parent.callPlugin(plugin,function,data);
		
		return 0;
	}
	
	
}

/*! \page page1
* \subsection PluginSupports PluginSupports
Checks whether a plugin has a global function of a desired name.

\par Full Signature
\luacode
PluginSupports(name,function)
\endluacode
\param name \b string the name of the plugin to interrogate.
\param function \b string the name of the global function to be checked.
\returns none
\par Example 
\luacode
if(PluginSupports("button_window","exportButtons")) then
CallPlugin("button_window","exportButtons")
end
\endluacode
*/
class PluginSupportsFunction extends JavaFunction {
	Plugin plugin;

	public PluginSupportsFunction(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
	}

	@Override
	public int execute() throws LuaException {
		String plugin = this.getParam(2).getString();
		String function = this.getParam(3).getString();
		
		boolean ret = this.plugin.parent.pluginSupports(plugin,function);
		L.pushBoolean(ret);
		return 1;
	}
}

/*! \page page1
\subsection SimulateInput SimulateInput
Simulates incoming data from the server. Will be processed for triggers but not for telnet data (although it probably should).

\par Full Signature
\luacode
SimulateInput(data)
\endluacode
\param data \b string the data to simulate
\returns note
\par Example 
\luacode
SimulateInput("\n[this looks like a prompt]->\n")
\endluacode
*/
class SimulateInputFunction extends JavaFunction {
	Plugin plugin;


	public SimulateInputFunction(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
		// TODO Auto-generated constructor stub
	}
	//public Si

	@Override
	public int execute() throws LuaException, RemoteException, UnsupportedEncodingException {
		// TODO Auto-generated method stub
		String str = this.L.LcheckString(2);
		
		plugin.mHandler.sendMessage(plugin.mHandler.obtainMessage(Connection.MESSAGE_PROCESS,str.getBytes(plugin.mEncoding)));
		
		return 0;
	}
}

/*! \page page1
\subsection GMCPSend Send_GMCP_Packet
Sends a packet of GMCP data to the server

\par Full Signature
\luacode
Send_GMCP_Packet(str)
\endluacode
\param str \b string the data to send, more in the GMCP documentation.
\returns note
\par Example 
\luacode
Send_GMCP_Packet("core.hello foo")
\endluacode
*/
class GMCPSendFunction extends JavaFunction {
	Plugin plugin;

	public GMCPSendFunction(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
		// TODO Auto-generated constructor stub
	}

	@Override
	public int execute() throws LuaException {
		String str = this.getParam(2).getString();
		Log.e("LUA","GMCP SEND:" + str);
		plugin.mHandler.sendMessage(plugin.mHandler.obtainMessage(Connection.MESSAGE_SENDGMCPDATA,str));
		return 0;
	}
	
	
}

/*! \page page1
\subsection MCPSend Send_MCP_Packet
Sends an MCP (Mud Client Protocol) out-of-band line or named message.

\par Full Signature
\luacode
Send_MCP_Packet(str)
\endluacode
\param str \b string either a full {@code #$#…} line, or {@code message-name key: val …}
\par Example
\luacode
Send_MCP_Packet("#$#dns-com-awns-ping 12345 id: 1")
Send_MCP_Packet("dns-com-awns-ping id: 1")
\endluacode
*/
class MCPSendFunction extends JavaFunction {
	Plugin plugin;

	public MCPSendFunction(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
	}

	@Override
	public int execute() throws LuaException {
		String str = this.getParam(2).getString();
		if (str != null && str.length() > 0) {
			plugin.parent.sendMcpPacket(str);
		}
		return 0;
	}
}

class GetMcpStatusFunction extends JavaFunction {
	Plugin plugin;

	public GetMcpStatusFunction(LuaState L, Plugin plugin) {
		super(L);
		this.plugin = plugin;
	}

	@Override
	public int execute() throws LuaException {
		@SuppressWarnings("unchecked")
		java.util.Map<String, String> cache =
				(java.util.Map<String, String>) plugin.parent.getMcpStatusCache();
		L.newTable();
		if (cache != null) {
			for (java.util.Map.Entry<String, String> e : cache.entrySet()) {
				L.pushString(e.getKey());
				L.pushString(e.getValue() != null ? e.getValue() : "");
				L.setTable(-3);
			}
		}
		return 1;
	}
}

