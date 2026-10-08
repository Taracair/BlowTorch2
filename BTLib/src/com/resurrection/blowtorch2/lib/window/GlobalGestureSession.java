package com.resurrection.blowtorch2.lib.window;

/** Decides what one touch sequence does under {@link GlobalGestures}.
 *
 * Android-free. The window feeds pointer positions and applies the decision.
 * Classic mode returns {@link Decision.Kind#IGNORE} so the existing scroll
 * and immediate two-finger copy stay on their own path.
 */
public final class GlobalGestureSession {

	public static final class Decision {
		public enum Kind {
			IGNORE, EAT, PREVIEW, CLEAR, FIRE, COPY, SCROLL
		}

		public final Kind kind;
		public final String command;
		public final String direction;
		public final float dx;
		public final float dy;
		/** Pointer index the preview is following. 0 is the first finger. */
		public final int finger;

		private Decision(final Kind kind, final String command, final String direction,
				final float dx, final float dy, final int finger) {
			this.kind = kind;
			this.command = command;
			this.direction = direction;
			this.dx = dx;
			this.dy = dy;
			this.finger = finger;
		}

		public static Decision ignore() {
			return new Decision(Kind.IGNORE, null, null, 0f, 0f, 0);
		}

		public static Decision eat() {
			return new Decision(Kind.EAT, null, null, 0f, 0f, 0);
		}

		public static Decision preview(final String direction, final String command,
				final int finger) {
			return new Decision(Kind.PREVIEW, command, direction, 0f, 0f, finger);
		}

		public static Decision clear() {
			return new Decision(Kind.CLEAR, null, null, 0f, 0f, 0);
		}

		public static Decision fire(final String command) {
			return new Decision(Kind.FIRE, command, null, 0f, 0f, 0);
		}

		public static Decision copy() {
			return new Decision(Kind.COPY, null, null, 0f, 0f, 0);
		}

		public static Decision scroll(final float dx, final float dy) {
			return new Decision(Kind.SCROLL, null, null, dx, dy, 0);
		}
	}

	private enum Phase {
		IDLE, TRACK1, ARMED1, PREVIEW1, DEAD1, SCROLL1,
		TRACK2, PREVIEW2, DEAD2, SCROLL2, LIFTED, CANCELLED
	}

	private GlobalGestures config = GlobalGestures.defaults();
	private float slop = 8f;
	private float travel = 24f;

	private Phase phase = Phase.IDLE;
	private boolean bypass;
	private long downTime;
	private float ox0, oy0, ox1, oy1;
	private float lx0, ly0, lx1, ly1;
	private float tipX, tipY;
	private int activeFinger;
	private boolean seenTwo;
	private boolean ownedScroll;
	private String lockedDir;
	private String lockedCmd;

	public void setConfig(final GlobalGestures config, final float slopPx, final float travelPx) {
		this.config = config != null ? config : GlobalGestures.defaults();
		this.slop = slopPx > 0f ? slopPx : 8f;
		this.travel = travelPx > 0f ? travelPx : 24f;
	}

	public void reset() {
		phase = Phase.IDLE;
		bypass = false;
		seenTwo = false;
		ownedScroll = false;
		lockedDir = null;
		lockedCmd = null;
	}

	public Decision onDown(final long time, final float x, final float y) {
		reset();
		downTime = time;
		ox0 = x;
		oy0 = y;
		lx0 = x;
		ly0 = y;
		if (config.mode() == GlobalGestures.MODE_CLASSIC) {
			bypass = true;
			return Decision.ignore();
		}
		phase = config.oneFingerGestures() ? Phase.TRACK1 : Phase.SCROLL1;
		return Decision.ignore();
	}

