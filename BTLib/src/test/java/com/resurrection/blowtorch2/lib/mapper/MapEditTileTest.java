package com.resurrection.blowtorch2.lib.mapper;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

/**
 * {@code .map note for temple} edits the tile whose game room id is
 * {@code temple}. The expected tiles below are the ones the command names.
 */
public class MapEditTileTest {

	@Test
	public void gameRoomIdSelectsThatTile() {
		MapTile temple = tile("uuid-temple", "temple");
		MapTile gate = tile("uuid-gate", "gate");
		MapTile current = tile("uuid-here", "here");
		assertSame(temple, MapEditTile.resolve(
				Arrays.asList(gate, temple), "temple", current));
		assertSame(temple, MapEditTile.resolve(
				Arrays.asList(gate, temple), " uuid-temple ", current));
	}

	@Test
	public void mapperIdWinsWhenItEqualsAnotherRoomsGameId() {
		MapTile named = tile("temple", "gate");
		MapTile game = tile("uuid-temple", "temple");
		assertSame(named, MapEditTile.resolve(
				Arrays.asList(game, named), "temple", null));
	}

	@Test
	public void blankIdIsTheCurrentTile() {
		MapTile current = tile("uuid-here", "here");
		assertSame(current, MapEditTile.resolve(
				Collections.singletonList(current), "  ", current));
		assertSame(current, MapEditTile.resolve(
				Collections.singletonList(current), null, current));
	}

	@Test
	public void unknownIdSelectsNothing() {
		MapTile temple = tile("uuid-temple", "temple");
		assertNull(MapEditTile.resolve(
				Collections.singletonList(temple), "shrine", temple));
	}

	@Test
	public void firstGameRoomIdWins() {
		MapTile first = tile("uuid-a", "temple");
		MapTile second = tile("uuid-b", "temple");
		assertSame(first, MapEditTile.resolve(
				Arrays.asList(first, second), " temple ", null));
	}

	private static MapTile tile(String id, String gameRoomId) {
		MapTile tile = new MapTile(id, "level", 0, 0);
		tile.setExternalId(gameRoomId);
		return tile;
	}
}
