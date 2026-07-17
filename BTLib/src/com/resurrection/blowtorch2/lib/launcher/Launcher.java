package com.resurrection.blowtorch2.lib.launcher;

import android.Manifest;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.LauncherActivity;
import android.app.ProgressDialog;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlertDialog.Builder;
import android.content.ComponentName;
import android.content.res.ColorStateList;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.text.SpannableString;
import android.text.format.Time;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.util.TypedValue;
import android.util.TimeFormatException;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.text.TextUtils;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
//import android.support.v4.app.ActivityCompat;
import com.google.android.material.snackbar.Snackbar;



import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.service.IConnectionBinder;
import com.resurrection.blowtorch2.lib.service.IConnectionBinderCallback;
import com.resurrection.blowtorch2.lib.service.ILauncherCallback;
import com.resurrection.blowtorch2.lib.service.StellarService;
import com.resurrection.blowtorch2.lib.window.MainWindow;
import com.resurrection.blowtorch2.lib.settings.ConfigurationLoader;
import com.resurrection.blowtorch2.lib.util.BlowTorchLogger;
import com.resurrection.blowtorch2.lib.ui.SDCardUtils;
import com.resurrection.blowtorch2.lib.ui.PermissionHelper;


import dalvik.system.PathClassLoader;


public class Launcher extends AppCompatActivity implements ReadyListener,ActivityCompat.OnRequestPermissionsResultCallback {
	
	public static final String PREFS_NAME = "CONDIALOG_SETTINGS";
	
	private Pattern xmlinsensitive = Pattern.compile("^.+\\.[Xx][Mm][Ll]$");
	private Matcher xmlimatcher = xmlinsensitive.matcher("");

	protected static final int MESSAGE_WHATSNEW = 1;
	protected static final int MESSAGE_IMPORT = 2;
	private static final int REQUEST_PICK_SETTINGS_ZIP = 2001;
	private static final int REQUEST_PICK_SERVER_LIST_XML = 2002;
	private static final int REQUEST_CREATE_SERVER_LIST_XML = 2003;
	private static final int REQUEST_CREATE_SETTINGS_ZIP = 2004;
	protected static final int MESSAGE_EXPORT = 3;

	protected static final int MESSAGE_USERNAME = 4;

	protected static final int RP_INFO = 100;
	protected static final int RP_EXPORT = 102;
	protected static final int RP_IMPORT = 103;
	protected static final int RP_STARTUP = 104;
	protected static final int MENU_IMPORT_SERVER_LIST = 100;
	protected static final int MENU_EXPORT_SERVER_LIST = 105;
	protected static final int MENU_USER_NAME = 106;
	protected static final int MENU_SDCARD_PERMISSIONS = 108;
	protected static final int MENU_APP_SETTINGS = 109;
	protected static final int MENU_BACKUP_ALL_SETTINGS = 110;
	protected static final int MENU_RESTORE_SETTINGS_BACKUP = 111;
	protected static final int MENU_ABOUT = 112;
	
	private IConnectionBinder service = null;
	
	private ArrayList<MudConnection> connections;
	private Launcher.ConnectionAdapter apdapter;
	
	ListView lv = null;
	
	Handler actionHandler;
	
	LauncherSettings launcher_settings;

	/** When false, saveXML() is a no-op so a failed load cannot wipe the on-disk list. */
	private boolean launcherSaveEnabled = false;
	
	//IConnectionBinder service;
	
	/*public enum LAUNCH_MODE {
		FREE,
		PAID,
		TEST
	}*/
	
	//private LAUNCH_MODE mode = LAUNCH_MODE.FREE;
	private String launcher_source = "";
	
	//make this save a change
	boolean dowhatsnew = false;

	
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		BlowTorchLogger.ensureLogFile(this);
		fixClassLoaderIssue();
		com.resurrection.blowtorch2.lib.service.LuaLibraryHelper.ensureCurrentVersion(this);
		//Log.e("LAUNCHER","Launched from package: " + this.getPackageName());
		//determine launch mode
		//Intent intent = this.getIntent();



		
		
		launcher_source = this.getIntent().getStringExtra("LAUNCH_MODE");
		if(launcher_source == null) {
			//Log.e("BlowTorch","Launcher not provided a valid launch source. Finishing.");
			this.finish();
		}
		
		/*if(launcher_source.equals("com.resurrection.blowtorch2.libtest")) {
			mode = LAUNCH_MODE.TEST;
			//Log.e("BlowTorch","Test Launcher Engaged.");
		} else if(launcher_source.equals("com.resurrection.blowtorch2.lib")) {
			//Log.e("BlowTorch","Free Launcher Engaged.");
			mode = LAUNCH_MODE.FREE;
		} else if(launcher_source.equals("com.resurrection.blowtorch2.libpro")) {
			//Log.e("BlowTorch","Paid Launcher Engaged");
			mode = LAUNCH_MODE.PAID;
		} else {
			//Log.e("BlowTorch","Launcher given source: " + launcher_source + " which is invalid, Finishing");
			this.finish();
		}*/
		
		actionHandler = new Handler() {
			public void handleMessage(Message msg) {
				switch(msg.what) {
				case MESSAGE_USERNAME:
					SharedPreferences.Editor edit = Launcher.this.getSharedPreferences("TEST_USER", Context.MODE_PRIVATE).edit();
					edit.putString("USER_NAME", (String)msg.obj);
					edit.commit();
					break;
				case MESSAGE_WHATSNEW:
					break;
				case MESSAGE_IMPORT:

					//if the file exists, we will get here, if not, it will go to file not found.
					try {
						LauncherSAXParser parser = new LauncherSAXParser((String)msg.obj,Launcher.this);
						launcher_settings = parser.load();
						if (launcher_settings == null) {
							Toast.makeText(Launcher.this, "Import failed: invalid launcher list XML.", Toast.LENGTH_LONG).show();
							return;
						}
						launcherSaveEnabled = true;
					} catch (RuntimeException e) {
						AlertDialog.Builder error = new AlertDialog.Builder(Launcher.this);
						error.setTitle("Error loading XML");
						error.setMessage(e.getMessage());
						error.setPositiveButton("Acknowledge.",new DialogInterface.OnClickListener() {
							
							public void onClick(DialogInterface dialog, int which) {
								dialog.dismiss();
							}
						});
						AlertDialog errordialog = error.create();
						errordialog.show();
						return;
					}
					//update this list to the new version.
					PackageManager m = Launcher.this.getPackageManager();
					String versionString = null;
					try {
						versionString = m.getPackageInfo(Launcher.this.getApplicationInfo().packageName, PackageManager.GET_CONFIGURATIONS).versionName;
					} catch (NameNotFoundException e) {
						//can't execute on our package aye?
						throw new RuntimeException(e);
					}
					launcher_settings.setCurrentVersion(versionString);
					buildList();
					saveXML();
					break;
				case MESSAGE_EXPORT:

					break;
				default:
					break;
				}
			}
		};
		
