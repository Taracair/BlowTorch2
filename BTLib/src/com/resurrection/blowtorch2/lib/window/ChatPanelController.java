package com.resurrection.blowtorch2.lib.window;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.chat.ChatMessage;
import com.resurrection.blowtorch2.lib.chat.ChatStore;
import com.resurrection.blowtorch2.lib.chat.ChatThreadSummary;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Left chat drawer over {@code window_container}, under ⋮ chrome (same z-order
 * idea as extra-text / mapper). Width is ~80% of the screen.
 */
public class ChatPanelController {

	static final String LAYER_TAG = "chat_panel";
	private static final int MESSAGE_LIMIT = 200;
	private static final long SLIDE_MS = 220L;
	private static final int FIND_STROKE = 0xCCF6E27A;
	private static final int FIND_STROKE_CURRENT = 0xFFF6E27A;

	public interface Host {
		MainWindow getMainWindow();

		String getConnectionDisplay();

		/** Same path as a typed line: encoding + CRLF through {@code sendData}. */
		void sendCommand(String text);

		/** Drawer finished showing or hiding. Default no-op (unread dot). */
		default void onChatVisibilityChanged() {}

		/** Literal Send-to-thread action whose threadId field equals this conversation. Null if none. */
		default String chatTriggerReplyTemplate(String threadId) { return null; }

		default void saveChatTriggerReplyTemplate(String threadId, String template) {}

		default boolean hasChatTrigger(String threadId) { return false; }
	}

	private final Host host;
	private View root;
	private View scrim;
	private View drawer;
	private TextView backBtn;
	private TextView titleView;
	private TextView closeBtn;
	private View inboxView;
	private View threadView;
	private EditText searchBox;
	private TextView inboxEmpty;
	private LinearLayout threadList;
	private EditText templateBox;
	private TextView settingsSave;
	private TextView threadDelete;
	private TextView orphanHint;
	private View dateFilter;
	private ScrollView messageScroll;
	private LinearLayout messageList;
	private EditText replyBox;
	private TextView sendBtn;
	private EditText mineNameBox;
	private LinearLayout mineColors;
	private View settingsPanel;
	private TextView settingsToggle;
	private View templateRow;
	private EditText threadSearchBox;
	private TextView filterFrom;
	private TextView filterTo;
	private TextView filter7d;
	private TextView filterAll;
	private TextView threadEmpty;
	private View mineRow;
	private View mineHelpButton;
	private View findNav;
	private TextView findPrev;
	private TextView findNext;
	private TextView findCount;
	private final ArrayList<View> findHits = new ArrayList<View>();
	private int findHitIndex = -1;
	private boolean resetFindToFirst;
	private String openThreadId;
	private String searchQuery = "";
	private String threadSearchQuery = "";
	private Long filterFromMs;
	private Long filterUntilExclusiveMs;
	private boolean settingsOpen;
	/**
	 * ⚙ is showing chat.json because the matching trigger still has {@code $1}
	 * (e.g. {@code tell $1 $text}). Quiet persist must not write that seeded
	 * line over the trigger, and must not write the unsubstituted trigger
	 * into the store.
	 */
	private boolean replyBoundFromUnfilledTrigger;
	/** Store template shown when the trigger still has {@code $1}. */
	private String boundStoreReplyFallback = "";
	private boolean attached;
	private boolean visible;
	private boolean animating;

	public ChatPanelController(Host host) {
		this.host = host;
	}

	public boolean isVisible() {
		return visible && root != null && root.getVisibility() == View.VISIBLE;
	}

	/** Overflow ⋮ Chat — always show. */
	public void show() {
		ensureAttached();
		if (root == null) {
			return;
		}
		MainWindow activity = host.getMainWindow();
		if (activity != null && activity.getChromeController() != null) {
			onImeLift(activity.getChromeController().getImeLiftPx());
		}
		if (visible) {
			refresh();
			return;
		}
		bindStore();
		showInbox();
		slideIn();
	}

	/** {@code .chat} binder callback — open is a toggle (no close AIDL). */
	public void toggle() {
		if (isVisible()) {
			hide();
		} else {
			show();
		}
	}

	/**
	 * {@code .chat <thread>} — open the drawer on that thread. Does not toggle
	 * closed if the drawer is already visible.
	 */
	public void openThreadFromCommand(String threadId) {
		ensureAttached();
		if (root == null) {
			return;
		}
		MainWindow activity = host.getMainWindow();
		if (activity != null && activity.getChromeController() != null) {
			onImeLift(activity.getChromeController().getImeLiftPx());
		}
		bindStore();
		String resolved = store().resolveThreadId(threadId);
		String id = resolved != null ? resolved
				: (threadId == null ? "" : threadId.trim());
		if (resolved == null && !inboxHasThreadId(id)) {
			if (activity != null) {
				Toast.makeText(activity, "No chat named " + id, Toast.LENGTH_SHORT).show();
			}
			showInbox();
			if (!visible) {
				slideIn();
			}
			return;
		}
		if (!visible) {
			showThread(id, true);
			slideIn();
			return;
		}
		showThread(id, true);
	}

	public void hide() {
		if (root == null || !visible || animating) {
			return;
		}
		slideOut();
	}

	public void refresh() {
		if (!isVisible()) {
			return;
		}
		bindStore();
		if (openThreadId != null) {
			showThread(openThreadId, false);
		} else {
			showInbox();
		}
	}

	/**
	 * Pin like extra-text (ChromeController would otherwise lift unmarked
	 * children with the IME). Pad the drawer by the game input bar height
	 * plus the IME, so Send sits above that bar instead of under it.
	 */
	public void onImeLift(int liftPx) {
		if (root == null) {
			return;
		}
		root.setTranslationY(0f);
		if (drawer != null) {
			int pad = Math.max(0, liftPx) + gameplayInputBarHeight();
			drawer.setPadding(drawer.getPaddingLeft(), drawer.getPaddingTop(),
					drawer.getPaddingRight(), pad);
		}
	}

