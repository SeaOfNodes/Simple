package com.seaofnodes.graph;

/** IDs refer to nodes in this compilation; near may include nodes since deleted. */
public record GraphEvent(Kind kind, long peep, long up, String phase,
                         int node, int repl, int[] near, int[] temps, String msg) {
    public enum Kind { BEFORE, RETURN, APPLY, PHASE, ERROR }

    public GraphEvent(Kind kind, long peep, long up, String phase, int node, int repl, int[] near, int[] temps) {
        this(kind, peep, up, phase, node, repl, near, temps, null);
    }

    public GraphEvent(Kind kind, long peep, long up, String phase, int node, int repl, int[] near) {
        this(kind, peep, up, phase, node, repl, near, new int[0]);
    }

    public GraphEvent { near = near.clone(); temps = temps.clone(); }
    @Override public int[] near() { return near.clone(); }
    // Created inside an enclosing peep; not yet returned to the parser.
    @Override public int[] temps() { return temps.clone(); }
}
