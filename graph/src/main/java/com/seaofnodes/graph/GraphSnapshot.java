package com.seaofnodes.graph;

import java.util.ArrayList;

/** A detached graph. Treat its lists as read-only after capture. */
public record GraphSnapshot(int ver, String comp, long step,
                            int[] roots, ArrayList<Node> nodes) {
    public static final int VER = 1;

    public GraphSnapshot {
        roots = roots.clone();
        nodes = new ArrayList<>(nodes);
    }

    @Override public int[] roots() { return roots.clone(); }

    public enum Kind { DATA, CTRL, MEM, SCOPE, PHI, REGION, LOOP, FUN, UNIT }
    public enum Role { DATA, CTRL, MEM, ASSOC }

    /** IDs are stable within one compilation. Labels and types are plain text. */
    public record Node(int id, String label, String type, Kind kind,
                       ArrayList<Edge> edges, Projection proj) {
        public Node {
            edges = new ArrayList<>(edges);
        }
    }

    /**
     * The containing Node is the use; def is the referenced node ID.
     * idx is the input slot on that use: use.in(idx) == def.
     * Each slot has a record, including holes (def == 0).
     * label is an optional binding name, not markup.
     */
    public record Edge(int idx, int def, Role role, String label) { }

    /** Projections remain real nodes; par == 0 means no parent is attached. */
    public record Projection(int par, int idx) { }
}
