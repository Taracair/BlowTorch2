package com.resurrection.blowtorch2.lib.mapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * Draws map tiles on a pan/zoomable grid with exit arrows/labels and
 * current-room highlight.
 */
public class MapperView extends View {

	public interface TileInteractionListener {
		void onTileTap(MapTile tile);
		void onTileLongPress(MapTile tile);
		/** Double-tap a tile (Set as Here). */
		void onTileDoubleTap(MapTile tile);
		/** Empty grid cell tapped (Draw mode). */
		void onEmptyTap(int gridX, int gridY);
		/** Empty grid cell long-pressed (Draw mode). */
		void onEmptyLongPress(int gridX, int gridY);
		/** Tile dragged to a new grid cell (long-press + drag). */
		void onTileDragEnd(MapTile tile, int gridX, int gridY);
		/**
		 * User tapped an overflow link label (more than
		 * {@link #MAX_VISIBLE_LINK_LABELS} commands on one edge).
		 */
		void onLinkCommandsTap(MapTile from, MapTile to, List<String> commands);
	}

	private static final float BASE_TILE = 56f;
	private static final float MIN_SCALE = 0.35f;
	private static final float MAX_SCALE = 3.5f;
	/** Step factor for {@link #zoomIn()}/{@link #zoomOut()} (CLI / buttons). */
	private static final float ZOOM_STEP = 1.25f;
	/** Grid pitch multiplier when Paths layout is on (space for arrows). */
	private static final float PATHS_PITCH = 1.75f;
	/** Drawn tile body as a fraction of the cell (Paths leaves a wide gutter). */
	private static final float PATHS_BODY_FRAC = 0.48f;
	private static final float PACK_BODY_FRAC = 0.88f;
	/** Show this many walk words on an edge before collapsing to “+N”. */
	public static final int MAX_VISIBLE_LINK_LABELS = 2;

	private static final class LinkBadge {
		final RectF bounds = new RectF();
		final String fromId;
		final String toId;
		final List<String> commands;

		LinkBadge(String fromId, String toId, List<String> commands) {
			this.fromId = fromId;
			this.toId = toId;
			this.commands = commands;
		}
	}

	private final Paint tileFill = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint tileStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint currentFill = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint selectedStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint exitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint linkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint linkLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint linkLabelBg = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint specialPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint bgPaint = new Paint();
	private final Paint gridPaint = new Paint();
	private final Paint dragGhostFill = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint dragGhostStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final RectF tmpRect = new RectF();
	private final Path arrowPath = new Path();
	private final List<LinkBadge> linkBadges = new ArrayList<LinkBadge>();

	private final ScaleGestureDetector scaleDetector;
	private final GestureDetector gestureDetector;

	private List<MapTile> tiles = new ArrayList<MapTile>();
	private String currentTileId;
	private String selectedTileId;
	private TileInteractionListener listener;

	private float scale = 1f;
	private float offsetX;
	private float offsetY;
	private boolean followMode = true;
	private boolean centeredOnce;
	private boolean showGrid;
	/**
	 * When true, cells are spaced apart so exit arrows/labels fit between tiles.
	 * When false (Pack), tiles sit nearly adjacent.
	 */
	private boolean pathsLayout = true;
	/** When true, long-press on a tile starts reposition drag. */
	private boolean tileDragEnabled = true;
	private boolean tileDragging;
	private MapTile draggingTile;
	private int dragGridX;
	private int dragGridY;

	private float lastPanX;
	private float lastPanY;
	private boolean panning;
	private boolean scaling;
	private final Handler handler = new Handler(Looper.getMainLooper());

	public MapperView(Context context) {
		this(context, null);
	}