	private int gameplayInputBarHeight() {
		MainWindow activity = host.getMainWindow();
		if (activity == null || activity.getChromeController() == null) {
			return 0;
		}
		RelativeLayout rl = (RelativeLayout) activity.findViewById(R.id.window_container);
		if (rl == null) {
			return 0;
		}
		View bar = activity.getChromeController().findGameplayInputBar(rl);
		if (bar == null || bar.getVisibility() == View.GONE) {
			return 0;
		}
		int h = bar.getHeight();
		if (h <= 0) {
			h = bar.getMeasuredHeight();
		}
		return Math.max(0, h);
	}

	public void detach() {
		if (drawer != null) {
			drawer.animate().cancel();
		}
		if (root != null && root.getParent() instanceof ViewGroup) {
			((ViewGroup) root.getParent()).removeView(root);
		}
		root = null;
		scrim = null;
		drawer = null;
		attached = false;
		visible = false;
		animating = false;
		openThreadId = null;
		settingsOpen = false;
		replyBoundFromUnfilledTrigger = false;
	}

	/**
	 * Fill the typed reply into a template. {@code $text} wins; otherwise
	 * {@code $1} (a common mix-up). {@code $name} is left alone.
	 */
	public static String fillReply(String template, String text) {
		if (template == null) {
			return "";
		}
		String t = text == null ? "" : text;
		if (template.contains("$text")) {
			return template.replace("$text", t);
		}
		if (template.contains("$1")) {
			return template.replace("$1", t);
		}
		return template;
	}

