package com.seaofnodes.simple.print;

import com.seaofnodes.graph.GraphAdapter;
import com.seaofnodes.graph.GraphSnapshot;
import com.seaofnodes.graph.GraphSnapshot.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.Node;
import java.util.ArrayList;

/** Chapter 2's view of the IR; browser, transport and layout are shared. */
public class SimpleGraphAdapter extends GraphAdapter<Node> {
    @Override protected boolean dead(Node n) { return n.isDead(); }
    @Override protected int id(Node n) { return n._nid; }
    @Override protected int nIns(Node n) { return n.nIns(); }
    @Override protected Node in(Node n, int idx) { return n.in(idx); }
    @Override protected int nOuts(Node n) { return n.nOuts(); }
    @Override protected Node out(Node n, int idx) { return n.out(idx); }

    private ArrayList<Edge> edges(Node n) {
        var edges = new ArrayList<Edge>();
        for( int i = 0; i < n.nIns(); i++ ) {
            String name = null;
            edges.add(new Edge(i, ref(n.in(i)), role(n, i), name));
        }
        return edges;
    }

    @Override protected GraphSnapshot.Node desc(Node n) {
        Projection proj = null;
        String label = n.label();
        return new GraphSnapshot.Node(n._nid, label == null ? n.getClass().getSimpleName() : label,
                                      n._type == null ? null : n._type.toString(), kind(n), edges(n), proj);
    }

    private Kind kind(Node n) {
        if( n instanceof StartNode ) return Kind.CTRL;
        return n.isCFG() ? Kind.CTRL : Kind.DATA;
    }

    private Role role(Node n, int i) {
        if( n instanceof ConstantNode ) return Role.ASSOC;
        if( i == 0 ) return Role.CTRL;
        return Role.DATA;
    }
}
