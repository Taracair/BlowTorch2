package com.resurrection.blowtorch2.lib.mapper;

/**
 * The layout values {@code applyLayoutMode} actually writes. Float position is
 * not part of a fullscreen frame, so a snapshot that only repeats those
 * numbers must not call {@code setLayoutParams}.
 */
public final class MapperOverlayGeometry {

	public final boolean fullscreen;
	public final int x;
	public final int y;
	public final int width;
	public final int height;
	public final int padLeft;
	public final int padTop;
	public final int padRight;
	public final int padBottom;
	public final boolean constrainAbove;
	public final int aboveViewId;

	private MapperOverlayGeometry(boolean fullscreen, int x, int y, int width, int height,
			int padLeft, int padTop, int padRight, int padBottom,
			boolean constrainAbove, int aboveViewId) {
		this.fullscreen = fullscreen;
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.padLeft = padLeft;
		this.padTop = padTop;
		this.padRight = padRight;
		this.padBottom = padBottom;
		this.constrainAbove = constrainAbove;
		this.aboveViewId = aboveViewId;
	}

	public static MapperOverlayGeometry capture(boolean fullscreen,
			int floatX, int floatY, int floatWidth, int floatHeight,
			float density, boolean constrainAbove, int aboveViewId) {
		if (fullscreen) {
			return new MapperOverlayGeometry(true, 0, 0, 0, 0,
					(int) (4 * density), (int) (8 * density),
					(int) (8 * density), (int) (4 * density),
					constrainAbove, constrainAbove ? aboveViewId : 0);
		}
		return new MapperOverlayGeometry(false, floatX, floatY, floatWidth, floatHeight,
				(int) (2 * density), (int) (2 * density),
				(int) (2 * density), (int) (2 * density),
				false, 0);
	}

	public boolean sameAs(MapperOverlayGeometry other) {
		if (other == null) {
			return false;
		}
		return fullscreen == other.fullscreen
				&& x == other.x
				&& y == other.y
				&& width == other.width
				&& height == other.height
				&& padLeft == other.padLeft
				&& padTop == other.padTop
				&& padRight == other.padRight
				&& padBottom == other.padBottom
				&& constrainAbove == other.constrainAbove
				&& aboveViewId == other.aboveViewId;
	}
}
