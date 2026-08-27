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

import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
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

	public interface Host {
		MainWindow getMainWindow();

		String getConnectionDisplay();

		/** Same path as a typed line: encoding + CRLF through {@code sendData}. */
		void sendCommand(String text);
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
	private TextView templateSet;
	private ScrollView messageScroll;
	private LinearLayout messageList;
	private EditText replyBox;
	private TextView sendBtn;
	private String openThreadId;
	private String searchQuery = "";
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
	 * {@code .chat vermin} — open the drawer on that thread. Does not toggle
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
		if (!visible) {
			showThread(threadId, true);
			slideIn();
			return;
		}
		showThread(threadId, true);
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
	 * children with the IME). Pad the drawer so the reply field stays above
	 * the keyboard.
	 */
	public void onImeLift(int liftPx) {
		if (root == null) {
			return;
		}
		root.setTranslationY(0f);
		if (drawer != null) {
			int pad = Math.max(0, liftPx);
			drawer.setPadding(drawer.getPaddingLeft(), drawer.getPaddingTop(),
					drawer.getPaddingRight(), pad);
		}
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
	}

	/**
	 * Fill {@code $text} in a reply template. {@code $name} / {@code $1} are
	 * the trigger's job when it stores the template.
	 */
	public static String fillReply(String template, String text) {
		if (template == null) {
			return "";
		}
		return template.replace("$text", text == null ? "" : text);
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
		templateSet = (TextView) root.findViewById(R.id.chat_template_set);
		messageScroll = (ScrollView) root.findViewById(R.id.chat_message_scroll);
		messageList = (LinearLayout) root.findViewById(R.id.chat_message_list);
		replyBox = (EditText) root.findViewById(R.id.chat_reply);
		sendBtn = (TextView) root.findViewById(R.id.chat_send);

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
		if (templateSet != null) {
			templateSet.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					saveTemplate();
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
		bringUnderChrome();
		drawer.animate().cancel();
		drawer.setTranslationX(-drawerWidthPx());
		drawer.animate().translationX(0f).setDuration(SLIDE_MS).start();
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
		animating = false;
		visible = false;
		openThreadId = null;
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
	}

	private void showInbox() {
		openThreadId = null;
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
		populateInbox();
	}

	private void showThread(String threadId, boolean resetReply) {
		if (threadId == null || threadId.length() == 0) {
			showInbox();
			return;
		}
		openThreadId = threadId;
		ChatStore store = store();
		store.markSeen(threadId);
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
		if (templateBox != null) {
			String tmpl = store.replyTemplate(threadId);
			templateBox.setText(tmpl == null ? "" : tmpl);
		}
		populateMessages(store.messages(threadId, MESSAGE_LIMIT));
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

	private void populateMessages(List<ChatMessage> messages) {
		if (messageList == null) {
			return;
		}
		messageList.removeAllViews();
		MainWindow activity = host.getMainWindow();
		if (activity == null) {
			return;
		}
		if (messages == null) {
			messages = java.util.Collections.emptyList();
		}
		LayoutInflater inflater = LayoutInflater.from(activity);
		DateFormat dayFmt = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault());
		DateFormat timeFmt = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault());
		int lastDay = Integer.MIN_VALUE;
		Calendar cal = Calendar.getInstance();
		for (int i = 0; i < messages.size(); i++) {
			ChatMessage m = messages.get(i);
			if (m == null) {
				continue;
			}
			View row = inflater.inflate(R.layout.chat_message_row, messageList, false);
			TextView day = (TextView) row.findViewById(R.id.chat_msg_day);
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
			if (title != null) {
				String t = m.getTitle();
				title.setText(t == null ? "" : t);
				title.setVisibility(t == null || t.length() == 0 ? View.GONE : View.VISIBLE);
			}
			if (body != null) {
				body.setText(m.getBody() == null ? "" : m.getBody());
			}
			if (when != null && m.getWhenMs() > 0) {
				when.setText(timeFmt.format(new Date(m.getWhenMs())));
			}
			messageList.addView(row);
		}
		if (messageScroll != null) {
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

	private void saveTemplate() {
		if (openThreadId == null || templateBox == null) {
			return;
		}
		String tmpl = templateBox.getText() == null ? "" : templateBox.getText().toString().trim();
		store().setReplyTemplate(openThreadId, tmpl);
		MainWindow activity = host.getMainWindow();
		if (activity != null) {
			Toast.makeText(activity, tmpl.length() == 0
					? "Reply template cleared"
					: "Reply template saved", Toast.LENGTH_SHORT).show();
		}
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
			String typed = templateBox.getText() == null ? "" : templateBox.getText().toString();
			if (typed.trim().length() > 0 && !typed.equals(template)) {
				store.setReplyTemplate(openThreadId, typed.trim());
				template = typed.trim();
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
		host.sendCommand(line);
		replyBox.setText("");
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
