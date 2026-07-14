package com.resurrection.blowtorch2.lib.forgemap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ForgemapPathfinder {

    private ForgemapPathfinder() {}

    public static String[] findPath(Map<String, Map<String, String>> graph, String from, String to) {
        if (from == null || to == null || from.equals(to)) return new String[] { from };
        List<String> open = new ArrayList<>();
        open.add(from);
        Map<String, String> cameFrom = new HashMap<>();
        Map<String, Integer> g = new HashMap<>();
        g.put(from, 0);

        while (!open.isEmpty()) {
            String current = lowest(open, g);
            if (current.equals(to)) {
                return reconstruct(cameFrom, to);
            }
            open.remove(current);
            Map<String, String> neighbors = graph.get(current);
            if (neighbors == null) continue;
            for (Map.Entry<String, String> e : neighbors.entrySet()) {
                String nid = e.getValue();
                int tg = g.get(current) + 1;
                if (!g.containsKey(nid) || tg < g.get(nid)) {
                    cameFrom.put(nid, current);
                    g.put(nid, tg);
                    if (!open.contains(nid)) open.add(nid);
                }
            }
        }
        return null;
    }

    private static String lowest(List<String> open, Map<String, Integer> g) {
        String best = null;
        int bestG = Integer.MAX_VALUE;
        for (String id : open) {
            int gv = g.containsKey(id) ? g.get(id) : Integer.MAX_VALUE;
            if (gv < bestG) {
                bestG = gv;
                best = id;
            }
        }
        return best;
    }

    private static String[] reconstruct(Map<String, String> cameFrom, String to) {
        List<String> path = new ArrayList<>();
        String node = to;
        while (node != null) {
            path.add(0, node);
            node = cameFrom.get(node);
        }
        return path.toArray(new String[0]);
    }
}