	public Decision onMove(final long time, final int pointerCount,
			final float x0, final float y0, final float x1, final float y1) {
		if (bypass || phase == Phase.IDLE) {
			note(pointerCount, x0, y0, x1, y1);
			return Decision.ignore();
		}
		if (pointerCount >= 2 && oneFingerAiming(phase)) {
			return cancelGesture();
		}
		if (pointerCount >= 2 && interceptTwo() && oneFingerPhase(phase)) {
			boolean had = lockedDir != null;
			beginTwo(x0, y0, x1, y1);
			Decision d = moveTwo(0f, 0f, 0f, 0f, x0, y0, x1, y1);
			if (d.kind == Decision.Kind.EAT && had) {
				return Decision.clear();
			}
			return d;
		}
		if (pointerCount >= 2 && config.mode() == GlobalGestures.MODE_ONE && !interceptTwo()) {
			note(pointerCount, x0, y0, x1, y1);
			return cancelGesture();
		}
		if (phase == Phase.SCROLL1) {
			float sdx = x0 - lx0;
			float sdy = y0 - ly0;
			note(pointerCount, x0, y0, x1, y1);
			if (ownedScroll && !seenTwo) {
				return Decision.scroll(sdx, sdy);
			}
			return seenTwo ? Decision.eat() : Decision.ignore();
		}
		float pdx0 = x0 - lx0;
		float pdy0 = y0 - ly0;
		float pdx1 = x1 - lx1;
		float pdy1 = y1 - ly1;
		note(pointerCount, x0, y0, x1, y1);
		if (phase == Phase.CANCELLED || phase == Phase.DEAD2 || phase == Phase.LIFTED) {
			return Decision.eat();
		}
		if (phase == Phase.PREVIEW1 || phase == Phase.DEAD1) {
			return aimOne(x0, y0);
		}
		if (phase == Phase.SCROLL2 && pointerCount < 2) {
			phase = Phase.LIFTED;
			return Decision.eat();
		}
		if (phase == Phase.SCROLL2 || phase == Phase.TRACK2 || phase == Phase.PREVIEW2) {
			return moveTwo(pdx0, pdy0, pdx1, pdy1, x0, y0, x1, y1);
		}
		if (seenTwo) {
			return Decision.eat();
		}
		return moveOne(time, x0, y0);
	}

	public Decision onPointerDown(final long time, final int pointerCount,
			final float x0, final float y0, final float x1, final float y1) {
		if (bypass) {
			return Decision.ignore();
		}
		if (pointerCount >= 3) {
			return cancelGesture();
		}
		if (oneFingerAiming(phase)) {
			return cancelGesture();
		}
		if (!interceptTwo()) {
			if (config.mode() == GlobalGestures.MODE_ONE) {
				return cancelGesture();
			}
			return Decision.ignore();
		}
		boolean had = lockedDir != null;
		beginTwo(x0, y0, x1, y1);
		return had ? Decision.clear() : Decision.eat();
	}

	private void beginTwo(final float x0, final float y0, final float x1, final float y1) {
		ox0 = x0;
		oy0 = y0;
		ox1 = x1;
		oy1 = y1;
		lx0 = x0;
		ly0 = y0;
		lx1 = x1;
		ly1 = y1;
		tipX = x0;
		tipY = y0;
		activeFinger = 1;
		phase = Phase.TRACK2;
		seenTwo = true;
		ownedScroll = false;
		lockedDir = null;
		lockedCmd = null;
	}

	public Decision onPointerUp(final long time, final int pointersRemaining) {
		return onPointerUp(time, pointersRemaining, activeFinger);
	}

	/** {@code liftedIndex} is the pointer that left. The other finger cancels. */
	public Decision onPointerUp(final long time, final int pointersRemaining,
			final int liftedIndex) {
		if (bypass) {
			return Decision.ignore();
		}
		if (phase == Phase.PREVIEW2 && liftedIndex != activeFinger) {
			return cancelGesture();
		}
		if (phase == Phase.PREVIEW1 || phase == Phase.PREVIEW2) {
			return fireLocked();
		}
		Decision tap = copyIfStill();
		if (tap != null) {
			return tap;
		}
		if (phase == Phase.TRACK2 || phase == Phase.SCROLL2) {
			phase = Phase.LIFTED;
			return Decision.eat();
		}
		if (phase == Phase.SCROLL1 && ownedScroll) {
			phase = Phase.LIFTED;
			return Decision.eat();
		}
		if (phase == Phase.SCROLL1 && !seenTwo) {
			return Decision.ignore();
		}
		return Decision.eat();
	}