		setContentView(R.layout.new_launcher_layout);
		androidx.appcompat.widget.Toolbar myToolbar = (androidx.appcompat.widget.Toolbar) findViewById(R.id.my_toolbar);
		setSupportActionBar(myToolbar);
		final View tableContainer = findViewById(R.id.table_container);
		ViewCompat.setOnApplyWindowInsetsListener(myToolbar, (view, windowInsets) -> {
			int topInset = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
			TypedValue actionBarSize = new TypedValue();
			if (getTheme().resolveAttribute(androidx.appcompat.R.attr.actionBarSize, actionBarSize, true)) {
				int barHeight = TypedValue.complexToDimensionPixelSize(
						actionBarSize.data, getResources().getDisplayMetrics());
				ViewGroup.LayoutParams lp = view.getLayoutParams();
				lp.height = barHeight + topInset;
				view.setLayoutParams(lp);
			}
			view.setPadding(view.getPaddingLeft(), topInset, view.getPaddingRight(), 0);
			return windowInsets;
		});
		if (tableContainer != null) {
			// Use padding (not margin): margin + alignParentBottom + AppCompat Button insets
			// was clipping/pushing New/Help labels off the visible button face after resume.
			ViewCompat.setOnApplyWindowInsetsListener(tableContainer, (view, windowInsets) -> {
				int bottomInset = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
				view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), bottomInset);
				return windowInsets;
			});
		}
		if(ConfigurationLoader.isTestMode(this)) {
			View testUpdate = findViewById(R.id.test_update);
			if (testUpdate != null) {
				testUpdate.setVisibility(View.VISIBLE);
			}
			TextView versionLabel = (TextView) findViewById(R.id.update_label);
			String versionName = "test";
			try {
				versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
			} catch (NameNotFoundException e) {
				e.printStackTrace();
			}
			if (versionLabel != null) {
				versionLabel.setText("Test " + versionName);
				versionLabel.setVisibility(View.VISIBLE);
			}
		}
		
		
		
		launcher_settings = new LauncherSettings();
		connections = new ArrayList<MudConnection>();
		
		lv = (ListView)findViewById(R.id.connection_list);
		apdapter = new ConnectionAdapter(lv.getContext(),R.layout.connection_row,connections);
		
		
		lv.setAdapter(apdapter);
		
		lv.setTextFilterEnabled(true);
		lv.setOnItemClickListener(new listItemClicked());
		lv.setOnItemLongClickListener(new listItemLongClicked());
		
		
		lv.setEmptyView(findViewById(R.id.launcher_empty));
		
		try { 
			FileInputStream fos = this.openFileInput("blowtorch_launcher_list.xml");
			fos.close();
			LauncherSAXParser parser = new LauncherSAXParser("blowtorch_launcher_list.xml",this);
			launcher_settings = parser.load();
			if (launcher_settings == null) {
				LauncherSettings recovered = tryRecoverLauncherList();
				if (recovered != null) {
					launcher_settings = recovered;
					launcherSaveEnabled = true;
					Toast.makeText(this, "Server list recovered from SD card backup.", Toast.LENGTH_LONG).show();
				} else {
					launcher_settings = new LauncherSettings();
					getConnectionsFromDisk();
					for (int i = 0; i < apdapter.getCount(); i++) {
						MudConnection tmp = apdapter.getItem(i);
						launcher_settings.getList().put(tmp.getDisplayName(), tmp.copy());
					}
					if (!launcher_settings.getList().isEmpty()) {
						launcherSaveEnabled = true;
					} else {
						Toast.makeText(this,
								"Could not load server list. Your saved list was not overwritten — use ⋮ → Import Server List to restore.",
								Toast.LENGTH_LONG).show();
					}
				}
			} else {
				launcherSaveEnabled = true;
			}
			//buildList();
			//Log.e("LAUNCHER","LOADING XML LAUNCHER");
		} catch (FileNotFoundException e) {
			//attempt to read the connections from disk.
			//Log.e("LAUNCHER","LOADING CRAPPY LAUNCHER");
			getConnectionsFromDisk();
			//fill the new settings
			int size = apdapter.getCount();
			Time t = new Time();
			t.set(System.currentTimeMillis());
			long starttime = System.currentTimeMillis();
			for(int i=0;i<size;i++) {
				MudConnection tmp = apdapter.getItem(i);
				Time oldertime = new Time();
				oldertime.set(starttime - 1000*i);
				tmp.setLastPlayed(oldertime.format2445());
				launcher_settings.getList().put(tmp.getDisplayName(), tmp.copy());
				
			}
			
			//get the version information.
			//PackageManager m = this.getPackageManager();
			//String versionString = null;
			//try {
			//	versionString = m.getPackageInfo("com.resurrection.blowtorch2.lib", PackageManager.GET_CONFIGURATIONS).versionName;
			//} catch (NameNotFoundException e1) {
				//can't execute on our package aye?
			//	throw new RuntimeException(e);
			//}
			
			//Log.e("LAUNCHER","LOADING OLD SETTINGS AND MARKING VERSION: " + versionString);
			launcher_settings.setCurrentVersion("1.0.4");
			
			launcherSaveEnabled = true;
			saveXML();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		int discovered = ProfileDiscovery.mergeDiscoveredProfiles(this, launcher_settings);
		if (discovered > 0) {
			launcherSaveEnabled = true;
			saveXML();
			Toast.makeText(this, getString(R.string.profiles_discovered, discovered), Toast.LENGTH_LONG).show();
		}
		
		//by here we should have a completly populated list and settings
		//check version code.
		PackageManager m = this.getPackageManager();
		String versionString = null;
		String versionPackage = launcher_source;
		if (versionPackage == null || versionPackage.length() == 0) {
			versionPackage = getPackageName();
		}
		try {
			versionString = m.getPackageInfo(versionPackage, PackageManager.GET_CONFIGURATIONS).versionName;
		} catch (NameNotFoundException e) {
			try {
				versionPackage = getPackageName();
				versionString = m.getPackageInfo(versionPackage, PackageManager.GET_CONFIGURATIONS).versionName;
			} catch (NameNotFoundException e2) {
				throw new RuntimeException(e2);
			}
		}
		
		int now_major = 1;
		int now_minor = 0;
		int now_rev = 0;
		
		int prev_major = 1;
		int prev_minor = 0;
		int prev_rev = 0;
		//compare version codes.
		Pattern version = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");
		Matcher vmatch = version.matcher(versionString);
		if(vmatch.matches()) {
			now_major = Integer.parseInt(vmatch.group(1));
			now_minor = Integer.parseInt(vmatch.group(2));
			now_rev = Integer.parseInt(vmatch.group(3));
		} else {
			//shouldn't really happen.
		}
		
		vmatch.reset(launcher_settings.getCurrentVersion());
		if(vmatch.matches()) {
			prev_major = Integer.parseInt(vmatch.group(1));
			prev_minor = Integer.parseInt(vmatch.group(2));
			prev_rev = Integer.parseInt(vmatch.group(3));
		} else {
			//shouldn't really happen, unless xml modification went haywire
		}
		
		boolean isoutdated = false;
		
		if(now_major > prev_major) {
			//Log.e("LAUNCHER","MAJOR NOW:" + now_major + " MAJOR PREV:" + prev_major);
			isoutdated = true;
		} else if (now_minor > prev_minor) {
			//Log.e("LAUNCHER","MINOR NOW:" + now_minor + " MINOR PREV:" + prev_minor);
			isoutdated = true;
		} else if (now_rev > prev_rev) {
			//Log.e("LAUNCHER","REV NOW:" + now_rev + " REV PREV:" + prev_rev);
			isoutdated = true;
		}
		
		if(isoutdated) {
			launcher_settings.setCurrentVersion(versionString);
			saveXML();
			//Log.e("LAUNCHER","DOING OUTATED, WAS " + launcher_settings.getCurrentVersion() + " NOW " + versionString);
		} else {
			//Log.e("LAUNCHER","NOT OUTDATED, WAS " + launcher_settings.getCurrentVersion() + " NOW " + versionString);
		}
		
		//if test mode, load test mode version
		//if(mode == LAUNCH_MODE.TEST) {
		if(ConfigurationLoader.isTestMode(this)) {
			int readver = this.getSharedPreferences("TEST_VERSION_DOWHATSNEW", Context.MODE_PRIVATE).getInt("TEST_VERSION", 0);
			int testVersion = 0;
			try {
				ApplicationInfo info = getPackageManager().getApplicationInfo(launcher_source, PackageManager.GET_META_DATA);
				if (info != null && info.metaData != null) {
					testVersion = info.metaData.getInt("BLOWTORCH_TEST_VERSION");
				}
			} catch (NameNotFoundException e) {
				e.printStackTrace();
			}
			
			if(testVersion != readver) {
				SharedPreferences.Editor edit = this.getSharedPreferences("TEST_VERSION_DOWHATSNEW", Context.MODE_PRIVATE).edit();
				edit.putInt("TEST_VERSION", testVersion);
				edit.commit();
			}
		}
		
		
		//getConnectionsFromDisk();
		
		Button newbutton = (Button)findViewById(R.id.new_connection);
		styleLauncherActionButton(newbutton);
		newbutton.setOnClickListener(new newClickedListener());

		Log.e("LAUNCHER","BINDING SERVICE (FGS starts on MUD connect via MainWindow)");
		String action = ConfigurationLoader.getConfigurationValue("serviceBindAction",Launcher.this);
		// Do not start foreground service from launcher alone — avoids idle "waiting" notification.
		View permissionRoot = findViewById(R.id.launcher_window_content);
		if (permissionRoot == null) {
			permissionRoot = tableContainer;
		}
		SDCardUtils.requestStartupPermissions(this, permissionRoot, RP_STARTUP);
		buildList();
		maybeBackupBeforeUpdate();
		if(!serviceBound) {
			//String action = ConfigurationLoader.getConfigurationValue("serviceBindAction",Launcher.this);
			bindService(new Intent(action,null,this, StellarService.class),connectionChecker,Context.BIND_AUTO_CREATE);
		}
		
	}
	
	private static void fixClassLoaderIssue()
	{
		ClassLoader myClassLoader = Launcher.class.getClassLoader();
		Thread.currentThread().setContextClassLoader(myClassLoader);
	}

	private void styleLauncherActionButton(Button button) {
		if (button == null) {
			return;
		}
		button.setEnabled(true);
		button.setClickable(true);
		button.setAllCaps(false);
		button.setAlpha(1f);
		ColorStateList whiteText = ColorStateList.valueOf(0xFFFFFFFF);
		button.setTextColor(whiteText);
		button.setGravity(Gravity.CENTER);
		button.setPadding(
				dpToPx(8),
				dpToPx(10),
				dpToPx(8),
				dpToPx(10));
		button.setMinHeight(dpToPx(48));
		button.setMinimumHeight(dpToPx(48));
		button.setBackgroundResource(R.drawable.launcher_action_button_bg);
		button.setBackgroundTintList(null);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			button.setBackgroundTintMode(null);
			button.setStateListAnimator(null);
			button.setElevation(0f);
		}
		button.setIncludeFontPadding(false);
		button.invalidate();
	}

	private int dpToPx(int dp) {
		return Math.round(TypedValue.applyDimension(
				TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()));
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			styleLauncherActionButton((Button) findViewById(R.id.new_connection));
		}
	}
  
	
	public static boolean isOutDated(Context c) {
		
		//version number is major.minor.rev.[test]
		//method, load up version string, split on the "." character, convert parts to a number
		
		
		return false;
	}
	
	boolean checkedUpdate = false;
	
	public void onStart() {
		super.onStart();
		//if(noConnections) {
		//	Toast msg = Toast.makeText(this, "No connections specified, select NEW to create.", Toast.LENGTH_LONG);
		//	msg.show();
		//}
		if(!serviceBound) {
			String action = ConfigurationLoader.getConfigurationValue("serviceBindAction",Launcher.this);
			bindService(new Intent(action,null,this, StellarService.class),connectionChecker,Context.BIND_AUTO_CREATE);
		}
	}
	
	boolean serviceBound = false;
	@Override
	public void onResume() {
		super.onResume();
		styleLauncherActionButton((Button) findViewById(R.id.new_connection));
		if(!serviceBound) {
			String action = ConfigurationLoader.getConfigurationValue("serviceBindAction",Launcher.this);
			bindService(new Intent(action,null,this, StellarService.class),connectionChecker,Context.BIND_AUTO_CREATE);
		}
		//buildList();
	}
	
	@Override
	public void onPause() {
		super.onPause();
		if(serviceConnected) {
			try {
				service.unregisterLauncherCallback(the_callback);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		unbindService(connectionChecker);
		serviceBound = false;
		serviceConnected = false;
		//if(serviceConnected) {
		//	unbindService(connectionChecker);
		//}
	}
	
	
	public void onDestroy() {
		//saveConnectionsToDisk();
		saveXML();
		super.onDestroy();
	}
	
	private class helpClickedListener implements View.OnClickListener {

		public void onClick(View v) {
			showAboutDialog();
		}
		
	}

	private void showAboutDialog() {
		new com.resurrection.blowtorch2.lib.window.AboutDialog(Launcher.this).show();
	}
	
	private class newClickedListener implements View.OnClickListener {
		public void onClick(View v) {
			//close the dialog for now
			//ConnectionPickerDialog.this.dismiss();
			NewConnectionDialog diag = new NewConnectionDialog(Launcher.this,Launcher.this);
			diag.show();
		}
	}
	
	private class listItemLongClicked implements ListView.OnItemLongClickListener {

		public boolean onItemLongClick(AdapterView<?> arg0, View arg1,
				int arg2, long arg3) {
			//Log.e("LAUNCHER","List item long clicked!");
			MudConnection muc = apdapter.getItem(arg2);
			
			
			Message delmsg = connectionModifier.obtainMessage(MSG_DELETECONNECTION);
			delmsg.obj = muc;
			
			Message modmsg = connectionModifier.obtainMessage(MSG_MODIFYCONNECTION);
			modmsg.obj = muc;
			
			AlertDialog.Builder build = new AlertDialog.Builder(Launcher.this)
				.setMessage("Which operation to perform on: " + muc.getDisplayName());
			AlertDialog dialog = build.create();
			dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Edit", modmsg);
			dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Delete",delmsg);
			dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
				
				public void onClick(DialogInterface arg0, int arg1) {
					arg0.dismiss();
				}
			});
			
			dialog.show();
			return true;
		}
		
	}
	boolean debug = true;
	private class listItemClicked implements ListView.OnItemClickListener {
		
		//@Override
		@TargetApi(11)
		public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
				long arg3) {
			
			Rect rect = new Rect();
		    Window win = Launcher.this.getWindow();
		    win.getDecorView().getWindowVisibleDisplayFrame(rect);
		    int statusBarHeight = rect.top;
		    int contentViewTop = win.findViewById(Window.ID_ANDROID_CONTENT).getTop();
		    int titleBarHeight = contentViewTop - statusBarHeight;
		    //Log.d("ID-ANDROID-CONTENT", "titleBarHeight = " + titleBarHeight );
		    
		    //if(Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
		    //	titleBarHeight += statusBarHeight;
		    //}
		    
		    SharedPreferences pref = Launcher.this.getSharedPreferences("STATUS_BAR_HEIGHT", 0);
			Editor e = pref.edit();
			e.putInt("STATUS_BAR_HEIGHT", statusBarHeight);
			e.putInt("TITLE_BAR_HEIGHT", titleBarHeight);
		    e.commit();
			
			MudConnection muc = apdapter.getItem(arg2);		
			
			Time the_time = new Time();
			the_time.set(System.currentTimeMillis());
			muc.setLastPlayed(the_time.format2445());
			
			saveXML();
			
			buildList();
			
			//if(debug) return;
			
			//Intent the_intent = new Intent(com.resurrection.blowtorch2.lib.window.MainWindow.class.getName());
	    	
	    	//the_intent.putExtra("DISPLAY",muc.getDisplayName());
	    	//the_intent.putExtra("HOST", muc.getHostName());
	    	//the_intent.putExtra("PORT", muc.getPortString());
	    	
	    	//write out the intent to the service so it can do some lookup work in advance of the connection, such as loading the settings wad
	    	//SharedPreferences prefs = Launcher.this.getSharedPreferences("SERVICE_INFO",0);
	    	//Editor edit = prefs.edit();
	    	//Log.e("WINDOW","SETTING " + muc.getDisplayName());
	    	
	    	
	    	//edit.putString("SETTINGS_PATH", muc.getDisplayName());
	    	//edit.commit();
	    	
	    	//check to see if the service is actually running
	    	
	    	//boolean found = isServiceRunning();
	    	
	    	//if(!found) {
    			//service is not running, reset the values in the shared prefs that the window uses to keep track of weather or not to finish init routines.
    			//kill all whitespace in the display name.
	    		launch = muc.copy();
	    		PermissionHelper.ensureInternetForFeature(Launcher.this,
	    				R.string.permission_feature_connect, new Runnable() {
	    			@Override
	    			public void run() {
	    				DoNewStartup();
	    			}
	    		});
	    	/*} else {
	    		//service exists, we should figure out the name of what it is playing.
	    		//Log.e("LAUNCHER","SERVICE IS RUNNING");
	    		launch = muc.copy();
	    		
	    		
	    		String action = ConfigurationLoader.getConfigurationValue("serviceBindAction",Launcher.this);
	    		bindService(new Intent(action),mConnection,0);
	    		
	    	}*/
	    	//}
	    	
		}

		
	}
	private MudConnection launch;
	
