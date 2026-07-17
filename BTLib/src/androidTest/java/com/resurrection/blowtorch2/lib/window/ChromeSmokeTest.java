package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.resurrection.blowtorch2.lib.R;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ChromeSmokeTest {

	@Test
	public void overflowMenuLivesInBottomChromeOverlay() {
		Context context = new ContextThemeWrapper(
				ApplicationProvider.getApplicationContext(),
				androidx.appcompat.R.style.Theme_AppCompat);
		View root = LayoutInflater.from(context).inflate(R.layout.window_layout, null, false);
		View overlay = root.findViewById(R.id.gameplay_chrome_overlay);
		View fabStrip = root.findViewById(R.id.gameplay_fab_strip);
		View overflow = root.findViewById(R.id.overflow_menu);
		View inputbar = root.findViewById(R.id.inputbar);
		View divider = root.findViewById(R.id.divider);
		assertNotNull(overlay);
		assertNotNull(fabStrip);
		assertNotNull(overflow);
		assertNotNull(inputbar);
		assertNotNull(divider);
		assertTrue(fabStrip.getParent() == overlay);
		assertTrue(overflow.getParent() == fabStrip);
		assertTrue(divider.getParent() == inputbar);
		FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) fabStrip.getLayoutParams();
		int gravity = params.gravity;
		assertTrue((gravity & Gravity.BOTTOM) != 0);
		assertTrue((gravity & Gravity.END) != 0 || (gravity & Gravity.RIGHT) != 0);
		RelativeLayout.LayoutParams inputLp = (RelativeLayout.LayoutParams) inputbar.getLayoutParams();
		assertTrue(inputLp.getRule(RelativeLayout.ALIGN_PARENT_BOTTOM) == RelativeLayout.TRUE);
	}
}
