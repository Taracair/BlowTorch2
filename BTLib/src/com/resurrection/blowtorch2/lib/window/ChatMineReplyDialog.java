package com.resurrection.blowtorch2.lib.window;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.chat.ChatStore;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * ⚙ submenu for My lines (several forms) and Reply. The drawer itself only
 * shows a one-line summary.
 */
final class ChatMineReplyDialog {

	interface Listener {
		void onSaved(String mineNeedle, String replyTemplate, int colorArgb);
	}

	private ChatMineReplyDialog() {
	}

	static void show(final Context context, final String mineStored,
			final String replyShown, final int colorArgb, final boolean focusReply,
			final Listener listener) {
		if (context == null || listener == null) {
			return;
		}
		final Dialog dialog = new Dialog(context, EditorDialogChrome.dialogTheme());
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		Window window = dialog.getWindow();
		if (window != null) {
			window.setBackgroundDrawableResource(R.drawable.dialog_window_crawler1);
			window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
					| WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
		}

		float d = context.getResources().getDisplayMetrics().density;
		int titleHeight = Math.round(42 * d);
		int pad = Math.round(12 * d);
		int minButton = Math.round(44 * d);
		int body = ContextCompat.getColor(context, R.color.chrome_body);
		int titleInk = ContextCompat.getColor(context, R.color.chrome_title_text);
		int bar = ContextCompat.getColor(context, R.color.chrome_title_bar);
		int desc = ContextCompat.getColor(context, R.color.chrome_description);

		LinearLayout shell = new LinearLayout(context);
		shell.setOrientation(LinearLayout.VERTICAL);
		shell.setBackgroundColor(body);

		LinearLayout titleRow = new LinearLayout(context);
		titleRow.setOrientation(LinearLayout.HORIZONTAL);
		titleRow.setGravity(Gravity.CENTER_VERTICAL);
		titleRow.setBackgroundColor(bar);
		titleRow.setPadding(pad, 0, Math.round(4 * d), 0);

		TextView titleView = new TextView(context);
		titleView.setText("My lines and Reply");
		titleView.setAllCaps(true);
		titleView.setTextColor(titleInk);
		titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
		titleView.setGravity(Gravity.CENTER_VERTICAL);
		LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
				0, titleHeight, 1f);
		titleRow.addView(titleView, titleLp);