	public MapperView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public MapperView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		tileFill.setColor(0xFF2A3A4A);
		tileFill.setStyle(Paint.Style.FILL);
		tileStroke.setColor(0xFF8AA0B8);
		tileStroke.setStyle(Paint.Style.STROKE);
		tileStroke.setStrokeWidth(2f);
		currentFill.setColor(0xFF3D6B4F);
		currentFill.setStyle(Paint.Style.FILL);
		selectedStroke.setColor(0xFFE8C547);
		selectedStroke.setStyle(Paint.Style.STROKE);
		selectedStroke.setStrokeWidth(3.5f);
		textPaint.setColor(0xFFEEEEEE);
		textPaint.setTextAlign(Paint.Align.CENTER);
		exitPaint.setColor(0xFFB8D4FF);
		exitPaint.setStrokeWidth(3f);
		exitPaint.setStyle(Paint.Style.STROKE);
		linkPaint.setColor(0xFF9EC5FF);
		linkPaint.setStrokeWidth(2.5f);
		linkPaint.setStyle(Paint.Style.STROKE);
		linkPaint.setStrokeCap(Paint.Cap.ROUND);
		linkLabelPaint.setColor(0xFFF0F6FF);
		linkLabelPaint.setTextAlign(Paint.Align.CENTER);
		linkLabelBg.setColor(0xCC102030);
		linkLabelBg.setStyle(Paint.Style.FILL);
		specialPaint.setColor(0xFFFFB060);
		specialPaint.setStyle(Paint.Style.FILL);
		bgPaint.setColor(0x00000000);
		gridPaint.setColor(0x33FFFFFF);
		gridPaint.setStrokeWidth(1f);
		gridPaint.setStyle(Paint.Style.STROKE);
		dragGhostFill.setColor(0x66E8C547);
		dragGhostFill.setStyle(Paint.Style.FILL);
		dragGhostStroke.setColor(0xFFE8C547);
		dragGhostStroke.setStyle(Paint.Style.STROKE);
		dragGhostStroke.setStrokeWidth(3f);

		scaleDetector = new ScaleGestureDetector(context,
				new ScaleGestureDetector.SimpleOnScaleGestureListener() {
					@Override
					public boolean onScaleBegin(ScaleGestureDetector detector) {
						scaling = true;
						panning = false;
						followMode = false;
						return true;
					}

					@Override
					public boolean onScale(ScaleGestureDetector detector) {
						float focusX = detector.getFocusX();
						float focusY = detector.getFocusY();
						float prev = scale;
						float next = clamp(prev * detector.getScaleFactor(), MIN_SCALE, MAX_SCALE);
						if (prev <= 0f || Math.abs(next - prev) < 0.0001f) {
							return true;
						}
						float worldX = (focusX - offsetX) / prev;
						float worldY = (focusY - offsetY) / prev;
						scale = next;
						offsetX = focusX - worldX * scale;
						offsetY = focusY - worldY * scale;
						invalidate();
						return true;
					}

					@Override
					public void onScaleEnd(ScaleGestureDetector detector) {
						scaling = false;
					}
				});

