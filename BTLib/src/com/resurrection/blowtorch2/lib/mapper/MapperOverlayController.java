package com.resurrection.blowtorch2.lib.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.window.MainWindow;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
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
	private View resizeHandle;
	private View dragHandle;
	private TextView modeBtn;
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
		}
		applyLayoutMode();
		if (modeBtn != null) {
			modeBtn.setText(fullscreen ? "Full" : "Float");
		}
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
		resizeHandle = overlayRoot.findViewById(R.id.mapper_resize_handle);
		dragHandle = overlayRoot.findViewById(R.id.mapper_drag_handle);
		modeBtn = (TextView) overlayRoot.findViewById(R.id.mapper_mode_btn);
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
		modeBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setFullscreen(!fullscreen);
			}
		});

		mapperView.setTileInteractionListener(new MapperView.TileInteractionListener() {
			@Override
			public void onTileTap(MapTile tile) {
				selectedTileId = tile.getId();
				String title = tile.getTitle() != null && tile.getTitle().length() > 0
						? tile.getTitle() : "(untitled)";
				Toast.makeText(host.getMainWindow(), title, Toast.LENGTH_SHORT).show();
			}

			@Override
			public void onTileLongPress(MapTile tile) {
				selectedTileId = tile.getId();
				showTileContext(tile);
			}
		});

		wireDragResize();
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
		if (fullscreen) {
			lp.width = RelativeLayout.LayoutParams.MATCH_PARENT;
			lp.height = RelativeLayout.LayoutParams.MATCH_PARENT;
			lp.leftMargin = 0;
			lp.topMargin = 0;
			overlayRoot.setPadding(
					(int) (4 * density),
					(int) (8 * density),
					(int) (8 * density),
					(int) (4 * density));
			if (resizeHandle != null) {
				resizeHandle.setVisibility(View.GONE);
			}
		} else {
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
			}
		}
		overlayRoot.setLayoutParams(lp);
		if (modeBtn != null) {
			modeBtn.setText(fullscreen ? "Full" : "Float");
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

	private void rebuildToolbar() {
		if (toolbar == null) {
			return;
		}
		toolbar.removeAllViews();
		String csv = controller != null ? controller.getToolbarActions() : snapshotToolbar;
		if (csv == null || csv.length() == 0) {
			csv = MapperController.DEFAULT_TOOLBAR;
		}
		String[] parts = csv.split(",");
		MainWindow activity = host.getMainWindow();
		float density = activity.getResources().getDisplayMetrics().density;
		for (String part : parts) {
			final String action = part.trim();
			if (action.length() == 0) {
				continue;
			}
			Button b = new Button(activity);
			b.setText(actionLabel(action));
			b.setTextSize(12f);
			b.setAllCaps(false);
			b.setMinHeight(0);
			b.setMinWidth(0);
			b.setPadding((int) (8 * density), (int) (4 * density),
					(int) (8 * density), (int) (4 * density));
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.WRAP_CONTENT,
					(int) (32 * density));
			lp.rightMargin = (int) (4 * density);
			b.setLayoutParams(lp);
			b.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					runToolbarAction(action);
				}
			});
			toolbar.addView(b);
		}
		Button edit = new Button(activity);
		edit.setText("Edit");
		edit.setTextSize(12f);
		edit.setAllCaps(false);
		edit.setMinHeight(0);
		edit.setMinWidth(0);
		edit.setPadding((int) (8 * density), (int) (4 * density),
				(int) (8 * density), (int) (4 * density));
		toolbar.addView(edit);
		edit.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				MapTile tile = selectedOrCurrentTile();
				if (tile != null) {
					openTileEditor(tile);
				} else {
					Toast.makeText(host.getMainWindow(), "No tile selected",
							Toast.LENGTH_SHORT).show();
				}
			}
		});
	}

	private static String actionLabel(String action) {
		String a = action.toLowerCase(Locale.US);
		if ("rec".equals(a) || "record".equals(a)) {
			return "Rec";
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
		return action;
	}

	private void runToolbarAction(String action) {
		String a = action.toLowerCase(Locale.US);
		if (controller == null) {
			if ("rec".equals(a) || "record".equals(a)) {
				host.runMapCommand("record toggle");
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
					controller.isRecording() ? "Recording on" : "Recording off",
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
				"Edit", "Path to here", "Change level", "Center"
		};
		new AlertDialog.Builder(activity)
				.setTitle(tile.getTitle() != null ? tile.getTitle() : "Tile")
				.setItems(items, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						switch (which) {
						case 0:
							openTileEditor(tile);
							break;
						case 1:
							pathToTile(tile);
							break;
						case 2:
							promptChangeLevel(tile);
							break;
						case 3:
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
								host.runMapCommand("level set " + name);
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
		if (controller != null) {
			new MapperTileEditorDialog(host.getMainWindow(), controller, tile).show();
			return;
		}
		final EditText title = new EditText(host.getMainWindow());
		title.setText(tile.getTitle() != null ? tile.getTitle() : "");
		title.setHint("Title");
		new AlertDialog.Builder(host.getMainWindow())
				.setTitle("Tile title")
				.setView(title)
				.setPositiveButton("Save", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						host.runMapCommand("title " + title.getText().toString());
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
			return;
		}
		if (titleView != null) {
			String name = map.getName() != null ? map.getName() : "Map";
			String level = "?";
			MapLevel l = map.findLevel(map.getCurrentLevelId());
			if (l != null && l.getName() != null) {
				level = l.getName();
			}
			boolean rec = controller != null ? controller.isRecording() : snapshotRecording;
			String recMark = rec ? " [REC]" : "";
			titleView.setText(name + " · L" + level + recMark);
		}
		mapperView.setTiles(tilesOnCurrentLevel(map));
		mapperView.setCurrentTileId(map.getCurrentTileId());
		mapperView.setSelectedTileId(selectedTileId);
		boolean follow = controller != null ? controller.isFollowPlayer() : snapshotFollow;
		mapperView.setFollowMode(follow);
		applyOpacity();
		rebuildToolbar();
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
	public void onMapModelChanged() {
		runOnUi(new Runnable() {
			@Override
			public void run() {
				pullSnapshotFromService();
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
