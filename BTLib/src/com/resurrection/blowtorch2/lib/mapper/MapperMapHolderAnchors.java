package com.resurrection.blowtorch2.lib.mapper;

/**
 * The two flags that pick map-holder rules: minimal uses the top of the
 * overlay, otherwise the holder sits below the title; tools showing means
 * the holder sits above the bottom bar.
 */
public final class MapperMapHolderAnchors {

	public final boolean minimal;
	public final boolean showTools;

	private MapperMapHolderAnchors(boolean minimal, boolean showTools) {
		this.minimal = minimal;
		this.showTools = showTools;
	}

	public static MapperMapHolderAnchors of(boolean minimal, boolean showTools) {
		return new MapperMapHolderAnchors(minimal, showTools);
	}

	public boolean sameAs(MapperMapHolderAnchors other) {
		return other != null && minimal == other.minimal && showTools == other.showTools;
	}
}
