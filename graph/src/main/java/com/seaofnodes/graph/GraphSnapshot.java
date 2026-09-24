package com.seaofnodes.graph;

import java.util.ArrayList;

/** A detached graph. Lists are read-only; scope is the active parser scope ID, or zero. */
public record GraphSnapshot(int ver, String comp, long step,
                            int[] roots, int scope, ArrayList<Node> nodes, ArrayList<Group> groups) {
    public static final int VER = 1;

    public GraphSnapshot {
        roots = roots.clone();
        nodes = new ArrayList<>(nodes);
        groups = new ArrayList<>(groups);
    }

    @Override public int[] roots() { return roots.clone(); }

    public enum Kind { DATA, CTRL, MEM, SCOPE, PHI, REGION, LOOP, FUN, UNIT, START, STOP }
    public enum Role { DATA, CTRL, MEM, ASSOC }

    /** Header ID, enclosing group (zero outside), and directly owned node IDs. */
    public record Group(int id, int par, int[] nodes) { }

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
     * jump is a known function entry for a CallEnd link, or zero; def stays the
     * actual Return. The browser can replace the drawn link with a shortcut.
     */
    public record Edge(int idx, int def, Role role, String label, int jump) {
        public Edge(int idx, int def, Role role, String label) { this(idx,def,role,label,0); }
    }

    /** Projections remain real nodes; par == 0 means no parent is attached. */
    public record Projection(int par, int idx) { }
}
