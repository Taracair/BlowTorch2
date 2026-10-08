package com.resurrection.blowtorch2.lib.window;

import java.util.ArrayList;
import java.util.List;

import com.resurrection.blowtorch2.lib.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * A draggable list over the game. Recent commands and the suggestion list
 * are two of these. Not an extra-text slot.
 */
public final class LastListPanel {

	public static final String LAYER_TAG = "last_list_overlay";
	public static final String SUGGEST_LAYER_TAG = "suggest_list_overlay";

	private static final int MIN_OPACITY = 20;
	private static final int MAX_OPACITY = 100;
	private static final int DEFAULT_OPACITY = 90;
	private static final int DEFAULT_X_DP = 16;
	private static final int DEFAULT_Y_DP = 72;
	private static final int DEFAULT_W_DP = 280;
	private static final int DEFAULT_H_DP = 260;
	private static final int MIN_W_DP = 140;
	private static final int MIN_H_DP = 120;
	private static final int HIGHLIGHT = 0x55FFFFFF;
	private static final int RULE = 0x33FFFFFF;

	public interface Host {
		List<String> lines();

		int historyMax();

		void setHistoryMax(int max);

		String world();

		/** 1 is the first row. Recent commands: newest. Suggestions: {@code .suggest 1}. */
		void resend(int index);

		/** The close button. A programmatic {@link #hide()} does not call this. */
		void closed();

		/** The window was just shown. Recent commands uses this to put the float away. */
		void shown();
	}

	private final Context context;
	private final Host host;
	private final FrameLayout root;
	private final LinearLayout titleBar;
	private final TextView grip;
	private final ScrollView scroll;
	private final LinearLayout rows;
	private final ArrayList<TextView> rowViews = new ArrayList<TextView>();

	private String boundWorld;
	private int opacity = DEFAULT_OPACITY;
	private int xDp = DEFAULT_X_DP;
	private int yDp = DEFAULT_Y_DP;
	private int wDp = DEFAULT_W_DP;
	private int hDp = DEFAULT_H_DP;
	private int fontSp = LastListRows.FONT_DEFAULT;
	private boolean separators = true;
	private boolean wrap = true;
	private boolean tappable = false;
	private boolean minimal = false;
	private boolean keepOpacity = false;
	private boolean open = false;
	private int highlighted = -1;
	private int primaryId = -1;
	private final LastListGesture gesture = new LastListGesture();
	private final String titleText;
	private final String emptyText;
	private final String prefsPrefix;
	private final boolean showGear;
	private final boolean tapByDefault;
	private final int defaultYDp;
	/** Recent commands remember how many lines to keep. Suggestions do not. */
	private final boolean countKept;

	public LastListPanel(final Context context, final Host host) {
		this(context, host, "Recent commands", "No commands yet.",
				"LASTLIST_", LAYER_TAG, true, false, DEFAULT_Y_DP, true);
	}

	public LastListPanel(final Context context, final Host host,
			final String titleText, final String emptyText, final String prefsPrefix,
			final String layerTag, final boolean showGear, final boolean tapByDefault,
			final int defaultYDp, final boolean countKept) {
		this.context = context;
		this.host = host;
		this.titleText = titleText;
		this.emptyText = emptyText;
		this.prefsPrefix = prefsPrefix;
		this.showGear = showGear;
		this.tapByDefault = tapByDefault;
		this.defaultYDp = defaultYDp;
		this.countKept = countKept;

		root = new FrameLayout(context);
		root.setTag(layerTag);
		root.setClickable(true);
		root.setFocusable(false);
		root.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

		LinearLayout column = new LinearLayout(context);
		column.setOrientation(LinearLayout.VERTICAL);
		column.setLayoutParams(new FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT));

		titleBar = new LinearLayout(context);
		titleBar.setOrientation(LinearLayout.HORIZONTAL);
		titleBar.setGravity(Gravity.CENTER_VERTICAL);
		titleBar.setMinimumHeight(dp(36));
		titleBar.setPadding(dp(4), dp(2), dp(4), dp(2));

		TextView title = new TextView(context);
		title.setText(titleText);
		title.setTextSize(13);
		title.setTextColor(0xFFE8EEF4);
		title.setSingleLine(true);
		LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
				0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
		title.setLayoutParams(titleLp);
		titleBar.addView(title);

