package com.resurrection.blowtorch2.lib.service.plugin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.keplerproject.luajava.JavaFunction;
import org.keplerproject.luajava.LuaException;
import org.keplerproject.luajava.LuaObject;
import org.keplerproject.luajava.LuaState;
import org.keplerproject.luajava.LuaStateFactory;
import org.xmlpull.v1.XmlSerializer;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.util.Xml;

import com.resurrection.blowtorch2.lib.alias.AliasData;
import com.resurrection.blowtorch2.lib.alias.AliasParser;
import com.resurrection.blowtorch2.lib.alias.AnchoredAliasCaptures;
import com.resurrection.blowtorch2.lib.responder.IteratorModifiedException;
import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.responder.toast.ToastResponder;
import com.resurrection.blowtorch2.lib.script.ScriptData;
import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.ConnectionPluginCallback;
import com.resurrection.blowtorch2.lib.service.SettingsChangedListener;
import com.resurrection.blowtorch2.lib.service.StellarService;
import com.resurrection.blowtorch2.lib.service.LuaLibraryHelper;
import com.resurrection.blowtorch2.lib.service.WindowToken;
import com.resurrection.blowtorch2.lib.service.plugin.function.NoteFunction;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.Option;
import com.resurrection.blowtorch2.lib.service.plugin.settings.PluginSettings;
import com.resurrection.blowtorch2.lib.service.plugin.settings.SettingsGroup;
import com.resurrection.blowtorch2.lib.timer.TimerData;
import com.resurrection.blowtorch2.lib.timer.TimerParser;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;
import com.resurrection.blowtorch2.lib.trigger.TriggerParser;
import com.resurrection.blowtorch2.lib.window.TextTree;
import com.resurrection.blowtorch2.lib.window.TextTree.Line;

public class Plugin implements SettingsChangedListener {
	//we are a lua plugin.
	//we can give users 
	//Matcher colorStripper = StellarService.colordata.matcher("");
	LuaState L = null;
	private PluginSettings settings = null;
	Handler mHandler = null;
	Handler innerHandler = null;
	//private String mName = null;
	private String fullPath;
	private String shortName;
	ConnectionPluginCallback parent;
	Context mContext;
	StringBuffer joined_alias = new StringBuffer();
	Pattern alias_replace = Pattern.compile(joined_alias.toString());
	Matcher alias_replacer = alias_replace.matcher("");
	Matcher alias_recursive = alias_replace.matcher("");
	String mEncoding = "UTF-8";
	Pattern whiteSpace = Pattern.compile("\\s");
	HashMap<String,CustomTimerTask> timerTasks = new HashMap<String,CustomTimerTask>();
	private boolean enabled = true;
	private String scriptBlock = "/";
	//private ArrayList<Integer> optionSkipSaveList = new ArrayList<Integer>();
	
	public static final int LUA_TNIL = 0;
	public static final int LUA_TBOOLEAN = 1;
	public static final int LUA_TTABLE = 5;
	public static final int LUA_TNUMBER = 3;
	public static final int LUA_TSTRING = 4;
	
	
	public Plugin(Handler h,ConnectionPluginCallback parent,String path,String dataDir) throws LuaException {
		setSettings(new PluginSettings());
		mHandler = h;
		L = LuaStateFactory.newLuaState();
		L.openLibs();
		//set up the path and cpath.
		
		
		
		//this is going to get ugly.
		//L.newTable();
		//L.pushString("package");
		//L.pushValue(-2);
		//L.setTable(LuaState.LUA_GLOBALSINDEX);
		
		//L.newTable();
		//L.pushString("preload");
		//L.pushValue(-2);
		//L.setTable(-4);
		//L.remove(-2);
		
		//L.pushString("lsqlite3");
		//--L.
		
		
		this.parent = parent;
		mContext = parent.getContext();
		//initTimers();
		initLua(path,dataDir);
		
	}
	
	public Plugin(PluginSettings settings,Handler h,ConnectionPluginCallback parent,String path,String dataDir) throws LuaException {
		this.settings = settings;
		mHandler = h;
	
		L = LuaStateFactory.newLuaState();
		mContext = parent.getContext();
		this.parent = parent;
		
		//initTimers();
		
		initLua(path,dataDir);
	}
	
	/*public Plugin(Handler serviceHandler, ConnectionPluginCallback parent2,
			String path) {
		this.mPath = path;
		
	}*/

	HashMap<String,Long> timerStartTimes;
	
	public void initTimers() {
		innerHandler = new Handler() { 
			public void handleMessage(Message msg) {
				switch(msg.what) {
				case 100:
					DoTimerResponders((String)msg.obj);
					break;
				}
			}
		};
		timerStartTimes = new HashMap<String,Long>();
		CONNECTION_TIMER = new Timer("blowtorch_"+this.getName()+"_timer",true);
		
		for(TimerData timer : settings.getTimers().values()) {
			timer.setRemainingTime(timer.getSeconds());
			if(timer.isPlaying()) {
				
				//l//ong startTime = SystemClock.elapsedRealtime();
				//CustomTimerTask task = new CustomTimerTask(timer.getName());
				//if(timer.isRepeat()) {
				//	timer.setStartTime(startTime);
				//	CONNECTION_TIMER.schedule(task, timer.getSeconds()*1000, timer.getSeconds()*1000);
				//} else {
				//	timer.setStartTime(startTime);
				//	CONNECTION_TIMER.schedule(task, timer.getSeconds()*1000);
				//}
				timer.setPlaying(false);
				startTimer(timer.getName());
			}
		}
	}

