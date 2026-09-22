package com.seaofnodes.simple.print;

import com.seaofnodes.graph.GraphAdapter;
import com.seaofnodes.graph.GraphSnapshot;
import com.seaofnodes.graph.GraphSnapshot.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.type.TypeMem;
import java.util.ArrayList;

/** Chapter 14's view of the IR; browser, transport and layout are shared. */
public class SimpleGraphAdapter extends GraphAdapter<Node> {
    @Override protected boolean dead(Node n) { return n.isDead(); }
    @Override protected int id(Node n) { return n._nid; }
    @Override protected int nIns(Node n) { return n.nIns(); }
    @Override protected Node in(Node n, int idx) { return n.in(idx); }
    @Override protected int nOuts(Node n) { return n.nOuts(); }
    @Override protected Node out(Node n, int idx) { return n.out(idx); }
    @Override protected int nDeps(Node n) { return n.nDeps(); }
    @Override protected Node dep(Node n, int idx) { return n.dep(idx); }

    private ArrayList<Edge> edges(Node n) {
        var edges = new ArrayList<Edge>();
        String[] names = n instanceof ScopeNode scope && n.nIns() != 0 ? scope.reverseNames() : null;
        for( int i = 0; i < n.nIns(); i++ ) {
            String name = names == null || i >= names.length ? null : names[i];
            edges.add(new Edge(i, ref(n.in(i)), role(n, i), name));
        }
        return edges;
    }

    @Override protected GraphSnapshot.Node desc(Node n) {
        Projection proj = n instanceof ProjNode p ? new Projection(ref(n.in(0)), p._idx)
            : n instanceof CProjNode p ? new Projection(ref(n.in(0)), p._idx) : null;
        String label = n.label();
        return new GraphSnapshot.Node(n._nid, label == null ? n.getClass().getSimpleName() : label,
                                      n._type == null ? null : n._type.toString(), kind(n), edges(n), proj);
    }

    private Kind kind(Node n) {
        if( n instanceof ScopeNode ) return Kind.SCOPE;
        if( n instanceof StartNode ) return Kind.CTRL;
        if( n instanceof LoopNode ) return Kind.LOOP;
        if( n instanceof RegionNode ) return Kind.REGION;
        if( n instanceof PhiNode ) return Kind.PHI;
        return n.isCFG() ? Kind.CTRL : isMem(n) ? Kind.MEM : Kind.DATA;
    }

    private Role role(Node n, int i) {
        if( n instanceof ScopeNode || n instanceof ConstantNode || n instanceof XCtrlNode ) return Role.ASSOC;
        if( n instanceof PhiNode ) return i == 0 ? Role.ASSOC : isMem(n) ? Role.MEM : Role.DATA;
        if( n instanceof ProjNode || n instanceof CProjNode ) return n.isCFG() ? Role.CTRL : isMem(n) ? Role.MEM : Role.DATA;
        if( n instanceof RegionNode && i == 0 ) return Role.ASSOC;
        if( i == 0 || n instanceof RegionNode || n instanceof StopNode ) return Role.CTRL;
        if( i == 1 && (n instanceof MemOpNode || n instanceof ReturnNode) ) return Role.MEM;
        Node def = n.in(i);
        if( def != null && isMem(def) ) return Role.MEM;
        return Role.DATA;
    }

    private boolean isMem(Node n) { return n.isMem() || n._type instanceof TypeMem; }
}