	/**
	 * True when a filled command still has {@code $text} or {@code $1}-style
	 * placeholders. Blank lines are false (the sender already skips those).
	 */
	public static boolean replyLooksUnfilled(String line) {
		if (line == null) {
			return false;
		}
		String s = line.trim();
		if (s.length() == 0) {
			return false;
		}
		if (s.contains("$text")) {
			return true;
		}
		for (int i = 0; i < s.length() - 1; i++) {
			if (s.charAt(i) == '$') {
				char d = s.charAt(i + 1);
				if (d >= '0' && d <= '9') {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * True when filling a dummy reply does not leave {@code $1}/{@code $text}.
	 * {@code tell $1 $text} is a trigger capture form, not a Send template.
	 */
	public static boolean replyTemplateReadyToSend(String template) {
		if (template == null || template.trim().length() == 0) {
			return false;
		}
		return !replyLooksUnfilled(fillReply(template, "x"));
	}

	private void ensureAttached() {
		if (attached && root != null) {
			return;
		}
		MainWindow activity = host.getMainWindow();
		if (activity == null || activity.isFinishing()) {
			return;
		}
		RelativeLayout container =
				(RelativeLayout) activity.findViewById(R.id.window_container);
		if (container == null) {
			return;
		}
		root = LayoutInflater.from(activity).inflate(R.layout.chat_panel, container, false);
		root.setTag(LAYER_TAG);
		scrim = root.findViewById(R.id.chat_scrim);
		drawer = root.findViewById(R.id.chat_drawer);
		backBtn = (TextView) root.findViewById(R.id.chat_back);
		titleView = (TextView) root.findViewById(R.id.chat_title);
		closeBtn = (TextView) root.findViewById(R.id.chat_close);
		inboxView = root.findViewById(R.id.chat_inbox);
		threadView = root.findViewById(R.id.chat_thread);
		searchBox = (EditText) root.findViewById(R.id.chat_search);
		inboxEmpty = (TextView) root.findViewById(R.id.chat_inbox_empty);
		threadList = (LinearLayout) root.findViewById(R.id.chat_thread_list);
		templateBox = (EditText) root.findViewById(R.id.chat_template);
		settingsSave = (TextView) root.findViewById(R.id.chat_settings_save);
		threadDelete = (TextView) root.findViewById(R.id.chat_thread_delete);
		orphanHint = (TextView) root.findViewById(R.id.chat_orphan_hint);
		dateFilter = root.findViewById(R.id.chat_date_filter);
		messageScroll = (ScrollView) root.findViewById(R.id.chat_message_scroll);
		messageList = (LinearLayout) root.findViewById(R.id.chat_message_list);
		replyBox = (EditText) root.findViewById(R.id.chat_reply);
		sendBtn = (TextView) root.findViewById(R.id.chat_send);
		mineNameBox = (EditText) root.findViewById(R.id.chat_mine_name);
		mineColors = (LinearLayout) root.findViewById(R.id.chat_mine_colors);
		settingsPanel = root.findViewById(R.id.chat_settings);
		settingsToggle = (TextView) root.findViewById(R.id.chat_settings_toggle);
		templateRow = root.findViewById(R.id.chat_template_row);
		threadSearchBox = (EditText) root.findViewById(R.id.chat_thread_search);
		filterFrom = (TextView) root.findViewById(R.id.chat_filter_from);
		filterTo = (TextView) root.findViewById(R.id.chat_filter_to);
		filter7d = (TextView) root.findViewById(R.id.chat_filter_7d);
		filterAll = (TextView) root.findViewById(R.id.chat_filter_all);
		threadEmpty = (TextView) root.findViewById(R.id.chat_thread_empty);
		mineRow = root.findViewById(R.id.chat_me_row);
		mineHelpButton = root.findViewById(R.id.chat_mine_help_button);
		findNav = root.findViewById(R.id.chat_find_nav);
		findPrev = (TextView) root.findViewById(R.id.chat_find_prev);
		findNext = (TextView) root.findViewById(R.id.chat_find_next);
		findCount = (TextView) root.findViewById(R.id.chat_find_count);

		int width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.80f);
		if (drawer != null) {
			ViewGroup.LayoutParams lp = drawer.getLayoutParams();
			if (lp != null) {
				lp.width = width;
				drawer.setLayoutParams(lp);
			}
		}

		RelativeLayout.LayoutParams hostLp = new RelativeLayout.LayoutParams(
				RelativeLayout.LayoutParams.MATCH_PARENT,
				RelativeLayout.LayoutParams.MATCH_PARENT);
		hostLp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
		hostLp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
		container.addView(root, hostLp);
		root.setVisibility(View.GONE);
		attached = true;
		wire();
		bringUnderChrome();
	}

	private void wire() {
		if (scrim != null) {
			scrim.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					hide();
				}
			});
		}
		if (closeBtn != null) {
			closeBtn.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					hide();
				}
			});
		}
		if (backBtn != null) {
			backBtn.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showInbox();
				}
			});
		}
		if (settingsToggle != null) {
			settingsToggle.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (openThreadId == null) {
						MainWindow activity = host.getMainWindow();
						if (activity != null) {
							Toast.makeText(activity,
									"Open a conversation to set My lines for that chat.",
									Toast.LENGTH_SHORT).show();
						}
						return;
					}
					if (settingsOpen) {
						persistThreadEdits(true);
					}
					settingsOpen = !settingsOpen;
					if (settingsOpen) {
						bindMineRow(true);
						bindTemplateFromTriggerOrStore();
					}
					applySettingsVisibility();
				}
			});
		}
		if (searchBox != null) {
			searchBox.addTextChangedListener(new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {
				}

				@Override
				public void afterTextChanged(Editable s) {
					searchQuery = s == null ? "" : s.toString();
					if (openThreadId == null) {
						populateInbox();
					}
				}
			});
		}
		if (settingsSave != null) {
			settingsSave.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					saveSettings();
				}
			});
		}
		if (threadDelete != null) {
			threadDelete.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					confirmDeleteConversation(openThreadId);
				}
			});
		}
		if (sendBtn != null) {
			sendBtn.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					sendReply();
				}
			});
		}
		if (replyBox != null) {
			replyBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
				@Override
				public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
					if (actionId == EditorInfo.IME_ACTION_SEND) {
						sendReply();
						return true;
					}
					return false;
				}
			});
		}
		wireMineRow();
		wireThreadFilter();
	}

	private void wireMineRow() {
		if (mineNameBox != null) {
			mineNameBox.setOnFocusChangeListener(new View.OnFocusChangeListener() {
				@Override
				public void onFocusChange(View v, boolean hasFocus) {
					if (!hasFocus) {
						saveMineName();
					}
				}
			});
		}
		if (mineHelpButton != null) {
			mineHelpButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					MainWindow activity = host.getMainWindow();
					if (activity != null) {
						EditorHelp.show(activity, "My lines and Reply",
								EditorHelp.CHAT_MY_LINES);
					}
				}
			});
		}
		paintMineColorChips();
	}

	private void paintMineColorChips() {
		if (mineColors == null) {
			return;
		}
		mineColors.removeAllViews();
		MainWindow activity = host.getMainWindow();
		if (activity == null) {
			return;
		}
		float d = activity.getResources().getDisplayMetrics().density;
		int size = (int) (22 * d);
		int gap = (int) (6 * d);
		int selected = store().mineColorArgb(openThreadId);
		for (int i = 0; i < ChatStore.MINE_COLOR_PRESETS.length; i++) {
			final int color = ChatStore.MINE_COLOR_PRESETS[i];
			View chip = new View(activity);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
			if (i > 0) {
				lp.leftMargin = gap;
			}
			chip.setLayoutParams(lp);
			GradientDrawable shape = new GradientDrawable();
			shape.setShape(GradientDrawable.OVAL);
			shape.setColor(color);
			if (color == selected) {
				shape.setStroke((int) (2 * d), 0xFFFFFFFF);
			}
			chip.setBackground(shape);
			chip.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (openThreadId == null) {
						return;
					}
					store().setMineColorArgb(openThreadId, color);
					paintMineColorChips();
					reloadThreadMessages();
				}
			});
			mineColors.addView(chip);
		}
	}

	private void bindMineRow() {
		bindMineRow(false);
	}

	private void bindMineRow(boolean force) {
		if (mineNameBox == null) {
			return;
		}
		if (openThreadId == null) {
			paintMineColorChips();
			return;
		}
		if (!force && mineNameBox.hasFocus()) {
			paintMineColorChips();
			return;
		}
		String needle = store().mineNeedle(openThreadId);
		if (needle == null) {
			needle = "";
		}
		String shown = mineNameBox.getText() == null ? "" : mineNameBox.getText().toString();
		if (!shown.equals(needle)) {
			mineNameBox.setText(needle);
		}
		paintMineColorChips();
	}

	private boolean persistMineNameIfChanged() {
		if (mineNameBox == null || openThreadId == null) {
			return false;
		}
		String typed = mineNameBox.getText() == null ? "" : mineNameBox.getText().toString().trim();
		String existing = store().mineNeedle(openThreadId);
		if (existing == null) {
			existing = "";
		}
		if (typed.equals(existing)) {
			return false;
		}
		if (typed.length() == 0 && existing.length() > 0 && !settingsOpen) {
			return false;
		}
		store().setMineNeedle(openThreadId, typed);
		return true;
	}

	private void saveMineName() {
		if (persistMineNameIfChanged()) {
			reloadThreadMessages();
		}
	}

	private void applySettingsVisibility() {
		boolean threadOpen = openThreadId != null && openThreadId.length() > 0;
		boolean show = settingsOpen && threadOpen;
		if (settingsPanel != null) {
			settingsPanel.setVisibility(show ? View.VISIBLE : View.GONE);
		}
		if (settingsToggle != null) {
			settingsToggle.setBackgroundColor(show ? 0x44FFFFFF : 0x00000000);
		}
		if (mineRow != null) {
			mineRow.setVisibility(show ? View.VISIBLE : View.GONE);
		}
		if (mineHelpButton != null) {
			mineHelpButton.setVisibility(show ? View.VISIBLE : View.GONE);
		}
		if (templateRow != null) {
			templateRow.setVisibility(show ? View.VISIBLE : View.GONE);
		}
		if (dateFilter != null) {
			dateFilter.setVisibility(show ? View.VISIBLE : View.GONE);
		}
		if (orphanHint != null) {
			boolean orphan = show && !host.hasChatTrigger(openThreadId);
			orphanHint.setVisibility(orphan ? View.VISIBLE : View.GONE);
		}
	}

	private void wireThreadFilter() {
		if (threadSearchBox != null) {
			threadSearchBox.addTextChangedListener(new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {
				}

				@Override
				public void afterTextChanged(Editable s) {
					threadSearchQuery = s == null ? "" : s.toString();
					resetFindToFirst = true;
					if (openThreadId != null) {
						reloadThreadMessages();
					} else {
						applyFindNav();
					}
				}
			});
			threadSearchBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
				@Override
				public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
					if (actionId == EditorInfo.IME_ACTION_SEARCH) {
						moveFindHit(1);
						return true;
					}
					return false;
				}
			});
		}
		if (findPrev != null) {
			findPrev.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					moveFindHit(-1);
				}
			});
		}
		if (findNext != null) {
			findNext.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					moveFindHit(1);
				}
			});
		}
		if (filterFrom != null) {
			filterFrom.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					pickFilterDate(true);
				}
			});
			filterFrom.setOnLongClickListener(new View.OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					filterFromMs = null;
					resetFindToFirst = true;
					paintDateChips();
					reloadThreadMessages();
					return true;
				}
			});
		}
		if (filterTo != null) {
			filterTo.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					pickFilterDate(false);
				}
			});
			filterTo.setOnLongClickListener(new View.OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					filterUntilExclusiveMs = null;
					resetFindToFirst = true;
					paintDateChips();
					reloadThreadMessages();
					return true;
				}
			});
		}
		if (filter7d != null) {
			filter7d.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					filterFromMs = Long.valueOf(startOfLocalDay(-6));
					filterUntilExclusiveMs = null;
					resetFindToFirst = true;
					paintDateChips();
					reloadThreadMessages();
				}
			});
		}
		if (filterAll != null) {
			filterAll.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					filterFromMs = null;
					filterUntilExclusiveMs = null;
					resetFindToFirst = true;
					paintDateChips();
					reloadThreadMessages();
				}
			});
		}
		paintDateChips();
		applyFindNav();
	}

	private void pickFilterDate(final boolean from) {
		MainWindow activity = host.getMainWindow();
		if (activity == null) {
			return;
		}
		Calendar cal = Calendar.getInstance();
		if (from && filterFromMs != null) {
			cal.setTimeInMillis(filterFromMs.longValue());
		} else if (!from && filterUntilExclusiveMs != null) {
			cal.setTimeInMillis(filterUntilExclusiveMs.longValue() - 1L);
		}
		DatePickerDialog dlg = new DatePickerDialog(activity,
				new DatePickerDialog.OnDateSetListener() {
					@Override
					public void onDateSet(DatePicker view, int year, int month, int day) {
						Calendar picked = Calendar.getInstance();
						picked.clear();
						picked.set(Calendar.YEAR, year);
						picked.set(Calendar.MONTH, month);
						picked.set(Calendar.DAY_OF_MONTH, day);
						long start = picked.getTimeInMillis();
						if (from) {
							filterFromMs = Long.valueOf(start);
						} else {
							picked.add(Calendar.DAY_OF_MONTH, 1);
							filterUntilExclusiveMs = Long.valueOf(picked.getTimeInMillis());
						}
						paintDateChips();
						resetFindToFirst = true;
						reloadThreadMessages();
					}
				},
				cal.get(Calendar.YEAR),
				cal.get(Calendar.MONTH),
				cal.get(Calendar.DAY_OF_MONTH));
		dlg.show();
	}

	private void paintDateChips() {
		DateFormat dayFmt = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault());
		if (filterFrom != null) {
			if (filterFromMs != null) {
				filterFrom.setText(dayFmt.format(new Date(filterFromMs.longValue())));
			} else {
				filterFrom.setText("From");
			}
		}
		if (filterTo != null) {
			if (filterUntilExclusiveMs != null) {
				filterTo.setText(dayFmt.format(new Date(filterUntilExclusiveMs.longValue() - 1L)));
			} else {
				filterTo.setText("To");
			}
		}
		boolean seven = filterFromMs != null && filterUntilExclusiveMs == null
				&& filterFromMs.longValue() == startOfLocalDay(-6);
		if (filter7d != null) {
			filter7d.setBackgroundColor(seven ? 0x663A8A8A : 0x332A3A4A);
		}
		boolean all = filterFromMs == null && filterUntilExclusiveMs == null;
		if (filterAll != null) {
			filterAll.setBackgroundColor(all ? 0x663A8A8A : 0x332A3A4A);
		}
	}

	private long startOfLocalDay(int offsetDays) {
		Calendar c = Calendar.getInstance();
		c.set(Calendar.HOUR_OF_DAY, 0);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);
		c.add(Calendar.DAY_OF_YEAR, offsetDays);
		return c.getTimeInMillis();
	}

	private boolean threadFilterActive() {
		return filterFromMs != null || filterUntilExclusiveMs != null;
	}

	private void reloadThreadMessages() {
		if (openThreadId == null) {
			return;
		}
		boolean dates = threadFilterActive();
		int limit = dates ? Integer.MAX_VALUE : MESSAGE_LIMIT;
		List<ChatMessage> msgs = store().messages(openThreadId, limit, null,
				filterFromMs, filterUntilExclusiveMs);
		boolean findOn = threadSearchQuery != null && threadSearchQuery.trim().length() > 0;
		populateMessages(msgs, !dates && !findOn);
	}

	private void clearThreadFilter() {
		threadSearchQuery = "";
		filterFromMs = null;
		filterUntilExclusiveMs = null;
		if (threadSearchBox != null) {
			threadSearchBox.setText("");
		}
		paintDateChips();
	}

	private int drawerWidthPx() {
		if (drawer != null && drawer.getLayoutParams() != null
				&& drawer.getLayoutParams().width > 0) {
			return drawer.getLayoutParams().width;
		}
		if (drawer != null && drawer.getWidth() > 0) {
			return drawer.getWidth();
		}
		MainWindow activity = host.getMainWindow();
		if (activity == null) {
			return 1;
		}
		return (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.80f);
	}

	private void slideIn() {
		if (root == null || drawer == null) {
			return;
		}
		visible = true;
		root.setVisibility(View.VISIBLE);
		host.onChatVisibilityChanged();
		bringUnderChrome();
		MainWindow activity = host.getMainWindow();
		if (activity != null && activity.getChromeController() != null) {
			onImeLift(activity.getChromeController().getImeLiftPx());
			drawer.post(new Runnable() {
				@Override
				public void run() {
					if (visible && activity.getChromeController() != null) {
						onImeLift(activity.getChromeController().getImeLiftPx());
					}
				}
			});
		}
		drawer.animate().cancel();
		drawer.setTranslationX(-drawerWidthPx());
		drawer.animate().translationX(0f).setDuration(SLIDE_MS)
				.withEndAction(new Runnable() {
					@Override
					public void run() {
						if (visible) {
							host.onChatVisibilityChanged();
						}
					}
				}).start();
	}

	private void slideOut() {
		if (root == null || drawer == null) {
			hideImmediate();
			return;
		}
		animating = true;
		drawer.animate().cancel();
		drawer.animate().translationX(-drawerWidthPx()).setDuration(SLIDE_MS)
				.withEndAction(new Runnable() {
					@Override
					public void run() {
						hideImmediate();
					}
				}).start();
	}

	private void hideImmediate() {
		if (openThreadId != null) {
			persistThreadEdits(false);
		}
		animating = false;
		visible = false;
		openThreadId = null;
		settingsOpen = false;
		replyBoundFromUnfilledTrigger = false;
		if (root != null) {
			root.setVisibility(View.GONE);
		}
		if (drawer != null) {
			drawer.setTranslationX(0f);
		}
		MainWindow activity = host.getMainWindow();
		if (activity != null && replyBox != null) {
			android.view.inputmethod.InputMethodManager imm =
					(android.view.inputmethod.InputMethodManager)
							activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
			if (imm != null) {
				imm.hideSoftInputFromWindow(replyBox.getWindowToken(), 0);
			}
		}
		host.onChatVisibilityChanged();
	}

	private void showInbox() {
		if (openThreadId != null) {
			persistThreadEdits(false);
		}
		openThreadId = null;
		settingsOpen = false;
		replyBoundFromUnfilledTrigger = false;
		if (backBtn != null) {
			backBtn.setVisibility(View.GONE);
		}
		if (titleView != null) {
			titleView.setText("Chat");
		}
		if (inboxView != null) {
			inboxView.setVisibility(View.VISIBLE);
		}
		if (threadView != null) {
			threadView.setVisibility(View.GONE);
		}
		applySettingsVisibility();
		applyFindNav();
		populateInbox();
	}

	private void showThread(String threadId, boolean resetReply) {
		if (threadId == null || threadId.length() == 0) {
			showInbox();
			return;
		}
		boolean switched = openThreadId == null || !threadId.equals(openThreadId);
		if (switched && openThreadId != null) {
			persistThreadEdits(false);
		}
		openThreadId = threadId;
		ChatStore store = store();
		store.markSeen(threadId);
		if (switched) {
			clearThreadFilter();
		}
		if (backBtn != null) {
			backBtn.setVisibility(View.VISIBLE);
		}
		if (inboxView != null) {
			inboxView.setVisibility(View.GONE);
		}
		if (threadView != null) {
			threadView.setVisibility(View.VISIBLE);
		}
		String title = threadId;
		List<ChatThreadSummary> threads = store.listThreads();
		if (threads != null) {
			for (int i = 0; i < threads.size(); i++) {
				ChatThreadSummary s = threads.get(i);
				if (s != null && threadId.equals(s.getThreadId())) {
					if (s.getTitle() != null && s.getTitle().length() > 0) {
						title = s.getTitle();
					}
					break;
				}
			}
		}
		if (titleView != null) {
			titleView.setText(title);
		}
		if (switched && templateBox != null) {
			if (settingsOpen) {
				bindTemplateFromTriggerOrStore();
			} else {
				String tmpl = store.replyTemplate(threadId);
				templateBox.setText(tmpl == null ? "" : tmpl);
			}
		}
		applySettingsVisibility();
		bindMineRow(switched);
		reloadThreadMessages();
		if (resetReply && replyBox != null) {
			replyBox.setText("");
			replyBox.requestFocus();
		}
	}

	private void populateInbox() {
		if (threadList == null) {
			return;
		}
		threadList.removeAllViews();
		MainWindow activity = host.getMainWindow();
		if (activity == null) {
			return;
		}
		List<ChatThreadSummary> rows = filterInbox(store());
		boolean empty = rows.isEmpty();
		if (inboxEmpty != null) {
			inboxEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
		}
		LayoutInflater inflater = LayoutInflater.from(activity);
		DateFormat timeFmt = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault());
		for (int i = 0; i < rows.size(); i++) {
			final ChatThreadSummary row = rows.get(i);
			if (row == null) {
				continue;
			}
			View item = inflater.inflate(R.layout.chat_thread_row, threadList, false);
			TextView title = (TextView) item.findViewById(R.id.chat_row_title);
			TextView preview = (TextView) item.findViewById(R.id.chat_row_preview);
			TextView when = (TextView) item.findViewById(R.id.chat_row_when);
			TextView unread = (TextView) item.findViewById(R.id.chat_row_unread);
			if (title != null) {
				String label = row.getTitle();
				if (label == null || label.length() == 0) {
					label = row.getThreadId();
				}
				title.setText(label);
			}
			if (preview != null) {
				preview.setText(row.getLastBody() == null ? "" : row.getLastBody());
			}
			if (when != null && row.getLastWhenMs() > 0) {
				when.setText(timeFmt.format(new Date(row.getLastWhenMs())));
			}
			if (unread != null) {
				if (row.getUnreadCount() > 0) {
					unread.setVisibility(View.VISIBLE);
					unread.setText(Integer.toString(row.getUnreadCount()));
				} else {
					unread.setVisibility(View.GONE);
				}
			}
			item.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showThread(row.getThreadId(), true);
				}
			});
			item.setOnLongClickListener(new View.OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					confirmDeleteConversation(row.getThreadId());
					return true;
				}
			});
			threadList.addView(item);
		}
	}

	private List<ChatThreadSummary> filterInbox(ChatStore store) {
		List<ChatThreadSummary> listed = store.listThreads();
		if (listed == null) {
			listed = java.util.Collections.emptyList();
		}
		String q = searchQuery == null ? "" : searchQuery.trim();
		if (q.length() == 0) {
			return listed;
		}
		String needle = q.toLowerCase(Locale.US);
		LinkedHashMap<String, ChatThreadSummary> byId =
				new LinkedHashMap<String, ChatThreadSummary>();
		for (int i = 0; i < listed.size(); i++) {
			ChatThreadSummary s = listed.get(i);
			if (s == null) {
				continue;
			}
			String title = s.getTitle() == null ? "" : s.getTitle();
			String body = s.getLastBody() == null ? "" : s.getLastBody();
			String id = s.getThreadId() == null ? "" : s.getThreadId();
			if (title.toLowerCase(Locale.US).contains(needle)
					|| body.toLowerCase(Locale.US).contains(needle)
					|| id.toLowerCase(Locale.US).contains(needle)) {
				byId.put(s.getThreadId(), s);
			}
		}
		List<ChatMessage> hits = store.search(q, null, null);
		if (hits != null) {
			for (int i = 0; i < hits.size(); i++) {
				ChatMessage m = hits.get(i);
				if (m == null || m.getThreadId() == null) {
					continue;
				}
				if (byId.containsKey(m.getThreadId())) {
					continue;
				}
				String title = m.getTitle();
				if (title == null || title.length() == 0) {
					title = m.getThreadId();
				}
				byId.put(m.getThreadId(), new ChatThreadSummary(m.getThreadId(),
						title, m.getBody(), m.getWhenMs(), 0));
			}
		}
		return new ArrayList<ChatThreadSummary>(byId.values());
	}

	private void populateMessages(List<ChatMessage> messages, boolean scrollToEnd) {
		if (messageList == null) {
			return;
		}
		findHits.clear();
		messageList.removeAllViews();
		MainWindow activity = host.getMainWindow();
		if (activity == null) {
			return;
		}
		if (messages == null) {
			messages = java.util.Collections.emptyList();
		}
		String needle = threadSearchQuery == null ? ""
				: threadSearchQuery.trim().toLowerCase(Locale.US);
		if (threadEmpty != null) {
			boolean emptyFilter = messages.isEmpty() && threadFilterActive();
			threadEmpty.setVisibility(emptyFilter ? View.VISIBLE : View.GONE);
		}
		LayoutInflater inflater = LayoutInflater.from(activity);
		DateFormat dayFmt = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault());
		DateFormat timeFmt = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault());
		float density = activity.getResources().getDisplayMetrics().density;
		int lastDay = Integer.MIN_VALUE;
		Calendar cal = Calendar.getInstance();
		ChatStore store = store();
		String threadTitle = openThreadId == null ? "" : openThreadId;
		for (int i = 0; i < messages.size(); i++) {
			ChatMessage m = messages.get(i);
			if (m == null) {
				continue;
			}
			View row = inflater.inflate(R.layout.chat_message_row, messageList, false);
			TextView day = (TextView) row.findViewById(R.id.chat_msg_day);
			LinearLayout align = (LinearLayout) row.findViewById(R.id.chat_msg_align);
			LinearLayout bubble = (LinearLayout) row.findViewById(R.id.chat_msg_bubble);
			TextView title = (TextView) row.findViewById(R.id.chat_msg_title);
			TextView body = (TextView) row.findViewById(R.id.chat_msg_body);
			TextView when = (TextView) row.findViewById(R.id.chat_msg_when);
			cal.setTimeInMillis(m.getWhenMs());
			int dayKey = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR);
			if (day != null && m.getWhenMs() > 0 && dayKey != lastDay) {
				day.setVisibility(View.VISIBLE);
				day.setText(dayFmt.format(new Date(m.getWhenMs())));
				lastDay = dayKey;
			}
			boolean mine = store.displayMine(m);
			boolean findHit = needle.length() > 0 && messageMatches(m, needle);
			if (align != null) {
				align.setGravity(mine ? Gravity.END : Gravity.START);
			}
			if (bubble != null) {
				int max = messageList.getWidth();
				if (max < 8) {
					max = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.80f);
				}
				int cap = (int) (max * 0.88f);
				if (body != null) {
					body.setMaxWidth(cap);
				}
				if (title != null) {
					title.setMaxWidth(cap);
				}
				GradientDrawable bg = new GradientDrawable();
				bg.setCornerRadius(14 * density);
				bg.setColor(mine ? store.mineColorArgb(openThreadId)
						: store.otherColorArgb());
				if (findHit) {
					bg.setStroke((int) (2 * density), FIND_STROKE);
				}
				bubble.setBackground(bg);
			}
			if (title != null) {
				String t = mine ? "You" : (m.getTitle() == null ? "" : m.getTitle());
				if (!mine && (t.equals(openThreadId) || t.equals(threadTitle))) {
					t = "";
				}
				title.setText(t);
				title.setVisibility(t.length() == 0 ? View.GONE : View.VISIBLE);
			}
			if (body != null) {
				body.setText(m.getBody() == null ? "" : m.getBody());
			}
			if (when != null && m.getWhenMs() > 0) {
				when.setText(timeFmt.format(new Date(m.getWhenMs())));
			}
			messageList.addView(row);
			if (findHit) {
				findHits.add(row);
			}
		}
		boolean jumpToFind = needle.length() > 0
				&& (resetFindToFirst || findHitIndex < 0);
		finishFindAfterPopulate(needle);
		if (needle.length() > 0) {
			if (jumpToFind && !findHits.isEmpty()) {
				scrollToFindHit();
			}
		} else if (scrollToEnd && messageScroll != null) {
			messageScroll.post(new Runnable() {
				@Override
				public void run() {
					if (messageScroll != null) {
						messageScroll.fullScroll(View.FOCUS_DOWN);
					}
				}
			});
		}
	}

	private static boolean messageMatches(ChatMessage m, String needleLower) {
		if (m == null || needleLower == null || needleLower.length() == 0) {
			return false;
		}
		String body = m.getBody() == null ? "" : m.getBody().toLowerCase(Locale.US);
		String title = m.getTitle() == null ? "" : m.getTitle().toLowerCase(Locale.US);
		return body.indexOf(needleLower) >= 0 || title.indexOf(needleLower) >= 0;
	}

	private void finishFindAfterPopulate(String needleLower) {
		if (needleLower == null || needleLower.length() == 0) {
			findHitIndex = -1;
			applyFindNav();
			return;
		}
		if (findHits.isEmpty()) {
			findHitIndex = -1;
			applyFindNav();
			return;
		}
		if (resetFindToFirst || findHitIndex < 0 || findHitIndex >= findHits.size()) {
			findHitIndex = 0;
		}
		resetFindToFirst = false;
		applyFindHighlightStyles();
		applyFindNav();
	}

	private void applyFindHighlightStyles() {
		MainWindow activity = host.getMainWindow();
		if (activity == null) {
			return;
		}
		float d = activity.getResources().getDisplayMetrics().density;
		for (int i = 0; i < findHits.size(); i++) {
			View row = findHits.get(i);
			LinearLayout bubble = (LinearLayout) row.findViewById(R.id.chat_msg_bubble);
			if (bubble == null || !(bubble.getBackground() instanceof GradientDrawable)) {
				continue;
			}
			GradientDrawable bg = (GradientDrawable) bubble.getBackground();
			boolean current = i == findHitIndex;
			bg.setStroke((int) ((current ? 3 : 2) * d),
					current ? FIND_STROKE_CURRENT : FIND_STROKE);
		}
	}

	private void applyFindNav() {
		boolean show = threadSearchQuery != null && threadSearchQuery.trim().length() > 0;
		if (findNav != null) {
			findNav.setVisibility(show ? View.VISIBLE : View.GONE);
		}
		if (!show || findCount == null) {
			return;
		}
		if (findHits.isEmpty() || findHitIndex < 0) {
			findCount.setText("0/0");
		} else {
			findCount.setText((findHitIndex + 1) + "/" + findHits.size());
		}
	}

	private void moveFindHit(int delta) {
		if (findHits.isEmpty()) {
			return;
		}
		int n = findHits.size();
		if (findHitIndex < 0) {
			findHitIndex = delta > 0 ? 0 : n - 1;
		} else {
			findHitIndex = (findHitIndex + delta) % n;
			if (findHitIndex < 0) {
				findHitIndex += n;
			}
		}
		applyFindHighlightStyles();
		applyFindNav();
		scrollToFindHit();
	}

	private void scrollToFindHit() {
		if (messageScroll == null || findHitIndex < 0 || findHitIndex >= findHits.size()) {
			return;
		}
		final View row = findHits.get(findHitIndex);
		messageScroll.post(new Runnable() {
			@Override
			public void run() {
				if (messageScroll == null || row.getParent() == null) {
					return;
				}
				int y = row.getTop();
				int h = row.getHeight();
				int vis = messageScroll.getHeight();
				int target = y - Math.max(0, (vis - h) / 2);
				if (target < 0) {
					target = 0;
				}
				messageScroll.smoothScrollTo(0, target);
			}
		});
	}

	private void persistThreadEdits(boolean includeTrigger) {
		saveMineName();
		saveSendableTemplateToStore();
		if (includeTrigger || settingsOpen) {
			saveTriggerTemplateFromBox();
		}
	}

	private void saveSettings() {
		saveMineName();
		if (openThreadId == null) {
			return;
		}
		String tmpl = templateText();
		saveSendableTemplateToStore();
		saveTriggerTemplateFromBox();
		MainWindow activity = host.getMainWindow();
		if (activity != null) {
			Toast.makeText(activity, tmpl.length() == 0
					? "Reply template cleared"
					: "Saved", Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * Chat.json is what Send uses. Do not store unsubstituted {@code $1}
	 * capture templates there — the next Send would refuse leftover {@code $1}.
	 */
	private void saveSendableTemplateToStore() {
		if (openThreadId == null || templateBox == null) {
			return;
		}
		String tmpl = templateText();
		if (tmpl.length() > 0 && !replyTemplateReadyToSend(tmpl)) {
			return;
		}
		String existing = store().replyTemplate(openThreadId);
		if (existing == null) {
			existing = "";
		}
		if (!tmpl.equals(existing)) {
			store().setReplyTemplate(openThreadId, tmpl);
		}
	}

	/**
	 * Do not write a seeded sendable line over a trigger that still has
	 * {@code $1}, unless the box was edited away from that seed.
	 */
	private void saveTriggerTemplateFromBox() {
		if (openThreadId == null) {
			return;
		}
		String box = templateText();
		if (replyBoundFromUnfilledTrigger && replyTemplateReadyToSend(box)) {
			String seed = boundStoreReplyFallback == null ? "" : boundStoreReplyFallback.trim();
			if (box.equals(seed)) {
				return;
			}
		}
		host.saveChatTriggerReplyTemplate(openThreadId, box);
	}

	private String templateText() {
		if (templateBox == null || templateBox.getText() == null) {
			return "";
		}
		return templateBox.getText().toString().trim();
	}

	private void bindTemplateFromTriggerOrStore() {
		if (templateBox == null || openThreadId == null) {
			return;
		}
		replyBoundFromUnfilledTrigger = false;
		String trigger = host.chatTriggerReplyTemplate(openThreadId);
		if (trigger != null && replyTemplateReadyToSend(trigger)) {
			templateBox.setText(trigger);
			boundStoreReplyFallback = "";
			return;
		}
		if (trigger != null && trigger.length() > 0) {
			replyBoundFromUnfilledTrigger = true;
		}
		String tmpl = store().replyTemplate(openThreadId);
		boundStoreReplyFallback = tmpl == null ? "" : tmpl;
		templateBox.setText(boundStoreReplyFallback);
	}

	private void sendReply() {
		MainWindow activity = host.getMainWindow();
		if (activity == null || openThreadId == null || replyBox == null) {
			return;
		}
		String text = replyBox.getText() == null ? "" : replyBox.getText().toString();
		ChatStore store = store();
		String template = store.replyTemplate(openThreadId);
		if (templateBox != null) {
			String typed = templateText();
			if (typed.length() > 0 && replyTemplateReadyToSend(typed)
					&& !typed.equals(template)) {
				store.setReplyTemplate(openThreadId, typed);
				template = typed;
			}
		}
		if (template == null || template.trim().length() == 0) {
			Toast.makeText(activity,
					"Set a reply template on the Send to thread action (e.g. tell $1 $text)",
					Toast.LENGTH_LONG).show();
			return;
		}
		String line = fillReply(template, text);
		if (line.trim().length() == 0) {
			return;
		}
		if (replyLooksUnfilled(line)) {
			Toast.makeText(activity,
					"Reply still has $1. Use $text for what you type, or put the name in the template (tell Bob $text).",
					Toast.LENGTH_LONG).show();
			return;
		}
		saveMineName();
		String typed = text.trim();
		if (typed.length() > 0) {
			store.appendOutgoing(openThreadId, "You", typed);
		}
		host.sendCommand(line);
		replyBox.setText("");
		reloadThreadMessages();
	}

	private boolean inboxHasThreadId(String threadId) {
		if (threadId == null || threadId.length() == 0) {
			return false;
		}
		List<ChatThreadSummary> listed = store().listThreads();
		if (listed == null) {
			return false;
		}
		for (int i = 0; i < listed.size(); i++) {
			ChatThreadSummary s = listed.get(i);
			if (s != null && threadId.equals(s.getThreadId())) {
				return true;
			}
		}
		return false;
	}

	private void confirmDeleteConversation(final String threadId) {
		if (threadId == null || threadId.length() == 0) {
			return;
		}
		MainWindow activity = host.getMainWindow();
		if (activity == null) {
			return;
		}
		AlertDialog.Builder b = new AlertDialog.Builder(activity);
		b.setTitle("Delete conversation?");
		b.setMessage("Messages in this conversation are removed. The Send to thread trigger is kept.");
		b.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				deleteConversation(threadId);
			}
		});
		b.setNegativeButton("Cancel", null);
		b.show();
	}

	private void deleteConversation(String threadId) {
		boolean wasOpen = openThreadId != null && openThreadId.equals(threadId);
		if (wasOpen) {
			openThreadId = null;
			settingsOpen = false;
			replyBoundFromUnfilledTrigger = false;
		}
		store().deleteThread(threadId);
		if (wasOpen) {
			showInbox();
		} else {
			populateInbox();
		}
		MainWindow activity = host.getMainWindow();
		if (activity != null) {
			Toast.makeText(activity, "Conversation deleted", Toast.LENGTH_SHORT).show();
		}
	}

	private ChatStore store() {
		MainWindow activity = host.getMainWindow();
		String display = host.getConnectionDisplay();
		return ChatStore.forWorld(activity, display);
	}

	private void bindStore() {
		store();
	}

	private void bringUnderChrome() {
		MainWindow activity = host.getMainWindow();
		if (activity == null || root == null) {
			return;
		}
		ChromeController chrome = activity.getChromeController();
		if (chrome != null) {
			chrome.bringViewUnderChrome(root);
		} else {
			View chromeView = activity.findViewById(R.id.gameplay_chrome_overlay);
			if (chromeView != null) {
				chromeView.bringToFront();
			}
		}
	}
}