	public Decision onUp(final long time, final float x, final float y) {
		if (ownedScroll && phase == Phase.SCROLL1) {
			reset();
			return Decision.eat();
		}
		if (bypass || phase == Phase.SCROLL1 || phase == Phase.TRACK1 || phase == Phase.ARMED1
				|| phase == Phase.IDLE) {
			reset();
			return Decision.ignore();
		}
		if (phase == Phase.PREVIEW1 || phase == Phase.PREVIEW2) {
			return fireLocked();
		}
		Decision tap = copyIfStill();
		if (tap != null) {
			return tap;
		}
		reset();
		return Decision.eat();
	}

	/** A stationary two-finger tap. Decided while both points are still stored,
	 * which is the pointer-up, not the last finger's up. */
	private Decision copyIfStill() {
		if ((phase == Phase.TRACK2 || phase == Phase.LIFTED) && twoCopyOn()
				&& hypot(lx0 - ox0, ly0 - oy0) <= slop
				&& hypot(lx1 - ox1, ly1 - oy1) <= slop) {
			phase = Phase.CANCELLED;
			lockedDir = null;
			lockedCmd = null;
			return Decision.copy();
		}
		return null;
	}

	public Decision onCancel() {
		if (ownedScroll && phase == Phase.SCROLL1) {
			reset();
			return Decision.eat();
		}
		if (bypass || phase == Phase.IDLE || phase == Phase.SCROLL1 || phase == Phase.TRACK1) {
			reset();
			return Decision.ignore();
		}
		boolean preview = phase == Phase.PREVIEW1 || phase == Phase.PREVIEW2;
		reset();
		return preview ? Decision.clear() : Decision.eat();
	}

	private Decision moveOne(final long time, final float x, final float y) {
		float dx = x - ox0;
		float dy = y - oy0;
		float dist = hypot(dx, dy);
		boolean holdPolicy = config.scroll() == GlobalGestures.SCROLL_HOLD;
		if (holdPolicy && time - downTime < config.holdMs()) {
			if (dist > slop) {
				phase = Phase.SCROLL1;
				return Decision.ignore();
			}
			return Decision.eat();
		}
		if (dist <= slop) {
			return Decision.eat();
		}
		return aimOne(x, y);
	}

	private Decision aimOne(final float x, final float y) {
		if (lockedDir != null) {
			return follow(x, y, 1);
		}
		String dir = GlobalGestures.direction(x - ox0, y - oy0, travel);
		if (dir == null) {
			return dropPreview(Phase.DEAD1);
		}
		String cmd = config.binding(1, dir);
		if (cmd == null) {
			if (blankOneFingerScrolls()) {
				return beginOwnedScroll(x - ox0, y - oy0);
			}
			return dropPreview(Phase.DEAD1);
		}
		return lockAim(dir, cmd, x, y, 1);
	}

	/** A new slice is measured from the tip of the current one, once the finger
	 * has moved the slop into that slice. Continuing the same way moves the tip. */
	private Decision follow(final float x, final float y, final int fingers) {
		float dx = x - tipX;
		float dy = y - tipY;
		float along = alongLocked(x, y);
		int at = fingers == 1 ? 0 : activeFinger;
		if (hypot(dx, dy) < slop) {
			if (along > 0f) {
				tipX = x;
				tipY = y;
			}
			return Decision.preview(lockedDir, lockedCmd, at);
		}
		String dir = GlobalGestures.direction(dx, dy, slop);
		if (dir == null || dir.equals(lockedDir)) {
			if (along > 0f) {
				tipX = x;
				tipY = y;
			}
			return Decision.preview(lockedDir, lockedCmd, at);
		}
		String cmd = config.binding(fingers, dir);
		if (cmd == null) {
			if (fingers == 1 && blankOneFingerScrolls()) {
				return beginOwnedScroll(x - tipX, y - tipY);
			}
			rebase(x, y, fingers);
			return dropPreview(fingers == 1 ? Phase.DEAD1 : Phase.TRACK2);
		}
		return lockAim(dir, cmd, x, y, fingers);
	}

