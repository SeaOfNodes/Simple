package com.seaofnodes.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Subclass once per chapter. Only the subclass knows that chapter's IR.
 * Capture on the compiler thread, while the graph is not being modified.
 * Hooks must only read the IR: no computing types, peepholes or scheduling.
 */
public abstract class GraphAdapter<N> {
    protected abstract int id(N node);
    protected abstract int nIns(N node);
    protected abstract N in(N node, int idx);
    protected abstract int nOuts(N node);
    protected abstract N out(N node, int idx);
    protected abstract GraphSnapshot.Node desc(N node);
    protected abstract boolean dead(N node);
    protected int nDeps(N node) { return 0; }
    protected N dep(N node, int idx) { throw new IndexOutOfBoundsException(idx); }

    protected final int ref(N node) { return node == null ? 0 : id(node); }

    /**
     * Follow both definitions and uses from the roots, including cycles.
     * Supply current/replacement nodes as extra roots while a peep is in flight;
     * they need not be attached to the program yet. Null roots/uses are ignored.
     * The caller supplies a new compilation key whenever node IDs reset.
     */
    @SafeVarargs
    public final GraphSnapshot snap(String comp, long step, N... roots) {
        var list = new ArrayList<N>();
        java.util.Collections.addAll(list, roots);
        return snap(comp, step, list);
    }

    public final GraphSnapshot snap(String comp, long step, ArrayList<N> roots) {
        return snap(comp, step, roots, 0);
    }

    /** scope names the active parser scope, which the caller includes in roots. */
    public final GraphSnapshot snap(String comp, long step, ArrayList<N> roots, int scope) {
        // Node IDs are dense; index directly without boxed keys or map entries.
        var seen = new ArrayList<N>();
        var todo = new ArrayDeque<N>();
        var rids = new int[roots.size()];
        int len = 0;
        for( N root : roots )
            if( enq(root, seen, todo) ) rids[len++] = id(root);
        var nodes = new ArrayList<GraphSnapshot.Node>();
        while( !todo.isEmpty() ) {
            N node = todo.removeFirst();
            nodes.add(desc(node));
            for( int i = 0; i < nIns(node); i++ ) enq(in(node, i), seen, todo);
            // Uses are a traversal shortcut, never the source of edge semantics.
            for( int i = 0; i < nOuts(node); i++ ) enq(out(node, i), seen, todo);
        }
        // Determinism without reordering the compiler's use lists.
        nodes.sort(Comparator.comparingInt(GraphSnapshot.Node::id));
        return new GraphSnapshot(GraphSnapshot.VER, comp, step,
                                 Arrays.copyOf(rids, len), scope, nodes);
    }

    private boolean enq(N node, ArrayList<N> seen, ArrayDeque<N> todo) {
        if( node == null ) return false;
        int nid = id(node);
        while( seen.size() <= nid ) seen.add(null);
        N old = seen.get(nid);
        if( old == null ) {
            seen.set(nid, node);
            todo.addLast(node);
            return true;
        }
        if( old != node )
            throw new IllegalArgumentException("Different nodes share ID " + nid +
                                               "; mixed compilations?");
        return false;
    }
}
