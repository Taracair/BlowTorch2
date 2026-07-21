package com.resurrection.blowtorch2.lib.mapper;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * Draws map tiles on a pan/zoomable grid with exit ticks and current-room highlight.
 */
public class MapperView extends View {

	public interface TileInteractionListener {
		void onTileTap(MapTile tile);
		void onTileLongPress(MapTile tile);
	}

	private static final float BASE_TILE = 56f;
	private static final float MIN_SCALE = 0.35f;
	private static final float MAX_SCALE = 3.5f;

	private final Paint tileFill = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint tileStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint currentFill = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint selectedStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint exitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint specialPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint bgPaint = new Paint();
	private final RectF tmpRect = new RectF();

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

	private float lastPanX;
	private float lastPanY;
	private boolean panning;
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
		specialPaint.setColor(0xFFFFB060);
		specialPaint.setStyle(Paint.Style.FILL);
		bgPaint.setColor(0x00000000);

		scaleDetector = new ScaleGestureDetector(context,
				new ScaleGestureDetector.SimpleOnScaleGestureListener() {
					@Override
					public boolean onScale(ScaleGestureDetector detector) {
						float focusX = detector.getFocusX();
						float focusY = detector.getFocusY();
						float prev = scale;
						scale = clamp(scale * detector.getScaleFactor(), MIN_SCALE, MAX_SCALE);
						offsetX = focusX - (focusX - offsetX) * (scale / prev);
						offsetY = focusY - (focusY - offsetY) * (scale / prev);
						followMode = false;
						invalidate();
						return true;
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
						MapTile tile = hitTest(e.getX(), e.getY());
						if (tile != null) {
							selectedTileId = tile.getId();
							invalidate();
							if (listener != null) {
								listener.onTileTap(tile);
							}
							return true;
						}
						return false;
					}

					@Override
					public void onLongPress(MotionEvent e) {
						MapTile tile = hitTest(e.getX(), e.getY());
						if (tile != null) {
							selectedTileId = tile.getId();
							invalidate();
							if (listener != null) {
								listener.onTileLongPress(tile);
							}
						}
					}

					@Override
					public boolean onDoubleTap(MotionEvent e) {
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

	public void centerOnCurrentTile(boolean animateIgnored) {
		MapTile tile = findTile(currentTileId);
		if (tile == null && tiles.size() > 0) {
			tile = tiles.get(0);
		}
		if (tile == null || getWidth() == 0 || getHeight() == 0) {
			return;
		}
		float tileSize = BASE_TILE * scale;
		float cx = tile.getGridX() * tileSize + tileSize * 0.5f;
		float cy = tile.getGridY() * tileSize + tileSize * 0.5f;
		offsetX = getWidth() * 0.5f - cx;
		offsetY = getHeight() * 0.5f - cy;
		invalidate();
	}

	public void centerOnTile(MapTile tile) {
		if (tile == null || getWidth() == 0 || getHeight() == 0) {
			return;
		}
		float tileSize = BASE_TILE * scale;
		float cx = tile.getGridX() * tileSize + tileSize * 0.5f;
		float cy = tile.getGridY() * tileSize + tileSize * 0.5f;
		offsetX = getWidth() * 0.5f - cx;
		offsetY = getHeight() * 0.5f - cy;
		invalidate();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);
		float tileSize = BASE_TILE * scale;
		textPaint.setTextSize(Math.max(8f, 11f * scale));

		for (MapTile tile : tiles) {
			if (tile == null) {
				continue;
			}
			float left = offsetX + tile.getGridX() * tileSize;
			float top = offsetY + tile.getGridY() * tileSize;
			tmpRect.set(left + 2, top + 2, left + tileSize - 2, top + tileSize - 2);
			boolean isCurrent = currentTileId != null && currentTileId.equals(tile.getId());
			boolean isSelected = selectedTileId != null && selectedTileId.equals(tile.getId());
			canvas.drawRoundRect(tmpRect, 6f * scale, 6f * scale, isCurrent ? currentFill : tileFill);
			canvas.drawRoundRect(tmpRect, 6f * scale, 6f * scale,
					isSelected ? selectedStroke : tileStroke);
			drawExits(canvas, tile, left, top, tileSize);
			String label = tile.getTitle();
			if (label == null || label.length() == 0) {
				label = shortId(tile.getId());
			}
			if (scale >= 0.55f) {
				canvas.drawText(truncate(label, scale), left + tileSize * 0.5f,
						top + tileSize * 0.55f, textPaint);
			}
		}
	}

	private void drawExits(Canvas canvas, MapTile tile, float left, float top, float tileSize) {
		float cx = left + tileSize * 0.5f;
		float cy = top + tileSize * 0.5f;
		float tick = Math.max(4f, 7f * scale);
		int specialIndex = 0;
		for (MapExit exit : tile.getExits()) {
			if (exit == null || exit.getCommand() == null) {
				continue;
			}
			String dir = MapDirections.normalize(exit.getCommand(), null);
			if (exit.isSpecial() || isVerticalOrPortal(dir)) {
				float ox = cx + (specialIndex % 3 - 1) * (6f * scale);
				float oy = cy + tileSize * 0.18f + (specialIndex / 3) * (6f * scale);
				canvas.drawCircle(ox, oy, Math.max(2.5f, 3.5f * scale), specialPaint);
				specialIndex++;
				continue;
			}
			float x1 = cx;
			float y1 = cy;
			float x2 = cx;
			float y2 = cy;
			boolean drawn = true;
			if ("n".equals(dir) || "north".equals(dir)) {
				y1 = top + 4;
				y2 = y1 + tick;
			} else if ("s".equals(dir) || "south".equals(dir)) {
				y1 = top + tileSize - 4;
				y2 = y1 - tick;
			} else if ("e".equals(dir) || "east".equals(dir)) {
				x1 = left + tileSize - 4;
				x2 = x1 - tick;
			} else if ("w".equals(dir) || "west".equals(dir)) {
				x1 = left + 4;
				x2 = x1 + tick;
			} else if ("ne".equals(dir)) {
				x1 = left + tileSize - 6;
				y1 = top + 6;
				x2 = x1 - tick * 0.7f;
				y2 = y1 + tick * 0.7f;
			} else if ("nw".equals(dir)) {
				x1 = left + 6;
				y1 = top + 6;
				x2 = x1 + tick * 0.7f;
				y2 = y1 + tick * 0.7f;
			} else if ("se".equals(dir)) {
				x1 = left + tileSize - 6;
				y1 = top + tileSize - 6;
				x2 = x1 - tick * 0.7f;
				y2 = y1 - tick * 0.7f;
			} else if ("sw".equals(dir)) {
				x1 = left + 6;
				y1 = top + tileSize - 6;
				x2 = x1 + tick * 0.7f;
				y2 = y1 - tick * 0.7f;
			} else {
				drawn = false;
				float ox = cx + (specialIndex % 3 - 1) * (6f * scale);
				float oy = cy + tileSize * 0.2f + (specialIndex / 3) * (6f * scale);
				canvas.drawCircle(ox, oy, Math.max(2.5f, 3.5f * scale), specialPaint);
				specialIndex++;
			}
			if (drawn) {
				canvas.drawLine(x1, y1, x2, y2, exitPaint);
			}
		}
	}

	private static boolean isVerticalOrPortal(String dir) {
		return "u".equals(dir) || "d".equals(dir) || "up".equals(dir) || "down".equals(dir)
				|| "in".equals(dir) || "out".equals(dir);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		scaleDetector.onTouchEvent(event);
		gestureDetector.onTouchEvent(event);
		final int action = event.getActionMasked();
		switch (action) {
		case MotionEvent.ACTION_DOWN:
			lastPanX = event.getX();
			lastPanY = event.getY();
			panning = true;
			break;
		case MotionEvent.ACTION_MOVE:
			if (panning && !scaleDetector.isInProgress() && event.getPointerCount() == 1) {
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
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:
			panning = false;
			break;
		default:
			break;
		}
		return true;
	}

	private MapTile hitTest(float x, float y) {
		float tileSize = BASE_TILE * scale;
		for (int i = tiles.size() - 1; i >= 0; i--) {
			MapTile tile = tiles.get(i);
			if (tile == null) {
				continue;
			}
			float left = offsetX + tile.getGridX() * tileSize;
			float top = offsetY + tile.getGridY() * tileSize;
			if (x >= left && x <= left + tileSize && y >= top && y <= top + tileSize) {
				return tile;
			}
		}
		return null;
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
