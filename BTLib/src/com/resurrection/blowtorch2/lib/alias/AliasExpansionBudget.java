package com.resurrection.blowtorch2.lib.alias;

/**
 * Caps the alias rewrites one typed line may cause across its semicolon
 * segments. {@link AliasRecursion} bounds one segment, but Connection feeds
 * every segment of a rewrite back through the aliases, so
 * {@code ^say (.+)$ -> Say one $1;say two $1} grew without end on the
 * connection thread (measured 26 Oct 2026: 18 min of CPU, nothing sent).
 */
public final class AliasExpansionBudget {

	/** {@code #100} of a one-alias command is 100 rewrites; ten times that. */
	public static final int DEFAULT_MAX_REWRITES = 1000;

	private final int max;
	private int used;

	public AliasExpansionBudget() {
		this(DEFAULT_MAX_REWRITES);
	}

	public AliasExpansionBudget(final int max) {
		this.max = max;
	}

	/** @return false once this rewrite would go past the cap. */
	public boolean spend() {
		used++;
		return used <= max;
	}

	public String limitMessage() {
		return "aliases rewrote this line more than " + max
				+ " times; an alias probably sends its own trigger again after a semicolon."
				+ " Nothing from this line was sent";
	}
}
