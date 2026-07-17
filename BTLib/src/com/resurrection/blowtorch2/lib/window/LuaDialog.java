package com.resurrection.blowtorch2.lib.window;

import com.resurrection.blowtorch2.lib.R;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.util.Log;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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
		super(context, R.style.BlowTorch_Dialog_FullScreen);
		mContext = context;
		mView = v;
		mTitle = title;
		mBorder = border;
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
		}
		if(mBorder != null) {
			this.getWindow().setBackgroundDrawable(mBorder);
		} else {
			this.getWindow().setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
		}
		
		MainWindow w = (MainWindow)mContext;
		if(w.isStatusBarHidden()) {
			this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
		
		Window window = this.getWindow();
		if (window != null) {
			window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
			WindowManager.LayoutParams attrs = window.getAttributes();
			attrs.width = WindowManager.LayoutParams.MATCH_PARENT;
			attrs.height = WindowManager.LayoutParams.MATCH_PARENT;
			attrs.gravity = Gravity.FILL;
			window.setAttributes(attrs);
		}

		// Force full-bleed content even when inflate kept tablet fixed sizes.
		ViewGroup.LayoutParams contentLp = mView.getLayoutParams();
		if (contentLp == null) {
			contentLp = new ViewGroup.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.MATCH_PARENT);
		} else {
			contentLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
			contentLp.height = ViewGroup.LayoutParams.MATCH_PARENT;
		}
		this.setContentView(mView, contentLp);

		ViewCompat.setOnApplyWindowInsetsListener(mView, (view, insets) -> {
			Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			view.setPadding(view.getPaddingLeft(), sys.top,
					view.getPaddingRight(), sys.bottom);
			return insets;
		});
		ViewCompat.requestApplyInsets(mView);
	}
}
