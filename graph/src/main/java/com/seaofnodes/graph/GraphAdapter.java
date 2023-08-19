package com.seaofnodes.graph;

import com.seaofnodes.print.BaseNode;
import static com.seaofnodes.graph.GraphSnapshot.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Subclass once per chapter. Only the subclass knows that chapter's IR.
 * Capture on the compiler thread, while the graph is not being modified.
 * Hooks must only read the IR: no computing types, peepholes or scheduling.
 */
public abstract class GraphAdapter<N extends BaseNode<N>> {
    protected final int id(N n) { return n._nid; }
    protected final int nIns(N n) { return n.nIns(); }
    protected final N in(N n, int i) { return n.in(i); }
    protected final int nOuts(N n) { return n.nOuts(); }
    protected final N out(N n, int i) { return n.out(i); }
    protected final boolean dead(N n) { return n.isDead(); }
    protected final int nDeps(N n) { return n.nDeps(); }
    protected final N dep(N n, int i) { return n.dep(i); }

    // Chapters supply only semantic distinctions that their IR has introduced.
    protected Kind kind(N n) { return n.isCFG() ? Kind.CTRL : Kind.DATA; }
    protected Role role(N n, int i) { return i==0 ? Role.CTRL : Role.DATA; }
    protected String[] edgeNames(N n) { return null; }
    protected String edgeName(N n, int i) { return null; }
    protected int edgeJump(N n, int i) { return 0; }
    protected int projectionIndex(N n) { return -1; }
    protected boolean folding(N n) { return false; }
    // Omit cached helpers with no graph uses; still walk through them.
    protected boolean show(N n) { return true; }

    protected final GraphSnapshot.Node desc(N n) {
        var edges=new ArrayList<Edge>();
        String[] names=edgeNames(n);
        for( int i=0; i<n.nIns(); i++ ) {
            String name=names!=null && i<names.length ? names[i] : edgeName(n,i);
            edges.add(new Edge(i,ref(n.in(i)),role(n,i),name,edgeJump(n,i)));
        }
        int idx=projectionIndex(n);
        Projection proj=idx<0 ? null : new Projection(n.nIns()==0 ? 0 : ref(n.in(0)),idx);
        String label=n.label();
        return new GraphSnapshot.Node(n._nid,label==null ? n.getClass().getSimpleName() : label,
                                      n.typeName(),kind(n),edges,proj,folding(n));
    }

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
            if( enq(root, seen, todo) && show(root) ) rids[len++] = id(root);
        var nodes = new ArrayList<GraphSnapshot.Node>();
        while( !todo.isEmpty() ) {
            N node = todo.removeFirst();
            if( show(node) ) nodes.add(desc(node));
            for( int i = 0; i < nIns(node); i++ ) enq(in(node, i), seen, todo);
            // Uses are a traversal shortcut, never the source of edge semantics.
            for( int i = 0; i < nOuts(node); i++ ) enq(out(node, i), seen, todo);
        }
        // Determinism without reordering the compiler's use lists.
        nodes.sort(Comparator.comparingInt(GraphSnapshot.Node::id));
        return new GraphSnapshot(GraphSnapshot.VER, comp, step,
                                 Arrays.copyOf(rids, len), scope, nodes, GraphGroups.build(nodes));
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
