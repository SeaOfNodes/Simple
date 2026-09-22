package com.seaofnodes.simple.print;

import com.seaofnodes.graph.GraphAdapter;
import com.seaofnodes.graph.GraphSnapshot;
import com.seaofnodes.graph.GraphSnapshot.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.type.TypeMem;
import java.util.ArrayList;

/** Chapter 25's view of the IR, including partially constructed parse-time graphs. */
public class SimpleGraphAdapter extends GraphAdapter<Node> {
    @Override protected boolean dead(Node n) { return n.isDead(); }
    @Override protected int nDeps(Node n) { return n.nDeps(); }
    @Override protected Node dep(Node n, int idx) { return n.dep(idx); }
    @Override protected int id(Node n) { return n._nid; }
    @Override protected int nIns(Node n) { return n.nIns(); }
    @Override protected Node in(Node n, int idx) { return n.in(idx); }
    @Override protected int nOuts(Node n) { return n.nOuts(); }
    @Override protected Node out(Node n, int idx) { return n.out(idx); }

    private ArrayList<Edge> edges(Node n) {
        var edges = new ArrayList<Edge>();
        for( int i = 0; i < n.nIns(); i++ ) {
            String name = n instanceof ScopeNode scope && i < scope._vars.size()
                ? scope.var(i)._name : null;
            edges.add(new Edge(i, ref(n.in(i)), role(n, i), name));
        }
        return edges;
    }

    @Override protected GraphSnapshot.Node desc(Node n) {
        Projection proj = n instanceof Proj p
            ? new Projection(n.nIns() == 0 ? 0 : ref(n.in(0)), p.idx()) : null;
        String label = n.label();
        return new GraphSnapshot.Node(n._nid, label == null ? n.getClass().getSimpleName() : label,
                                      n._type == null ? null : n._type.toString(), kind(n), edges(n), proj);
    }

    private Kind kind(Node n) {
        if( n instanceof ScopeNode ) return Kind.SCOPE;
        if( n instanceof StartCUNode || n instanceof StopCUNode ) return Kind.UNIT;
        if( n instanceof FunNode ) return Kind.FUN;
        // Start inherits Loop for whole-program analysis, but is not a source loop.
        if( n instanceof StartNode ) return Kind.CTRL;
        if( n instanceof LoopNode ) return Kind.LOOP;
        if( n instanceof RegionNode ) return Kind.REGION;
        if( n instanceof PhiNode ) return Kind.PHI;
        if( n instanceof CFGNode ) return Kind.CTRL;
        return isMem(n) ? Kind.MEM : Kind.DATA;
    }

    private Role role(Node n, int i) {
        if( n instanceof ScopeNode || n instanceof ConstantNode || n instanceof FunPtrNode ||
            n instanceof CtrlNode || n instanceof XCtrlNode )
            return Role.ASSOC;
        if( n instanceof PhiNode )
            return i == 0 ? Role.ASSOC : isMem(n) ? Role.MEM : Role.DATA;
        if( n instanceof Proj )
            return n instanceof CFGNode ? Role.CTRL : isMem(n) ? Role.MEM : Role.DATA;
        if( n instanceof RegionNode && i == 0 ) return Role.ASSOC;
        // Preserve known slot roles even when an input is not attached/typed yet.
        if( i == 0 || n instanceof RegionNode || n instanceof StopNode ) return Role.CTRL;
        if( n instanceof MemMergeNode ||
            i == 1 && (n instanceof MemOpNode || n instanceof ReturnNode || n instanceof CallNode) ||
            n instanceof EscapeNode && i >= 2 ) return Role.MEM;
        Node def = n.in(i);
        if( def instanceof CFGNode ) return Role.CTRL;
        if( def != null && isMem(def) ) return Role.MEM;
        return Role.DATA;
    }

    private boolean isMem(Node n) {
        // These Phi subclasses are memory even before their first type computation.
        return n instanceof MemPhiNode || n instanceof BulkMemPhiNode || n.isMem() || n._type instanceof TypeMem;
    }
}
