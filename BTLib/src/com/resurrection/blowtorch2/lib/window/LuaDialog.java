package com.resurrection.blowtorch2.lib.window;

import com.resurrection.blowtorch2.lib.R;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.util.Log;
import android.widget.RelativeLayout.LayoutParams;

public class LuaDialog extends Dialog {

	private static final String TAG = "LuaDialog";
	private View mView = null;
	private Context mContext = null;
	private boolean mTitle;
	private Drawable mBorder;;
	
	public LuaDialog(Context context) {
		super(context);
		// TODO Auto-generated constructor stub
	}
	
	public LuaDialog(Context context,View v,boolean title,Drawable border) {
		super(context,android.R.style.Theme_Black);
		mContext = context;
		mView = v;
		mTitle = title;
		mBorder = border;
		
		
		
		
		//this.setCont
	}

	private boolean canShow() {
		if (!(mContext instanceof Activity)) {
			return true;
		}
		Activity activity = (Activity) mContext;
		if (activity.isFinishing()) {
			return false;
		}
		return Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !activity.isDestroyed();
	}
	
	@Override
	public void show() {
		if (!canShow()) {
			return;
		}
		try {
			super.show();
		} catch (WindowManager.BadTokenException e) {
			Log.w(TAG, "Unable to show dialog; activity token is stale", e);
		}
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		if(!mTitle) {
			this.requestWindowFeature(Window.FEATURE_NO_TITLE);
			//this.getWindow().setFla
		}
		//this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
		if(mBorder != null) {
			this.getWindow().setBackgroundDrawable(mBorder);
		} else {
			this.getWindow().setBackgroundDrawableResource(com.resurrection.blowtorch2.lib.R.drawable.dialog_window_crawler1);
		}
		
		//Window w = this.getWindow();
		
		//WindowManager.LayoutParams wparams = w.getAttributes();
		//params
		//wparams.height = WindowManager.LayoutParams.WRAP_CONTENT;
		//wparams.width = WindowManager.LayoutParams.WRAP_CONTENT;
		
		
		//w.setAttributes(wparams);
		//mView = v;	
		
		//ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
		//mView.setLayoutParams(params);
		//mView.setScrollContainer(false);
		
		MainWindow w = (MainWindow)mContext;
		if(w.isStatusBarHidden()) {
			this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
		
		// Selection-style content (aliases/triggers/timers/button sets) should use full width.
		this.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
		//this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
		if(mView.getLayoutParams() != null) {
			//LayoutParams tmp = (LayoutParams) mView.getLayoutParams();
			//ViewGroup.LayoutParams p = new ViewGroup.LayoutParams(tmp.width, tmp.height);
			//this.setContentView(mView,mView.getLayoutParams());
			
			this.setContentView(mView,mView.getLayoutParams());
		} else {
			this.setContentView(mView);
		}
	}
	
	
	
	
}
