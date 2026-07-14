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
	public void overflowMenuIsAnchoredAboveInputBar() {
		Context context = new ContextThemeWrapper(
				ApplicationProvider.getApplicationContext(),
				androidx.appcompat.R.style.Theme_AppCompat);
		View root = LayoutInflater.from(context).inflate(R.layout.window_layout, null, false);
		View overflow = root.findViewById(R.id.overflow_menu);
		View inputbar = root.findViewById(R.id.inputbar);
		assertNotNull(overflow);
		assertNotNull(inputbar);
		RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) overflow.getLayoutParams();
		assertTrue(params.getRule(RelativeLayout.ABOVE) == inputbar.getId());
		int endRule = params.getRule(RelativeLayout.ALIGN_PARENT_END);
		int rightRule = params.getRule(RelativeLayout.ALIGN_PARENT_RIGHT);
		assertTrue(endRule == RelativeLayout.TRUE || rightRule == RelativeLayout.TRUE);
	}
}