//	private ServiceConnection mConnection = new ServiceConnection() {
//
//		public void onServiceConnected(ComponentName arg0, IBinder arg1) {
//			
//			if(arg1 == null) {
//				return;
//			}
//			
//			service = IConnectionBinder.Stub.asInterface(arg1); //turn the binder into something useful
//			
//			String test = "";
//			String against = launch.getHostName() +":"+ launch.getPortString();
//			try {
//				test = service.getConnectedTo();
//			} catch (RemoteException e) {
//				throw new RuntimeException(e);
//			}
//			
//			if(!test.equals(against)) {
//				//does not equal, show the warning.
//				AlertDialog.Builder builder = new AlertDialog.Builder(Launcher.this);
//				builder.setMessage("Service already connected to " + test + "\nDisconnect and launch " + launch.getDisplayName() + "?");
//				builder.setTitle("Currently Connected");
//				builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
//					
//					public void onClick(DialogInterface dialog, int which) {
//						Launcher.this.unbindService(connectionChecker);
//						stopService(new Intent(com.resurrection.blowtorch2.lib.service.IConnectionBinder.class.getName()));
//						DoNewStartup();
//					}
//				});
//				
//				builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
//					
//					public void onClick(DialogInterface dialog, int which) {
//						Launcher.this.unbindService(connectionChecker);
//						dialog.dismiss();
//					}
//				});
//				AlertDialog connected = builder.create();
//				connected.show();
//			} else {
//				//are equal, proceed with normal startup.
//				Launcher.this.unbindService(connectionChecker);
//				DoFinalStartup();
//			}
//			
//		}

	