		gestureDetector = new GestureDetector(context,
				new GestureDetector.SimpleOnGestureListener() {
					@Override
					public boolean onDown(MotionEvent e) {
						return true;
					}

					@Override
					public boolean onSingleTapConfirmed(MotionEvent e) {
						LinkBadge badge = hitLinkBadge(e.getX(), e.getY());
						if (badge != null && listener != null) {
							MapTile from = findTile(badge.fromId);
							MapTile to = findTile(badge.toId);
							if (from != null && to != null) {
								listener.onLinkCommandsTap(from, to,
										new ArrayList<String>(badge.commands));
								return true;
							}
						}
						MapTile tile = hitTest(e.getX(), e.getY());
						if (tile != null) {
							selectedTileId = tile.getId();
							invalidate();
							if (listener != null) {
								listener.onTileTap(tile);
							}
							return true;
						}
						if (listener != null) {
							int[] g = screenToGrid(e.getX(), e.getY());
							listener.onEmptyTap(g[0], g[1]);
						}
						return true;
					}

					@Override
					public void onLongPress(MotionEvent e) {
						MapTile tile = hitTest(e.getX(), e.getY());
						if (tile != null) {
							selectedTileId = tile.getId();
							if (tileDragEnabled) {
								tileDragging = true;
								draggingTile = tile;
								int[] g = screenToGrid(e.getX(), e.getY());
								dragGridX = g[0];
								dragGridY = g[1];
								panning = false;
								invalidate();
								return;
							}
							invalidate();
							if (listener != null) {
								listener.onTileLongPress(tile);
							}
							return;
						}
						if (listener != null) {
							int[] g = screenToGrid(e.getX(), e.getY());
							listener.onEmptyLongPress(g[0], g[1]);
						}
					}

					@Override
					public boolean onDoubleTap(MotionEvent e) {
						MapTile tile = hitTest(e.getX(), e.getY());
						if (tile != null) {
							selectedTileId = tile.getId();
							invalidate();
							if (listener != null) {
								listener.onTileDoubleTap(tile);
							}
							return true;
						}
						centerOnCurrentTile(true);
						return true;
					}
				});
	}

	public void setTileInteractionListener(TileInteractionListener listener) {
		this.listener = listener;
	}

	public void setTiles(List<MapTile> tiles) {
		this.tiles = tiles != null ? new ArrayList<MapTile>(tiles) : new ArrayList<MapTile>();
		if (!centeredOnce && currentTileId != null) {
			handler.post(new Runnable() {
				@Override
				public void run() {
					centerOnCurrentTile(false);
					centeredOnce = true;
				}
			});
		}
		invalidate();
	}

	public void setCurrentTileId(String currentTileId) {
		String prev = this.currentTileId;
		this.currentTileId = currentTileId;
		if (followMode && currentTileId != null
				&& (prev == null || !prev.equals(currentTileId))) {
			centerOnCurrentTile(false);
		}
		invalidate();
	}

	public void setSelectedTileId(String selectedTileId) {
		this.selectedTileId = selectedTileId;
		invalidate();
	}

	public String getSelectedTileId() {
		return selectedTileId;
	}

	public void setFollowMode(boolean followMode) {
		this.followMode = followMode;
		if (followMode) {
			centerOnCurrentTile(false);
		}
	}

	public boolean isFollowMode() {
		return followMode;
	}

	public void setShowGrid(boolean showGrid) {
		this.showGrid = showGrid;
		invalidate();
	}

	public boolean isShowGrid() {
		return showGrid;
	}

	/** Spread tiles for arrows (Paths) vs packed neighbors (Pack). */
	public void setPathsLayout(boolean pathsLayout) {
		if (this.pathsLayout == pathsLayout) {
			return;
		}
		float oldCs = cellSize();
		float centerWorldX = 0f;
		float centerWorldY = 0f;
		if (oldCs > 0f && getWidth() > 0 && getHeight() > 0) {
			centerWorldX = (getWidth() * 0.5f - offsetX) / oldCs;
			centerWorldY = (getHeight() * 0.5f - offsetY) / oldCs;
		}
		this.pathsLayout = pathsLayout;
		if (oldCs > 0f && getWidth() > 0 && getHeight() > 0) {
			float newCs = cellSize();
			offsetX = getWidth() * 0.5f - centerWorldX * newCs;
			offsetY = getHeight() * 0.5f - centerWorldY * newCs;
		}
		invalidate();
	}

	public boolean isPathsLayout() {
		return pathsLayout;
	}

	public void setTileDragEnabled(boolean tileDragEnabled) {
		this.tileDragEnabled = tileDragEnabled;
		if (!tileDragEnabled) {
			tileDragging = false;
			draggingTile = null;
		}
	}

	public boolean isTileDragEnabled() {
		return tileDragEnabled;
	}

	/** Spacing of the logical grid (cell pitch). */
	private float cellSize() {
		float pitch = pathsLayout ? PATHS_PITCH : 1f;
		float cs = BASE_TILE * scale * pitch;
		return cs < 1f ? 1f : cs;
	}

	/** Drawn size of the tile body inside its cell. */
	private float bodySize() {
		return cellSize() * (pathsLayout ? PATHS_BODY_FRAC : PACK_BODY_FRAC);
	}

	private float bodyLeft(int gridX) {
		float cs = cellSize();
		float bs = bodySize();
		return offsetX + gridX * cs + (cs - bs) * 0.5f;
	}

	private float bodyTop(int gridY) {
		float cs = cellSize();
		float bs = bodySize();
		return offsetY + gridY * cs + (cs - bs) * 0.5f;
	}

	private float cellCenterX(int gridX) {
		float cs = cellSize();
		return offsetX + gridX * cs + cs * 0.5f;
	}

	private float cellCenterY(int gridY) {
		float cs = cellSize();
		return offsetY + gridY * cs + cs * 0.5f;
	}

	/** Convert screen coordinates to map grid cell. */
	public int[] screenToGrid(float x, float y) {
		float cs = cellSize();
		int gx = (int) Math.floor((x - offsetX) / cs);
		int gy = (int) Math.floor((y - offsetY) / cs);
		return new int[] { gx, gy };
	}

	public void centerOnCurrentTile(boolean animateIgnored) {
		MapTile tile = findTile(currentTileId);
		if (tile == null && tiles.size() > 0) {
			tile = tiles.get(0);
		}
		centerOnTile(tile);
	}

	public void centerOnTile(MapTile tile) {
		if (tile == null || getWidth() == 0 || getHeight() == 0) {
			return;
		}
		float cs = cellSize();
		offsetX = getWidth() * 0.5f - (tile.getGridX() * cs + cs * 0.5f);
		offsetY = getHeight() * 0.5f - (tile.getGridY() * cs + cs * 0.5f);
		invalidate();
	}

	/** Zoom in by {@link #ZOOM_STEP}, keeping the view center stable. */
	public void zoomIn() {
		zoomAtViewCenter(scale * ZOOM_STEP);
	}

	/** Zoom out by {@link #ZOOM_STEP}, keeping the view center stable. */
	public void zoomOut() {
		zoomAtViewCenter(scale / ZOOM_STEP);
	}

	/** Reset scale to 1, keeping the view center stable. */
	public void zoomReset() {
		zoomAtViewCenter(1f);
	}

	/**
	 * Multiply current scale by {@code factor}, keeping the view center stable.
	 * Same focus math as pinch zoom with focus at the view midpoint.
	 */
	public void zoomBy(float factor) {
		if (factor <= 0f || Float.isNaN(factor) || Float.isInfinite(factor)) {
			return;
		}
		zoomAtViewCenter(scale * factor);
	}

	/** Apply a target scale while pinning the world point under the view center. */
	private void zoomAtViewCenter(float nextScale) {
		followMode = false;
		float prev = scale;
		float next = clamp(nextScale, MIN_SCALE, MAX_SCALE);
		if (prev <= 0f || Math.abs(next - prev) < 0.0001f) {
			return;
		}
		if (getWidth() <= 0 || getHeight() <= 0) {
			scale = next;
			invalidate();
			return;
		}
		float focusX = getWidth() * 0.5f;
		float focusY = getHeight() * 0.5f;
		float worldX = (focusX - offsetX) / prev;
		float worldY = (focusY - offsetY) / prev;
		scale = next;
		offsetX = focusX - worldX * scale;
		offsetY = focusY - worldY * scale;
		invalidate();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);
		float cs = cellSize();
		float bs = bodySize();
		if (showGrid && cs > 8f && getWidth() > 0 && getHeight() > 0) {
			int minX = (int) Math.floor((-offsetX) / cs) - 1;
			int maxX = (int) Math.ceil((getWidth() - offsetX) / cs) + 1;
			int minY = (int) Math.floor((-offsetY) / cs) - 1;
			int maxY = (int) Math.ceil((getHeight() - offsetY) / cs) + 1;
			maxX = Math.min(maxX, minX + 40);
			maxY = Math.min(maxY, minY + 60);
			for (int gx = minX; gx <= maxX; gx++) {
				float x = offsetX + gx * cs;
				canvas.drawLine(x, 0, x, getHeight(), gridPaint);
			}
			for (int gy = minY; gy <= maxY; gy++) {
				float y = offsetY + gy * cs;
				canvas.drawLine(0, y, getWidth(), y, gridPaint);
			}
		}

		linkBadges.clear();
		drawLinkArrows(canvas, bs);

		textPaint.setTextSize(Math.max(8f, 11f * scale * (pathsLayout ? 0.95f : 1f)));
		for (MapTile tile : tiles) {
			if (tile == null) {
				continue;
			}
			float left = bodyLeft(tile.getGridX());
			float top = bodyTop(tile.getGridY());
			tmpRect.set(left, top, left + bs, top + bs);
			boolean isCurrent = currentTileId != null && currentTileId.equals(tile.getId());
			boolean isSelected = selectedTileId != null && selectedTileId.equals(tile.getId());
			canvas.drawRoundRect(tmpRect, 6f * scale, 6f * scale, isCurrent ? currentFill : tileFill);
			canvas.drawRoundRect(tmpRect, 6f * scale, 6f * scale,
					isSelected ? selectedStroke : tileStroke);
			drawSpecialExitDots(canvas, tile, left, top, bs);
			String label = tile.getTitle();
			if (label == null || label.length() == 0) {
				label = shortId(tile.getId());
			}
			if (scale >= 0.45f) {
				canvas.drawText(truncate(label, scale), left + bs * 0.5f,
						top + bs * 0.55f, textPaint);
			}
		}
		if (tileDragging && draggingTile != null) {
			float left = bodyLeft(dragGridX);
			float top = bodyTop(dragGridY);
			tmpRect.set(left, top, left + bs, top + bs);
			canvas.drawRoundRect(tmpRect, 6f * scale, 6f * scale, dragGhostFill);
			canvas.drawRoundRect(tmpRect, 6f * scale, 6f * scale, dragGhostStroke);
		}
	}

	/**
	 * Draw directed arrows between tiles for exits that have a known destination
	 * on the same drawn set. Labels show walk words; overflow becomes a tappable +N.
	 */
	private void drawLinkArrows(Canvas canvas, float bodySize) {
		if (scale < 0.4f) {
			return;
		}
		Map<String, List<String>> grouped = new HashMap<String, List<String>>();
		for (MapTile tile : tiles) {
			if (tile == null || tile.getExits() == null) {
				continue;
			}
			for (MapExit exit : tile.getExits()) {
				if (exit == null || exit.getToId() == null || exit.getCommand() == null) {
					continue;
				}
				MapTile dest = findTile(exit.getToId());
				if (dest == null) {
					continue;
				}
				String key = tile.getId() + "\0" + dest.getId();
				List<String> cmds = grouped.get(key);
				if (cmds == null) {
					cmds = new ArrayList<String>();
					grouped.put(key, cmds);
				}
				String cmd = exit.getCommand().trim();
				if (cmd.length() > 0 && !cmds.contains(cmd)) {
					cmds.add(cmd);
				}
			}
		}

		linkPaint.setStrokeWidth(Math.max(1.5f, 2.2f * scale));
		linkLabelPaint.setTextSize(Math.max(8f, 9.5f * scale));
		// Start/end arrows just outside the tile body so labels sit in the gutter.
		float edge = bodySize * 0.52f;

		for (Map.Entry<String, List<String>> e : grouped.entrySet()) {
			String key = e.getKey();
			int sep = key.indexOf('\0');
			if (sep <= 0) {
				continue;
			}
			MapTile from = findTile(key.substring(0, sep));
			MapTile to = findTile(key.substring(sep + 1));
			List<String> cmds = e.getValue();
			if (from == null || to == null || cmds == null || cmds.isEmpty()) {
				continue;
			}
			float fromCx = cellCenterX(from.getGridX());
			float fromCy = cellCenterY(from.getGridY());
			float toCx = cellCenterX(to.getGridX());
			float toCy = cellCenterY(to.getGridY());
			float dx = toCx - fromCx;
			float dy = toCy - fromCy;
			float len = (float) Math.sqrt(dx * dx + dy * dy);
			if (len < 4f) {
				continue;
			}
			float ux = dx / len;
			float uy = dy / len;
			// Parallel offset so A→B and B→A do not overlap.
			float ox = -uy * (5f * scale);
			float oy = ux * (5f * scale);
			float x1 = fromCx + ux * edge + ox;
			float y1 = fromCy + uy * edge + oy;
			float x2 = toCx - ux * edge + ox;
			float y2 = toCy - uy * edge + oy;
			canvas.drawLine(x1, y1, x2, y2, linkPaint);
			drawArrowHead(canvas, x2, y2, ux, uy);

			float midX = (x1 + x2) * 0.5f;
			float midY = (y1 + y2) * 0.5f - 4f * scale;
			String label = formatLinkLabel(cmds);
			float tw = linkLabelPaint.measureText(label);
			float pad = 4f * scale;
			float bh = linkLabelPaint.getTextSize() + pad * 1.2f;
			tmpRect.set(midX - tw * 0.5f - pad, midY - bh + pad * 0.3f,
					midX + tw * 0.5f + pad, midY + pad * 0.5f);
			canvas.drawRoundRect(tmpRect, 4f * scale, 4f * scale, linkLabelBg);
			canvas.drawText(label, midX, midY, linkLabelPaint);
			if (cmds.size() > MAX_VISIBLE_LINK_LABELS) {
				LinkBadge badge = new LinkBadge(from.getId(), to.getId(), cmds);
				badge.bounds.set(tmpRect);
				// Enlarge hit target a bit for fat fingers.
				badge.bounds.inset(-6f * scale, -6f * scale);
				linkBadges.add(badge);
			}
		}
	}

	private static String formatLinkLabel(List<String> cmds) {
		if (cmds.size() <= MAX_VISIBLE_LINK_LABELS) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < cmds.size(); i++) {
				if (i > 0) {
					sb.append(" · ");
				}
				sb.append(shortCmd(cmds.get(i)));
			}
			return sb.toString();
		}
		return shortCmd(cmds.get(0)) + " · " + shortCmd(cmds.get(1))
				+ " +" + (cmds.size() - MAX_VISIBLE_LINK_LABELS);
	}

	private static String shortCmd(String cmd) {
		if (cmd == null) {
			return "?";
		}
		String c = cmd.trim();
		if (c.length() <= 12) {
			return c;
		}
		return c.substring(0, 11) + "…";
	}

	private void drawArrowHead(Canvas canvas, float tipX, float tipY, float ux, float uy) {
		float size = Math.max(6f, 8f * scale);
		float bx = tipX - ux * size;
		float by = tipY - uy * size;
		float px = -uy * size * 0.55f;
		float py = ux * size * 0.55f;
		arrowPath.reset();
		arrowPath.moveTo(tipX, tipY);
		arrowPath.lineTo(bx + px, by + py);
		arrowPath.lineTo(bx - px, by - py);
		arrowPath.close();
		linkPaint.setStyle(Paint.Style.FILL);
		canvas.drawPath(arrowPath, linkPaint);
		linkPaint.setStyle(Paint.Style.STROKE);
	}

	/** Dots for specials / level exits that have no drawable destination arrow. */
	private void drawSpecialExitDots(Canvas canvas, MapTile tile, float left, float top,
			float tileSize) {
		float cx = left + tileSize * 0.5f;
		float cy = top + tileSize * 0.5f;
		int specialIndex = 0;
		for (MapExit exit : tile.getExits()) {
			if (exit == null || exit.getCommand() == null) {
				continue;
			}
			MapTile dest = exit.getToId() != null ? findTile(exit.getToId()) : null;
			if (dest != null) {
				continue; // drawn as arrow
			}
			float ox = cx + (specialIndex % 3 - 1) * (6f * scale);
			float oy = cy + tileSize * 0.18f + (specialIndex / 3) * (6f * scale);
			canvas.drawCircle(ox, oy, Math.max(2.5f, 3.5f * scale), specialPaint);
			specialIndex++;
		}
	}

	private LinkBadge hitLinkBadge(float x, float y) {
		for (int i = linkBadges.size() - 1; i >= 0; i--) {
			LinkBadge b = linkBadges.get(i);
			if (b.bounds.contains(x, y)) {
				return b;
			}
		}
		return null;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		scaleDetector.onTouchEvent(event);
		if (!tileDragging && !scaling && event.getPointerCount() < 2) {
			gestureDetector.onTouchEvent(event);
		}
		final int action = event.getActionMasked();
		switch (action) {
		case MotionEvent.ACTION_DOWN:
			lastPanX = event.getX();
			lastPanY = event.getY();
			panning = !tileDragging;
			scaling = false;
			break;
		case MotionEvent.ACTION_POINTER_DOWN:
			// Second finger: stop one-finger pan so pinch does not jump.
			panning = false;
			scaling = true;
			break;
		case MotionEvent.ACTION_MOVE:
			if (tileDragging) {
				int[] g = screenToGrid(event.getX(), event.getY());
				if (g[0] != dragGridX || g[1] != dragGridY) {
					dragGridX = g[0];
					dragGridY = g[1];
					invalidate();
				}
				break;
			}
			if (panning && !scaling && !scaleDetector.isInProgress()
					&& event.getPointerCount() == 1) {
				float dx = event.getX() - lastPanX;
				float dy = event.getY() - lastPanY;
				if (Math.abs(dx) > 1f || Math.abs(dy) > 1f) {
					offsetX += dx;
					offsetY += dy;
					lastPanX = event.getX();
					lastPanY = event.getY();
					followMode = false;
					invalidate();
				}
			}
			break;
		case MotionEvent.ACTION_POINTER_UP: {
			// Remaining finger must not inherit stale pan deltas from before pinch.
			int upIndex = event.getActionIndex();
			int remainIndex = upIndex == 0 ? 1 : 0;
			if (remainIndex < event.getPointerCount()) {
				lastPanX = event.getX(remainIndex);
				lastPanY = event.getY(remainIndex);
			}
			panning = event.getPointerCount() - 1 == 1 && !tileDragging;
			if (event.getPointerCount() - 1 < 2) {
				scaling = false;
			}
			break;
		}
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:
			if (tileDragging) {
				MapTile moved = draggingTile;
				int gx = dragGridX;
				int gy = dragGridY;
				tileDragging = false;
				draggingTile = null;
				invalidate();
				if (moved != null && listener != null) {
					listener.onTileDragEnd(moved, gx, gy);
				}
			}
			panning = false;
			scaling = false;
			break;
		default:
			break;
		}
		return true;
	}

	private MapTile hitTest(float x, float y) {
		float cs = cellSize();
		float bs = bodySize();
		// Prefer the drawn body; fall back to the full cell for fat-finger taps.
		MapTile bodyHit = null;
		MapTile cellHit = null;
		for (int i = tiles.size() - 1; i >= 0; i--) {
			MapTile tile = tiles.get(i);
			if (tile == null) {
				continue;
			}
			float bl = bodyLeft(tile.getGridX());
			float bt = bodyTop(tile.getGridY());
			if (x >= bl && x <= bl + bs && y >= bt && y <= bt + bs) {
				bodyHit = tile;
				break;
			}
			float cl = offsetX + tile.getGridX() * cs;
			float ct = offsetY + tile.getGridY() * cs;
			if (cellHit == null
					&& x >= cl && x <= cl + cs && y >= ct && y <= ct + cs) {
				cellHit = tile;
			}
		}
		return bodyHit != null ? bodyHit : cellHit;
	}

	private MapTile findTile(String id) {
		if (id == null) {
			return null;
		}
		for (MapTile tile : tiles) {
			if (tile != null && id.equals(tile.getId())) {
				return tile;
			}
		}
		return null;
	}

	private static float clamp(float v, float min, float max) {
		return Math.max(min, Math.min(max, v));
	}

	private static String shortId(String id) {
		if (id == null) {
			return "?";
		}
		return id.length() > 4 ? id.substring(0, 4) : id;
	}

	private static String truncate(String s, float scale) {
		int max = scale >= 1.2f ? 14 : (scale >= 0.8f ? 10 : 6);
		if (s == null) {
			return "";
		}
		if (s.length() <= max) {
			return s;
		}
		return s.substring(0, Math.max(1, max - 1)) + "…";
	}
}
