package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
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
	public void overflowMenuSitsAboveDividerInWindowContainer() {
		Context context = new ContextThemeWrapper(
				ApplicationProvider.getApplicationContext(),
				androidx.appcompat.R.style.Theme_AppCompat);
		View root = LayoutInflater.from(context).inflate(R.layout.window_layout, null, false);
		View container = root.findViewById(R.id.window_container);
		View fabStrip = root.findViewById(R.id.gameplay_fab_strip);
		View overflow = root.findViewById(R.id.overflow_menu);
		View inputbar = root.findViewById(R.id.inputbar);
		View divider = root.findViewById(R.id.divider);
		assertNotNull(container);
		assertNotNull(fabStrip);
		assertNotNull(overflow);
		assertNotNull(inputbar);
		assertNotNull(divider);
		assertTrue(fabStrip.getParent() == container);
		assertTrue(overflow.getParent() == fabStrip);
		RelativeLayout.LayoutParams stripLp = (RelativeLayout.LayoutParams) fabStrip.getLayoutParams();
		assertTrue(stripLp.getRule(RelativeLayout.ABOVE) == divider.getId());
		assertTrue(stripLp.getRule(RelativeLayout.ALIGN_PARENT_END) == RelativeLayout.TRUE
				|| stripLp.getRule(RelativeLayout.ALIGN_PARENT_RIGHT) == RelativeLayout.TRUE);
		RelativeLayout.LayoutParams inputLp = (RelativeLayout.LayoutParams) inputbar.getLayoutParams();
		assertTrue(inputLp.getRule(RelativeLayout.ALIGN_PARENT_BOTTOM) == RelativeLayout.TRUE);
	}
}
