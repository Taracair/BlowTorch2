package com.resurrection.blowtorch2.lib.forgemap;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RoomFingerprintTest {

    @Test
    public void normalizeTitleStripsNoise() {
        assertEquals("market square", RoomFingerprint.normalizeTitle("=== Market Square ==="));
    }

    @Test
    public void fingerprintStableForSameRoom() {
        Map<String, String> exits = new HashMap<>();
        exits.put("n", "?");
        exits.put("e", "?");
        String a = RoomFingerprint.fingerprintFromParts("Market", exits, "town", null);
        String b = RoomFingerprint.fingerprintFromParts("Market", exits, "town", null);
        assertEquals(a, b);
        assertTrue(a.startsWith("fp_"));
    }

    @Test
    public void vnumFingerprintPrefix() {
        assertEquals("vn_12345", RoomFingerprint.fingerprintFromGmcpVnum("12345"));
    }

    @Test
    public void scorePrefersVnum() {
        int score = RoomFingerprint.scoreMatch("A", "fp_aa", "9", "B", "fp_bb", "9");
        assertEquals(100, score);
    }

    @Test
    public void parseExitsFindsDirections() {
        Map<String, String> exits = RoomFingerprint.parseExitsLine("Exits: north east west");
        assertTrue(exits.containsKey("n"));
        assertTrue(exits.containsKey("e"));
        assertTrue(exits.containsKey("w"));
    }
}