		if (showGear) {
			TextView gear = new TextView(context);
			gear.setText("⚙");
			gear.setTextSize(18);
			gear.setTextColor(0xFFE8EEF4);
			gear.setPadding(dp(8), dp(4), dp(8), dp(4));
			gear.setContentDescription(titleText + " settings");
			gear.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(final View v) {
					showOptions();
				}
			});
			titleBar.addView(gear);
		}

		ImageButton close = new ImageButton(context);
		close.setImageResource(R.drawable.ic_window_close);
		close.setBackgroundColor(Color.TRANSPARENT);
		close.setContentDescription("Close");
		int closePx = dp(32);
		close.setLayoutParams(new LinearLayout.LayoutParams(closePx, closePx));
		close.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				host.closed();
				hide();
			}
		});
		titleBar.addView(close);
		column.addView(titleBar, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT));

		rows = new LinearLayout(context);
		rows.setOrientation(LinearLayout.VERTICAL);
		rows.setOnTouchListener(rowTouch());

		final GestureDetector hold = new GestureDetector(context,
				new GestureDetector.SimpleOnGestureListener() {
					@Override
					public void onLongPress(final MotionEvent event) {
						if (showGear && minimal && !tappable) {
							showOptions();
						}
					}
				});
		scroll = new ScrollView(context) {
			@Override
			public boolean onTouchEvent(final MotionEvent event) {
				if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN
						&& LastListGesture.exitFrame(minimal, pointerInsideScreen(
								pointerScreenX(event, event.getActionIndex()),
								pointerScreenY(event, event.getActionIndex())))) {
					revealFrame();
				}
				if (showGear && minimal && !tappable) {
					hold.onTouchEvent(event);
				}
				return super.onTouchEvent(event);
			}
		};
		scroll.setFillViewport(true);
		scroll.addView(rows, new FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.WRAP_CONTENT));
		column.addView(scroll, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
		root.addView(column);

		grip = new TextView(context);
		grip.setText("◢");
		grip.setTextSize(16);
		grip.setTextColor(0xFF9AA7B5);
		grip.setGravity(Gravity.CENTER);
		grip.setContentDescription("Resize");
		FrameLayout.LayoutParams gripLp = new FrameLayout.LayoutParams(dp(40), dp(40));
		gripLp.gravity = Gravity.BOTTOM | Gravity.END;
		grip.setLayoutParams(gripLp);
		root.addView(grip);

		title.setOnTouchListener(dragListener());
		titleBar.setOnTouchListener(dragListener());
		grip.setOnTouchListener(resizeListener());
		applyChrome();
	}

	public void attach(final RelativeLayout parent) {
		if (root.getParent() != null) {
			return;
		}
		parent.addView(root, new RelativeLayout.LayoutParams(dp(wDp), dp(hDp)));
		root.setVisibility(View.GONE);
	}

	public boolean isShowing() {
		return root.getVisibility() == View.VISIBLE;
	}

	public void show() {
		host.shown();
		bindWorld(false);
		open = true;
		root.setVisibility(View.VISIBLE);
		root.bringToFront();
		saveGeometry();
		refresh();
	}

	public void hide() {
		open = false;
		root.setVisibility(View.GONE);
		saveGeometry();
	}

	/** Hide without changing the saved open flag. */
	public void conceal() {
		root.setVisibility(View.GONE);
	}

	/** What this world last saved. Binds first so a new activity sees the file. */
	public boolean leftOpen() {
		bindWorld(false);
		return open;
	}

	/** {@code .lastlist frame on} shows the title. {@code frame off} hides it. */
	public void setChrome(final boolean showChrome) {
		host.shown();
		bindWorld(false);
		minimal = !showChrome;
		open = true;
		applyChrome();
		root.setVisibility(View.VISIBLE);
		root.bringToFront();
		saveGeometry();
		refresh();
	}

	/** {@code .lastlist frame} with no on or off. Loads the saved chrome, then flips it. */
	public void toggleChrome() {
		bindWorld(false);
		setChrome(minimal);
	}

	/**
	 * The world on screen changed. Save whether this world left the window
	 * open, then show or hide whatever the next world had.
	 */
	public void onWorldChanged() {
		if (boundWorld != null) {
			if (isShowing()) {
				open = true;
			}
			saveGeometry();
		}
		bindWorld(true);
		if (open) {
			root.setVisibility(View.VISIBLE);
			root.bringToFront();
			refresh();
		} else {
			root.setVisibility(View.GONE);
		}
	}

	public void ensureBound() {
		bindWorld(false);
	}

	public void showOptions() {
		final EditText opacityField = numberField(Integer.toString(opacity), "Opacity 20–100");
		final EditText sizeField = numberField(Integer.toString(host.historyMax()),
				"Commands kept 10–100");
		final EditText fontField = numberField(Integer.toString(fontSp), "Font size 10–32");
		final CheckBox rulesBox = check(
				countKept ? "Lines between commands" : "Lines between rows", separators);
		final CheckBox clipBox = check("Don't wrap lines", !wrap);
		final CheckBox tapBox = check("Tappable", tappable);
		final CheckBox minimalBox = check("Minimal frame", minimal);
		final CheckBox keepBox = check("Keep opacity", keepOpacity);

		LinearLayout form = new LinearLayout(context);
		form.setOrientation(LinearLayout.VERTICAL);
		int pad = dp(16);
		form.setPadding(pad, pad, pad, 0);
		form.addView(label("Opacity"));
		form.addView(opacityField);
		if (countKept) {
			form.addView(label("Commands kept"));
			form.addView(sizeField);
		}
		form.addView(label("Font size"));
		form.addView(fontField);
		form.addView(rulesBox);
		form.addView(clipBox);
		form.addView(hint(countKept
				? "Each command stays on one line, so a narrow window cuts off the end."
				: "Each row stays on one line, so a narrow window cuts off the end."));
		form.addView(tapBox);
		form.addView(hint(countKept
				? "A tap sends that line. Dragging up or down scrolls, the same as with this off, and does not send. "
						+ "A second finger outside the list cancels the tap."
				: "A tap puts that word in the bar and does not send it. Dragging up or down scrolls, "
						+ "the same as with this off, and does not send. "
						+ "A second finger outside the list cancels the tap."));
		form.addView(minimalBox);
		form.addView(keepBox);
		form.addView(hint(countKept
				? "Minimal frame hides the title, gear, and close. "
						+ "Keep opacity leaves the window fill and only hides that chrome. "
						+ "Hold a row and tap the list with a second finger to bring the frame back. "
						+ ".lastlist frame on does the same. .lastlist frame off hides it."
				: "Minimal frame hides the title, gear, and close. "
						+ "Keep opacity leaves the window fill and only hides that chrome. "
						+ "Hold a row and tap the list with a second finger to bring the frame back."));

		ScrollView formScroll = new ScrollView(context);
		formScroll.addView(form);

		new AlertDialog.Builder(context)
				.setTitle(titleText)
				.setView(formScroll)
				.setPositiveButton("Done", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(final DialogInterface dialog, final int which) {
						applyForm(opacityField, sizeField, fontField, rulesBox, clipBox, tapBox,
								minimalBox, keepBox);
					}
				})
				.setNeutralButton("Show window", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(final DialogInterface dialog, final int which) {
						applyForm(opacityField, sizeField, fontField, rulesBox, clipBox, tapBox,
								minimalBox, keepBox);
						show();
					}
				})
				.setNegativeButton("Cancel", null)
				.show();
	}

	public void refresh() {
		highlighted = -1;
		rows.removeAllViews();
		rowViews.clear();
		rows.setPadding(dp(8), dp(4), dp(8), minimal ? dp(4) : dp(28));
		List<String> lines = host.lines();
		if (lines == null || lines.isEmpty()) {
			TextView empty = rowView(emptyText, false);
			rows.addView(empty);
			return;
		}
		int padV = dp(3);
		for (int i = 0; i < lines.size(); i++) {
			if (separators && i > 0) {
				View rule = new View(context);
				rule.setBackgroundColor(RULE);
				LinearLayout.LayoutParams ruleLp = new LinearLayout.LayoutParams(
						LinearLayout.LayoutParams.MATCH_PARENT, 1);
				ruleLp.topMargin = dp(2);
				ruleLp.bottomMargin = dp(2);
				rule.setLayoutParams(ruleLp);
				rows.addView(rule);
			}
			String line = lines.get(i);
			TextView row = rowView(line == null ? "" : line, true);
			row.setPadding(dp(4), padV, dp(4), padV);
			row.setLayoutParams(new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT));
			rows.addView(row);
			rowViews.add(row);
		}
	}

	private void bindWorld(final boolean force) {
		String world = host.world();
		if (world == null || world.length() == 0) {
			world = "Connection";
		}
		if (!force && world.equals(boundWorld)) {
			return;
		}
		boundWorld = world;
		SharedPreferences prefs = context.getSharedPreferences(prefsName(world),
				Context.MODE_PRIVATE);
		xDp = prefs.getInt("x", DEFAULT_X_DP);
		yDp = prefs.getInt("y", defaultYDp);
		wDp = Math.max(MIN_W_DP, prefs.getInt("w", DEFAULT_W_DP));
		hDp = Math.max(MIN_H_DP, prefs.getInt("h", DEFAULT_H_DP));
		opacity = clamp(prefs.getInt("opacity", DEFAULT_OPACITY), MIN_OPACITY, MAX_OPACITY);
		fontSp = LastListRows.fontSp(prefs.getInt("font", LastListRows.FONT_DEFAULT));
		separators = prefs.getBoolean("separators", true);
		wrap = prefs.getBoolean("wrap", true);
		tappable = prefs.getBoolean("tappable", tapByDefault);
		minimal = prefs.getBoolean("minimal", false);
		keepOpacity = prefs.getBoolean("keepOpacity", false);
		open = prefs.getBoolean("open", false);
		applyGeometry();
		applyChrome();
	}

	private void applyForm(final EditText opacityField, final EditText sizeField,
			final EditText fontField, final CheckBox rulesBox, final CheckBox clipBox,
			final CheckBox tapBox, final CheckBox minimalBox, final CheckBox keepBox) {
		opacity = clamp(parseOr(opacityField, opacity), MIN_OPACITY, MAX_OPACITY);
		fontSp = LastListRows.fontSp(parseOr(fontField, fontSp));
		separators = rulesBox.isChecked();
		wrap = !clipBox.isChecked();
		tappable = tapBox.isChecked();
		minimal = minimalBox.isChecked();
		keepOpacity = keepBox.isChecked();
		applyChrome();
		saveGeometry();
		if (countKept && sizeField != null) {
			int max = clamp(parseOr(sizeField, host.historyMax()), 10, 100);
			host.setHistoryMax(max);
		}
		refresh();
	}

	private void applyGeometry() {
		RelativeLayout.LayoutParams lp = params();
		if (lp == null) {
			return;
		}
		lp.width = dp(wDp);
		lp.height = dp(hDp);
		lp.leftMargin = dp(xDp);
		lp.topMargin = dp(yDp);
		lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
		lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
		root.setLayoutParams(lp);
	}

	private void applyChrome() {
		rows.setClickable(tappable);
		titleBar.setVisibility(minimal ? View.GONE : View.VISIBLE);
		grip.setVisibility(minimal ? View.GONE : View.VISIBLE);
		if (minimal && !keepOpacity) {
			root.setBackgroundColor(Color.TRANSPARENT);
			return;
		}
		int alpha = opacity * 255 / 100;
		root.setBackgroundColor(Color.argb(alpha, 0x1A, 0x1A, 0x1A));
		titleBar.setBackgroundColor(Color.argb(alpha, 0x24, 0x30, 0x40));
	}

	private void saveGeometry() {
		if (boundWorld == null) {
			return;
		}
		RelativeLayout.LayoutParams lp = params();
		if (lp != null && lp.width > 0 && lp.height > 0) {
			xDp = pxToDp(lp.leftMargin);
			yDp = pxToDp(lp.topMargin);
			wDp = Math.max(MIN_W_DP, pxToDp(lp.width));
			hDp = Math.max(MIN_H_DP, pxToDp(lp.height));
		}
		context.getSharedPreferences(prefsName(boundWorld), Context.MODE_PRIVATE)
				.edit()
				.putInt("x", xDp)
				.putInt("y", yDp)
				.putInt("w", wDp)
				.putInt("h", hDp)
				.putInt("opacity", opacity)
				.putInt("font", fontSp)
				.putBoolean("separators", separators)
				.putBoolean("wrap", wrap)
				.putBoolean("tappable", tappable)
				.putBoolean("minimal", minimal)
				.putBoolean("keepOpacity", keepOpacity)
				.putBoolean("open", open)
				.commit();
	}

	/** Write the current place and open flag. Does not change either. */
	public void flush() {
		saveGeometry();
	}

	/** After the game windows are rebuilt they sit above this one. */
	public void raiseIfOpen() {
		if (root.getVisibility() == View.VISIBLE) {
			root.bringToFront();
		}
	}

	private TextView rowView(final String text, final boolean command) {
		TextView view = new TextView(context);
		view.setTypeface(Typeface.MONOSPACE);
		view.setTextSize(fontSp);
		view.setTextColor(command ? 0xFFE8EEF4 : 0xFF9AA7B5);
		view.setText(text);
		if (!wrap) {
			view.setSingleLine(true);
			view.setEllipsize(TextUtils.TruncateAt.END);
		}
		return view;
	}

	private void highlight(final int index) {
		if (index == highlighted) {
			return;
		}
		highlighted = index;
		for (int i = 0; i < rowViews.size(); i++) {
			rowViews.get(i).setBackgroundColor(i == index ? HIGHLIGHT : Color.TRANSPARENT);
		}
	}

	private View.OnTouchListener rowTouch() {
		return new View.OnTouchListener() {
			@Override
			public boolean onTouch(final View v, final MotionEvent event) {
				if (!tappable || rowViews.isEmpty()) {
					return false;
				}
				int action = event.getActionMasked();
				if (action == MotionEvent.ACTION_POINTER_DOWN) {
					int index = event.getActionIndex();
					boolean inside = pointerInsideScreen(
							pointerScreenX(event, index), pointerScreenY(event, index));
					boolean exit = LastListGesture.exitFrame(minimal, inside);
					boolean cancel = LastListGesture.cancelTap(tappable, inside);
					gesture.secondFinger(exit, cancel);
					if (exit || cancel) {
						highlight(-1);
					}
					if (exit) {
						revealFrame();
					}
					return true;
				}
				switch (action) {
				case MotionEvent.ACTION_DOWN:
					primaryId = event.getPointerId(0);
					int downRow = rowUnder(event.getY());
					gesture.down(downRow);
					highlight(downRow);
					return true;
				case MotionEvent.ACTION_MOVE:
					return true;
				case MotionEvent.ACTION_POINTER_UP:
					if (event.getPointerId(event.getActionIndex()) == primaryId) {
						finishTap();
					}
					return true;
				case MotionEvent.ACTION_UP:
					finishTap();
					return true;
				case MotionEvent.ACTION_CANCEL:
					gesture.cancelTouch();
					highlight(-1);
					return true;
				default:
					return true;
				}
			}
		};
	}

	private void finishTap() {
		int send = gesture.up();
		highlight(-1);
		if (send >= 1) {
			host.resend(send);
		}
	}

	private void revealFrame() {
		if (!minimal) {
			return;
		}
		minimal = false;
		applyChrome();
		saveGeometry();
		root.post(new Runnable() {
			@Override
			public void run() {
				refresh();
			}
		});
	}

	/**
	 * A second finger on the game text never reaches the list: the window
	 * gives that pointer its own target. The activity sees it first.
	 */
	public void onWindowPointer(final MotionEvent event) {
		if (event == null || event.getActionMasked() != MotionEvent.ACTION_POINTER_DOWN
				|| !gesture.tracking()) {
			return;
		}
		int index = event.getActionIndex();
		boolean inside = pointerInsideScreen(pointerScreenX(event, index),
				pointerScreenY(event, index));
		if (LastListGesture.exitFrame(minimal, inside)) {
			gesture.secondFinger(true, false);
			highlight(-1);
			revealFrame();
			return;
		}
		if (LastListGesture.cancelTap(tappable, inside)) {
			gesture.secondFinger(false, true);
			highlight(-1);
		}
	}

	private int pointerScreenX(final MotionEvent event, final int pointer) {
		return LastListGesture.screenAxis(event.getRawX(), event.getX(0), event.getX(pointer));
	}

	/** Local Y changes when the rows scroll, so a drag delta has to be screen Y. */
	private int pointerScreenY(final MotionEvent event, final int pointer) {
		return LastListGesture.screenAxis(event.getRawY(), event.getY(0), event.getY(pointer));
	}

	private boolean pointerInsideScreen(final int x, final int y) {
		int[] rootLoc = new int[2];
		root.getLocationOnScreen(rootLoc);
		return LastListGesture.contains(x, y, rootLoc[0], rootLoc[1],
				rootLoc[0] + root.getWidth(), rootLoc[1] + root.getHeight());
	}

	private int rowUnder(final float y) {
		int[] top = new int[rowViews.size()];
		int[] bottom = new int[rowViews.size()];
		for (int i = 0; i < rowViews.size(); i++) {
			View row = rowViews.get(i);
			top[i] = row.getTop();
			bottom[i] = row.getBottom();
		}
		return LastListRows.rowAt(Math.round(y), top, bottom);
	}

	private TextView label(final String text) {
		TextView view = new TextView(context);
		view.setText(text);
		view.setPadding(0, dp(8), 0, dp(2));
		return view;
	}

	private TextView hint(final String text) {
		TextView view = new TextView(context);
		view.setText(text);
		view.setTextSize(12);
		view.setPadding(0, 0, 0, dp(4));
		return view;
	}

	private CheckBox check(final String text, final boolean on) {
		CheckBox box = new CheckBox(context);
		box.setText(text);
		box.setChecked(on);
		return box;
	}

	private EditText numberField(final String value, final String hint) {
		EditText field = new EditText(context);
		field.setSingleLine(true);
		field.setInputType(InputType.TYPE_CLASS_NUMBER);
		field.setHint(hint);
		field.setText(value);
		return field;
	}

	private View.OnTouchListener dragListener() {
		return new View.OnTouchListener() {
			float downX;
			float downY;
			int startX;
			int startY;

			@Override
			public boolean onTouch(final View v, final MotionEvent event) {
				RelativeLayout.LayoutParams lp = params();
				if (lp == null) {
					return false;
				}
				switch (event.getActionMasked()) {
				case MotionEvent.ACTION_DOWN:
					downX = event.getRawX();
					downY = event.getRawY();
					startX = lp.leftMargin;
					startY = lp.topMargin;
					return true;
				case MotionEvent.ACTION_MOVE:
					lp.leftMargin = Math.max(0, startX + Math.round(event.getRawX() - downX));
					lp.topMargin = Math.max(0, startY + Math.round(event.getRawY() - downY));
					root.setLayoutParams(lp);
					return true;
				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_CANCEL:
					saveGeometry();
					return true;
				default:
					return false;
				}
			}
		};
	}

	private View.OnTouchListener resizeListener() {
		return new View.OnTouchListener() {
			float downX;
			float downY;
			int startW;
			int startH;

			@Override
			public boolean onTouch(final View v, final MotionEvent event) {
				RelativeLayout.LayoutParams lp = params();
				if (lp == null) {
					return false;
				}
				switch (event.getActionMasked()) {
				case MotionEvent.ACTION_DOWN:
					downX = event.getRawX();
					downY = event.getRawY();
					startW = lp.width;
					startH = lp.height;
					return true;
				case MotionEvent.ACTION_MOVE:
					lp.width = Math.max(dp(MIN_W_DP), startW + Math.round(event.getRawX() - downX));
					lp.height = Math.max(dp(MIN_H_DP), startH + Math.round(event.getRawY() - downY));
					root.setLayoutParams(lp);
					return true;
				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_CANCEL:
					saveGeometry();
					return true;
				default:
					return false;
				}
			}
		};
	}

	private RelativeLayout.LayoutParams params() {
		if (root.getLayoutParams() instanceof RelativeLayout.LayoutParams) {
			return (RelativeLayout.LayoutParams) root.getLayoutParams();
		}
		return null;
	}

	private int dp(final int value) {
		float density = context.getResources().getDisplayMetrics().density;
		return Math.round(value * density);
	}

	private int pxToDp(final int px) {
		float density = context.getResources().getDisplayMetrics().density;
		if (density <= 0f) {
			return px;
		}
		return Math.round(px / density);
	}

	private static int clamp(final int value, final int min, final int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static int parseOr(final EditText field, final int fallback) {
		try {
			return Integer.parseInt(field.getText().toString().trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private String prefsName(final String world) {
		String safe = world.replaceAll("[^A-Za-z0-9._-]+", "_");
		return prefsPrefix + safe;
	}
}
