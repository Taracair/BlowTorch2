package com.resurrection.blowtorch2.lib.alias;

import java.util.ArrayDeque;

/**
 * Caps the alias rewrites one typed line may cause across its semicolon
 * segments. {@link AliasRecursion} bounds one segment, but Connection feeds
 * every segment of a rewrite back through the aliases, so
 * {@code ^say (.+)$ -> Say one $1;say two $1} grew without end on the
 * connection thread (measured 26 Oct 2026: 18 min of CPU, nothing sent).
 *
 * <p>Depth is the real bound: each level of that loop makes the line longer, so
 * a total-only cap of 1000 still froze the phone for about 90 s on egosum's 145
 * aliases. The total stays for breadth ({@code x -> x;x}).
 */
public final class AliasExpansionBudget {

	/** {@code #100} of a one-alias command is 100 rewrites; ten times that. */
	public static final int DEFAULT_MAX_REWRITES = 1000;

	/** Same bound as {@link AliasRecursion#DEFAULT_MAX_PASSES}. */
	public static final int DEFAULT_MAX_DEPTH = AliasRecursion.DEFAULT_MAX_PASSES;

	private final int maxRewrites;
	private final int maxDepth;
	private int used;
	/** Unread children per open rewrite, innermost on top. Segments are walked depth-first. */
	private final ArrayDeque<int[]> pending = new ArrayDeque<int[]>();

	public AliasExpansionBudget() {
		this(DEFAULT_MAX_REWRITES, DEFAULT_MAX_DEPTH);
	}

	public AliasExpansionBudget(final int maxRewrites, final int maxDepth) {
		this.maxRewrites = maxRewrites;
		this.maxDepth = maxDepth;
	}

	/** Call once per segment taken off the list. @return that segment's depth. */
	public int take() {
		while (!pending.isEmpty() && pending.peek()[0] == 0) {
			pending.pop();
		}
		int depth = pending.size();
		if (depth > 0) {
			pending.peek()[0]--;
		}
		return depth;
	}

	/** @return false once rewriting the current segment would go past either cap. */
	public boolean spend() {
		used++;
		return used <= maxRewrites && pending.size() < maxDepth;
	}

	/** The current segment's rewrite was put back as {@code count} segments. */
	public void children(final int count) {
		pending.push(new int[] { count });
	}

	public String limitMessage() {
		return "aliases kept rewriting this line (limit " + maxDepth + " levels deep, "
				+ maxRewrites + " in total); an alias probably sends its own trigger again"
				+ " after a semicolon. Nothing from this line was sent";
	}
}