	private Decision lockAim(final String dir, final String cmd, final float x, final float y,
			final int fingers) {
		lockedDir = dir;
		lockedCmd = cmd;
		tipX = x;
		tipY = y;
		phase = fingers == 1 ? Phase.PREVIEW1 : Phase.PREVIEW2;
		return Decision.preview(dir, cmd, fingers == 1 ? 0 : activeFinger);
	}

	private void rebase(final float x, final float y, final int fingers) {
		lockedDir = null;
		lockedCmd = null;
		tipX = x;
		tipY = y;
		if (fingers == 1 || activeFinger == 0) {
			ox0 = x;
			oy0 = y;
		}
		if (fingers == 2 && activeFinger == 1) {
			ox1 = x;
			oy1 = y;
		}
	}

	private float alongLocked(final float x, final float y) {
		float ux = 0f;
		float uy = 0f;
		if ("n".equals(lockedDir)) {
			uy = -1f;
		} else if ("s".equals(lockedDir)) {
			uy = 1f;
		} else if ("e".equals(lockedDir)) {
			ux = 1f;
		} else if ("w".equals(lockedDir)) {
			ux = -1f;
		} else if ("ne".equals(lockedDir)) {
			ux = 0.7071f;
			uy = -0.7071f;
		} else if ("nw".equals(lockedDir)) {
			ux = -0.7071f;
			uy = -0.7071f;
		} else if ("se".equals(lockedDir)) {
			ux = 0.7071f;
			uy = 0.7071f;
		} else if ("sw".equals(lockedDir)) {
			ux = -0.7071f;
			uy = 0.7071f;
		}
		return (x - tipX) * ux + (y - tipY) * uy;
	}

	private Decision dropPreview(final Phase back) {
		boolean shown = lockedDir != null;
		lockedDir = null;
		lockedCmd = null;
		phase = back;
		return shown ? Decision.clear() : Decision.eat();
	}

	private Decision moveTwo(final float pdx0, final float pdy0, final float pdx1, final float pdy1,
			final float x0, final float y0, final float x1, final float y1) {
		float d0 = hypot(x0 - ox0, y0 - oy0);
		float d1 = hypot(x1 - ox1, y1 - oy1);
		float lead = Math.max(d0, d1);
		float trail = Math.min(d0, d1);
		// Once a two-finger pan has started, a paused finger does not turn it
		// back into a gesture.
		if (phase == Phase.SCROLL2) {
			return scrollFingers(pdx0, pdy0, pdx1, pdy1);
		}
		// With two fingers, in One finger or Both: two fingers are the scroll.
		if (config.scroll() == GlobalGestures.SCROLL_TWO
				&& (config.mode() == GlobalGestures.MODE_ONE
						|| config.mode() == GlobalGestures.MODE_BOTH)) {
			if (lead > slop) {
				phase = Phase.SCROLL2;
				return scrollFingers(pdx0, pdy0, pdx1, pdy1);
			}
			return Decision.eat();
		}
		// Same direction, not the same speed. A finger inside two slops stays the anchor.
		if (twoScrollOn() && fingersAgree(x0, y0, x1, y1, lead, trail)) {
			phase = Phase.SCROLL2;
			return scrollFingers(pdx0, pdy0, pdx1, pdy1);
		}
		if (!twoDirOn()) {
			return Decision.eat();
		}
		if (lockedDir != null) {
			return follow(activeFinger == 0 ? x0 : x1, activeFinger == 0 ? y0 : y1, 2);
		}
		if (lead < travel) {
			return Decision.eat();
		}
		int finger = d1 >= d0 ? 1 : 0;
		float dx = finger == 0 ? x0 - ox0 : x1 - ox1;
		float dy = finger == 0 ? y0 - oy0 : y1 - oy1;
		String dir = GlobalGestures.direction(dx, dy, travel);
		if (dir == null) {
			return dropPreview(Phase.TRACK2);
		}
		String cmd = config.binding(2, dir);
		if (cmd == null) {
			return dropPreview(Phase.TRACK2);
		}
		activeFinger = finger;
		return lockAim(dir, cmd, finger == 0 ? x0 : x1, finger == 0 ? y0 : y1, 2);
	}