	private void initLua(String launchPath,String mDataDir) throws LuaException {
		//need to set up global functions, it all goes here.
		
		if(mDataDir == null) {
			//this is bad.
		} else {
			
			//set up the path/cpath.
			//TODO: add the plugin load path.
			String packagePath = mDataDir + "/lua/share/5.1/?.lua";
			if(launchPath != null && !launchPath.equals("")) {
				if(launchPath.contains("autohunt")) {
					int ten = launchPath.length();
					
				}
				File file = new File(launchPath);
				String dir = file.getParent();
				//file.getPar
				//L.pushString(dir);
				packagePath += ";" + dir + "/?.lua";
			}
			L.getGlobal("package");
			L.pushString(packagePath);
			L.setField(-2, "path");
			
			L.pushString(LuaLibraryHelper.buildCPath(mContext, mDataDir));
			L.setField(-2, "cpath");
			L.pop(1);
			
		}
		TriggerEnabledFunction tef = new TriggerEnabledFunction(L, this);
		tef.register("EnableTrigger");
		L.pushJavaObject(settings.getTriggers());
		L.setGlobal("triggers");
		
		L.pushJavaObject(mContext);
		L.setGlobal("context");

		try {
			String dn = parent.getDisplayName();
			String hn = parent.getHostName();
			L.pushString(dn != null ? dn : "");
			L.setGlobal("connection_display");
			L.pushString(hn != null ? hn : "");
			L.setGlobal("connection_host");
		} catch (Throwable t) {
			L.pushString("");
			L.setGlobal("connection_display");
			L.pushString("");
			L.setGlobal("connection_host");
		}
		
		NoteFunction nf = new NoteFunction(L,this,mHandler);
		nf.register("Note");
		
		//DrawWindowFunction dwf = new DrawWindowFunction(L,this,mHandler);
		//dwf.register("DrawWindow");
		
		WindowFunction wf = new WindowFunction(L, this);
		ExecuteScriptFunction esf = new ExecuteScriptFunction(L, this);
		GetWindowFunction mwf = new GetWindowFunction(L, this);
		WindowBufferFunction wbf = new WindowBufferFunction(L, this);
		RegisterFunctionCallback rfc = new RegisterFunctionCallback(L, this);
		WindowXCallSFunction wxctf = new WindowXCallSFunction(L, this);
		AppendLineToWindowFunction altwf = new AppendLineToWindowFunction(L, this);
		InvalidateWindowTextFunction iwtf = new InvalidateWindowTextFunction(L, this);
		GMCPSendFunction gsf = new GMCPSendFunction(L, this);
		MCPSendFunction msf = new MCPSendFunction(L, this);
		GetMcpStatusFunction gmsf = new GetMcpStatusFunction(L, this);
		UserPresentFunction upf = new UserPresentFunction(L, this);
		WindowXCallBFunction wxcbf = new WindowXCallBFunction(L, this);
		GetExternalStorageDirectoryFunction gesdf = new GetExternalStorageDirectoryFunction(L, this);
		GetDisplayDensityFunction gdsdf = new GetDisplayDensityFunction(L, this);
		AppendWindowSettingsFunction awsf = new AppendWindowSettingsFunction(L, this);
		GetStatusBarHeight gsbshf = new GetStatusBarHeight(L, this);
		//StatusBarHiddenMethod sghm = new StatusBarHiddenMethod(L);
		GetActionBarHeightFunction gabhf = new GetActionBarHeightFunction(L, this);
		GetPluginInstallDirectoryFunction gpidf = new GetPluginInstallDirectoryFunction(L, this);
		SendToServerFunction stsf = new SendToServerFunction(L, this);
		GetPluginIdFunction gpuidf = new GetPluginIdFunction(L, this);
		GetPluginSettingsFunction gpsf = new GetPluginSettingsFunction(L, this);
		ReloadSettingsFunction rlsf = new ReloadSettingsFunction(L, this);
		SaveSettingsFunction ssfun = new SaveSettingsFunction(L, this);
		NewTriggerFunction ntf = new NewTriggerFunction(L, this);
		DeleteTriggerFunction dtf = new DeleteTriggerFunction(L, this);
		CallPluginFunction cpf = new CallPluginFunction(L, this);
		PluginSupportsFunction psf = new PluginSupportsFunction(L, this);
		EnableTriggerGroupFunction etgf = new EnableTriggerGroupFunction(L, this);
		SimulateInputFunction sif = new SimulateInputFunction(L, this);
		DeleteTriggerGroupFunction dtgf = new DeleteTriggerGroupFunction(L, this);
		GetPluginNameFunction gpnf = new GetPluginNameFunction(L, this);
		//common functions
		
		gabhf.register("GetActionBarHeight");
		gdsdf.register("GetDisplayDensity");
		gesdf.register("GetExternalStorageDirectory");
		gpuidf.register("GetPluginID");
		gpidf.register("GetPluginInstallDirectory");
		gsbshf.register("GetStatusBarHeight");
		stsf.register("SendToServer");
		//server functions
		altwf.register("AppendLineToWindow");
		awsf.register("AppendWindowSettings");
		esf.register("ExecuteScript");
		gpsf.register("GetPluginSettings");
		mwf.register("GetWindowTokenByName");
		iwtf.register("InvalidateWindowText");
		wf.register("NewWindow");
		rlsf.register("ReloadSettings");
		rfc.register("RegisterSpecialCommand");
		ssfun.register("SaveSettings");
		gsf.register("Send_GMCP_Packet");
		msf.register("Send_MCP_Packet");
		gmsf.register("Get_MCP_Status");
		upf.register("UserPresent");
		wbf.register("WindowBuffer");
		wxctf.register("WindowXCallS");
		wxcbf.register("WindowXCallB");
		ntf.register("NewTrigger");
		dtf.register("DeleteTrigger");
		cpf.register("CallPlugin");
		psf.register("PluginSupports");
		etgf.register("EnableTriggerGroup");
		sif.register("Simulate");
		dtgf.register("DeleteTriggerGroup");
		gpnf.register("GetPluginName");
		/*L.getGlobal("Note");
		L.pushString("this is a test");
		int ret = L.pcall(1, 0, 0);
		if(ret != 0) {
			Log.e("LUA","TRIED TO CALL NOTE BUT FAILED: "+L.getLuaObject(L.getTop()).getString());
		}*/
		
		
		
		
		/*L.pushNil();
		while(L.next(LuaState.LUA_GLOBALSINDEX) != 0) {
			String two = L.typeName(L.type(-2));
			String one = L.typeName(L.type(-1));
			Log.e("LUA","value: " + two + " data: " + one);
		}*/
	}
	
	//public void 

	public void setSettings(PluginSettings settings) {
		this.settings = settings;
		
	}

	public PluginSettings getSettings() {
		return settings;
	}
	
