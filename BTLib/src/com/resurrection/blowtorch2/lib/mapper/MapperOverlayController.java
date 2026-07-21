package com.resurrection.blowtorch2.lib.mapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.window.MainWindow;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Attaches the mapper overlay to {@link MainWindow}'s {@code window_container}
 * (below {@code gameplay_chrome_overlay} so ⋮ stays tappable).
 */
public class MapperOverlayController
		implements MapperUiBridge, MapperController.Listener {

	public interface Host {
		MainWindow getMainWindow();
		String getRecentBufferText(int maxLines);
		void sendMapperPath(List<String> commands);
		/** Pull live map JSON from the :stellar Connection (may be empty). */
		String fetchMapperSnapshotJson();
		/** Run a `.map …` subcommand in the service process (e.g. "record toggle"). */
		void runMapCommand(String args);
	}

	private final Host host;
	private MapperController controller;
	private View overlayRoot;
	private MapperView mapperView;
	private TextView titleView;
	private LinearLayout toolbar;
	private HorizontalScrollView toolbarScroll;
	private TextView toolbarHintLeft;
	private TextView toolbarHintRight;
	private View resizeHandle;
	private View dragHandle;
	private TextView modeFloatBtn;
	private TextView modeFullBtn;
	private TextView modeBrowseBtn;
	private TextView modeEditBtn;
	private MudMap snapshotMap;
	private boolean snapshotRecording;
	private boolean snapshotFollow = true;
	private int snapshotOpacity = 85;
	private String snapshotToolbar = MapperController.DEFAULT_TOOLBAR;
	private boolean attached;
	private boolean visible;
	private boolean fullscreen;
	private int floatWidth;
	private int floatHeight;
	private int floatX;
	private int floatY;
	private String selectedTileId;
	/** When true: tap FROM tile, then TO tile → pick walk verb to link/unlink. */
	private boolean linkEditMode;
	private String linkFromTileId;
	/** When true: tap empty cell to place tiles; long-press places + sets here. */
	private boolean drawEditMode;
	/** Spread layout for arrows (true) vs packed neighbors (false). */
	private boolean pathsLayout = true;
	/**
	 * Session Browse/Edit flag. UI process often has no live MapperController
	 * (service owns it) — keep local copy and sync via {@code .map mode} + snapshot.
	 */
	private boolean sessionEditMode;

	public MapperOverlayController(Host host) {
		this.host = host;
	}

	public void bind(MapperController controller) {
		if (this.controller != null) {
			this.controller.removeListener(this);
			if (this.controller.getUiBridge() == this) {
				this.controller.setUiBridge(null);
			}
		}
		this.controller = controller;
		if (controller != null) {
			controller.addListener(this);
			controller.setUiBridge(this);
			fullscreen = !controller.isPreferFloat();
			applyOpacity();
			refreshFromController();
		}
	}

	public MapperController getController() {
		return controller;
	}

	public boolean isVisible() {
		return visible;
	}

	public void toggle() {
		if (visible) {
			close();
		} else {
			open();
		}
	}

	public void open() {
		ensureAttached();
		if (overlayRoot == null) {
			return;
		}
		visible = true;
		overlayRoot.setVisibility(View.VISIBLE);
		applyLayoutMode();
		pullSnapshotFromService();
		refreshFromController();
		bringUnderChrome();
	}

	public void close() {
		visible = false;
		if (overlayRoot != null) {
			overlayRoot.setVisibility(View.GONE);
		}
	}

	public void setFullscreen(boolean fullscreen) {
		this.fullscreen = fullscreen;
		if (controller != null) {
			controller.setPreferFloat(!fullscreen);
		} else {
			host.runMapCommand(fullscreen ? "mode fullscreen" : "mode float");
		}
		applyLayoutMode();
		updateDisplayModeToggleUi();
	}

	private void ensureAttached() {
		if (attached && overlayRoot != null && overlayRoot.getParent() != null) {
			return;
		}
		MainWindow activity = host.getMainWindow();
		if (activity == null) {
			return;
		}
		RelativeLayout container = (RelativeLayout) activity.findViewById(R.id.window_container);
		if (container == null) {
			return;
		}
		if (overlayRoot != null && overlayRoot.getParent() instanceof ViewGroup) {
			((ViewGroup) overlayRoot.getParent()).removeView(overlayRoot);
		}
		LayoutInflater inflater = LayoutInflater.from(activity);
		overlayRoot = inflater.inflate(R.layout.mapper_overlay, container, false);
		mapperView = (MapperView) overlayRoot.findViewById(R.id.mapper_view);
		titleView = (TextView) overlayRoot.findViewById(R.id.mapper_title);
		toolbar = (LinearLayout) overlayRoot.findViewById(R.id.mapper_toolbar);
		toolbarScroll = (HorizontalScrollView) overlayRoot.findViewById(R.id.mapper_toolbar_scroll);
		toolbarHintLeft = (TextView) overlayRoot.findViewById(R.id.mapper_toolbar_hint_left);
		toolbarHintRight = (TextView) overlayRoot.findViewById(R.id.mapper_toolbar_hint_right);
		wireToolbarScrollHints();
		resizeHandle = overlayRoot.findViewById(R.id.mapper_resize_handle);
		dragHandle = overlayRoot.findViewById(R.id.mapper_drag_handle);
		modeFloatBtn = (TextView) overlayRoot.findViewById(R.id.mapper_mode_float);
		modeFullBtn = (TextView) overlayRoot.findViewById(R.id.mapper_mode_full);
		modeBrowseBtn = (TextView) overlayRoot.findViewById(R.id.mapper_mode_browse);
		modeEditBtn = (TextView) overlayRoot.findViewById(R.id.mapper_mode_edit);
		TextView closeBtn = (TextView) overlayRoot.findViewById(R.id.mapper_close_btn);

		float density = activity.getResources().getDisplayMetrics().density;
		floatWidth = (int) (280 * density);
		floatHeight = (int) (320 * density);
		floatX = (int) (12 * density);
		floatY = (int) (48 * density);

		if (controller != null) {
			fullscreen = !controller.isPreferFloat();
		}

		closeBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				close();
			}
		});
		if (modeFloatBtn != null) {
			modeFloatBtn.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					setFullscreen(false);
				}
			});
		}
		if (modeFullBtn != null) {
			modeFullBtn.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					setFullscreen(true);
				}
			});
		}
		updateDisplayModeToggleUi();
		if (modeBrowseBtn != null) {
			modeBrowseBtn.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					setEditModeFromUi(false);
				}
			});
		}
		if (modeEditBtn != null) {
			modeEditBtn.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					setEditModeFromUi(true);
				}
			});
		}
		updateEditModeToggleUi();
		wireCategoryChips();
		if (titleView != null) {
			titleView.setOnLongClickListener(new View.OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					openFloorsRadial();
					return true;
				}
			});
		}

		mapperView.setTileInteractionListener(new MapperView.TileInteractionListener() {
			@Override
			public void onTileTap(MapTile tile) {
				selectedTileId = tile.getId();
				if (linkEditMode) {
					onLinkEditTap(tile);
					return;
				}
				String title = tile.getTitle() != null && tile.getTitle().length() > 0
						? tile.getTitle() : shortTileLabel(tile);
				Toast.makeText(host.getMainWindow(), title, Toast.LENGTH_SHORT).show();
			}

			@Override
			public void onTileLongPress(MapTile tile) {
				selectedTileId = tile.getId();
				showTileContext(tile);
			}

			@Override
			public void onTileDoubleTap(MapTile tile) {
				if (tile == null) {
					return;
				}
				selectedTileId = tile.getId();
				if (linkEditMode) {
					onLinkEditTap(tile);
					return;
				}
				runSetHere(tile.getId());
			}

			@Override
			public void onEmptyTap(int gridX, int gridY) {
				if (drawEditMode) {
					placeTileAt(gridX, gridY, false);
				}
			}

			@Override
			public void onEmptyLongPress(int gridX, int gridY) {
				if (drawEditMode) {
					placeTileAt(gridX, gridY, true);
				} else {
					Toast.makeText(host.getMainWindow(),
							"Turn on Draw to place tiles here", Toast.LENGTH_SHORT).show();
				}
			}

			@Override
			public void onTileDragEnd(MapTile tile, int gridX, int gridY) {
				if (tile == null) {
					return;
				}
				if (tile.getGridX() == gridX && tile.getGridY() == gridY) {
					showTileContext(tile);
					return;
				}
				runMoveTile(tile.getId(), gridX, gridY);
			}

			@Override
			public void onLinkCommandsTap(MapTile from, MapTile to, List<String> commands) {
				showLinkCommandsPopup(from, to, commands);
			}

			@Override
			public void onInterLevelExitTap(MapTile from, MapExit exit, MapTile dest) {
				jumpToInterLevelDest(dest);
			}
		});

		wireDragResize();
		if (mapperView != null) {
			mapperView.setTileDragEnabled(true);
			mapperView.setPathsLayout(pathsLayout);
		}
		rebuildToolbar();
		applyOpacity();

		RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
				RelativeLayout.LayoutParams.MATCH_PARENT,
				RelativeLayout.LayoutParams.MATCH_PARENT);
		lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
		lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
		View inputbar = container.findViewById(R.id.inputbar);
		if (inputbar != null && inputbar.getId() != View.NO_ID) {
			lp.addRule(RelativeLayout.ABOVE, R.id.inputbar);
		}
		container.addView(overlayRoot, lp);
		overlayRoot.setVisibility(View.GONE);
		attached = true;
		bringUnderChrome();
	}

	private void bringUnderChrome() {
		if (overlayRoot != null) {
			overlayRoot.bringToFront();
		}
		MainWindow activity = host.getMainWindow();
		if (activity != null) {
			View chrome = activity.findViewById(R.id.gameplay_chrome_overlay);
			if (chrome != null) {
				chrome.bringToFront();
			}
		}
	}

	private void applyLayoutMode() {
		if (overlayRoot == null) {
			return;
		}
		ViewGroup.LayoutParams raw = overlayRoot.getLayoutParams();
		if (!(raw instanceof RelativeLayout.LayoutParams)) {
			return;
		}
		RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) raw;
		MainWindow activity = host.getMainWindow();
		float density = activity.getResources().getDisplayMetrics().density;
		View inputbar = activity.findViewById(R.id.inputbar);
		if (fullscreen) {
			lp.width = RelativeLayout.LayoutParams.MATCH_PARENT;
			lp.height = RelativeLayout.LayoutParams.MATCH_PARENT;
			lp.leftMargin = 0;
			lp.topMargin = 0;
			lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
			lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
			lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
			if (inputbar != null) {
				lp.addRule(RelativeLayout.ABOVE, R.id.inputbar);
			}
			// Stay clear of input chrome — MATCH_PARENT alone was eating the toolbar.
			overlayRoot.setPadding(
					(int) (4 * density),
					(int) (8 * density),
					(int) (8 * density),
					(int) (4 * density));
			if (resizeHandle != null) {
				resizeHandle.setVisibility(View.GONE);
			}
		} else {
			lp.removeRule(RelativeLayout.ABOVE);
			lp.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
			lp.removeRule(RelativeLayout.ALIGN_PARENT_LEFT);
			lp.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT);
			lp.width = floatWidth;
			lp.height = floatHeight;
			lp.leftMargin = floatX;
			lp.topMargin = floatY;
			overlayRoot.setPadding(
					(int) (2 * density),
					(int) (2 * density),
					(int) (2 * density),
					(int) (2 * density));
			if (resizeHandle != null) {
				resizeHandle.setVisibility(View.VISIBLE);
				resizeHandle.bringToFront();
			}
		}
		overlayRoot.setLayoutParams(lp);
		updateDisplayModeToggleUi();
	}

	private void updateDisplayModeToggleUi() {
		final int active = 0xFFE8C547;
		final int inactive = 0xFF888888;
		final int activeBg = 0x33000000;
		final int clearBg = 0x00000000;
		if (modeFloatBtn != null) {
			modeFloatBtn.setTextColor(fullscreen ? inactive : active);
			modeFloatBtn.setBackgroundColor(fullscreen ? clearBg : activeBg);
			modeFloatBtn.getPaint().setFakeBoldText(!fullscreen);
			modeFloatBtn.invalidate();
		}
		if (modeFullBtn != null) {
			modeFullBtn.setTextColor(fullscreen ? active : inactive);
			modeFullBtn.setBackgroundColor(fullscreen ? activeBg : clearBg);
			modeFullBtn.getPaint().setFakeBoldText(fullscreen);
			modeFullBtn.invalidate();
		}
	}

	private void applyOpacity() {
		if (overlayRoot == null) {
			return;
		}
		int pct = controller != null ? controller.getOpacity() : snapshotOpacity;
		overlayRoot.setAlpha(Math.max(40, Math.min(100, pct)) / 100f);
	}

	private void wireDragResize() {
		if (dragHandle != null) {
			dragHandle.setOnTouchListener(new View.OnTouchListener() {
				float lastX;
				float lastY;
				@Override
				public boolean onTouch(View v, MotionEvent event) {
					if (fullscreen) {
						return false;
					}
					switch (event.getActionMasked()) {
					case MotionEvent.ACTION_DOWN:
						lastX = event.getRawX();
						lastY = event.getRawY();
						return true;
					case MotionEvent.ACTION_MOVE:
						floatX += (int) (event.getRawX() - lastX);
						floatY += (int) (event.getRawY() - lastY);
						lastX = event.getRawX();
						lastY = event.getRawY();
						applyLayoutMode();
						return true;
					default:
						return false;
					}
				}
			});
		}
		if (resizeHandle != null) {
			resizeHandle.setOnTouchListener(new View.OnTouchListener() {
				float lastX;
				float lastY;
				@Override
				public boolean onTouch(View v, MotionEvent event) {
					if (fullscreen) {
						return false;
					}
					switch (event.getActionMasked()) {
					case MotionEvent.ACTION_DOWN:
						lastX = event.getRawX();
						lastY = event.getRawY();
						return true;
					case MotionEvent.ACTION_MOVE:
						float density = host.getMainWindow().getResources()
								.getDisplayMetrics().density;
						floatWidth = Math.max((int) (180 * density),
								floatWidth + (int) (event.getRawX() - lastX));
						floatHeight = Math.max((int) (200 * density),
								floatHeight + (int) (event.getRawY() - lastY));
						lastX = event.getRawX();
						lastY = event.getRawY();
						applyLayoutMode();
						return true;
					default:
						return false;
					}
				}
			});
		}
	}

	private void wireToolbarScrollHints() {
		if (toolbarScroll == null) {
			return;
		}
		toolbarScroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
			@Override
			public void onScrollChange(View v, int scrollX, int scrollY,
					int oldScrollX, int oldScrollY) {
				updateToolbarScrollHints();
			}
		});
		if (toolbarHintLeft != null) {
			toolbarHintLeft.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (toolbarScroll != null) {
						toolbarScroll.smoothScrollBy(
								-(int) (80 * host.getMainWindow().getResources()
										.getDisplayMetrics().density), 0);
					}
				}
			});
		}
		if (toolbarHintRight != null) {
			toolbarHintRight.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (toolbarScroll != null) {
						toolbarScroll.smoothScrollBy(
								(int) (80 * host.getMainWindow().getResources()
										.getDisplayMetrics().density), 0);
					}
				}
			});
		}
	}

	private void scheduleToolbarScrollHintUpdate() {
		if (toolbarScroll == null) {
			return;
		}
		toolbarScroll.post(new Runnable() {
			@Override
			public void run() {
				updateToolbarScrollHints();
			}
		});
		toolbarScroll.getViewTreeObserver().addOnGlobalLayoutListener(
				new ViewTreeObserver.OnGlobalLayoutListener() {
					@Override
					public void onGlobalLayout() {
						toolbarScroll.getViewTreeObserver()
								.removeOnGlobalLayoutListener(this);
						updateToolbarScrollHints();
					}
				});
	}

	private void updateToolbarScrollHints() {
		if (toolbarScroll == null || toolbar == null) {
			return;
		}
		int scrollX = toolbarScroll.getScrollX();
		int viewW = toolbarScroll.getWidth();
		int contentW = toolbar.getWidth();
		boolean canScroll = contentW > viewW + 2;
		boolean moreLeft = canScroll && scrollX > 2;
		boolean moreRight = canScroll && scrollX + viewW < contentW - 2;
		if (toolbarHintLeft != null) {
			toolbarHintLeft.setVisibility(moreLeft ? View.VISIBLE : View.GONE);
		}
		if (toolbarHintRight != null) {
			// Always show › when there is overflow to the right, or a static
			// cue when content is wider than the strip at scroll start.
			toolbarHintRight.setVisibility(moreRight || (canScroll && !moreLeft)
					? View.VISIBLE : View.GONE);
		}
	}

	private Button makeToolbarButton(MainWindow activity, float density, String label) {
		Button b = new Button(activity);
		b.setText(label);
		b.setTextSize(12f);
		b.setAllCaps(false);
		b.setMinHeight(0);
		b.setMinWidth(0);
		b.setPadding((int) (8 * density), (int) (4 * density),
				(int) (8 * density), (int) (4 * density));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT,
				(int) (36 * density));
		lp.rightMargin = (int) (4 * density);
		b.setLayoutParams(lp);
		return b;
	}

	private static String actionLabel(String action, boolean recording) {
		String a = action.toLowerCase(Locale.US);
		if ("rec".equals(a) || "record".equals(a)) {
			return recording ? "Stop" : "Record";
		}
		if ("follow".equals(a)) {
			return "Follow";
		}
		if ("l-".equals(a) || "level-".equals(a) || "prev".equals(a)) {
			return "L-";
		}
		if ("l+".equals(a) || "level+".equals(a) || "next".equals(a)) {
			return "L+";
		}
		if ("find".equals(a) || "search".equals(a)) {
			return "Find";
		}
		if ("undo".equals(a)) {
			return "Undo";
		}
		if ("center".equals(a)) {
			return "Center";
		}
		if ("close".equals(a)) {
			return "Close";
		}
		if ("capture".equals(a)) {
			return "Capture";
		}
		if ("links".equals(a) || "link".equals(a)) {
			return "Links";
		}
		if ("save".equals(a) || "export".equals(a)) {
			return "Save";
		}
		return action;
	}

	private void runToolbarAction(String action) {
		String a = action.toLowerCase(Locale.US);
		if ("links".equals(a) || "link".equals(a)) {
			toggleLinkEditMode();
			return;
		}
		if ("save".equals(a) || "export".equals(a)) {
			if (controller != null) {
				toastStatus(controller.save());
			} else {
				host.runMapCommand("save");
			}
			return;
		}
		if (controller == null) {
			if ("rec".equals(a) || "record".equals(a)) {
				boolean on = snapshotRecording;
				host.runMapCommand(on ? "record off" : "record on");
			} else if ("follow".equals(a)) {
				host.runMapCommand("follow toggle");
			} else if ("l-".equals(a) || "level-".equals(a) || "prev".equals(a)) {
				host.runMapCommand("level prev");
			} else if ("l+".equals(a) || "level+".equals(a) || "next".equals(a)) {
				host.runMapCommand("level next");
			} else if ("find".equals(a) || "search".equals(a)) {
				openSearch();
				return;
			} else if ("undo".equals(a)) {
				host.runMapCommand("undo");
			} else if ("center".equals(a)) {
				centerOnPlayer();
				return;
			} else if ("close".equals(a)) {
				close();
				return;
			} else if ("capture".equals(a)) {
				openCapture();
				return;
			} else {
				Toast.makeText(host.getMainWindow(), "Unknown: " + action, Toast.LENGTH_SHORT).show();
				return;
			}
			pullSnapshotFromService();
			return;
		}
		if ("rec".equals(a) || "record".equals(a)) {
			controller.setRecording(!controller.isRecording());
			Toast.makeText(host.getMainWindow(),
					controller.isRecording() ? "Recording on" : "Recording stopped",
					Toast.LENGTH_SHORT).show();
		} else if ("follow".equals(a)) {
			controller.setFollow(!controller.isFollowPlayer());
			if (mapperView != null) {
				mapperView.setFollowMode(controller.isFollowPlayer());
			}
		} else if ("l-".equals(a) || "level-".equals(a) || "prev".equals(a)) {
			toastStatus(controller.levelPrev());
		} else if ("l+".equals(a) || "level+".equals(a) || "next".equals(a)) {
			toastStatus(controller.levelNext());
		} else if ("find".equals(a) || "search".equals(a)) {
			openSearch();
		} else if ("undo".equals(a)) {
			toastStatus(controller.undoStatus());
		} else if ("center".equals(a)) {
			centerOnPlayer();
		} else if ("close".equals(a)) {
			close();
		} else if ("capture".equals(a)) {
			openCapture();
		} else {
			Toast.makeText(host.getMainWindow(), "Unknown: " + action, Toast.LENGTH_SHORT).show();
		}
		refreshFromController();
	}

	private void toggleLinkEditMode() {
		if (!isControllerEditMode()) {
			Toast.makeText(host.getMainWindow(), "Switch to Edit mode first",
					Toast.LENGTH_SHORT).show();
			return;
		}
		linkEditMode = !linkEditMode;
		linkFromTileId = null;
		if (linkEditMode && drawEditMode) {
			drawEditMode = false;
			if (mapperView != null) {
				mapperView.setShowGrid(false);
			}
		}
		if (linkEditMode) {
			Toast.makeText(host.getMainWindow(),
					"Links: tap FROM tile, then TO tile", Toast.LENGTH_LONG).show();
		} else {
			Toast.makeText(host.getMainWindow(), "Links mode off", Toast.LENGTH_SHORT).show();
		}
		updateTitleForLinkMode();
		rebuildToolbar();
	}

	private void togglePathsLayout() {
		pathsLayout = !pathsLayout;
		if (mapperView != null) {
			mapperView.setPathsLayout(pathsLayout);
		}
		if (pathsLayout) {
			Toast.makeText(host.getMainWindow(),
					"Paths: tiles spaced so exit arrows/labels show",
					Toast.LENGTH_SHORT).show();
		} else {
			Toast.makeText(host.getMainWindow(),
					"Pack: tiles compressed next to each other",
					Toast.LENGTH_SHORT).show();
		}
		rebuildToolbar();
	}

	private void toggleDrawEditMode() {
		if (!isControllerEditMode()) {
			Toast.makeText(host.getMainWindow(), "Switch to Edit mode first",
					Toast.LENGTH_SHORT).show();
			return;
		}
		drawEditMode = !drawEditMode;
		if (drawEditMode && linkEditMode) {
			linkEditMode = false;
			linkFromTileId = null;
		}
		if (mapperView != null) {
			mapperView.setShowGrid(drawEditMode);
			mapperView.setTileDragEnabled(true);
		}
		if (drawEditMode) {
			Toast.makeText(host.getMainWindow(),
					"Draw: tap empty=place · long-press empty=Here",
					Toast.LENGTH_LONG).show();
		} else {
			Toast.makeText(host.getMainWindow(), "Draw mode off", Toast.LENGTH_SHORT).show();
		}
		updateTitleForDrawMode();
		rebuildToolbar();
	}

	private void placeTileAt(final int gridX, final int gridY, final boolean setHere) {
		promptTileTitle(new TitleCallback() {
			@Override
			public void onTitle(String title) {
				if (controller != null) {
					toastStatus(controller.placeTile(Integer.valueOf(gridX),
							Integer.valueOf(gridY), title, setHere));
					refreshFromController();
				} else {
					String args = "add " + gridX + " " + gridY;
					if (title != null && title.length() > 0) {
						args = args + " " + title;
					}
					if (setHere) {
						args = args + " here";
					}
					host.runMapCommand(args);
					pullSnapshotFromService();
				}
			}
		});
	}

	private interface TitleCallback {
		void onTitle(String title);
	}

	private void promptTileTitle(final TitleCallback cb) {
		MainWindow activity = host.getMainWindow();
		final EditText input = new EditText(activity);
		input.setHint("Room title (optional)");
		input.setSingleLine(true);
		new AlertDialog.Builder(activity)
				.setTitle("New tile")
				.setView(input)
				.setPositiveButton("Place", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						cb.onTitle(input.getText().toString().trim());
					}
				})
				.setNeutralButton("No title", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						cb.onTitle(null);
					}
				})
				.setNegativeButton("Cancel", null)
				.show();
	}

	private void runSetHere(String tileId) {
		if (controller != null) {
			toastStatus(controller.setHere(tileId));
			refreshFromController();
		} else {
			host.runMapCommand("here " + tileId);
			pullSnapshotFromService();
		}
	}

	private void runDeleteTile(String tileId) {
		if (controller != null) {
			toastStatus(controller.deleteTile(tileId));
			refreshFromController();
		} else {
			host.runMapCommand("delete " + tileId);
			pullSnapshotFromService();
		}
	}

	private void runMoveTile(String tileId, int x, int y) {
		if (controller != null) {
			toastStatus(controller.moveTileOnGrid(tileId, x, y));
			refreshFromController();
		} else {
			host.runMapCommand("move " + tileId + " " + x + " " + y);
			pullSnapshotFromService();
		}
	}

	private void promptMoveTile(final MapTile tile) {
		if (tile == null) {
			return;
		}
		MainWindow activity = host.getMainWindow();
		LinearLayout root = new LinearLayout(activity);
		root.setOrientation(LinearLayout.VERTICAL);
		int pad = (int) (12 * activity.getResources().getDisplayMetrics().density);
		root.setPadding(pad, pad, pad, pad);
		final EditText xEdit = new EditText(activity);
		xEdit.setHint("grid X");
		xEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
				| android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
		xEdit.setText(Integer.toString(tile.getGridX()));
		final EditText yEdit = new EditText(activity);
		yEdit.setHint("grid Y");
		yEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
				| android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
		yEdit.setText(Integer.toString(tile.getGridY()));
		root.addView(xEdit);
		root.addView(yEdit);
		new AlertDialog.Builder(activity)
				.setTitle("Move " + shortTileLabel(tile))
				.setView(root)
				.setPositiveButton("Move", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						try {
							int x = Integer.parseInt(xEdit.getText().toString().trim());
							int y = Integer.parseInt(yEdit.getText().toString().trim());
							runMoveTile(tile.getId(), x, y);
						} catch (NumberFormatException e) {
							Toast.makeText(activity, "Invalid coordinates",
									Toast.LENGTH_SHORT).show();
						}
					}
				})
				.setNegativeButton("Cancel", null)
				.show();
	}

	private void promptAddNeighbor(final MapTile from) {
		if (from == null) {
			return;
		}
		MainWindow activity = host.getMainWindow();
		final EditText cmdEdit = new EditText(activity);
		cmdEdit.setHint("go west / n / out …");
		cmdEdit.setSingleLine(true);
		LinearLayout root = new LinearLayout(activity);
		root.setOrientation(LinearLayout.VERTICAL);
		int pad = (int) (12 * activity.getResources().getDisplayMetrics().density);
		root.setPadding(pad, pad, pad, pad);
		TextView info = new TextView(activity);
		info.setText("Add neighbor from " + shortTileLabel(from));
		root.addView(info);
		root.addView(cmdEdit);
		HorizontalScrollView hsv = new HorizontalScrollView(activity);
		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		String[] verbs = new String[] {
				"n", "s", "e", "w", "u", "d", "in", "out",
				"go north", "go south", "go east", "go west"
		};
		for (final String verb : verbs) {
			Button qb = new Button(activity);
			qb.setText(verb);
			qb.setTextSize(11f);
			qb.setAllCaps(false);
			qb.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					cmdEdit.setText(verb);
				}
			});
			row.addView(qb);
		}
		hsv.addView(row);
		root.addView(hsv);
		new AlertDialog.Builder(activity)
				.setTitle("Add neighbor")
				.setView(root)
				.setPositiveButton("Add", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						String cmd = cmdEdit.getText().toString().trim();
						if (cmd.length() == 0) {
							Toast.makeText(activity, "Enter a walk command",
									Toast.LENGTH_SHORT).show();
							return;
						}
						if (controller != null) {
							toastStatus(controller.addNeighbor(from.getId(), cmd));
							refreshFromController();
						} else {
							host.runMapCommand("neighbor " + cmd + " from " + from.getId());
							pullSnapshotFromService();
						}
					}
				})
				.setNegativeButton("Cancel", null)
				.show();
	}

	private void updateTitleForDrawMode() {
		if (titleView == null) {
			return;
		}
		if (!drawEditMode) {
			if (!linkEditMode) {
				refreshFromController();
			}
			return;
		}
		titleView.setText("Draw · tap empty cell");
	}

	private void onLinkEditTap(MapTile tile) {
		if (tile == null) {
			return;
		}
		if (linkFromTileId == null) {
			linkFromTileId = tile.getId();
			if (mapperView != null) {
				mapperView.setSelectedTileId(tile.getId());
			}
			Toast.makeText(host.getMainWindow(),
					"FROM " + shortTileLabel(tile) + " — now tap TO",
					Toast.LENGTH_SHORT).show();
			updateTitleForLinkMode();
			return;
		}
		if (linkFromTileId.equals(tile.getId())) {
			Toast.makeText(host.getMainWindow(), "Pick a different TO tile",
					Toast.LENGTH_SHORT).show();
			return;
		}
		final String fromId = linkFromTileId;
		final MapTile toTile = tile;
		linkFromTileId = null;
		updateTitleForLinkMode();
		showLinkVerbDialog(fromId, toTile);
	}

	private void showLinkVerbDialog(final String fromId, final MapTile toTile) {
		MudMap map = controller != null ? controller.getMap() : snapshotMap;
		final MapTile from = map != null ? map.findTile(fromId) : null;
		if (from == null || toTile == null) {
			return;
		}
		MainWindow activity = host.getMainWindow();
		LinearLayout root = new LinearLayout(activity);
		root.setOrientation(LinearLayout.VERTICAL);
		int pad = (int) (12 * activity.getResources().getDisplayMetrics().density);
		root.setPadding(pad, pad, pad, pad);

		TextView info = new TextView(activity);
		info.setText("Walk command from " + shortTileLabel(from)
				+ " → " + shortTileLabel(toTile)
				+ "\n(e.g. go west, n, out)");
		root.addView(info);

		final EditText cmdEdit = new EditText(activity);
		cmdEdit.setHint("go west");
		cmdEdit.setSingleLine(true);
		root.addView(cmdEdit);

		LinearLayout quick = new LinearLayout(activity);
		quick.setOrientation(LinearLayout.HORIZONTAL);
		String[] verbs = new String[] {
				"n", "s", "e", "w", "u", "d", "in", "out",
				"go north", "go south", "go east", "go west", "go out"
		};
		// Wrap quick verbs in a horizontal scroll via nested layout — keep a short row
		HorizontalScrollView hsv = new HorizontalScrollView(activity);
		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		for (final String verb : verbs) {
			Button qb = new Button(activity);
			qb.setText(verb);
			qb.setTextSize(11f);
			qb.setAllCaps(false);
			qb.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					cmdEdit.setText(verb);
					cmdEdit.setSelection(verb.length());
				}
			});
			row.addView(qb);
		}
		hsv.addView(row);
		root.addView(hsv);

		// Existing exits on FROM for unlink
		if (from.getExits() != null && !from.getExits().isEmpty()) {
			TextView exLabel = new TextView(activity);
			exLabel.setText("Existing exits (tap to unlink):");
			exLabel.setPadding(0, pad, 0, 0);
			root.addView(exLabel);
			for (final MapExit exit : from.getExits()) {
				if (exit == null || exit.getCommand() == null) {
					continue;
				}
				Button ub = new Button(activity);
				String dest = exit.getToId() != null ? shortId(exit.getToId()) : "?";
				ub.setText("Unlink " + exit.getCommand() + " → " + dest);
				ub.setAllCaps(false);
				ub.setTextSize(12f);
				ub.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						runUnlink(fromId, exit.getCommand());
					}
				});
				root.addView(ub);
			}
		}

		final AlertDialog dialog = new AlertDialog.Builder(activity)
				.setTitle("Add link")
				.setView(root)
				.setPositiveButton("Link", null)
				.setNegativeButton("Cancel", null)
				.create();
		dialog.setOnShowListener(new DialogInterface.OnShowListener() {
			@Override
			public void onShow(DialogInterface d) {
				dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
						new View.OnClickListener() {
							@Override
							public void onClick(View v) {
								String cmd = cmdEdit.getText().toString().trim();
								if (cmd.length() == 0) {
									Toast.makeText(activity, "Enter a walk command",
											Toast.LENGTH_SHORT).show();
									return;
								}
								runLink(fromId, cmd, toTile.getId());
								linkEditMode = false;
								linkFromTileId = null;
								updateTitleForLinkMode();
								rebuildToolbar();
								dialog.dismiss();
							}
						});
			}
		});
		dialog.show();
	}

	private void showUnlinkPicker(final MapTile from) {
		if (from == null || from.getExits() == null || from.getExits().isEmpty()) {
			Toast.makeText(host.getMainWindow(), "No exits on this tile",
					Toast.LENGTH_SHORT).show();
			return;
		}
		final List<MapExit> exits = new ArrayList<MapExit>();
		List<CharSequence> labels = new ArrayList<CharSequence>();
		for (MapExit e : from.getExits()) {
			if (e == null || e.getCommand() == null) {
				continue;
			}
			exits.add(e);
			labels.add(e.getCommand() + " → " + shortId(e.getToId()));
		}
		CharSequence[] items = labels.toArray(new CharSequence[labels.size()]);
		new AlertDialog.Builder(host.getMainWindow())
				.setTitle("Unlink exit")
				.setItems(items, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						if (which >= 0 && which < exits.size()) {
							runUnlink(from.getId(), exits.get(which).getCommand());
						}
					}
				})
				.setNegativeButton("Cancel", null)
				.show();
	}

	/** Expand overflow link label (+N) into a small menu of walk commands. */
	private void showLinkCommandsPopup(final MapTile from, final MapTile to,
			final List<String> commands) {
		if (from == null || to == null || commands == null || commands.isEmpty()) {
			return;
		}
		CharSequence[] items = new CharSequence[commands.size()];
		for (int i = 0; i < commands.size(); i++) {
			items[i] = commands.get(i);
		}
		new AlertDialog.Builder(host.getMainWindow())
				.setTitle(shortTileLabel(from) + " → " + shortTileLabel(to))
				.setItems(items, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						if (which < 0 || which >= commands.size()) {
							return;
						}
						final String cmd = commands.get(which);
						new AlertDialog.Builder(host.getMainWindow())
								.setTitle(cmd)
								.setMessage("Unlink this exit?")
								.setPositiveButton("Unlink",
										new DialogInterface.OnClickListener() {
											@Override
											public void onClick(DialogInterface d, int w) {
												runUnlink(from.getId(), cmd);
											}
										})
								.setNegativeButton("Close", null)
								.show();
					}
				})
				.setNegativeButton("Close", null)
				.show();
	}

	private void runLink(String fromId, String cmd, String toId) {
		if (controller != null) {
			toastStatus(controller.linkBetween(fromId, cmd, toId));
			refreshFromController();
		} else {
			host.runMapCommand("link " + cmd + " from " + fromId + " to " + toId);
			pullSnapshotFromService();
		}
	}

	private void runUnlink(String fromId, String cmd) {
		if (controller != null) {
			toastStatus(controller.unlinkBetween(fromId, cmd));
			refreshFromController();
		} else {
			host.runMapCommand("unlink " + cmd + " from " + fromId);
			pullSnapshotFromService();
		}
	}

	private void updateTitleForLinkMode() {
		if (titleView == null) {
			return;
		}
		if (!linkEditMode) {
			if (drawEditMode) {
				updateTitleForDrawMode();
			} else {
				refreshFromController();
			}
			return;
		}
		if (linkFromTileId == null) {
			titleView.setText("Links · tap FROM");
		} else {
			titleView.setText("Links · tap TO");
		}
	}

	private static String shortTileLabel(MapTile tile) {
		if (tile == null) {
			return "?";
		}
		if (tile.getTitle() != null && tile.getTitle().length() > 0) {
			return tile.getTitle();
		}
		return shortId(tile.getId());
	}

	private static String shortId(String id) {
		if (id == null || id.length() == 0) {
			return "?";
		}
		return id.length() > 6 ? id.substring(0, 6) : id;
	}

	private void toastStatus(String msg) {
		if (msg != null && msg.length() > 0) {
			Toast.makeText(host.getMainWindow(), msg, Toast.LENGTH_SHORT).show();
		}
	}

	private MapTile selectedOrCurrentTile() {
		MudMap map = controller != null ? controller.getMap() : snapshotMap;
		if (map == null) {
			return null;
		}
		if (selectedTileId != null) {
			MapTile t = map.findTile(selectedTileId);
			if (t != null) {
				return t;
			}
		}
		if (controller != null) {
			return controller.currentTile();
		}
		return map.findTile(map.getCurrentTileId());
	}

	private void pathToTile(MapTile tile) {
		MudMap map = controller != null ? controller.getMap() : snapshotMap;
		if (map == null || tile == null) {
			return;
		}
		String from = map.getCurrentTileId();
		List<String> path = MapPathfinder.findCommands(map, from, tile.getId());
		if (path == null || path.isEmpty()) {
			Toast.makeText(host.getMainWindow(), "No path", Toast.LENGTH_SHORT).show();
			return;
		}
		Toast.makeText(host.getMainWindow(), "Path: " + join(path, ";"), Toast.LENGTH_LONG).show();
		boolean auto = controller != null && controller.isPathAutoSend();
		if (auto) {
			host.sendMapperPath(path);
		}
	}

	private void showTileContext(final MapTile tile) {
		final MainWindow activity = host.getMainWindow();
		CharSequence[] items = new CharSequence[] {
				"Set as Here", "Edit", "Move…", "Add neighbor…", "Edit links…",
				"Path to here", "Change level", "Delete tile", "Center"
		};
		new AlertDialog.Builder(activity)
				.setTitle(tile.getTitle() != null ? tile.getTitle() : "Tile")
				.setItems(items, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						switch (which) {
						case 0:
							runSetHere(tile.getId());
							break;
						case 1:
							openTileEditor(tile);
							break;
						case 2:
							promptMoveTile(tile);
							break;
						case 3:
							promptAddNeighbor(tile);
							break;
						case 4:
							showEditLinksMenu(tile);
							break;
						case 5:
							pathToTile(tile);
							break;
						case 6:
							promptChangeLevel(tile);
							break;
						case 7:
							confirmDeleteTile(tile);
							break;
						case 8:
							if (mapperView != null) {
								mapperView.setCurrentTileId(tile.getId());
								mapperView.centerOnTile(tile);
							}
							break;
						default:
							break;
						}
					}
				})
				.show();
	}

	/**
	 * Edit links: add (pick destination on map, then walk verb) or unlink existing.
	 */
	private void showEditLinksMenu(final MapTile from) {
		if (from == null) {
			return;
		}
		CharSequence[] items = new CharSequence[] {
				"Add link (tap destination)…",
				"Unlink existing…"
		};
		new AlertDialog.Builder(host.getMainWindow())
				.setTitle("Links from " + shortTileLabel(from))
				.setItems(items, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						if (which == 0) {
							startAddLinkPickDestination(from);
						} else if (which == 1) {
							showUnlinkPicker(from);
						}
					}
				})
				.setNegativeButton("Cancel", null)
				.show();
	}

	/** Prefill Links mode FROM this tile; next tap chooses TO, then verb dialog. */
	private void startAddLinkPickDestination(MapTile from) {
		if (from == null) {
			return;
		}
		if (!isControllerEditMode()) {
			Toast.makeText(host.getMainWindow(), "Switch to Edit mode first",
					Toast.LENGTH_SHORT).show();
			return;
		}
		if (drawEditMode) {
			drawEditMode = false;
			if (mapperView != null) {
				mapperView.setShowGrid(false);
			}
		}
		linkEditMode = true;
		linkFromTileId = from.getId();
		selectedTileId = from.getId();
		if (mapperView != null) {
			mapperView.setSelectedTileId(from.getId());
		}
		updateTitleForLinkMode();
		rebuildToolbar();
		Toast.makeText(host.getMainWindow(),
				"FROM " + shortTileLabel(from) + " — tap destination tile, then enter walk word",
				Toast.LENGTH_LONG).show();
	}

	private void confirmDeleteTile(final MapTile tile) {
		new AlertDialog.Builder(host.getMainWindow())
				.setTitle("Delete tile?")
				.setMessage(shortTileLabel(tile))
				.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						runDeleteTile(tile.getId());
					}
				})
				.setNegativeButton("Cancel", null)
				.show();
	}

	private void promptChangeLevel(final MapTile tile) {
		MudMap map = controller != null ? controller.getMap() : snapshotMap;
		if (map == null || tile == null) {
			return;
		}
		final List<MapLevel> levels = map.getLevels();
		CharSequence[] names = new CharSequence[levels.size()];
		for (int i = 0; i < levels.size(); i++) {
			MapLevel l = levels.get(i);
			names[i] = l.getName() != null ? l.getName() : l.getId();
		}
		new AlertDialog.Builder(host.getMainWindow())
				.setTitle("Move to level")
				.setItems(names, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						if (which >= 0 && which < levels.size()) {
							MapLevel level = levels.get(which);
							String name = level.getName() != null ? level.getName() : level.getId();
							if (controller != null) {
								toastStatus(controller.moveTileLevel(tile.getId(), name));
							} else {
								host.runMapCommand("level move " + tile.getId() + " " + name);
								pullSnapshotFromService();
							}
						}
					}
				})
				.show();
	}

	private void openTileEditor(MapTile tile) {
		if (tile == null) {
			return;
		}
		selectedTileId = tile.getId();
		if (mapperView != null) {
			mapperView.setSelectedTileId(tile.getId());
		}
		if (controller != null) {
			new MapperTileEditorDialog(host.getMainWindow(), controller, tile).show();
			return;
		}
		// Service process owns the map — edit via .map title|note for <id>
		MainWindow activity = host.getMainWindow();
		LinearLayout root = new LinearLayout(activity);
		root.setOrientation(LinearLayout.VERTICAL);
		int pad = (int) (12 * activity.getResources().getDisplayMetrics().density);
		root.setPadding(pad, pad, pad, pad);
		final EditText title = new EditText(activity);
		title.setHint("Title");
		title.setSingleLine(true);
		title.setText(tile.getTitle() != null ? tile.getTitle() : "");
		final EditText notes = new EditText(activity);
		notes.setHint("Notes");
		notes.setMinLines(2);
		notes.setText(tile.getNotes() != null ? tile.getNotes() : "");
		root.addView(title);
		root.addView(notes);
		final String tileId = tile.getId();
		new AlertDialog.Builder(activity)
				.setTitle("Edit tile")
				.setView(root)
				.setPositiveButton("Save", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						String t = title.getText().toString();
						String n = notes.getText().toString();
						host.runMapCommand("title for " + tileId + " " + t);
						host.runMapCommand("note for " + tileId + " " + n);
						pullSnapshotFromService();
					}
				})
				.setNegativeButton("Cancel", null)
				.show();
	}

	private void openSearch() {
		if (controller != null) {
			new MapperSearchDialog(host.getMainWindow(), controller,
					new MapperSearchDialog.Callback() {
						@Override
						public void onGo(MapTile tile, List<String> path) {
							if (tile != null && mapperView != null) {
								selectedTileId = tile.getId();
								mapperView.setSelectedTileId(tile.getId());
								mapperView.centerOnTile(tile);
							}
							if (path != null && !path.isEmpty() && controller.isPathAutoSend()) {
								host.sendMapperPath(path);
							}
						}

						@Override
						public void onPath(MapTile tile, List<String> path) {
							if (path == null || path.isEmpty()) {
								Toast.makeText(host.getMainWindow(), "No path",
										Toast.LENGTH_SHORT).show();
							} else {
								Toast.makeText(host.getMainWindow(),
										"Path: " + join(path, ";"), Toast.LENGTH_LONG).show();
							}
						}
					}).show();
			return;
		}
		host.runMapCommand("find ");
		Toast.makeText(host.getMainWindow(),
				"Use .map find <query> in the input bar (or open Find after recording).",
				Toast.LENGTH_LONG).show();
	}

	private void openCapture() {
		if (controller != null) {
			new MapperCapturePreviewDialog(host.getMainWindow(), controller,
					host.getRecentBufferText(40)).show();
			return;
		}
		host.runMapCommand("capture preview");
	}

	private void openLevelBrowser() {
		MainWindow activity = host.getMainWindow();
		if (activity == null) {
			return;
		}
		MapperLevelBrowserDialog.show(activity, new MapperLevelBrowserDialog.Host() {
			@Override
			public MudMap getMap() {
				if (controller != null) {
					return controller.getMap();
				}
				return snapshotMap;
			}

			@Override
			public String getCurrentLevelId() {
				MudMap map = getMap();
				return map != null ? map.getCurrentLevelId() : null;
			}

			@Override
			public boolean isEditMode() {
				return isControllerEditMode();
			}

			@Override
			public void browseLevel(String levelId) {
				runBrowseLevel(levelId);
			}

			@Override
			public void goHereOnLevel(String levelId) {
				if (controller != null) {
					toastStatus(controller.goToLevel(levelId, true));
					refreshFromController();
				} else {
					runBrowseLevel(levelId);
					MudMap map = snapshotMap;
					MapTile first = null;
					if (map != null && levelId != null) {
						for (MapTile t : map.getTiles()) {
							if (t != null && levelId.equals(t.getLevelId())) {
								first = t;
								break;
							}
						}
					}
					if (first != null) {
						runSetHere(first.getId());
					}
				}
			}

			@Override
			public void floorUp() {
				runFloorUp();
			}

			@Override
			public void floorDown() {
				runFloorDown();
			}

			@Override
			public void centerOnTile(MapTile tile) {
				if (tile != null && mapperView != null) {
					selectedTileId = tile.getId();
					mapperView.setSelectedTileId(tile.getId());
					mapperView.centerOnTile(tile);
				}
			}

			@Override
			public void deleteLevel(final String levelId) {
				confirmDeleteLevel(levelId);
			}
		});
	}

	private void confirmDeleteLevel(final String levelId) {
		MainWindow activity = host.getMainWindow();
		if (activity == null || levelId == null || levelId.length() == 0) {
			return;
		}
		MudMap map = controller != null ? controller.getMap() : snapshotMap;
		MapLevel level = map != null ? map.findLevel(levelId) : null;
		String name;
		if (level != null && level.getName() != null && level.getName().length() > 0) {
			name = level.getName();
		} else if (level != null) {
			name = String.valueOf(level.getIndex());
		} else {
			name = levelId.length() > 8 ? levelId.substring(0, 8) : levelId;
		}
		int tileCount = 0;
		if (map != null) {
			for (MapTile t : map.getTiles()) {
				if (t != null && levelId.equals(t.getLevelId())) {
					tileCount++;
				}
			}
		}
		final String message = "Delete level \"" + name + "\" and " + tileCount
				+ " tiles? This cannot be undone easily (Undo may help).";
		new AlertDialog.Builder(activity)
				.setTitle("Delete level?")
				.setMessage(message)
				.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						if (controller != null) {
							toastStatus(controller.deleteLevel(levelId));
							refreshFromController();
						} else {
							host.runMapCommand("level delete " + levelId);
							pullSnapshotFromService();
						}
					}
				})
				.setNegativeButton("Cancel", null)
				.show();
	}

	private void rebuildToolbar() {
		// Bottom toolbar retired — tools live in top category radials (Nav/Floors/Build/File).
		if (toolbar != null) {
			toolbar.removeAllViews();
		}
	}

	private void wireCategoryChips() {
		if (overlayRoot == null) {
			return;
		}
		TextView nav = (TextView) overlayRoot.findViewById(R.id.mapper_cat_nav);
		TextView floors = (TextView) overlayRoot.findViewById(R.id.mapper_cat_floors);
		TextView build = (TextView) overlayRoot.findViewById(R.id.mapper_cat_build);
		TextView file = (TextView) overlayRoot.findViewById(R.id.mapper_cat_file);
		if (nav != null) {
			nav.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					openNavRadial();
				}
			});
		}
		if (floors != null) {
			floors.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					openFloorsRadial();
				}
			});
		}
		if (build != null) {
			build.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					openBuildRadial();
				}
			});
		}
		if (file != null) {
			file.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					openFileRadial();
				}
			});
		}
	}

	private void openNavRadial() {
		showRadialOnOverlay(new Runnable() {
			@Override
			public void run() {
				MapperRadialMenu.showNav((ViewGroup) overlayRoot, radialListener());
			}
		});
	}

	private void openFloorsRadial() {
		showRadialOnOverlay(new Runnable() {
			@Override
			public void run() {
				MapperRadialMenu.showFloors((ViewGroup) overlayRoot, radialListener());
			}
		});
	}

	private void openBuildRadial() {
		showRadialOnOverlay(new Runnable() {
			@Override
			public void run() {
				MapperRadialMenu.showBuild((ViewGroup) overlayRoot, radialListener());
			}
		});
	}

	private void openFileRadial() {
		showRadialOnOverlay(new Runnable() {
			@Override
			public void run() {
				MapperRadialMenu.showFile((ViewGroup) overlayRoot, radialListener());
			}
		});
	}

	private void showRadialOnOverlay(Runnable show) {
		if (overlayRoot == null || !(overlayRoot instanceof ViewGroup)) {
			return;
		}
		show.run();
	}

	private MapperRadialMenu.Listener radialListener() {
		return new MapperRadialMenu.Listener() {
			@Override
			public void onRadialAction(String action) {
				runRadialAction(action);
			}
		};
	}

	private void openLevelsRadial() {
		openFloorsRadial();
	}

	private void openToolsRadial() {
		openBuildRadial();
	}

	private void runRadialAction(String action) {
		if (action == null) {
			return;
		}
		if (MapperRadialMenu.ACTION_LIST.equals(action)
				|| MapperRadialMenu.ACTION_LEVELS.equals(action)) {
			openLevelBrowser();
		} else if (MapperRadialMenu.ACTION_UP.equals(action)
				|| MapperRadialMenu.ACTION_FLOOR_UP.equals(action)) {
			runFloorUp();
		} else if (MapperRadialMenu.ACTION_DOWN.equals(action)
				|| MapperRadialMenu.ACTION_FLOOR_DOWN.equals(action)) {
			runFloorDown();
		} else if (MapperRadialMenu.ACTION_ROOT.equals(action)) {
			runGoRootLevel();
		} else if (MapperRadialMenu.ACTION_PARENT.equals(action)) {
			runGoParentDoor();
		} else if (MapperRadialMenu.ACTION_DELETE_LEVEL.equals(action)) {
			confirmDeleteCurrentLevel();
		} else if (MapperRadialMenu.ACTION_PATHS.equals(action)) {
			togglePathsLayout();
		} else if (MapperRadialMenu.ACTION_DRAW.equals(action)) {
			toggleDrawEditMode();
		} else if (MapperRadialMenu.ACTION_LINKS.equals(action)) {
			toggleLinkEditMode();
		} else if (MapperRadialMenu.ACTION_HERE.equals(action)) {
			MapTile tile = selectedOrCurrentTile();
			if (tile == null) {
				Toast.makeText(host.getMainWindow(), "Select a tile first",
						Toast.LENGTH_SHORT).show();
			} else {
				runSetHere(tile.getId());
			}
		} else if (MapperRadialMenu.ACTION_EDIT.equals(action)) {
			MapTile tile = selectedOrCurrentTile();
			if (tile != null) {
				openTileEditor(tile);
			} else {
				Toast.makeText(host.getMainWindow(), "No tile selected",
						Toast.LENGTH_SHORT).show();
			}
		} else if (MapperRadialMenu.ACTION_SAVE.equals(action)) {
			if (controller != null) {
				toastStatus(controller.save());
			} else {
				host.runMapCommand("save");
				Toast.makeText(host.getMainWindow(), "Saving map…",
						Toast.LENGTH_SHORT).show();
			}
		} else if (MapperRadialMenu.ACTION_EXPORT.equals(action)) {
			if (controller != null) {
				toastStatus(controller.exportMap(""));
			} else {
				host.runMapCommand("export");
				Toast.makeText(host.getMainWindow(), "Exporting map…",
						Toast.LENGTH_SHORT).show();
			}
		} else if (MapperRadialMenu.ACTION_FIND.equals(action)) {
			openSearch();
		} else if (MapperRadialMenu.ACTION_REC.equals(action)) {
			runToolbarAction("rec");
		} else if (MapperRadialMenu.ACTION_FOLLOW.equals(action)) {
			runToolbarAction("follow");
		} else if (MapperRadialMenu.ACTION_UNDO.equals(action)) {
			runToolbarAction("undo");
		} else if (MapperRadialMenu.ACTION_CENTER.equals(action)) {
			runToolbarAction("center");
		} else if (MapperRadialMenu.ACTION_CLOSE.equals(action)) {
			close();
		} else if (MapperRadialMenu.ACTION_MAPS.equals(action)) {
			host.runMapCommand("maps");
			Toast.makeText(host.getMainWindow(), "Listed maps in buffer (.map maps)",
					Toast.LENGTH_SHORT).show();
		} else if (MapperRadialMenu.ACTION_NEW.equals(action)) {
			promptNewMap();
		}
	}

	private void promptNewMap() {
		MainWindow activity = host.getMainWindow();
		if (activity == null) {
			return;
		}
		final android.widget.EditText input = new android.widget.EditText(activity);
		input.setHint("map name");
		input.setSingleLine(true);
		input.setText("default");
		new AlertDialog.Builder(activity)
				.setTitle("New map")
				.setView(input)
				.setPositiveButton("Create", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						String name = input.getText() != null
								? input.getText().toString().trim() : "";
						if (name.length() == 0) {
							name = "default";
						}
						if (controller != null) {
							toastStatus(controller.newMap(name));
							refreshFromController();
						} else {
							host.runMapCommand("new " + name);
							pullSnapshotFromService();
						}
					}
				})
				.setNegativeButton("Cancel", null)
				.show();
	}

	/** Browse/go to unanchored root level (index 0, or first without anchor). */
	private void runGoRootLevel() {
		MudMap map = controller != null ? controller.getMap() : snapshotMap;
		if (map == null || map.getLevels() == null || map.getLevels().isEmpty()) {
			Toast.makeText(host.getMainWindow(), "No levels", Toast.LENGTH_SHORT).show();
			return;
		}
		MapLevel root = null;
		for (MapLevel level : map.getLevels()) {
			if (level == null) {
				continue;
			}
			String anchor = level.getAnchorTileId();
			if (anchor == null || anchor.length() == 0) {
				if (level.getIndex() == 0) {
					root = level;
					break;
				}
				if (root == null) {
					root = level;
				}
			}
		}
		if (root == null) {
			root = map.getLevels().get(0);
		}
		if (root == null || root.getId() == null) {
			Toast.makeText(host.getMainWindow(), "No root level", Toast.LENGTH_SHORT).show();
			return;
		}
		runBrowseLevel(root.getId());
	}

	/** Return to the anchor tile of the current level, if any. */
	private void runGoParentDoor() {
		MudMap map = controller != null ? controller.getMap() : snapshotMap;
		if (map == null) {
			Toast.makeText(host.getMainWindow(), "No map", Toast.LENGTH_SHORT).show();
			return;
		}
		MapLevel current = map.findLevel(map.getCurrentLevelId());
		if (current == null) {
			Toast.makeText(host.getMainWindow(), "No current level", Toast.LENGTH_SHORT).show();
			return;
		}
		String anchorId = current.getAnchorTileId();
		if (anchorId == null || anchorId.length() == 0) {
			Toast.makeText(host.getMainWindow(), "Already at root (no door)",
					Toast.LENGTH_SHORT).show();
			return;
		}
		MapTile door = map.findTile(anchorId);
		if (door == null) {
			Toast.makeText(host.getMainWindow(), "Anchor tile missing",
					Toast.LENGTH_SHORT).show();
			return;
		}
		if (door.getLevelId() != null) {
			runBrowseLevel(door.getLevelId());
		}
		runSetHere(door.getId());
		if (mapperView != null) {
			mapperView.centerOnTile(door);
		}
	}

	/**
	 * Delete current level — requires Edit mode; confirms then calls
	 * {@link MapperController#deleteLevel}.
	 */
	private void confirmDeleteCurrentLevel() {
		if (!isControllerEditMode()) {
			Toast.makeText(host.getMainWindow(),
					"Switch to Edit mode first",
					Toast.LENGTH_SHORT).show();
			return;
		}
		MudMap map = controller != null ? controller.getMap() : snapshotMap;
		if (map == null) {
			Toast.makeText(host.getMainWindow(), "No map", Toast.LENGTH_SHORT).show();
			return;
		}
		MapLevel current = map.findLevel(map.getCurrentLevelId());
		if (current == null) {
			Toast.makeText(host.getMainWindow(), "No current level", Toast.LENGTH_SHORT).show();
			return;
		}
		confirmDeleteLevel(current.getId());
	}

	private void runBrowseLevel(String levelId) {
		if (levelId == null || levelId.length() == 0) {
			return;
		}
		if (controller != null) {
			toastStatus(controller.browseLevel(levelId));
			refreshFromController();
			return;
		}
		MudMap map = snapshotMap;
		MapLevel level = map != null ? map.findLevel(levelId) : null;
		if (level != null) {
			String name = level.getName() != null ? level.getName() : level.getId();
			host.runMapCommand("level set " + name);
			pullSnapshotFromService();
		} else {
			Toast.makeText(host.getMainWindow(), "Unknown level", Toast.LENGTH_SHORT).show();
		}
	}

	private void runFloorUp() {
		if (controller != null) {
			toastStatus(controller.levelNext());
			refreshFromController();
		} else {
			host.runMapCommand("level next");
			pullSnapshotFromService();
		}
	}

	private void runFloorDown() {
		if (controller != null) {
			toastStatus(controller.levelPrev());
			refreshFromController();
		} else {
			host.runMapCommand("level prev");
			pullSnapshotFromService();
		}
	}

	public void refreshFromController() {
		if (mapperView == null) {
			return;
		}
		MudMap map = snapshotMap;
		if (map == null && controller != null) {
			map = controller.getMap();
		}
		if (map == null) {
			if (titleView != null) {
				titleView.setText("Map");
			}
			mapperView.setTiles(new ArrayList<MapTile>());
			mapperView.setTileIndex(new HashMap<String, MapTile>());
			mapperView.setLevelIndex(new HashMap<String, MapLevel>());
			return;
		}
		if (titleView != null && !linkEditMode && !drawEditMode) {
			titleView.setText(formatTitleBreadcrumb(map));
		}
		mapperView.setTileIndex(buildTileIndex(map));
		mapperView.setLevelIndex(buildLevelIndex(map));
		mapperView.setTiles(tilesOnCurrentLevel(map));
		mapperView.setCurrentTileId(map.getCurrentTileId());
		mapperView.setSelectedTileId(selectedTileId);
		boolean follow = controller != null ? controller.isFollowPlayer() : snapshotFollow;
		mapperView.setFollowMode(follow);
		applyOpacity();
		updateEditModeToggleUi();
		rebuildToolbar();
	}

	private boolean isControllerEditMode() {
		if (controller != null) {
			return controller.isEditMode();
		}
		return sessionEditMode;
	}

	/**
	 * Title-bar Browse|Edit segmented control. Switching to Browse force-offs
	 * Draw and Links tools. When the map engine lives in the service process
	 * (controller == null), flip a local flag and send {@code .map mode …}.
	 */
	private void setEditModeFromUi(final boolean edit) {
		if (isControllerEditMode() == edit) {
			updateEditModeToggleUi();
			return;
		}
		sessionEditMode = edit;
		if (controller != null) {
			controller.setEditMode(edit);
		} else {
			host.runMapCommand(edit ? "mode edit" : "mode browse");
		}
		if (!edit) {
			forceOffDrawAndLinks();
		}
		updateEditModeToggleUi();
		Toast.makeText(host.getMainWindow(),
				edit ? "Edit mode" : "Browse mode", Toast.LENGTH_SHORT).show();
		if (controller != null) {
			if (!linkEditMode && !drawEditMode) {
				refreshFromController();
			}
		} else {
			pullSnapshotFromService();
		}
	}

	private void forceOffDrawAndLinks() {
		boolean changed = false;
		if (drawEditMode) {
			drawEditMode = false;
			changed = true;
			if (mapperView != null) {
				mapperView.setShowGrid(false);
			}
		}
		if (linkEditMode) {
			linkEditMode = false;
			linkFromTileId = null;
			changed = true;
		}
		if (changed) {
			updateTitleForLinkMode();
			rebuildToolbar();
		}
	}

	private void updateEditModeToggleUi() {
		boolean edit = isControllerEditMode();
		final int active = 0xFFE8C547;
		final int inactive = 0xFF888888;
		final int activeBg = 0x33000000;
		final int clearBg = 0x00000000;
		if (modeBrowseBtn != null) {
			modeBrowseBtn.setTextColor(edit ? inactive : active);
			modeBrowseBtn.setBackgroundColor(edit ? clearBg : activeBg);
			modeBrowseBtn.getPaint().setFakeBoldText(!edit);
			modeBrowseBtn.invalidate();
		}
		if (modeEditBtn != null) {
			modeEditBtn.setTextColor(edit ? active : inactive);
			modeEditBtn.setBackgroundColor(edit ? activeBg : clearBg);
			modeEditBtn.getPaint().setFakeBoldText(edit);
			modeEditBtn.invalidate();
		}
	}

	private String formatTitleBreadcrumb(MudMap map) {
		String name = map.getName() != null ? map.getName() : "Map";
		String level = "?";
		MapLevel l = map.findLevel(map.getCurrentLevelId());
		if (l != null && l.getName() != null) {
			level = l.getName();
		} else if (l != null) {
			level = Integer.toString(l.getIndex());
		}
		boolean rec = controller != null ? controller.isRecording() : snapshotRecording;
		String recMark = rec ? " [REC]" : "";
		String modeMark = isControllerEditMode() ? "[Edit] " : "[Browse] ";
		String base = modeMark + name + " · L" + level;
		String anchorId = l != null ? l.getAnchorTileId() : null;
		if (anchorId != null && anchorId.length() > 0) {
			MapTile anchor = map.findTile(anchorId);
			String via = shortTileLabel(anchor);
			base = base + " ← " + via;
		}
		return base + recMark;
	}

	private Map<String, MapTile> buildTileIndex(MudMap map) {
		HashMap<String, MapTile> byId = new HashMap<String, MapTile>();
		if (map == null || map.getTiles() == null) {
			return byId;
		}
		for (MapTile t : map.getTiles()) {
			if (t != null && t.getId() != null) {
				byId.put(t.getId(), t);
			}
		}
		return byId;
	}

	private Map<String, MapLevel> buildLevelIndex(MudMap map) {
		HashMap<String, MapLevel> byId = new HashMap<String, MapLevel>();
		if (map == null || map.getLevels() == null) {
			return byId;
		}
		for (MapLevel level : map.getLevels()) {
			if (level != null && level.getId() != null) {
				byId.put(level.getId(), level);
			}
		}
		return byId;
	}

	/**
	 * Jump camera / Here to an inter-level exit destination and toast the level name.
	 */
	private void jumpToInterLevelDest(final MapTile dest) {
		if (dest == null) {
			return;
		}
		selectedTileId = dest.getId();
		MudMap map = controller != null ? controller.getMap() : snapshotMap;
		String levelName = "?";
		if (map != null) {
			MapLevel destLevel = map.findLevel(dest.getLevelId());
			if (destLevel != null && destLevel.getName() != null
					&& destLevel.getName().length() > 0) {
				levelName = destLevel.getName();
			} else if (destLevel != null) {
				levelName = "L" + destLevel.getIndex();
			}
		}
		if (controller != null) {
			// Browse to dest level for camera, then Set Here on the exit target.
			controller.browseLevel(dest.getLevelId());
			controller.setHere(dest.getId());
			refreshFromController();
		} else if (snapshotMap != null) {
			snapshotMap.setCurrentTileId(dest.getId());
			snapshotMap.setCurrentLevelId(dest.getLevelId());
			refreshFromController();
		}
		final String destId = dest.getId();
		final String toastLevel = levelName;
		if (mapperView != null) {
			mapperView.post(new Runnable() {
				@Override
				public void run() {
					MudMap m = controller != null ? controller.getMap() : snapshotMap;
					MapTile t = m != null ? m.findTile(destId) : null;
					if (t == null) {
						return;
					}
					mapperView.setSelectedTileId(t.getId());
					mapperView.setFollowMode(false);
					mapperView.centerOnTile(t);
				}
			});
		}
		Toast.makeText(host.getMainWindow(), "→ " + toastLevel, Toast.LENGTH_SHORT).show();
	}

	/** Pull JSON from the service process and apply to the overlay. */
	public void pullSnapshotFromService() {
		if (host == null) {
			return;
		}
		String json = host.fetchMapperSnapshotJson();
		if (json == null || json.length() == 0) {
			return;
		}
		try {
			org.json.JSONObject root = new org.json.JSONObject(json);
			snapshotRecording = root.optBoolean("recording", false);
			snapshotFollow = root.optBoolean("follow", true);
			sessionEditMode = root.optBoolean("editMode", sessionEditMode);
			snapshotOpacity = root.optInt("opacity", 85);
			if (root.has("preferFloat")) {
				fullscreen = !root.optBoolean("preferFloat", true);
			}
			String tb = root.optString("toolbar", "");
			if (tb != null && tb.length() > 0) {
				snapshotToolbar = tb;
			}
			snapshotMap = MapStore.fromJson(json);
			applyOpacity();
			applyLayoutMode();
			refreshFromController();
		} catch (Exception e) {
			android.util.Log.w("BlowTorch", "mapper snapshot parse failed", e);
		}
	}

	private List<MapTile> tilesOnCurrentLevel(MudMap map) {
		ArrayList<MapTile> out = new ArrayList<MapTile>();
		if (map == null) {
			return out;
		}
		String level = map.getCurrentLevelId();
		for (MapTile t : map.getTiles()) {
			if (t == null) {
				continue;
			}
			if (level == null || level.equals(t.getLevelId())) {
				out.add(t);
			}
		}
		return out;
	}

	@Override
	public void openMapUi() {
		runOnUi(new Runnable() {
			@Override
			public void run() {
				open();
			}
		});
	}

	@Override
	public void closeMapUi() {
		runOnUi(new Runnable() {
			@Override
			public void run() {
				close();
			}
		});
	}

	@Override
	public void toggleMapUi() {
		runOnUi(new Runnable() {
			@Override
			public void run() {
				toggle();
			}
		});
	}

	@Override
	public void setMapMode(final boolean fullscreenMode) {
		runOnUi(new Runnable() {
			@Override
			public void run() {
				setFullscreen(fullscreenMode);
			}
		});
	}

	@Override
	public void centerOnPlayer() {
		runOnUi(new Runnable() {
			@Override
			public void run() {
				if (mapperView != null) {
					mapperView.setFollowMode(true);
					mapperView.centerOnCurrentTile(true);
				}
			}
		});
	}

	@Override
	public void zoomMap(final String action) {
		runOnUi(new Runnable() {
			@Override
			public void run() {
				if (mapperView == null || action == null) {
					return;
				}
				String a = action.trim().toLowerCase(java.util.Locale.US);
				if (a.equals("in") || a.equals("+")) {
					mapperView.zoomIn();
				} else if (a.equals("out") || a.equals("-")) {
					mapperView.zoomOut();
				} else if (a.equals("reset") || a.equals("1") || a.equals("100%")) {
					mapperView.zoomReset();
				} else {
					try {
						mapperView.zoomBy(Float.parseFloat(a));
					} catch (NumberFormatException ignored) {
					}
				}
			}
		});
	}

	@Override
	public void onMapModelChanged() {
		runOnUi(new Runnable() {
			@Override
			public void run() {
				pullSnapshotFromService();
				if (!isControllerEditMode()) {
					forceOffDrawAndLinks();
				}
				refreshFromController();
			}
		});
	}

	@Override
	public void onMapperChanged() {
		onMapModelChanged();
	}

	public void detach() {
		if (controller != null) {
			controller.removeListener(this);
			if (controller.getUiBridge() == this) {
				controller.setUiBridge(null);
			}
		}
		if (overlayRoot != null && overlayRoot.getParent() instanceof ViewGroup) {
			((ViewGroup) overlayRoot.getParent()).removeView(overlayRoot);
		}
		overlayRoot = null;
		attached = false;
		visible = false;
	}

	private void runOnUi(Runnable r) {
		MainWindow activity = host.getMainWindow();
		if (activity != null) {
			activity.runOnUiThread(r);
		}
	}

	private static String join(List<String> parts, String sep) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				sb.append(sep);
			}
			sb.append(parts.get(i));
		}
		return sb.toString();
	}
}
