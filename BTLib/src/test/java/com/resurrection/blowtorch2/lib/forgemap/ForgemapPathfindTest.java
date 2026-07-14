package com.resurrection.blowtorch2.lib.forgemap;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ForgemapPathfindTest {

    @Test
    public void aStarFindsSimplePath() {
        Map<String, Map<String, String>> graph = new HashMap<>();
        putEdge(graph, "a", "n", "b");
        putEdge(graph, "b", "n", "c");
        putEdge(graph, "a", "e", "d");
        putEdge(graph, "d", "n", "c");

        String[] path = ForgemapPathfinder.findPath(graph, "a", "c");
        assertNotNull(path);
        assertEquals("a", path[0]);
        assertEquals("c", path[path.length - 1]);
        assertTrue(path.length <= 3);
    }

    private static void putEdge(Map<String, Map<String, String>> graph, String from, String dir, String to) {
        if (!graph.containsKey(from)) graph.put(from, new HashMap<String, String>());
        graph.get(from).put(dir, to);
        if (!graph.containsKey(to)) graph.put(to, new HashMap<String, String>());
        String rev = reverse(dir);
        if (rev != null) graph.get(to).put(rev, from);
    }

    private static String reverse(String dir) {
        if ("n".equals(dir)) return "s";
        if ("s".equals(dir)) return "n";
        if ("e".equals(dir)) return "w";
        if ("w".equals(dir)) return "e";
        return null;
    }

    private static void assertTrue(boolean v) {
        org.junit.Assert.assertTrue(v);
    }
}