	private final HashMap<String,String> captureMap = new HashMap<String,String>();
	/*public void process(TextTree input,StellarService service,boolean windowOpen,Handler pump,String display) {
		//if(this.settings.getName().equals("map_miniwindow")) {
			//inspection
		//<String,TriggerData> triggers = this.settings.getTriggers();
		//	Collection<TriggerData> c = triggers.values();
		//	c.contains("foo");
		//}
		String host = "";
		int port = 0;
		if(this.settings.getTriggers().size() == 0) return;
		if(sortedTriggers == null) {
			sortTriggers();
		}
		
		List<TriggerData> triggers = sortedTriggers;//new ArrayList<TriggerData>(this.settings.getTriggers().values());
//		Collections.sort(triggers,new Comparator() {
//		
//			
//			
//
//			public int compare(Object arg0, Object arg1) {
//				// TODO Auto-generated method stub
//				TriggerData a = (TriggerData)arg0;
//				TriggerData b = (TriggerData)arg1;
//				//if(a.getSequence() == 5 || b.getSequence() == 5) {
//					//Log.e("COMP","STOP HERE");
//				//}
//				if(a.getSequence() > b.getSequence()) return 1;
//				if(a.getSequence() < b.getSequence()) return -1;
//				
//				return 0;
//			}
//			
//		});
//		//sick ass shit in the hiznizouous
		ListIterator<TextTree.Line> it = input.getLines().listIterator(input.getLines().size());
		boolean keepEvaluating = true;
		int lineNum = input.getLines().size();
		while(it.hasPrevious() && keepEvaluating) {
			boolean done = false;
			boolean modified = false;
			//while(!done) {
				
				//try {
					TextTree.Line l = it.previous();
				//} catch(ConcurrentModificationException e) {
				//	modified = true;
				//	it = input.getLines().listIterator(input.getLines().size() - 1)
				//}
			//}
			//if(!modified) {
				lineNum = lineNum - 1;
			//}
			//StringBuffer tmp = TextTree.deColorLine(l);
			//test this line against each trigger.
			String str = TextTree.deColorLine(l).toString();
			for(TriggerData t : triggers) {
				if(!t.isInterpretAsRegex() && (t.getPattern().startsWith("%")
						|| t.getPattern().startsWith("@"))) {
					
				} else {
					if(t.isEnabled()) {
						
						t.getMatcher().reset(str);
						while(t.getMatcher().find() && keepEvaluating) {
							if(t.isFireOnce() && t.isFired()) {
								//do nothiong
							} else {
								if(t.isFireOnce()) {
									t.setFired(true);
								}
								
								captureMap.clear();
								for(int i=0;i<=t.getMatcher().groupCount();i++) {
									captureMap.put(Integer.toString(i), t.getMatcher().group(i));
								}
								for(TriggerResponder responder : t.getResponders()) {
									try {
										responder.doResponse(service.getApplicationContext(),input,lineNum,it,l,0,0,"",t, display,host,port, StellarService.getNotificationId(), windowOpen, pump,captureMap,L,t.getName(),mEncoding);
									} catch(IteratorModifiedException e) {
										it = e.getIterator();
									}
									if(input.getLines().size() == 0) {
										return;
									}
								}
								if(!t.isKeepEvaluating()) {
									keepEvaluating = false;
									break;
								}
								
								
							}
						}
					}
				}
			}
		}
		//return null;
	}
	
	public boolean process2(TextTree.Line l,String stripped,int lineNum,TextTree input,StellarService service,boolean windowOpen,Handler pump,String display) throws IteratorModifiedException {
		boolean modified = false;
		if(getSettings().getTriggers().size() == 0) return false;
		if(sortedTriggers == null) {
			sortTriggers();
			buildTriggerSystem();
			if(sortedTriggers == null) {
				return false;
			}
		}
		String host = "";
		int port = 0;
		//for(TriggerData t: sortedTriggers) {
		//	if(!t.isInterpretAsRegex() && t.getPattern().startsWith("%")) {
				
		//	} else {
				//if(t.isEnabled()) {
					massiveMatcher.reset(stripped);
					while(massiveMatcher.find()) {
						int index = -1;
						for(int i=1;i<=massiveMatcher.groupCount();i++) {
							if(massiveMatcher.group(i) != null) {
								index = i;
								i = massiveMatcher.groupCount()+1;
							}
						}
						
						if(index > 0) {
							TriggerData t = sortedTriggerMap.get(index);
							if(t.isFireOnce() && t.isFired()) {
								
							} else {
								if(t.isFireOnce()) {
									t.setFired(true);
								}
							}
							
							int start = massiveMatcher.start();
							int end = massiveMatcher.end();
							String matched = massiveMatcher.group(0);
							
							captureMap.clear();
							for(int i=index;i<=(t.getMatcher().groupCount()+index);i++) {
								captureMap.put(Integer.toString(i), massiveMatcher.group(i));
							}
							
							for(TriggerResponder responder : t.getResponders()) {
	
									responder.doResponse(service.getApplicationContext(),input,lineNum,null,l,start,end,matched,t, display,host,port, StellarService.getNotificationId(), windowOpen, pump,captureMap,L,t.getName(),mEncoding);
								
								if(input.getLines().size() == 0) {
									return true;
								}
							}
							if(!t.isKeepEvaluating()) {
								//keepEvaluating = false;
								break;
							}
						}
					}
				//}
			//}
		//}
			
		return modified;
	}*/
	
	public void initScripts(ArrayList<WindowToken> windows) {
		//for(Script)
		
		
		/*for(String script : settings.getScripts().keySet()) {
			//Log.e("LUA","ATTEMPTING TO LOAD:" + script + "\n" + settings.getScripts().get(script));
			if(script.equals("global")) {
				int ret =L.LdoString(settings.getScripts().get(script));
				if(ret != 0) {
					Log.e("LUA","PROBLEM LOADING SCRIPT:" + L.getLuaObject(-1).getString());
				}
			}
		}*/
	}
	


	


	
	
	

/*! \page page1 Lua Functions
 
\subsection sec1 Note
This is the basic linkage between Lua and the Console. The function will echo the parameter string to the main window.
\par Full Signature
\luacode
Note(text)
\endluacode
\param text The text to echo back
\returns nothing
\par Example
\luacode
Note("Example text!")
\endluacode
*/ 


	
	

	


	


	


	

	public void shutdown() {
		// TODO Auto-generated method stub
		//L.close();
		L = null;
	}

	public String getName() {
		// TODO Auto-generated method stub
		return settings.getName();
	}
	
	public void displayLuaError(String message) {
		mHandler.sendMessage(mHandler.obtainMessage(Connection.MESSAGE_PLUGINLUAERROR,"\n" + Colorizer.getRedColor() + message + Colorizer.getWhiteColor() + "\n"));
	}

