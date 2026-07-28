package com.resurrection.blowtorch2.lib.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;

/**
 * The exact Room.Info payloads an eden-test session puts on the wire.
 *
 * <p>Captured from the game window, which echoes the payload verbatim, so what
 * is written here is what the server actually sent — including the percent
 * encoded braces around the exits object. That is not valid JSON, and the
 * parser wraps the whole room in a catch that ignores it, so a malformed exits
 * object silently costs the entire room: no name, no coordinates, no exits.
 */
public class MapperEdenPayloadTest {

	/** Verbatim from the session, only the long desc trimmed. */
	private static final String GROVE =
			"{\"num\":1001022,\"name\":\"Forgotten Grove\",\"terrain\":\"unknown\","
			+ "\"coords\":{\"id\":0,\"x\":15,\"y\":2,\"z\":0},"
			+ "\"map\":\"https://eden-test.rpgframework.de:4079/world/world01/01/mmp.xml\","
			+ "\"exits\":%7B\"S\":1001019,\"N\":1001023%7D,"
			+ "\"desc\":\"This secluded grove is hidden deep within the forest.\"}";

	private static final String EDGE =
			"{\"num\":1001019,\"name\":\"Northeastern Forest Edge\",\"terrain\":\"unknown\","
			+ "\"coords\":{\"id\":0,\"x\":15,\"y\":3,\"z\":0},"
			+ "\"map\":\"https://eden-test.rpgframework.de:4079/world/world01/01/mmp.xml\","
			+ "\"exits\":%7B\"S\":1001017,\"W\":1001020,\"N\":1001022%7D,"
			+ "\"desc\":\"The forest thins out here.\"}";

	private MapperController mapper;

	@Before
	public void setUp() {
		mapper = new MapperController(new Object());
	}

	private MapTile tileNamed(String title) {
		for (MapTile t : mapper.getMap().getTiles()) {
			if (t != null && title.equals(t.getTitle())) {
				return t;
			}
		}
		return null;
	}

	@Test
	public void theRoomSurvivesPercentEncodedExits() {
		mapper.onGmcpRoomRaw("Room.Info", GROVE);
		assertNotNull("the room itself must not be lost because exits were malformed",
				tileNamed("Forgotten Grove"));
	}

	@Test
	public void exitsAreReadDespiteThePercentEncoding() {
		mapper.onGmcpRoomRaw("Room.Info", GROVE);
		MapTile grove = tileNamed("Forgotten Grove");
		assertNotNull(grove);
		assertEquals("both listed exits should be there", 2, grove.getExits().size());
	}

	@Test
	public void walkingBetweenTwoEdenRoomsLinksThem() {
		mapper.onGmcpRoomRaw("Room.Info", GROVE);
		mapper.onPlayerCommand("s");
		mapper.onGmcpRoomRaw("Room.Info", EDGE);

		assertNotNull(tileNamed("Forgotten Grove"));
		assertNotNull(tileNamed("Northeastern Forest Edge"));
	}
}
