package com.resurrection.blowtorch2.lib.mapper;

/**
 * What a recorded walk command does on the map: planar grid step, level change,
 * or special (off-grid neighbor / smart-return).
 */
public final class MapMoveEffect {

	public enum Kind {
		GRID,
		LEVEL,
		SPECIAL
	}

	public final Kind kind;
	/** GRID: +x east, +y south. */
	public final int dx;
	public final int dy;
	/** LEVEL: +1 up / −1 down. */
	public final int levelDelta;

	private MapMoveEffect(Kind kind, int dx, int dy, int levelDelta) {
		this.kind = kind;
		this.dx = dx;
		this.dy = dy;
		this.levelDelta = levelDelta;
	}

	public static MapMoveEffect grid(int dx, int dy) {
		return new MapMoveEffect(Kind.GRID, dx, dy, 0);
	}

	public static MapMoveEffect level(int delta) {
		return new MapMoveEffect(Kind.LEVEL, 0, 0, delta);
	}

	public static MapMoveEffect special() {
		return new MapMoveEffect(Kind.SPECIAL, 0, 0, 0);
	}

	@Override
	public String toString() {
		if (kind == Kind.GRID) {
			return "grid " + dx + " " + dy;
		}
		if (kind == Kind.LEVEL) {
			return "level " + levelDelta;
		}
		return "special";
	}
}