	public void execute(String callback,String args) {
		L.getGlobal("debug");
		L.getField(L.getTop(), "traceback");
		L.remove(-2);
		
		L.getGlobal(callback);
		if(L.getLuaObject(-1).isFunction()) {
			L.pushString(args);
			
			int ret = L.pcall(1, 1, -3);
			
			if(ret != 0) {
				displayLuaError("Error calling function callback:"+settings.getName()+"("+callback+"):"+L.getLuaObject(-1).getString());
			} else {
				//Log.e("PLUGIN","Successfuly called plugin function:"+settings.getName()+"("+callback+")");
				L.pop(2);
			}
		
		} else {
			L.pop(2);
		}
		
		checkStack("ExecuteCallback");
		
	}

	public void xcallS(String function, String str) {
		L.getGlobal("debug");
		L.getField(-1, "traceback");
		L.remove(-2);
		
		L.getGlobal(function);
		if(L.getLuaObject(-1).isFunction()) {
			//pushTable("",map);
			L.pushString(str);
			int ret = L.pcall(1, 1, -3);
			if(ret != 0) {
				displayLuaError("PluginXCallS Error:" + L.getLuaObject(-1).getString());
			} else {
				//success
				L.pop(2);
			}
		} else {
			L.pop(2);
		}
		
		checkStack("PluginXCallS");
	}
	
	private void pushTable(String key,Map<String,Object> map) {
		if(!key.equals("")) {
			L.pushString(key);
		}
		
		L.newTable();
		
		for(String tmp : map.keySet()) {
			Object o = map.get(tmp);
			if(o instanceof Map) {
				pushTable(tmp,(Map)o);
			} else {
				if(o instanceof String) {
					L.pushString(tmp);
					L.pushString((String)o);
					L.setTable(-3);
				} else if(o instanceof Integer) {
					L.pushString(tmp);
					L.pushString(Integer.toString((Integer)o));
					L.setTable(-3);
				}
			}
		}
		if(!key.equals("")) {
			L.setTable(-3);
		}
	}

	public LuaState getLuaState() {
		// TODO Auto-generated method stub
		return L;
	}
	
	/*public void outputXMLInternal(XmlSerializer out) throws IllegalArgumentException, IllegalStateException, IOException {
		//see if lua has a SaveXML method.
		
		L.getGlobal("saveXML");
		if(L.getLuaObject(-1).isFunction()) {
			//call it and allow plugin to dump settings to the main wad.
			//need to dump plugin constants.
			//then call lua.
			out.startTag("", "plugin");
			dumpPluginCommonData(out);
			dumpLuaData(out);
			out.endTag("", "plugin");
		}
	}
	
	public void outputXMLExternal(StellarService service,XmlSerializer out) throws IllegalArgumentException, IllegalStateException, IOException {
		out.startTag("", "plugin");
		dumpPluginCommonData(out);
		dumpLuaData(out);
		out.endTag("", "plugin");
	}*/
	
	/*private void dumpLuaData(XmlSerializer out) {
		//now call the saveXML function in lua.
		L.getGlobal("debug");
		L.getField(-1, "traceback");
		L.remove(-2);
		L.getGlobal("saveXML");
		if(L.getLuaObject(-1).isFunction()) {
			//out.startT
			
			L.pushJavaObject(out);
			
			int ret = L.pcall(1, 1, -3);
			if(ret != 0) {
				displayLuaError("Plugin SaveXML Error:" + L.getLuaObject(-1).getString());
			} else {
				//success
			}
			
		} else {
			L.pop(2);
		}
		
		checkStack("SaveXML");
	}*/
	
/*	public void outputXMLExternal(StellarService service) {
		L.getGlobal("saveXML");
		if(L.getLuaObject(-1).isFunction()) {
			//set up file for writing, calling saveXML.
			try {
				FileOutputStream fos = service.openFileOutput(settings.getPath(), Context.MODE_PRIVATE);
				
				XmlSerializer out = Xml.newSerializer();
				
				StringWriter writer = new StringWriter();
				
				out.setOutput(writer);
				
				out.startDocument("UTF-8", true);
				
				out.startTag("", "plugin");

				dumpPluginCommonData(out);
				
				out.endTag("", "plugin");
				out.endDocument();
				
				fos.write(writer.toString().getBytes());
				
				fos.close();
				
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalStateException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			
		} else {
			//no saveXML detected, no saving needed, as the next parse will just pick up the same info.
	
		}
	}*/
	
