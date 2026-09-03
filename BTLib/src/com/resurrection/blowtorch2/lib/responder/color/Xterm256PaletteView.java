package com.resurrection.blowtorch2.lib.responder.color;

import com.resurrection.blowtorch2.lib.R;
import com.resurrection.blowtorch2.lib.service.Colorizer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;

public class Xterm256PaletteView extends View {

	public interface OnIndexSelectedListener {
		void onIndexSelected(int index);
	}

	private static final int COLUMNS = 16;
	private static final int CELLS = 256;

	private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

	private OnIndexSelectedListener listener;
	private int[] cellColors;
	private int selectedIndex = -1;
	private int touchSlop;
	private float downX;
	private float downY;
	private boolean dragging;
	private boolean downOnCell;

	public Xterm256PaletteView(Context context) {
		super(context);
		init(context);
	}

	public Xterm256PaletteView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public Xterm256PaletteView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context);
	}

	private void init(Context context) {
		setClickable(true);
		touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
		outlinePaint.setStyle(Paint.Style.STROKE);
		accentPaint.setStyle(Paint.Style.STROKE);
		int accent = context.getResources().getColor(R.color.chrome_accent, null);
		accentPaint.setColor(accent);
		outlinePaint.setColor(0xFF000000);
	}

	public void setOnIndexSelectedListener(OnIndexSelectedListener listener) {
		this.listener = listener;
	}

	public int getSelectedIndex() {
		return selectedIndex;
	}

	public void setSelectedIndex(int index) {
		int next = (index >= 0 && index < CELLS) ? index : -1;
		if (next == selectedIndex) {
			return;
		}
		selectedIndex = next;
		invalidate();
	}

	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		setAlpha(enabled ? 1f : 0.45f);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int min = (int) (COLUMNS * 14f * getResources().getDisplayMetrics().density);
		int width = resolveSize(Math.max(min, getSuggestedMinimumWidth()),
				widthMeasureSpec);
		int heightMode = MeasureSpec.getMode(heightMeasureSpec);
		int heightSize = MeasureSpec.getSize(heightMeasureSpec);
		int height = width;
		if (heightMode == MeasureSpec.EXACTLY) {
			height = heightSize;
		} else if (heightMode == MeasureSpec.AT_MOST) {
			height = Math.min(width, heightSize);
		}
		setMeasuredDimension(width, height);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		int w = getWidth();
		int h = getHeight();
		if (w <= 0 || h <= 0) {
			return;
		}
		float cellW = w / (float) COLUMNS;
		float cellH = h / (float) COLUMNS;
		ensureCellColors();
		float density = getResources().getDisplayMetrics().density;
		outlinePaint.setStrokeWidth(density);
		accentPaint.setStrokeWidth(2f * density);
		for (int i = 0; i < CELLS; i++) {
			int row = i / COLUMNS;
			int col = i % COLUMNS;
			float left = col * cellW;
			float top = row * cellH;
			fillPaint.setColor(cellColors[i]);
			canvas.drawRect(left, top, left + cellW, top + cellH, fillPaint);
		}
		if (selectedIndex >= 0 && selectedIndex < CELLS) {
			int row = selectedIndex / COLUMNS;
			int col = selectedIndex % COLUMNS;
			float left = col * cellW;
			float top = row * cellH;
			float inset = density;
			canvas.drawRect(left + inset, top + inset,
					left + cellW - inset, top + cellH - inset, outlinePaint);
			canvas.drawRect(left + inset * 2f, top + inset * 2f,
					left + cellW - inset * 2f, top + cellH - inset * 2f,
					accentPaint);
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (!isEnabled()) {
			return false;
		}
		float cellW = getWidth() / (float) COLUMNS;
		float cellH = getHeight() / (float) COLUMNS;
		ViewParent parent = getParent();
		switch (event.getActionMasked()) {
		case MotionEvent.ACTION_DOWN:
			downX = event.getX();
			downY = event.getY();
			dragging = false;
			downOnCell = cellAt(event.getX(), event.getY(), cellW, cellH) >= 0;
			if (downOnCell && parent != null) {
				parent.requestDisallowInterceptTouchEvent(true);
			}
			return downOnCell || super.onTouchEvent(event);
		case MotionEvent.ACTION_MOVE:
			if (!dragging) {
				float dx = event.getX() - downX;
				float dy = event.getY() - downY;
				if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
					dragging = true;
					if (Math.abs(dy) > Math.abs(dx) && parent != null) {
						parent.requestDisallowInterceptTouchEvent(false);
					}
				}
			}
			return true;
		case MotionEvent.ACTION_UP:
			if (parent != null) {
				parent.requestDisallowInterceptTouchEvent(false);
			}
			if (!dragging && downOnCell) {
				int index = cellAt(event.getX(), event.getY(), cellW, cellH);
				if (index >= 0) {
					setSelectedIndex(index);
					playSoundEffect(android.view.SoundEffectConstants.CLICK);
					if (listener != null) {
						listener.onIndexSelected(index);
					}
				}
			}
			dragging = false;
			downOnCell = false;
			return true;
		case MotionEvent.ACTION_CANCEL:
			if (parent != null) {
				parent.requestDisallowInterceptTouchEvent(false);
			}
			dragging = false;
			downOnCell = false;
			return true;
		default:
			return super.onTouchEvent(event);
		}
	}

	private void ensureCellColors() {
		if (cellColors != null) {
			return;
		}
		cellColors = new int[CELLS];
		for (int i = 0; i < CELLS; i++) {
			cellColors[i] = Colorizer.get256ColorValue(Integer.valueOf(i));
		}
	}

	private int cellAt(float x, float y, float cellW, float cellH) {
		if (cellW <= 0f || cellH <= 0f) {
			return -1;
		}
		int col = (int) (x / cellW);
		int row = (int) (y / cellH);
		if (col < 0 || col >= COLUMNS || row < 0 || row >= COLUMNS) {
			return -1;
		}
		return row * COLUMNS + col;
	}
}