//		public void onServiceDisconnected(ComponentName arg0) {
//			
//		}
//		
//	};
	public boolean serviceConnected = false;
	private ServiceConnection connectionChecker = new ServiceConnection() {

		//private boolean connected = false;
		
		public void onServiceConnected(ComponentName arg0, IBinder arg1) {
			Launcher.this.serviceConnected = true;
			Log.e("LAUNCHER","SERVICE CONNECTED");
			service = IConnectionBinder.Stub.asInterface(arg1);
			try {
				service.registerLauncherCallback(the_callback);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			serviceBound = true;
			serviceConnected = true;
			//try {
				//connectedList = (List<String>)tmp.getConnections();
			//} catch (RemoteException e) {
				// TODO Auto-generated catch block
			//	e.printStackTrace();
			//}
			//try {
				//Launcher.this.unbindService(this);
			//} catch (IllegalArgumentException e) {
				
			//}
			buildList();
			
//			if(connectedList != null) {
//				for(int i=0;i<apdapter.getCount();i++) {
//					apdapter.getItem(i).setConnected(connectedList.contains(apdapter.getItem(i).getDisplayName()));
//				}
//			}
//			
//			apdapter.notifyDataSetInvalidated();
		}

		public void onServiceDisconnected(ComponentName name) {
			Launcher.this.serviceConnected = false;
			Log.e("LAUNCHER","SERVICE DISCONNECTED");
		}
		
		//public boolean isConnected() {
		//	return connected;
		//}
		
		
	};
	
	List<String> connectedList = null;
	
	
	public final int MSG_DELETECONNECTION = 101;
	public final int MSG_MODIFYCONNECTION = 102;
	public Handler connectionModifier = new Handler() {
		public void handleMessage(Message msg) {
			switch(msg.what) {
			case MSG_DELETECONNECTION:
				MudConnection todelete = (MudConnection)msg.obj;
				launcher_settings.getList().remove(todelete.getDisplayName());
				buildList();
				//MudConnection todelete = (MudConnection)msg.obj;
				//apdapter.remove(todelete);
				//apdapter.notifyDataSetChanged();
				break;
			case MSG_MODIFYCONNECTION:
				MudConnection tomodify = (MudConnection)msg.obj;
				NewConnectionDialog diag = new NewConnectionDialog(Launcher.this,Launcher.this,tomodify);
				diag.show();
				break;
			default:
				break;
			}
		}
	};
	
	public void ready(MudConnection newData) {
		//promote this one to the head of the class.
		Time t = new Time();
		t.set(System.currentTimeMillis());
		newData.setLastPlayed(t.format2445());
		launcher_settings.getList().put(newData.getDisplayName(), newData);
		launcherSaveEnabled = true;
		buildList();
		saveXML();
	}

    /*public void ready(String displayname,String host,String port) {
    	
    	
		MudConnection muc = new MudConnection();
		muc.setDisplayName(displayname);
		muc.setHostName(host);
		muc.setPortString(port);
		
		launcher_settings.getList().put(muc.getDisplayName(), muc.copy());
		buildList();


    	
    }*/
    
    public void modify(MudConnection old, MudConnection newData) {
    	launcher_settings.getList().remove(old.getDisplayName());
    	launcher_settings.getList().put(newData.getDisplayName(), newData);
    	launcherSaveEnabled = true;
    	buildList();
    	saveXML();
    }
    
	/*public void modify(String displayname, String host, String port,MudConnection old) {

		MudConnection muc = new MudConnection();
		muc.setDisplayName(displayname);
		muc.setHostName(host);
		muc.setPortString(port);
		
		apdapter.remove(old);
		
		apdapter.add(muc);
		apdapter.notifyDataSetChanged();
	}*/
    
	private void getConnectionsFromDisk() {
		//This is here for posterity. It will only be used to fallback.
		
		SharedPreferences pref = this.getSharedPreferences(PREFS_NAME, 0);
		
		String thestring = pref.getString("STRINGS", "");
		if(thestring == null || thestring == "") { return; }
		
		Pattern connection = Pattern.compile("([^\\|]+)");
		Pattern breakout = Pattern.compile("(.+):(.+):(.+)");
		
		Matcher c_m = connection.matcher(thestring);
		
		while(c_m.find()) {
			String operate = c_m.group(1);
			Matcher o_m = breakout.matcher(operate);
			while(o_m.find()) {
				String displayname = o_m.group(1);
				String hostname = o_m.group(2);
				String portstring = o_m.group(3);
				
				MudConnection muc = new MudConnection();
				muc.setDisplayName(displayname);
				muc.setHostName(hostname);
				muc.setPortString(portstring);
				
				apdapter.add(muc);
			}
		}
		
	}
	
	private File resolveLauncherListDirectory(boolean external) {
		return SDCardUtils.resolveBlowTorchSubdir(this, SDCardUtils.SUBDIR_LAUNCHER);
	}

	private File resolveBackupDirectory(boolean external) {
		return SDCardUtils.resolveBlowTorchSubdir(this, SDCardUtils.SUBDIR_BACKUPS);
	}

	private void DoImportMenu(boolean external) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.launcher_menu_import_server_list);
		builder.setItems(new CharSequence[] {
				"Pick file…",
				"Import from default directory"
		}, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				if (which == 0) {
					pickServerListXmlFile();
				} else {
					showDefaultDirectoryServerListImport(external);
				}
			}
		});
		builder.setNegativeButton("Cancel", null);
		builder.show();
	}

	private void pickServerListXmlFile() {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("*/*");
		intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
				"text/xml",
				"application/xml",
				"application/octet-stream"
		});
		startActivityForResult(intent, REQUEST_PICK_SERVER_LIST_XML);
	}

	private void showDefaultDirectoryServerListImport(boolean external) {
		File btermdir = resolveLauncherListDirectory(external);
		HashMap<String,String> xmlfiles = new HashMap<String,String>();
		File[] listed = btermdir.listFiles(xml_only);
		if (listed != null) {
			for (File xml : listed) {
				if (xml != null && xml.isFile()) {
					xmlfiles.put(xml.getName(), xml.getPath());
				}
			}
		}

		if (xmlfiles.isEmpty()) {
			Toast.makeText(this,
					"No server-list .xml files in:\n" + btermdir.getAbsolutePath(),
					Toast.LENGTH_LONG).show();
			return;
		}

		final String[] names = new String[xmlfiles.size()];
		final String[] entries = new String[xmlfiles.size()];
		int i = 0;
		for (String name : xmlfiles.keySet()) {
			names[i] = name;
			entries[i] = xmlfiles.get(name);
			i++;
		}

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Select server list");
		builder.setItems(names, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				actionHandler.sendMessage(actionHandler.obtainMessage(MESSAGE_IMPORT, entries[which]));
				dialog.dismiss();
			}
		});
		builder.setNegativeButton("Cancel", null);
		builder.show();
	}
	
	FilenameFilter xml_only = new FilenameFilter() {

		public boolean accept(File arg0, String arg1) {
			//return arg1.endsWith(".xml");
			xmlimatcher.reset(arg1);
			if(xmlimatcher.matches()) {
				return true;
			} else {
				return false;
			}
		}
		
	};
	
	private boolean isServiceRunning() {
		ActivityManager activityManager = (ActivityManager)Launcher.this.getSystemService(Context.ACTIVITY_SERVICE);
    	List<RunningServiceInfo> services = activityManager.getRunningServices(Integer.MAX_VALUE);
    	boolean found = false;
    	String serviceName = getApplicationContext().getPackageName() + ConfigurationLoader.getConfigurationValue("serviceProcessName",this);
    	
    	for(RunningServiceInfo service : services) {
    		//Log.e("LAUNCHER","FOUND:" + service.service.getClassName());
    		//service.service.
    		if(com.resurrection.blowtorch2.lib.service.StellarService.class.getName().equals(service.service.getClassName())) {
    			//service is running, don't do anything.
    			//Log.e(":Launcher","Service lives in: " + service.process);
    			if(service.process.equals(serviceName)) found = true;
    			/*if(mode == LAUNCH_MODE.FREE) {
    				
    				if(service.process.equals("com.resurrection.blowtorch2.lib:stellar_free")) found = true;
    			} else if(mode == LAUNCH_MODE.TEST) {
    				if(service.process.equals("com.resurrection.blowtorch2.lib:stellar_test")) found = true;
    			}*/
    			
    		} else {

    			
    		}
    	}
		return found;
	}
	
	private void DoExport(String filename, boolean external) {
		String message;
		try {
			if (filename == null) {
				filename = "";
			}
			filename = filename.trim();
			if (filename.length() == 0) {
				Toast.makeText(this, "Enter a file name first.", Toast.LENGTH_SHORT).show();
				return;
			}

			String updatedname = filename;
			Pattern xmlend = Pattern.compile("^.+\\.[Xx][Mm][Ll]$");
			Matcher xmlmatch = xmlend.matcher(updatedname);
			if (!xmlmatch.matches()) {
				updatedname = filename + ".xml";
			}

			File launcherdir = resolveLauncherListDirectory(external);
			if (!SDCardUtils.ensureWritableDirectory(launcherdir)) {
				throw new IOException("Cannot create writable folder:\n" + launcherdir.getAbsolutePath());
			}
			File file = new File(launcherdir, updatedname);

			FileWriter writer = new FileWriter(file);
			BufferedWriter tmp = new BufferedWriter(writer);
			tmp.write(LauncherSettings.writeXml(launcher_settings));
			tmp.close();

			message = "Saved server list:\n" + file.getPath();
			if (!external) {
				message += "\n(App storage — removed if the app is uninstalled.)";
			}
		} catch (Exception e) {
			Log.e("LAUNCHER", "Export server list failed", e);
			Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
			return;
		}

		View root = findViewById(R.id.launcher_window_content);
		if (root == null) {
			Toast.makeText(this, message, Toast.LENGTH_LONG).show();
			return;
		}
		Snackbar bar = Snackbar.make(root, message, Snackbar.LENGTH_INDEFINITE)
				.setAction(android.R.string.ok, new View.OnClickListener() {
					@Override
					public void onClick(View view) {
					}
				});
		View snackbarView = bar.getView();
		TextView textView = (TextView) snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
		if (textView != null) {
			textView.setMaxLines(6);
		}
		bar.show();
	}
	
	private void copySettingsFile(File source, File dest) throws IOException {
		InputStream in = new FileInputStream(source);
		OutputStream out = new FileOutputStream(dest);
		byte[] buf = new byte[1024];
		int len;
		while ((len = in.read(buf)) > 0) {
			out.write(buf, 0, len);
		}
		in.close();
		out.close();
	}
	
	private void DoBackupAllSettings() {
		AskBackupAllSettings();
	}

	private void AskBackupAllSettings() {
		final boolean external = SDCardUtils.hasStoragePermissions(this);
		final File dir = resolveBackupDirectory(external);

		AlertDialog.Builder b = new AlertDialog.Builder(this);
		b.setTitle(R.string.launcher_menu_backup_all_settings);

		LinearLayout layout = new LinearLayout(this);
		layout.setOrientation(LinearLayout.VERTICAL);
		int pad = (int) (16 * getResources().getDisplayMetrics().density);
		layout.setPadding(pad, pad / 2, pad, pad / 2);

		TextView hint = new TextView(this);
		hint.setText("Creates a zip of all session .xml settings in private storage.\n\nDefault directory:\n"
				+ dir.getAbsolutePath());
		layout.addView(hint);
		b.setView(layout);

		b.setPositiveButton("Backup to default directory", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				writeSettingsBackupZip(null);
			}
		});
		b.setNeutralButton("Choose location…", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				SimpleDateFormat stampFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US);
				pickSettingsZipCreateLocation(stampFormat.format(new Date()) + ".zip");
			}
		});
		b.setNegativeButton("Cancel", null);
		b.show();
	}

	private void writeSettingsBackupZip(File preferredZip) {
		writeSettingsBackupZip(preferredZip, true);
	}

	/**
	 * @param showDialog when false (auto backup after version bump), use a Toast instead of a
	 *                   blocking "Backup ready" dialog so startup is not alarming.
	 */
	private void writeSettingsBackupZip(File preferredZip, boolean showDialog) {
		try {
			SimpleDateFormat stampFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US);
			String stamp = stampFormat.format(new Date());
			File zipFile = preferredZip;
			if (zipFile == null) {
				boolean external = SDCardUtils.hasStoragePermissions(this);
				File backupRoot = resolveBackupDirectory(external);
				zipFile = new File(backupRoot, stamp + ".zip");
			}
			File parent = zipFile.getParentFile();
			if (!SDCardUtils.ensureWritableDirectory(parent)) {
				throw new IOException("Cannot create writable backup folder:\n"
						+ (parent != null ? parent.getAbsolutePath() : "(null)"));
			}
			int copied = zipSettingsDirectory(getFilesDir(), zipFile);
			try {
				mirrorBackupToDocuments(zipFile);
			} catch (IOException mirrorError) {
				Log.w("BlowTorch", "Optional Documents mirror failed: " + mirrorError.getMessage());
			}
			String summary = "Saved " + copied + " settings file(s).\n" + zipFile.getAbsolutePath();
			if (showDialog) {
				AlertDialog.Builder done = new AlertDialog.Builder(this);
				done.setTitle("Backup ready");
				done.setMessage(summary
						+ "\n\nAfter reinstall: Launcher ⋮ → Restore Settings Backup → pick this zip.");
				done.setPositiveButton("OK", null);
				done.show();
			} else {
				Toast.makeText(this,
						"App updated — settings backup saved (" + copied + " file(s)).\n"
								+ zipFile.getName(),
						Toast.LENGTH_LONG).show();
			}
		} catch (IOException e) {
			Log.e("LAUNCHER", "Backup failed", e);
			Toast.makeText(this, "Backup failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}

	private void pickSettingsZipCreateLocation(String suggestedName) {
		Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("application/zip");
		intent.putExtra(Intent.EXTRA_TITLE, suggestedName);
		startActivityForResult(intent, REQUEST_CREATE_SETTINGS_ZIP);
	}

	/** Optional safety copy when the installed versionName changes (skip first install). */
	private void maybeBackupBeforeUpdate() {
		SharedPreferences prefs = getSharedPreferences("BT_UPDATE_BACKUP", Context.MODE_PRIVATE);
		String last = prefs.getString("last_version", "");
		String current = "";
		try {
			current = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
		} catch (NameNotFoundException e) {
			return;
		}
		if (current == null) {
			return;
		}
		if (last.length() == 0) {
			prefs.edit().putString("last_version", current).apply();
			return;
		}
		if (current.equals(last)) {
			return;
		}
		try {
			writeSettingsBackupZip(null, false);
			prefs.edit().putString("last_version", current).apply();
		} catch (Exception e) {
			Log.w("BlowTorch", "Pre-update backup skipped: " + e.getMessage());
		}
	}
	
	private int zipSettingsDirectory(File sourceDir, File zipFile) throws IOException {
		int copied = 0;
		if (sourceDir == null || !sourceDir.isDirectory()) {
			throw new IOException("Settings directory unavailable");
		}
		ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));
		try {
			String[] names = sourceDir.list();
			if (names != null) {
				byte[] buffer = new byte[4096];
				for (String name : names) {
					if (name == null || !name.endsWith(".xml")) {
						continue;
					}
					File file = new File(sourceDir, name);
					if (!file.isFile()) {
						continue;
					}
					ZipEntry entry = new ZipEntry(name);
					zos.putNextEntry(entry);
					FileInputStream in = new FileInputStream(file);
					try {
						int len;
						while ((len = in.read(buffer)) > 0) {
							zos.write(buffer, 0, len);
						}
					} finally {
						in.close();
					}
					zos.closeEntry();
					copied++;
				}
			}
		} finally {
			zos.close();
		}
		return copied;
	}
	
	private void mirrorBackupToDocuments(File zipFile) throws IOException {
		File docsRoot = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "BlowTorch2/backups");
		if (!docsRoot.mkdirs() && !docsRoot.exists()) {
			return;
		}
		copySettingsFile(zipFile, new File(docsRoot, zipFile.getName()));
	}

	private static class SettingsImportCandidate {
		final File file;
		final String label;

		SettingsImportCandidate(File file, String label) {
			this.file = file;
			this.label = label;
		}
	}

	private void collectSettingsImportCandidates(ArrayList<File> choices, ArrayList<String> labels) {
		boolean external = SDCardUtils.hasStoragePermissions(this);
		File defaultBackup = resolveBackupDirectory(external);
		File sd = Environment.getExternalStorageDirectory();
		File[] scanRoots = new File[] {
				defaultBackup,
				new File(sd, "BlowTorch2/backups"),
				new File(sd, "BlowTorch/backups"),
				new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "BlowTorch2/backups"),
				new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "BlowTorch2"),
				Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
		};
		java.util.HashSet<String> seen = new java.util.HashSet<String>();
		ArrayList<SettingsImportCandidate> candidates = new ArrayList<SettingsImportCandidate>();
		for (File root : scanRoots) {
			if (root == null || !root.exists()) {
				continue;
			}
			boolean allowFolders = root.getAbsolutePath().contains("BlowTorch2");
			File[] entries = root.listFiles();
			if (entries == null) {
				continue;
			}
			for (File entry : entries) {
				if (entry.isDirectory()) {
					if (!allowFolders || entry.getName().endsWith(".zip")) {
						continue;
					}
					String[] inner = entry.list();
					if (inner != null && inner.length > 0) {
						boolean hasXml = false;
						for (String name : inner) {
							if (name.endsWith(".xml")) {
								hasXml = true;
								break;
							}
						}
						if (hasXml) {
							String key = entry.getAbsolutePath();
							if (seen.add(key)) {
								candidates.add(new SettingsImportCandidate(entry,
										"[folder] " + root.getName() + "/" + entry.getName()));
							}
						}
					}
					continue;
				}
				if (!entry.getName().endsWith(".zip")) {
					continue;
				}
				String key = entry.getAbsolutePath();
				if (seen.add(key)) {
					candidates.add(new SettingsImportCandidate(entry, root.getName() + "/" + entry.getName()));
				}
			}
		}
		java.util.Collections.sort(candidates, new Comparator<SettingsImportCandidate>() {
			public int compare(SettingsImportCandidate a, SettingsImportCandidate b) {
				return Long.signum(b.file.lastModified() - a.file.lastModified());
			}
		});
		for (SettingsImportCandidate candidate : candidates) {
			choices.add(candidate.file);
			labels.add(candidate.label);
		}
	}

	private void AskImportSettings() {
		final ArrayList<File> choices = new ArrayList<File>();
		final ArrayList<String> labels = new ArrayList<String>();
		collectSettingsImportCandidates(choices, labels);
		labels.add("Browse for .zip file…");
		choices.add(null);
		if (labels.size() == 1) {
			pickSettingsZipFile();
			return;
		}
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.launcher_menu_restore_settings_backup);
		builder.setItems(labels.toArray(new String[labels.size()]), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				File selected = choices.get(which);
				if (selected == null) {
					pickSettingsZipFile();
					return;
				}
				try {
					int restored = restoreBackup(selected);
					Toast.makeText(Launcher.this,
							"Restored " + restored + " file(s). Restart BlowTorch to reload settings.",
							Toast.LENGTH_LONG).show();
				} catch (IOException e) {
					Toast.makeText(Launcher.this, "Restore failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
				}
			}
		});
		builder.setNegativeButton("Cancel", null);
		builder.show();
	}

	private void pickSettingsZipFile() {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("*/*");
		intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
				"application/zip",
				"application/x-zip-compressed",
				"application/octet-stream"
		});
		startActivityForResult(intent, REQUEST_PICK_SETTINGS_ZIP);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode != Activity.RESULT_OK || data == null) {
			return;
		}
		Uri uri = data.getData();
		if (uri == null) {
			return;
		}
		try {
			if (requestCode == REQUEST_PICK_SETTINGS_ZIP) {
				int restored = restoreBackupFromUri(uri);
				Toast.makeText(this,
						"Restored " + restored + " file(s). Restart BlowTorch to reload settings.",
						Toast.LENGTH_LONG).show();
			} else if (requestCode == REQUEST_PICK_SERVER_LIST_XML) {
				importServerListFromUri(uri);
			} else if (requestCode == REQUEST_CREATE_SERVER_LIST_XML) {
				exportServerListToUri(uri);
			} else if (requestCode == REQUEST_CREATE_SETTINGS_ZIP) {
				File tempZip = new File(SDCardUtils.resolveCacheDir(this), "export_settings_backup.zip");
				int copied = zipSettingsDirectory(getFilesDir(), tempZip);
				OutputStream out = getContentResolver().openOutputStream(uri);
				if (out == null) {
					Toast.makeText(this, "Could not write selected location.", Toast.LENGTH_LONG).show();
					return;
				}
				InputStream in = new FileInputStream(tempZip);
				byte[] buffer = new byte[4096];
				int len;
				while ((len = in.read(buffer)) > 0) {
					out.write(buffer, 0, len);
				}
				in.close();
				out.close();
				Toast.makeText(this, "Backup saved (" + copied + " file(s)).", Toast.LENGTH_LONG).show();
			}
		} catch (IOException e) {
			Toast.makeText(this, "Operation failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}

	private void importServerListFromUri(Uri uri) throws IOException {
		InputStream in = getContentResolver().openInputStream(uri);
		if (in == null) {
			throw new IOException("Could not open selected file");
		}
		File temp = new File(SDCardUtils.resolveCacheDir(this), "import_server_list.xml");
		OutputStream out = new FileOutputStream(temp);
		byte[] buffer = new byte[4096];
		int len;
		while ((len = in.read(buffer)) > 0) {
			out.write(buffer, 0, len);
		}
		in.close();
		out.close();
		actionHandler.sendMessage(actionHandler.obtainMessage(MESSAGE_IMPORT, temp.getAbsolutePath()));
	}

	private void exportServerListToUri(Uri uri) throws IOException {
		OutputStream out = getContentResolver().openOutputStream(uri);
		if (out == null) {
			throw new IOException("Could not write selected location");
		}
		byte[] data = LauncherSettings.writeXml(launcher_settings).getBytes("UTF-8");
		out.write(data);
		out.close();
		Toast.makeText(this, "Server list exported.", Toast.LENGTH_LONG).show();
	}

	private int restoreBackupFromUri(Uri uri) throws IOException {
		InputStream in = getContentResolver().openInputStream(uri);
		if (in == null) {
			throw new IOException("Could not open selected file");
		}
		File tempZip = new File(SDCardUtils.resolveCacheDir(this), "import_settings.zip");
		OutputStream out = new FileOutputStream(tempZip);
		byte[] buffer = new byte[4096];
		int len;
		while ((len = in.read(buffer)) > 0) {
			out.write(buffer, 0, len);
		}
		in.close();
		out.close();
		return restoreZipBackup(tempZip);
	}
	
	private int restoreBackup(File backup) throws IOException {
		if (backup.isDirectory()) {
			return restoreDirectoryBackup(backup);
		}
		return restoreZipBackup(backup);
	}
	
	private int restoreDirectoryBackup(File backupDir) throws IOException {
		int restored = 0;
		String[] names = backupDir.list();
		if (names == null) {
			return 0;
		}
		for (String name : names) {
			if (!name.endsWith(".xml")) {
				continue;
			}
			copySettingsFile(new File(backupDir, name), new File(getFilesDir(), name));
			restored++;
		}
		return restored;
	}
	
	private int restoreZipBackup(File zipFile) throws IOException {
		int restored = 0;
		ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
		ZipEntry entry;
		byte[] buffer = new byte[4096];
		while ((entry = zis.getNextEntry()) != null) {
			if (entry.isDirectory() || !entry.getName().endsWith(".xml")) {
				zis.closeEntry();
				continue;
			}
			File out = new File(getFilesDir(), new File(entry.getName()).getName());
			FileOutputStream fos = new FileOutputStream(out);
			int len;
			while ((len = zis.read(buffer)) > 0) {
				fos.write(buffer, 0, len);
			}
			fos.close();
			zis.closeEntry();
			restored++;
		}
		zis.close();
		return restored;
	}
	
	private void DoWhatsNew() throws NameNotFoundException { 
		
		//get the version information.
		PackageManager m = this.getPackageManager();
		String versionString = null;
		try {
			versionString = m.getPackageInfo(launcher_source, PackageManager.GET_CONFIGURATIONS).versionName;
		} catch (NameNotFoundException e) {
			//can't execute on our package aye?
			throw new RuntimeException(e);
		}
		
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		
		if(ConfigurationLoader.isTestMode(this)) {
			builder.setTitle("Version " + versionString + " details!");
			
			final SpannableString s = new SpannableString(Launcher.this.getResources().getString(R.string.whatisnew_test));
		    Linkify.addLinks(s, Linkify.ALL);
	
			builder.setMessage(s);
		} else {
			builder.setTitle("Version " + versionString + " details!");
			
			final SpannableString s = new SpannableString(Launcher.this.getResources().getString(R.string.whatisnew));
		    Linkify.addLinks(s, Linkify.ALL);
	
			builder.setMessage(s);
		}
		builder.setPositiveButton("Dismiss", new DialogInterface.OnClickListener() {
			
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		
		AlertDialog diag = builder.create();
		
		//TextView message = (TextView) diag.findViewById(android.R.id.message);
		
		diag.show();
		
		((TextView)diag.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());

		
	}
	
	private void DoNewStartup() {
		Pattern invalidchars = Pattern.compile("\\W"); 
		Matcher replacebadchars = invalidchars.matcher(launch.getDisplayName());
		String prefsname = replacebadchars.replaceAll("") + ".PREFS";
		//prefsname = prefsname.replaceAll("/", "");
		
		SharedPreferences sprefs = Launcher.this.getSharedPreferences(prefsname,0);
		//servicestarted = prefs.getBoolean("CONNECTED", false);
		//finishStart = prefs.getBoolean("FINISHSTART", true);
		SharedPreferences.Editor editor = sprefs.edit();
		editor.putBoolean("CONNECTED", false);
		editor.putBoolean("FINISHSTART", true);
		editor.commit();
		//Log.e("LAUNCHER","SERVICE NOT STARTED, AM RESETTING THE INITIALIZER BOOLS IN " + prefsname);
		
		//Launcher.this.startActivity(the_intent);
		//SharedPreferences sprefs = Launcher.this.getSharedPreferences(prefsname,0);
		//SharedPreferences.Editor editor = sprefs.edit();
		//editor.putBoolean("CONNECTED", false);
		//editor.putBoolean("FINISHSTART", true);
		editor.commit();
		
		
		//launch = muc;
		DoFinalStartup();
	}
	
	private void DoFinalStartup() {
		Intent the_intent = null;
		/*if(mode == LAUNCH_MODE.TEST) {
			the_intent = new Intent("com.resurrection.blowtorch2.lib.window.MainWindow.TEST_MODE");
		} else {
			//Log.e("BlowTorch","LAUNCHING NORMAL MODE!");
			the_intent = new Intent("com.resurrection.blowtorch2.lib.window.MainWindow.NORMAL_MODE");
		}*/
		
		String windowAction = ConfigurationLoader.getConfigurationValue("windowAction",this);
		the_intent = new Intent(windowAction);
		the_intent.setClass(this, MainWindow.class);
    	the_intent.putExtra("DISPLAY",launch.getDisplayName());
    	the_intent.putExtra("HOST", launch.getHostName());
    	the_intent.putExtra("PORT", launch.getPortString());
    	
    	//write out the intent to the service so it can do some lookup work in advance of the connection, such as loading the settings wad
    	//SharedPreferences prefs = Launcher.this.getSharedPreferences("SERVICE_INFO",0);
    	//Editor edit = prefs.edit();
    	//Log.e("WINDOW","SETTING " + muc.getDisplayName());
    	
    	
    	//edit.putString("SETTINGS_PATH", launch.getDisplayName());
    	//edit.commit();
		//Pattern invalidchars = Pattern.compile("\\W"); 
		//Matcher replacebadchars = invalidchars.matcher(launch.getDisplayName());
		//String prefsname = replacebadchars.replaceAll("") + ".PREFS";
		///prefsname = prefsname.replaceAll("/", "");
		
		
		//Log.e("LAUNCHER","SERVICE NOT STARTED, AM RESETTING THE INITIALIZER BOOLS IN " + prefsname);
		
    	
    	SharedPreferences prefs = Launcher.this.getSharedPreferences("SERVICE_INFO",0);
    	Editor edit = prefs.edit();
    	
    	
    	edit.putString("SETTINGS_PATH", launch.getDisplayName());
    	edit.commit();
    	
    	//this.unbindService(connectionChecker);
    	
		Launcher.this.startActivity(the_intent);
	}
	
	private ConnectionComparator ccmp = new ConnectionComparator();
	
	private void buildList() {
		apdapter.clear();
		
		
		for(MudConnection m : launcher_settings.getList().values()) {
			
			
			apdapter.add(m);
		}
		
		apdapter.sort(ccmp);
		
		if(serviceBound) {
			try {
				connectedList = (List<String>)service.getConnections();
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
			}
			if(connectedList != null) {
				for(int i=0;i<apdapter.getCount();i++) {
					apdapter.getItem(i).setConnected(connectedList.contains(apdapter.getItem(i).getDisplayName()));
				}	
			}
		}
		
		//String action = ConfigurationLoader.getConfigurationValue("serviceBindAction",Launcher.this);
		//if(!serviceConnected) {
			//this.startService(new Intent(action));
			//fgds
		//} else {
			
		//}
		apdapter.notifyDataSetChanged();
		//this.bindService(service, conn, flags)
	}
	
	private LauncherSettings tryRecoverLauncherList() {
		String sdRoot = SDCardUtils.getSDCardRoot(this, true);
		File storageRoot = Environment.getExternalStorageDirectory();
		File[] candidates = new File[] {
				new File(storageRoot, sdRoot + "/recovered/blowtorch_launcher_list.xml"),
				new File(storageRoot, sdRoot + "/launcher/blowtorch_launcher_list.xml"),
		};
		for (File candidate : candidates) {
			if (!candidate.exists()) {
				continue;
			}
			LauncherSAXParser parser = new LauncherSAXParser(candidate.getAbsolutePath(), this);
			LauncherSettings settings = parser.load();
			if (settings != null && !settings.getList().isEmpty()) {
				return settings;
			}
		}
		File launcherDir = new File(storageRoot, sdRoot + "/launcher/");
		if (launcherDir.isDirectory()) {
			File[] files = launcherDir.listFiles(xml_only);
			if (files != null) {
				for (File xml : files) {
					LauncherSAXParser parser = new LauncherSAXParser(xml.getAbsolutePath(), this);
					LauncherSettings settings = parser.load();
					if (settings != null && !settings.getList().isEmpty()) {
						return settings;
					}
				}
			}
		}
		return null;
	}

	private void backupLauncherListFile() {
		File current = new File(getFilesDir(), "blowtorch_launcher_list.xml");
		if (!current.exists()) {
			return;
		}
		File backup = new File(getFilesDir(), "blowtorch_launcher_list.xml.bak");
		try {
			InputStream in = new FileInputStream(current);
			OutputStream out = new FileOutputStream(backup);
			byte[] buffer = new byte[1024];
			int read;
			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
			in.close();
			out.close();
		} catch (IOException e) {
			Log.e("BLOWTORCH", "Failed to back up launcher list", e);
		}
	}

	private void saveXML() {
		if (!launcherSaveEnabled) {
			return;
		}
		try {
			backupLauncherListFile();
			FileOutputStream fos = this.openFileOutput("blowtorch_launcher_list.xml",Context.MODE_PRIVATE);
			fos.write(LauncherSettings.writeXml(launcher_settings).getBytes("UTF-8"));
			fos.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private class ConnectionComparator implements Comparator<MudConnection> {

		public int compare(MudConnection a, MudConnection b) {
			//pos it above, negative if below
			Time at = new Time();
			Time bt = new Time();
			
			//check if either have haver been played.
			if(a.getLastPlayed().equals("never")) {
				return 1;
			} else if(b.getLastPlayed().equals("never")) {
				return -1;
			} else if(b.getLastPlayed().equals("never") && a.getLastPlayed().equals("never")){
				return 0; //they are both never, so they are equal.
			}
			
			try{
				
				at.parse(a.getLastPlayed());
				bt.parse(b.getLastPlayed());
			} catch (TimeFormatException e) {
				return 0;
			}
			return Time.compare(bt, at);
		}
		
	}
	
	public boolean onCreateOptionsMenu(Menu menu) {

		menu.add(0, MENU_IMPORT_SERVER_LIST, 0, R.string.launcher_menu_import_server_list);
		menu.add(0, MENU_EXPORT_SERVER_LIST, 0, R.string.launcher_menu_export_server_list);
		menu.add(0, MENU_BACKUP_ALL_SETTINGS, 0, R.string.launcher_menu_backup_all_settings);
		menu.add(0, MENU_RESTORE_SETTINGS_BACKUP, 0, R.string.launcher_menu_restore_settings_backup);
		if (ConfigurationLoader.isTestMode(this)) {
			menu.add(0, MENU_USER_NAME, 0, "User Name");
		}
		menu.add(0, MENU_SDCARD_PERMISSIONS, 0, "SDCard Permissions");
		menu.add(0, MENU_APP_SETTINGS, 0, "App Settings");
		menu.add(0, MENU_ABOUT, 0, R.string.launcher_menu_about);

		return true;

	}

	private void AskExportFileName(final boolean external) {
		final File dir = resolveLauncherListDirectory(external);

		AlertDialog.Builder b = new AlertDialog.Builder(this);
		b.setTitle(R.string.launcher_menu_export_server_list);

		LinearLayout layout = new LinearLayout(this);
		layout.setOrientation(LinearLayout.VERTICAL);
		int pad = (int) (16 * getResources().getDisplayMetrics().density);
		layout.setPadding(pad, pad / 2, pad, pad / 2);

		TextView hint = new TextView(this);
		hint.setText("Default directory:\n" + dir.getAbsolutePath());
		layout.addView(hint);

		entry = new EditText(this);
		entry.setHint("Enter file name");
		entry.setSingleLine(true);
		layout.addView(entry);
		b.setView(layout);

		b.setPositiveButton("Export to default directory", null);
		b.setNeutralButton("Choose location…", null);
		b.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				dialog.dismiss();
			}
		});

		final AlertDialog exporter = b.create();
		exporter.setOnShowListener(new DialogInterface.OnShowListener() {
			@Override
			public void onShow(DialogInterface dialog) {
				exporter.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						String name = entry.getText() != null ? entry.getText().toString().trim() : "";
						if (TextUtils.isEmpty(name)) {
							Toast.makeText(Launcher.this, "Enter a file name first.", Toast.LENGTH_SHORT).show();
							return;
						}
						DoExport(name, external);
						exporter.dismiss();
					}
				});
				exporter.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						String name = entry.getText() != null ? entry.getText().toString().trim() : "";
						if (TextUtils.isEmpty(name)) {
							name = "blowtorch_launcher_list.xml";
						}
						xmlimatcher.reset(name);
						if (!xmlimatcher.matches()) {
							name = name + ".xml";
						}
						pickExportServerListFile(name);
						exporter.dismiss();
					}
				});
			}
		});
		exporter.show();
	}

	private void pickExportServerListFile(String suggestedName) {
		Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("application/xml");
		intent.putExtra(Intent.EXTRA_TITLE, suggestedName);
		startActivityForResult(intent, REQUEST_CREATE_SERVER_LIST_XML);
	}

	private EditText entry = null;

	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case MENU_IMPORT_SERVER_LIST:
			SDCardUtils.hasPermissions(this, findViewById(R.id.launcher_window_content), RP_IMPORT, new Runnable() {
				@Override
				public void run() {
					DoImportMenu(SDCardUtils.hasStoragePermissions(Launcher.this));
				}
			});
			break;
		case MENU_EXPORT_SERVER_LIST:
			SDCardUtils.hasPermissions(this, findViewById(R.id.launcher_window_content), RP_EXPORT, new Runnable() {
				@Override
				public void run() {
					AskExportFileName(SDCardUtils.hasStoragePermissions(Launcher.this));
				}
			});
			break;
		case MENU_BACKUP_ALL_SETTINGS:
			SDCardUtils.hasPermissions(this, findViewById(R.id.launcher_window_content), RP_EXPORT, new Runnable() {
				@Override
				public void run() {
					AskBackupAllSettings();
				}
			});
			break;
		case MENU_RESTORE_SETTINGS_BACKUP:
			SDCardUtils.hasPermissions(this, findViewById(R.id.launcher_window_content), RP_IMPORT, new Runnable() {
				@Override
				public void run() {
					AskImportSettings();
				}
			});
			break;
		case MENU_USER_NAME:

			break;
		case MENU_SDCARD_PERMISSIONS:
			boolean state = SDCardUtils.hasPermissions(this,findViewById(R.id.launcher_window_content), RP_INFO);
			if(state == true) {
				showPermissionsMessage(true);
			}
			break;
		case MENU_APP_SETTINGS:
			Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			intent.setData(Uri.parse("package:" + Launcher.this.getPackageName()));
			Launcher.this.startActivity(intent);
			break;
		case MENU_ABOUT:
			showAboutDialog();
			break;

		default:
			break;
		}

		return true;
	}
	
	
    
	private class ConnectionAdapter extends ArrayAdapter<MudConnection> {
		private ArrayList<MudConnection> items;
		private int textcolor;
		public ConnectionAdapter(Context context, int txtviewresid, ArrayList<MudConnection> objects) {
			super(context, txtviewresid, objects);
			
			this.items = objects;
		}
		
		public View getView(int position, View convertView, ViewGroup parent) {
			View v = convertView;
			if(v == null) {
				LayoutInflater li = (LayoutInflater)this.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = li.inflate(R.layout.connection_row, null);
				//TextView title = (TextView)v.findViewById(R.id.displayname);
				//textcolor = title.getText
			}
			
			MudConnection m = items.get(position);
			if(m != null) {
				TextView title = (TextView)v.findViewById(R.id.displayname);
				TextView host = (TextView)v.findViewById(R.id.hoststring);
				//TextView port = (TextView)v.findViewById(R.id.port);
				if(title != null) {
					title.setText(" " + m.getDisplayName());
				}
				if(host != null) {
					String hostLine = "\t"  + m.getHostName() + ":" + m.getPortString();
					if (service != null) {
						try {
							String dur = service.getConnectionDurationText(m.getDisplayName());
							if (dur != null && dur.length() > 0) {
								if (m.isConnected()) {
									hostLine = hostLine + "  ·  "
											+ getString(R.string.launcher_connected_duration, dur);
								} else {
									hostLine = hostLine + "  ·  "
											+ getString(R.string.launcher_last_duration, dur);
								}
							}
						} catch (RemoteException e) {
							e.printStackTrace();
						}
					}
					host.setText(hostLine);
				}
				
				if(m.isConnected()) {
					title.setTextColor(0xFF66FF66);
				} else {
					title.setTextColor(0xEEF5F5F5);
				}
				//if(port != null) {
				//	port.setText(" Port: " + m.getPortString());
				//}
			}
			return v;
			
			
		}
		
		
	}
	
	ILauncherCallback the_callback = new ILauncherCallback.Stub() {

		@Override
		public void connectionDisconnected() throws RemoteException {
			Launcher.this.runOnUiThread(new Runnable() {

				@Override
				public void run() {
					Launcher.this.buildList();
				}
				
			});
		}


		
	};

	private void showImportMessage(final boolean external) {
		String dir = SDCardUtils.getSDCardRoot(this,external);
		String message = (external == true) ? String.format(getString(R.string.launcher_import_granted),dir) : String.format(getString(R.string.launcher_import_denied),dir);
		Snackbar bar = Snackbar.make(findViewById(R.id.launcher_window_content), message,
				Snackbar.LENGTH_INDEFINITE)
				.setAction(android.R.string.ok,new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						DoImportMenu(external);
					}});

		View snackbarView = bar.getView();
		TextView textView = (TextView) snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
		textView.setMaxLines(5);  // show multiple line
		bar.show();
	}

	private void showPermissionsMessage(boolean granted) {
		File rootDir = SDCardUtils.resolveBlowTorchRoot(this);
		String dir = rootDir.getAbsolutePath();
		String message = granted
				? String.format(getString(R.string.sd_perm_granted), dir)
				: String.format(getString(R.string.sd_perm_denies), dir);
		if (SDCardUtils.needsAllFilesAccessPrompt()) {
			message = "Grant All files access for /BlowTorch/ (settings, backups, logs). Opening system settings…";
			SDCardUtils.openAllFilesAccessSettings(this);
		} else if (granted) {
			SDCardUtils.resolveBlowTorchRoot(this);
		}
		Snackbar bar = Snackbar.make(findViewById(R.id.launcher_window_content), message,
				Snackbar.LENGTH_INDEFINITE)
				.setAction(android.R.string.ok,new View.OnClickListener() {
					@Override
					public void onClick(View view) {

					}});

		View snackbarView = bar.getView();
		TextView textView = (TextView) snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
		textView.setMaxLines(5);  // show multiple line
		bar.show();
	}

	private void showStartupPermissionsResult() {
		String storageState = SDCardUtils.hasStoragePermissions(this) ? "granted" : "denied";
		Toast.makeText(this, getString(R.string.startup_permissions_result, storageState), Toast.LENGTH_LONG).show();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions,
										   int[] grantResults) {
		final View root = findViewById(R.id.launcher_window_content);
		final boolean external = SDCardUtils.hasStoragePermissions(this);
		final int featureRes = PermissionHelper.featureMessageForRequestCode(requestCode);

		switch(requestCode) {
			case RP_STARTUP:
				PermissionHelper.handlePermissionResult(this, root, requestCode, RP_STARTUP, permissions,
						grantResults, featureRes, new Runnable() {
					@Override
					public void run() {
						showStartupPermissionsResult();
					}
				}, new Runnable() {
					@Override
					public void run() {
						showStartupPermissionsResult();
					}
				});
				break;
			case RP_INFO:
				PermissionHelper.handlePermissionResult(this, root, requestCode, RP_INFO, permissions,
						grantResults, featureRes, new Runnable() {
					@Override
					public void run() {
						showPermissionsMessage(external);
					}
				}, null);
				break;
			case RP_EXPORT:
				PermissionHelper.handlePermissionResult(this, root, requestCode, RP_EXPORT, permissions,
						grantResults, featureRes, new Runnable() {
					@Override
					public void run() {
						AskExportFileName(external);
					}
				}, null);
				break;
			case RP_IMPORT:
				PermissionHelper.handlePermissionResult(this, root, requestCode, RP_IMPORT, permissions,
						grantResults, featureRes, new Runnable() {
					@Override
					public void run() {
						DoImportMenu(external);
					}
				}, null);
				break;
			default:
				break;
		}
	}

}