	private void dumpPluginCommonData(XmlSerializer out) {
		try{
			out.attribute("", "author", settings.getAuthor());
			out.attribute("", "name", settings.getName());
			out.attribute("", "id", Integer.toString(settings.getId()));
			
		
			//out.
			//dump common/normal plugin data.
			for(AliasData alias : settings.getAliases().values()) {
				AliasParser.saveAliasToXML(out, alias);
			}
			
			for(TriggerData trigger : settings.getTriggers().values()) {
				TriggerParser.saveTriggerToXML(out, trigger);
			}
			
			for(TimerData timer : settings.getTimers().values()) {
				TimerParser.saveTimerToXML(out,timer);
			}
			
			for(String script : settings.getScripts().keySet()) {
				
				ScriptData d = settings.getScripts().get(script);
				out.startTag("", "script");
				out.attribute("", "name", script);
				if(d.isExecute()) {
					out.attribute("", "execute", "true");
				}
				
				//out.text(settings.getScripts().get(script));
				out.cdsect(d.getData());
				
				//out.cdsect(text)
				out.endTag("", "script");
			}
			
			/*L.pop(1);
			L.getGlobal("debug");
			L.getField(-1,"traceback");
			L.remove(-2);
			//L.pushJavaObject(out);
			L.getGlobal("saveXML");
			L.pushJavaObject(out);
			int ret = L.pcall(1, 1, -3);
			if(ret != 0) {
				Log.e("PLUGIN","SaveXML Error:" + L.getLuaObject(-1).getString());
				return;
			}*/
		
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void setFullPath(String fullPath) {
		this.fullPath = fullPath;
	}

	public String getFullPath() {
		return fullPath;
	}

	public void setShortName(String shortName) {
		this.shortName = shortName;
	}

	public String getShortName() {
		return shortName;
	}
	
	public int getTriggerCount() {
		return settings.getTriggers().size();
	}
	
	public int getAliasCount() {
		return settings.getAliases().size();
	}
	
	public int getTimerCount() {
		return settings.getTimers().size();
	}
	
	public int getScriptCount() {
		return settings.getScripts().size();
	}
	
	public String getStorageType() {
		switch(settings.getLocationType()) {
		case INTERNAL:
			return "INTERNAL";
			//break;
		case EXTERNAL:
			return "EXTERNAL";
			//break;
		default:
			return "OOPS";
				//break;
		}
	}

	public void handleGMCPCallback(String callback, HashMap<String, Object> data) {
		L.getGlobal("debug");
		L.getField(L.getTop(), "traceback");
		L.remove(-2);
		
		L.getGlobal(callback);
		if(L.getLuaObject(L.getTop()).isFunction()) {
			pushTable("",data);
			int ret = L.pcall(1, 1, -3);
			if(ret != 0) {
				displayLuaError("Error calling gmcp callback:" + callback + " error:\n"+L.getLuaObject(-1).getString());
			} else {
				//success.
				L.pop(2);
			}
		} else {
			//callback not defined.
			L.pop(2);
		}
		
		//checkStack("GMCP Callback");
	}
	
	public void addTrigger(TriggerData data) {
		this.getSettings().getTriggers().put(data.getName(), data);
		this.sortTriggers();
		parent.buildTriggerSystem();
		settings.setDirty(true);
	}

	public void updateTrigger(TriggerData from, TriggerData to) {
		TriggerData tmp = this.getSettings().getTriggers().remove(from.getName());
		this.getSettings().getTriggers().put(to.getName(),to);
		tmp = null;
		this.sortTriggers();
		parent.buildTriggerSystem();
		settings.setDirty(true);
	}
	
	HashMap<Integer,AliasData> aliasMap = null;
	public void buildAliases() {
		joined_alias.setLength(0);
		
		aliasMap = new HashMap<Integer,AliasData>();
		//Object[] a = the_settings.getAliases().keySet().toArray();
		Object[] a = getSettings().getAliases().values().toArray();
		int currentGroup = 1;
		
		String prefix = "\\b";
		String suffix = "\\b";
		//StringBuffer joined_alias = new StringBuffer();
		
		if(a.length > 0) {
			int j=0;
			for(int i=0;i<a.length;i++) {
				if(((AliasData)a[i]).isEnabled()) {
					if(((AliasData)a[i]).getPre().startsWith("^")) { prefix = ""; } else { prefix = "\\b"; }
					if(((AliasData)a[i]).getPre().endsWith("$")) { suffix = ""; } else { suffix = "\\b"; }
					String tmp = "("+prefix+((AliasData)a[i]).getPre()+suffix+")";
					joined_alias.append(tmp);
					Matcher m = Pattern.compile(tmp).matcher("");
					aliasMap.put(currentGroup, (AliasData)a[i]);
					currentGroup += m.groupCount();
					j=i+1;
					i=a.length;
					
				}
			}
			for(int i=j;i<a.length;i++) {
				if(((AliasData)a[i]).isEnabled()) {
					if(((AliasData)a[i]).getPre().startsWith("^")) { prefix = ""; } else { prefix = "\\b"; }
					if(((AliasData)a[i]).getPre().endsWith("$")) { suffix = ""; } else { suffix = "\\b"; }
					String tmp = "("+prefix+((AliasData)a[i]).getPre()+suffix+")";
					//joined_alias.append(tmp);
					Matcher m = Pattern.compile(tmp).matcher("");
					aliasMap.put(currentGroup, (AliasData)a[i]);
					currentGroup += m.groupCount();
					
					joined_alias.append("|");
					joined_alias.append(tmp);
					//joined_alias.append("("+prefix+((AliasData)a[i]).getPre()+suffix+")");
				}
			}
			
		}
		
		alias_replace = Pattern.compile(joined_alias.toString());
		alias_replacer = alias_replace.matcher("");
		alias_recursive = alias_replace.matcher("");
		//Log.e("SERVICE","BUILDING ALIAS PATTERN: " + joined_alias.toString());
	}
	
	public byte[] doAliasReplacement(byte[] input,Boolean reprocess) {
		if(joined_alias.length() > 0) {

			//Pattern to_replace = Pattern.compile(joined_alias.toString());
			byte[] retval = null;
			//Matcher replacer = null;
			try {
				alias_replacer.reset(new String(input,mEncoding));//replacer = to_replace.matcher(new String(bytes,the_settings.getEncoding()));
			} catch (UnsupportedEncodingException e1) {
				throw new RuntimeException(e1);
			}
			
			StringBuffer replaced = new StringBuffer();
			
			boolean found = false;
			boolean doTail = true;
			while(alias_replacer.find()) {
				found = true;
				
				
				int index = -1;
				for(int i=1;i<=alias_replacer.groupCount();i++) {
					if(alias_replacer.group(i) != null) {
						index = i;
						i=alias_replacer.groupCount();
					}
				}
				//String str = alias_replacer.group(0);
				AliasData replace_with = aliasMap.get(index);
				//AliasData replace_with = getSettings().getAliases().get(alias_replacer.group(0));
				//do special replace if only ^ is matched.
				//do lua execute if ^ and $ is matched
				
				
				boolean startAnchor = replace_with.getPre().startsWith("^");
				boolean endAnchor = replace_with.getPre().endsWith("$");
				
				if(startAnchor && !endAnchor) {
					doTail = false;
					//do special replace.
					String[] tParts = null;
					try {
						tParts = whiteSpace.split(new String(input,mEncoding));
					} catch (UnsupportedEncodingException e) {
						e.printStackTrace();
					}
					HashMap<String,String> map = new HashMap<String,String>();
					for(int i=0;i<tParts.length;i++) {
						map.put(Integer.toString(i), tParts[i]);
					}
					ToastResponder r = new ToastResponder();
					String finalString = r.translate(replace_with.getPost(), map);
					
					replaced.append(finalString);
					
				} else if(startAnchor && endAnchor) {
					String matched = alias_replacer.group(index);
					HashMap<String,String> anchorCaptures = AnchoredAliasCaptures.fromMatch(replace_with.getPre(), matched);

					ToastResponder t = new ToastResponder();
					String finalString = t.translate(replace_with.getPost(), anchorCaptures);

					if(finalString.startsWith(scriptBlock)) {
						this.runLuaString(finalString.substring(scriptBlock.length(),finalString.length()));
					} else {
						alias_replacer.appendReplacement(replaced, Matcher.quoteReplacement(finalString));
					}
					doTail = false;
					
				} else {
					alias_replacer.appendReplacement(replaced, replace_with.getPost());
				}
			}
			if(doTail) {
				alias_replacer.appendTail(replaced);
			}
			StringBuffer buffertemp = new StringBuffer();
			if(found) { //if we replaced a match, we need to continue the find/match process until none are found.
				boolean recursivefound = false;
				boolean eatTail = false;
				do {
					recursivefound = false;
					
					//Matcher recursivematch = to_replace.matcher(replaced.toString());
					alias_recursive.reset(replaced.toString());
					buffertemp.setLength(0);
					while(alias_recursive.find()) {
						recursivefound = true;
						int idx = -1;
						for(int i=1;i<=alias_recursive.groupCount();i++) {
							if(alias_recursive.group(i) != null) {
								idx = i;
								i=alias_recursive.groupCount();
							}
						}
						//String str = alias_replacer.group(0);
						AliasData replace_with = aliasMap.get(idx);
						//AliasData replace_with = getSettings().getAliases().get(alias_recursive.group(0));
						if(replace_with.getPre().startsWith("^") && ! replace_with.getPre().endsWith("$")) {
							ToastResponder r = new ToastResponder();
							String[] tParts = null;
							
							String tmpInput = replaced.toString();
							int index = tmpInput.indexOf(";");
							String rest = "";
							if(index > -1) {
								rest = tmpInput.substring(index+1,tmpInput.length());
								tmpInput = tmpInput.substring(0,index);
							}
							String sepchar = "";
							if(rest.length()>0) {
								sepchar = ";";
							}
							tParts = whiteSpace.split(tmpInput);
							
							HashMap<String,String> map = new HashMap<String,String>();
							for(int i=0;i<tParts.length;i++) {
								map.put(Integer.toString(i), tParts[i]);
							} 
							eatTail = true;
							alias_recursive.appendReplacement(buffertemp, r.translate(replace_with.getPost(),map) + sepchar +rest);
							reprocess = false;
						} else if(replace_with.getPre().startsWith("^") && replace_with.getPre().endsWith("$")) {
							String matched = alias_recursive.group(idx);
							HashMap<String,String> anchorCaptures = AnchoredAliasCaptures.fromMatch(replace_with.getPre(), matched);
							ToastResponder r = new ToastResponder();
							String finalString = r.translate(replace_with.getPost(), anchorCaptures);
							eatTail = true;
							alias_recursive.appendReplacement(buffertemp, Matcher.quoteReplacement(finalString));
							reprocess = false;
						} else {
							alias_recursive.appendReplacement(buffertemp, replace_with.getPost());
						}
						
					}
					if(recursivefound) {
						if(!eatTail) {
							alias_recursive.appendTail(buffertemp);
						}
						replaced.setLength(0);
						replaced.append(buffertemp);
						
					}
				} while(recursivefound == true);
			}
			//so replacer should contain the transformed string now.
			//pull the bytes back out.
			try {
				retval = replaced.toString().getBytes(mEncoding);
			} catch (UnsupportedEncodingException e1) {
				throw new RuntimeException(e1);
			}
			
			replaced.setLength(0);
			
			return retval;
		} else {
			return input;
		}
	}

	private Timer CONNECTION_TIMER;
	
	private class CustomTimerTask extends java.util.TimerTask {

		private String name;
		private long startTime;
		public CustomTimerTask(String name) {
			this.name = name;
			startTime = SystemClock.elapsedRealtime();
		}
		
		@Override
		public void run() {
			innerHandler.sendMessage(innerHandler.obtainMessage(100,name));
			TimerData d = getSettings().getTimers().get(name);
			if(d != null) {
				if(!d.isRepeat()) {
					timerTasks.remove(d.getName());
				} else {
					CustomTimerTask t = timerTasks.get(name);
					t.setStartTime(SystemClock.elapsedRealtime());
				}
			}
			
		}

		public long getStartTime() {
			return startTime;
		}

		public void setStartTime(long startTime) {
			this.startTime = startTime;
		}
		
	}
	
	private void DoTimerResponders(String ordinal) {
		//synchronized(the_settings) {
			
			//just a precaution, 
			if(innerHandler == null) {
				return; //responders need the handler, and will choke on null.
			}
			
			if(!getSettings().getTimers().containsKey(ordinal)) {
				return; // no ordinal
			}
			
			TimerData data = getSettings().getTimers().get(ordinal);
			if(data == null) {
				return; //this shoudn't happen. means there is a null entry in the map.
			}
			
			//hasListener = isWindowShowing();
			for(TriggerResponder responder : data.getResponders()) {
				try {
					responder.doResponse(mContext,null,0,null,null,0,0,"",(Object)getSettings().getTimers().get(ordinal), parent.getDisplayName(),parent.getHostName(),parent.getPort(), StellarService.getNotificationId(), parent.isWindowShowing(), mHandler,captureMap,L,Plugin.this.getSettings().getTimers().get(ordinal).getName(),mEncoding);
				} catch (IteratorModifiedException e) {
					// won't ever get here because gag/replace actions can't be applied to timers.
				}
				//service.
				//responder.doResponse(parent.getContext(),null,null,null,null, parent.getDisplayName(), StellarService.getNotificationId(), parent.isWindowShowing(), mHandler, null,L,data.getName());
			}
			
			if(data.isRepeat()) {
				stopTimer(ordinal);
				startTimer(ordinal);
			} else {
				stopTimer(ordinal);
			}
		//}
	}
	
	//public void initTimers() {
		
	//}
	
	public void startTimer(String key) {
		TimerData d = getSettings().getTimers().get(key);
		if(d == null) {
			return;
		}
		if(timerTasks.containsKey(d.getName())) {
			//already playing.
			return;
		}
		
		if(d.isPlaying()) {
			//already playing
		} else {
			if(d.getRemainingTime() != d.getSeconds()) {
				CustomTimerTask task = new CustomTimerTask(d.getName());
				long startTime = SystemClock.elapsedRealtime() - ((d.getSeconds() - d.getRemainingTime())*1000);
				if(d.isRepeat()) {
					d.setStartTime(startTime);
					CONNECTION_TIMER.schedule(task, d.getRemainingTime()*1000, d.getSeconds()*1000);
				} else {
					d.setStartTime(startTime);
					CONNECTION_TIMER.schedule(task, d.getRemainingTime()*1000);
				}
				
				timerTasks.put(d.getName(), task);
			} else {
				CustomTimerTask task = new CustomTimerTask(d.getName());
				long startTime = SystemClock.elapsedRealtime();
				if(d.isRepeat()) {
					d.setStartTime(startTime);
					CONNECTION_TIMER.schedule(task, d.getSeconds()*1000, d.getSeconds()*1000);
				} else {
					d.setStartTime(startTime);
					CONNECTION_TIMER.schedule(task, d.getSeconds()*1000);
				}
				timerTasks.put(d.getName(), task);
			}
			
			d.setPlaying(true);
		}
		
	}
	
	public void stopTimer(String key) {
		CustomTimerTask task = timerTasks.get(key);
		if(task != null) {
			task.cancel();
			timerTasks.remove(key);
		}
		TimerData d = getSettings().getTimers().get(key);
		if(d != null) {
			d.setRemainingTime(d.getSeconds());
			d.setPlaying(false);
		}
	}
	
	public void pauseTimer(String key) {
		CustomTimerTask task = timerTasks.get(key);
		
		if(task != null) {
			task.cancel();
			timerTasks.remove(key);
		} else {
			return;
		}
		
		long taskStartTime = task.getStartTime();
		
		TimerData d = getSettings().getTimers().get(key);
		if(d != null) {
			//calculate the remaining seconds.
			long now = SystemClock.elapsedRealtime();
			int elapsed = d.getSeconds() - (int) Math.floor((now - taskStartTime)/1000);
			d.setRemainingTime(elapsed);
			d.setPlaying(false);
		}
		
	}
	
	public void resetTimer(String obj) {
		CustomTimerTask task = timerTasks.get(obj);
		boolean running = false;
		if(task != null) {
			//was running, need to restart
			//task.cancel();
			//timerTasks.remove(obj);
			running = true;
		}
		
		if(!running) {
			stopTimer(obj);
		} else {
			stopTimer(obj);
			startTimer(obj);
		}
	}
	
	public void updateTimerProgress() {
		for(String key : timerTasks.keySet()) {
			CustomTimerTask t = timerTasks.get(key);
			long taskStart = t.getStartTime();
			long now = SystemClock.elapsedRealtime();
			int elapsed = (int) Math.floor((now - taskStart)/1000);
			TimerData d = getSettings().getTimers().get(key);
			if(d != null) {
				d.setRemainingTime(d.getSeconds() - elapsed);
			}
		}
	}
	
	public void updateBooleanSetting(String key,boolean value) {
		settings.getOptions().updateBoolean(key,value);
		settings.setDirty(true);
	}
	
	public void updateIntegerSetting(String key,int value) {
		settings.getOptions().updateInteger(key,value);
		settings.setDirty(true);
	}
	
	public void updateFloatSetting(String key,float value) {
		settings.getOptions().updateFloat(key,value);
		settings.setDirty(true);
	}
	
	public void updateStringSetting(String key,String value) {
		settings.getOptions().updateString(key,value);
		settings.setDirty(true);
	}
	
	public void setEncoding(String encoding) {
		this.mEncoding = encoding;
	}

	
/*! \page entry_points Lua State Entry Points
 * \section entrypoints Background Service Entry Points
 * \subsection OnBackgroundStartup OnBackgroundStartup
 * Called when all plugins have been parsed and loaded, but before the connection to the server is initiated.
 * 
 * \param none
 */
	public void doBackgroundStartup() {
		L.getGlobal("debug");
		L.getField(-1, "traceback");
		L.remove(-2);
		
		L.getGlobal("OnBackgroundStartup");
		if(L.getLuaObject(-1).isFunction()) {
			int ret = L.pcall(0, 1, -2);
			if(ret != 0) {
				displayLuaError("Error in OnBackgroundStartup:"+L.getLuaObject(-1).getString());
			} else {
				L.pop(2);
			}
		} else {
			L.pop(2);
		}
		
		checkStack("OnBackgroundStartup");
	}

/*! \page entry_points
 * \subsection OnXmlExport OnXmlExport
 * When the BlowTorch core has initiated a settings serialization (saves the settings) this will be called to notify the plugin that it needs to serialize any data that it needs to, and provides an android.xml.XMLSerializer that is set up to be either to the main settings wad or the external plugin's descriptor file.
 * 
 * \param out \b android.xml.XmlSerialzer represent the output serializer object.
 * 
 * \note Please check the documentation of the android java class or the examples for saving data for details of what the body of this function should look like. 
 */

	public void scriptXmlExport(XmlSerializer out) {
		L.getGlobal("debug");
		L.getField(-1, "traceback");
		L.remove(-2);
		
		L.getGlobal("OnXmlExport");
		if(L.getLuaObject(-1).isFunction()) {
			L.pushJavaObject(out);
			int retval = L.pcall(1, 1, -3);
			if(retval != 0) {
				displayLuaError("Plugin: "+this.getName()+" OnXmlExport() Error:" + L.getLuaObject(-1).getString());
			} else {
				L.pop(2);
			}
		} else {
			L.pop(2);
		}
		checkStack("OnXmlExport");
		
	}
	
	private ArrayList<TriggerData> sortedTriggers = null;
	public void sortTriggers() {
		if(this.settings.getTriggers().size() == 0) return;
		sortedTriggers = new ArrayList<TriggerData>(this.settings.getTriggers().values());
		Collections.sort(sortedTriggers,new Comparator() {

			
			

			public int compare(Object arg0, Object arg1) {
				// TODO Auto-generated method stub
				TriggerData a = (TriggerData)arg0;
				TriggerData b = (TriggerData)arg1;
				//if(a.getSequence() == 5 || b.getSequence() == 5) {
					//Log.e("COMP","STOP HERE");
				//}
				if(a.getSequence() > b.getSequence()) return 1;
				if(a.getSequence() < b.getSequence()) return -1;
				
				return 0;
			}
			
		});
	}
	
	public ArrayList<TriggerData> getSortedTriggers() {
		if(sortedTriggers == null) { sortTriggers(); }
		return sortedTriggers;
	}
	
	public void buildTriggerSystem() {
		//start with the global settings.
		long start = System.currentTimeMillis();
		sortedTriggerMap = new HashMap<Integer,TriggerData>();
		//triggerPluginMap = new HashMap<Integer,Plugin>();
		int working = 1;
		triggerBuilder.setLength(0);
		boolean addseparator = false;
		ArrayList<TriggerData> tmp = sortedTriggers;
		if(tmp == null) {
			sortTriggers();
			tmp = sortedTriggers;
			if(tmp == null || tmp.size() == 0) return;
		}
		if(tmp != null && tmp.size() > 0) {
			for(int i=0;i<tmp.size();i++) {
				TriggerData t = tmp.get(i);
				if(!t.isInterpretAsRegex() && (t.getPattern().startsWith("%")
						|| t.getPattern().startsWith("@"))) {
					
				} else {
					if(t.isEnabled()) {
						if(i == 0) {
							triggerBuilder.append("(");
							triggerBuilder.append(t.getPattern());
							triggerBuilder.append(")");
							addseparator = true;
						} else {
							triggerBuilder.append("|(");
							triggerBuilder.append(t.getPattern());
							triggerBuilder.append(")");
						}
						sortedTriggerMap.put(working, t);
						//triggerPluginMap.put(working, the_settings);
						working += t.getMatcher().groupCount()+1;
					}
				}
			}
		}
		
		massiveTriggerString = triggerBuilder.toString();
		
		massivePattern = Pattern.compile(massiveTriggerString,Pattern.MULTILINE);
		//massiveTriggerString = massiveTriggerString.replace("|", "\n");
		//Log.e("MASSIVE",massiveTriggerString);
		massiveMatcher = massivePattern.matcher("");
		
		long delta = System.currentTimeMillis() - start;
		//Log.e("TRIGGERS","TIMEPROFILE "+getSettings().getName()+" trigger system took " + delta + " millis to build.");
	}
	
	StringBuilder triggerBuilder = new StringBuilder();
	String massiveTriggerString = null;
	Pattern massivePattern = null;
	Matcher massiveMatcher = null;
	HashMap<Integer,TriggerData> sortedTriggerMap = null;
	//HashMap<Integer,Plugin> triggerPluginMap = null;

/*! \page entry_points
 * \subsection OnOptionsChanged OnOptionsChanged
 * This function is called whenever a plugin defined option has changed through the user activating the options menu UI.
 * 
 * \param key \b string the key value of the option that changed
 * \param value \b string the new value of the option
 * 
 * \note There are a few demonstrations on how to use this function in the button window and chat window plugins.
 */
	
	@Override
	public void updateSetting(String key, String value) {
		if(L != null) {
			L.getGlobal("debug");
			L.getField(-1, "traceback");
			L.remove(-2);
			
			L.getGlobal("OnOptionChanged");
			if(L.getLuaObject(-1).isFunction()) {
				L.pushString(key);
				L.pushString(value);
				int ret = L.pcall(2, 1, -4);
				if(ret != 0) {
					displayLuaError("Error in OnOptionChanged:"+L.getLuaObject(-1).getString());
					
				} else {
					L.pop(2);
				}
			} else {
				L.pop(2);
			}
			
			checkStack("OnOptionChanged");
		}
	}

	public void pushOptionsToLua() {
		dumpOption(this.getSettings().getOptions());
	}
	
	private void dumpOption(SettingsGroup group) {
		ArrayList<Option> options = group.getOptions();
		if(!this.getSettings().getName().equals("button_window")) {
			long foo = System.currentTimeMillis();
		}
		for(Option o : options) {
			if(o instanceof SettingsGroup) {
				dumpOption((SettingsGroup)o);
			} else {
				BaseOption tmp = (BaseOption)o;
				L.getGlobal("debug");
				L.getField(-1, "traceback");
				L.remove(-2);
				
				L.getGlobal("OnOptionChanged");
				if(L.getLuaObject(-1).isFunction()) {
					L.pushString(tmp.getKey());
					L.pushString(tmp.getValue().toString());
					int ret = L.pcall(2, 1, -4);
					if(ret != 0) {
						displayLuaError("Error in OnOptionChanged:"+L.getLuaObject(-1).getString());
					} else {
						L.pop(2);
					}
				} else {
					L.pop(2);
				}
				
				checkStack("OnOptionChanged");
			}
		}
	}

	public void setEnabled(boolean enabled) {
		enabled = true;
	}
	
	public boolean isEnabled() {
		return enabled;
	}

	public void markTriggersDirty() {
		parent.setTriggersDirty();
		
	}
	
	private boolean debug = true;
	private void checkStack(String method) {
		int top = L.getTop();
		//Log.e("PLUGIN","checking stack after "+method+" size: "+Integer.toString(top));
	}

	public void callFunction(String function) {
		L.getGlobal("debug");
		L.getField(-1, "traceback");
		L.remove(-2);
		
		L.getGlobal(function);
		if(L.getLuaObject(-1).isFunction()) {
			//L.pushJavaObject(out);
			int retval = L.pcall(0, 1, -2);
			if(retval != 0) {
				displayLuaError("Plugin: "+this.getName()+" Script callback("+function+") Error:" + L.getLuaObject(-1).getString());
			} else {
				L.pop(2);
			}
		} else {
			displayLuaError("No function named: "+function+" in plugin: "+this.getName());
			L.pop(2);
		}
	}

	public void callFunction(String function, String data) {
		L.getGlobal("debug");
		L.getField(-1, "traceback");
		L.remove(-2);
		
		L.getGlobal(function);
		if(L.getLuaObject(-1).isFunction()) {
			//L.pushJavaObject(out);
			L.pushString(data);
			int retval = L.pcall(1, 1, -2);
			if(retval != 0) {
				displayLuaError("Plugin: "+this.getName()+" Script callback("+function+") Error:" + L.getLuaObject(-1).getString());
			} else {
				L.pop(2);
			}
		} else {
			displayLuaError("No function named: "+function+" in plugin: "+this.getName());
			L.pop(2);
		}
	}

	public boolean checkPluginSupports(String function) {
		if(L != null) {
			L.getGlobal(function);
			if(L.isFunction(-1)) {
				L.pop(1);
				return true;
			} else {
				L.pop(1);
				return false;
			}
		}
		
		return false;
	}
	
	public boolean runLuaString(String str) {
		//boolean ret = false;
		if(L != null) {
			L.getGlobal("debug");
			L.getField(-1, "traceback");
			L.remove(-2);
			
			int ret = L.LloadString(str);
			if(ret != 0) {
				//invalid lua, no dice for you
				displayLuaError(L.getLuaObject(-1).getString());
				return false;
			}
			
			ret = L.pcall(0, 1, -2);
			if(ret != 0) {
				displayLuaError(L.getLuaObject(-1).getString());
				return true;
			} else {
				return true;
			}
			
		}
		return false;
	}

	public String getScriptBlock() {
		return scriptBlock;
	}

	public void setScriptBlock(String scriptBlock) {
		this.scriptBlock = scriptBlock;
	}

	public String getOptionValue(String key) {
		// TODO Auto-generated method stub
		SettingsGroup g = settings.getOptions();
		
		return g.getOptionValue(key);
	}


	
	
	
}
