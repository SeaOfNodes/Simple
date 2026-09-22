package com.seaofnodes.graph;

/** IDs refer to nodes in this compilation; near may include nodes since deleted. */
public record GraphEvent(Kind kind, long peep, long up, String phase,
                         int node, int repl, int[] near) {
    public enum Kind { BEFORE, RETURN, APPLY, PHASE }

    public GraphEvent { near = near.clone(); }
    @Override public int[] near() { return near.clone(); }
}