	/** 0.35 is about 70 degrees. The slower finger may lag past two slops; inside that it stays an anchor. */
	private boolean fingersAgree(final float x0, final float y0, final float x1, final float y1,
			final float lead, final float trail) {
		if (lead < travel || trail < slop * 2f) {
			return false;
		}
		float vx0 = x0 - ox0;
		float vy0 = y0 - oy0;
		float vx1 = x1 - ox1;
		float vy1 = y1 - oy1;
		float a = hypot(vx0, vy0);
		float b = hypot(vx1, vy1);
		if (a < 1f || b < 1f) {
			return false;
		}
		return (vx0 * vx1 + vy0 * vy1) / (a * b) > 0.35f;
	}

	/** Same-direction motion uses the midpoint. A finger that did not move this frame does not halve it. */
	private Decision scrollFingers(final float pdx0, final float pdy0,
			final float pdx1, final float pdy1) {
		float m0 = hypot(pdx0, pdy0);
		float m1 = hypot(pdx1, pdy1);
		if (m0 < 1f) {
			return Decision.scroll(pdx1, pdy1);
		}
		if (m1 < 1f) {
			return Decision.scroll(pdx0, pdy0);
		}
		return Decision.scroll((pdx0 + pdx1) * 0.5f, (pdy0 + pdy1) * 0.5f);
	}

	/** 1 pans sideways, 2 scrolls the backlog. No sideways room stays on the backlog. */
	static int scrollAxis(final float dx, final float dy, final boolean canPanX) {
		if (canPanX && Math.abs(dx) > Math.abs(dy)) {
			return 1;
		}
		return 2;
	}

	private Decision fireLocked() {
		String cmd = lockedCmd;
		phase = Phase.CANCELLED;
		lockedDir = null;
		lockedCmd = null;
		if (cmd == null) {
			return Decision.eat();
		}
		return Decision.fire(cmd);
	}

	private Decision beginOwnedScroll(final float dx, final float dy) {
		ownedScroll = true;
		phase = Phase.SCROLL1;
		lockedDir = null;
		lockedCmd = null;
		return Decision.scroll(dx, dy);
	}

	/** Hold, then gesture: a direction with no command scrolls. Off does not. */
	private boolean blankOneFingerScrolls() {
		return config.scroll() == GlobalGestures.SCROLL_HOLD && config.oneFingerGestures();
	}

	private Decision cancelGesture() {
		boolean preview = phase == Phase.PREVIEW1 || phase == Phase.PREVIEW2;
		phase = Phase.CANCELLED;
		ownedScroll = false;
		lockedDir = null;
		lockedCmd = null;
		return preview ? Decision.clear() : Decision.eat();
	}

	private void note(final int pointerCount, final float x0, final float y0,
			final float x1, final float y1) {
		lx0 = x0;
		ly0 = y0;
		if (pointerCount > 1) {
			lx1 = x1;
			ly1 = y1;
		}
	}

	private static boolean oneFingerPhase(final Phase phase) {
		return phase == Phase.TRACK1 || phase == Phase.ARMED1 || phase == Phase.PREVIEW1
				|| phase == Phase.DEAD1 || phase == Phase.SCROLL1 || phase == Phase.LIFTED;
	}

	/** A one-finger swipe is already on screen. A second finger ends it. */
	private static boolean oneFingerAiming(final Phase phase) {
		return phase == Phase.PREVIEW1 || phase == Phase.ARMED1 || phase == Phase.DEAD1;
	}

	private boolean interceptTwo() {
		return config.twoFingerMode()
				|| (config.mode() == GlobalGestures.MODE_ONE
						&& config.scroll() == GlobalGestures.SCROLL_TWO);
	}

	private boolean twoDirOn() {
		return config.twoFingerMode() && config.twoDir();
	}

	private boolean twoScrollOn() {
		return config.mode() == GlobalGestures.MODE_BOTH && config.twoScroll();
	}

	private boolean twoCopyOn() {
		if (config.twoFingerMode()) {
			return config.twoCopy();
		}
		return config.mode() == GlobalGestures.MODE_ONE
				&& config.scroll() == GlobalGestures.SCROLL_TWO;
	}

	private static float hypot(final float dx, final float dy) {
		return (float) Math.hypot(dx, dy);
	}
}
