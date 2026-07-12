package com.offsetnull.bt.forgemap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Room fingerprinting for ForgeMap — mirrors Lua forgemap/resolver.lua logic.
 */
public final class RoomFingerprint {

    private RoomFingerprint() {}

    public static String normalizeTitle(String s) {
        if (s == null) return "";
        String t = stripAnsi(s).toLowerCase(Locale.US).trim();
        t = t.replaceAll("[\\p{Punct}\\p{Cntrl}]", " ");
        t = t.replaceAll("\\s+", " ");
        return t.trim();
    }

    public static String stripAnsi(String s) {
        if (s == null) return "";
        return s.replaceAll("\u001B\\[[0-9;]*m", "");
    }

    public static String hashString(String s) {
        long h = 5381;
        for (int i = 0; i < s.length(); i++) {
            h = ((h * 33) + s.charAt(i)) % 2147483647L;
        }
        return String.format(Locale.US, "%08x", h);
    }

    public static String fingerprintFromParts(String title, Map<String, String> exits, String area, String desc) {
        StringBuilder parts = new StringBuilder();
        parts.append(normalizeTitle(title));
        parts.append('|');
        parts.append(area == null ? "" : area.toLowerCase(Locale.US).trim());
        if (exits != null && !exits.isEmpty()) {
            List<String> keys = new ArrayList<>(exits.keySet());
            Collections.sort(keys);
            for (String k : keys) {
                String v = exits.get(k);
                parts.append('|').append(k).append(':');
                if (v != null && !v.isEmpty()) parts.append(v);
                else parts.append('?');
            }
        }
        if (desc != null && !desc.isEmpty()) {
            String d = normalizeTitle(desc);
            if (d.length() > 96) d = d.substring(0, 96);
            parts.append("|d:").append(d);
        }
        return "fp_" + hashString(parts.toString());
    }

    public static String fingerprintFromGmcpVnum(String vnum) {
        return "vn_" + vnum;
    }

    public static int scoreMatch(String tileName, String tileFp, String tileVnum,
                                 String obsName, String obsFp, String obsVnum) {
        if (obsVnum != null && tileVnum != null && obsVnum.equals(tileVnum)) return 100;
        if (obsFp != null && obsFp.equals(tileFp)) return 95;
        int score = 0;
        String nt = normalizeTitle(tileName);
        String nn = normalizeTitle(obsName);
        if (!nt.isEmpty() && !nn.isEmpty()) {
            if (nt.equals(nn)) score += 60;
            else if (nt.contains(nn) || nn.contains(nt)) score += 40;
        }
        return score;
    }

    public static Map<String, String> parseExitsLine(String line) {
        Map<String, String> exits = new TreeMap<>();
        if (line == null) return exits;
        String clean = stripAnsi(line).toLowerCase(Locale.US);
        int idx = clean.indexOf("exit");
        if (idx >= 0) clean = clean.substring(idx);
        String[] tokens = clean.split("[^a-z\\-]+");
        for (String token : tokens) {
            if (token.isEmpty()) continue;
            String dir = token;
            if (dir.equals("north")) dir = "n";
            else if (dir.equals("south")) dir = "s";
            else if (dir.equals("east")) dir = "e";
            else if (dir.equals("west")) dir = "w";
            else if (dir.equals("up")) dir = "u";
            else if (dir.equals("down")) dir = "d";
            if (dir.length() <= 2) exits.put(dir, "?");
        }
        return exits;
    }
}