		TextView help = new TextView(context);
		help.setText("?");
		help.setTextColor(desc);
		help.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
		help.setGravity(Gravity.CENTER);
		help.setClickable(true);
		help.setFocusable(true);
		help.setContentDescription("How to fill My lines and Reply");
		help.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				EditorHelp.show(context, "My lines and Reply", EditorHelp.CHAT_MY_LINES);
			}
		});
		titleRow.addView(help, new LinearLayout.LayoutParams(
				Math.round(36 * d), titleHeight));
		shell.addView(titleRow, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, titleHeight));

		LinearLayout form = new LinearLayout(context);
		form.setOrientation(LinearLayout.VERTICAL);
		form.setPadding(pad, pad, pad, pad);

		TextView mineLabel = caption(context, "My lines", titleInk);
		form.addView(mineLabel);
		TextView mineHint = caption(context,
				"One form per line (or semicolons). A name like Ada already matches says and asks. Ada says does not match Ada asks — add that line.",
				desc);
		mineHint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
		form.addView(mineHint);

		final EditText mineBox = new EditText(context);
		mineBox.setBackgroundResource(R.drawable.input_bar_bg);
		mineBox.setTextColor(titleInk);
		mineBox.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
		mineBox.setSingleLine(false);
		mineBox.setMinLines(3);
		mineBox.setMaxLines(8);
		mineBox.setHorizontallyScrolling(false);
		mineBox.setGravity(Gravity.TOP | Gravity.START);
		mineBox.setInputType(InputType.TYPE_CLASS_TEXT
				| InputType.TYPE_TEXT_FLAG_MULTI_LINE);
		mineBox.setImeOptions(EditorInfo.IME_FLAG_NO_ENTER_ACTION);
		mineBox.setText(ChatStore.mineNeedleEditorText(mineStored));
		form.addView(mineBox, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT));

		TextView colorLabel = caption(context, "Bubble colour", titleInk);
		LinearLayout.LayoutParams colorLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);
		colorLp.topMargin = Math.round(10 * d);
		form.addView(colorLabel, colorLp);
		final int[] selected = new int[] { colorArgb };
		final LinearLayout chips = new LinearLayout(context);
		chips.setOrientation(LinearLayout.HORIZONTAL);
		paintColorChips(context, chips, selected, d);
		form.addView(chips);

		TextView replyLabel = caption(context, "Reply", titleInk);
		LinearLayout.LayoutParams replyLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);
		replyLp.topMargin = Math.round(12 * d);
		form.addView(replyLabel, replyLp);
		TextView replyHint = caption(context,
				"Command Send uses. $text is the reply box (tell Bob $text). tell $1 $text is the trigger capture form, not Send.",
				desc);
		replyHint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
		form.addView(replyHint);

		final EditText replyBox = new EditText(context);
		replyBox.setBackgroundResource(R.drawable.input_bar_bg);
		replyBox.setTextColor(titleInk);
		replyBox.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
		replyBox.setSingleLine(true);
		replyBox.setText(replyShown == null ? "" : replyShown);
		form.addView(replyBox, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT));

		ScrollView scroll = new ScrollView(context);
		scroll.addView(form);
		shell.addView(scroll, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

		LinearLayout footer = new LinearLayout(context);
		footer.setOrientation(LinearLayout.HORIZONTAL);
		footer.setPadding(pad, Math.round(6 * d), pad, Math.round(6 * d));
		footer.setBackgroundColor(bar);

		Button cancel = new Button(context);
		cancel.setText("Cancel");
		cancel.setMinHeight(minButton);
		cancel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
			}
		});
		Button save = new Button(context);
		save.setText("Save");
		save.setMinHeight(minButton);
		save.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String mine = ChatStore.canonicalizeMineNeedle(
						mineBox.getText() == null ? "" : mineBox.getText().toString());
				String reply = replyBox.getText() == null
						? "" : replyBox.getText().toString().trim();
				listener.onSaved(mine, reply, selected[0]);
				dialog.dismiss();
			}
		});
		LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(
				0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		footer.addView(cancel, half);
		footer.addView(save, half);
		shell.addView(footer, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT));

		dialog.setContentView(shell, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		dialog.setCanceledOnTouchOutside(true);
		EditorDialogChrome.applyFloatingWrapContentHeight(dialog);
		Window after = dialog.getWindow();
		if (after != null) {
			after.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
					| WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
		}
		liftShellAboveIme(shell);
		dialog.show();
		final EditText focus = focusReply ? replyBox : mineBox;
		focus.post(new Runnable() {
			@Override
			public void run() {
				focus.requestFocus();
				int y = focus.getTop();
				View parent = (View) focus.getParent();
				while (parent != null && parent != scroll) {
					y += parent.getTop();
					if (!(parent.getParent() instanceof View)) {
						break;
					}
					parent = (View) parent.getParent();
				}
				scroll.smoothScrollTo(0, Math.max(0, y));
			}
		});
	}

	/**
	 * This is a floating window; MainWindow is {@code adjustNothing}. Pad the
	 * shell by the IME so Save stays above the keyboard. ADJUST_RESIZE on the
	 * dialog is the other path (same as alias/trigger editors).
	 */
	private static void liftShellAboveIme(final View shell) {
		ViewCompat.setOnApplyWindowInsetsListener(shell, (view, insets) -> {
			Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
			view.setPadding(view.getPaddingLeft(), view.getPaddingTop(),
					view.getPaddingRight(), ime.bottom);
			return insets;
		});
		ViewCompat.requestApplyInsets(shell);
	}

	private static TextView caption(Context context, String text, int color) {
		TextView t = new TextView(context);
		t.setText(text);
		t.setTextColor(color);
		t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
		return t;
	}

	private static void paintColorChips(final Context context, final LinearLayout chips,
			final int[] selected, final float d) {
		chips.removeAllViews();
		int size = (int) (22 * d);
		int gap = (int) (6 * d);
		for (int i = 0; i < ChatStore.MINE_COLOR_PRESETS.length; i++) {
			final int color = ChatStore.MINE_COLOR_PRESETS[i];
			View chip = new View(context);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
			if (i > 0) {
				lp.leftMargin = gap;
			}
			chip.setLayoutParams(lp);
			GradientDrawable shape = new GradientDrawable();
			shape.setShape(GradientDrawable.OVAL);
			shape.setColor(color);
			if (color == selected[0]) {
				shape.setStroke((int) (2 * d), 0xFFFFFFFF);
			}
			chip.setBackground(shape);
			chip.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selected[0] = color;
					paintColorChips(context, chips, selected, d);
				}
			});
			chips.addView(chip);
		}
	}
}
