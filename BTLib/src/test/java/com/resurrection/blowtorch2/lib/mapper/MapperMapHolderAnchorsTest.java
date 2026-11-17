package com.resurrection.blowtorch2.lib.mapper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MapperMapHolderAnchorsTest {

	@Test
	public void theFirstLayoutHasNothingToMatch() {
		assertFalse(MapperMapHolderAnchors.of(false, true).sameAs(null));
	}

	@Test
	public void repeatingTheSameChromeDoesNotChangeTheHolder() {
		assertTrue(MapperMapHolderAnchors.of(false, true)
				.sameAs(MapperMapHolderAnchors.of(false, true)));
	}

	@Test
	public void hidingToolsOrTheTitleChangesTheHolder() {
		MapperMapHolderAnchors shown = MapperMapHolderAnchors.of(false, true);
		assertFalse(shown.sameAs(MapperMapHolderAnchors.of(false, false)));
		assertFalse(shown.sameAs(MapperMapHolderAnchors.of(true, false)));
	}
}
