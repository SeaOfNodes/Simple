package com.seaofnodes.simple.print;

import com.seaofnodes.graph.GraphAdapter;
import com.seaofnodes.graph.GraphSnapshot;
import com.seaofnodes.graph.GraphSnapshot.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.Node;
import java.util.ArrayList;

/** Chapter 4's view of the IR; no knowledge of browser, transport or layout. */
public class SimpleGraphAdapter extends GraphAdapter<Node> {
    @Override protected boolean dead(Node n) { return n.isDead(); }
    @Override protected int id(Node n) { return n._nid; }
    @Override protected int nIns(Node n) { return n.nIns(); }
    @Override protected Node in(Node n, int idx) { return n.in(idx); }
    @Override protected int nOuts(Node n) { return n.nOuts(); }
    @Override protected Node out(Node n, int idx) { return n.out(idx); }

    private ArrayList<Edge> edges(Node n) {
        String[] names = n instanceof ScopeNode scope ? scope.reverseNames() : null;
        var edges = new ArrayList<Edge>();
        for( int i = 0; i < n.nIns(); i++ ) {
            Role role = n instanceof ScopeNode || n instanceof ConstantNode ? Role.ASSOC
                : n instanceof ProjNode ? (n.isCFG() ? Role.CTRL : Role.DATA)
                : i == 0 ? Role.CTRL : Role.DATA;
            edges.add(new Edge(i, ref(n.in(i)), role, names == null ? null : names[i]));
        }
        return edges;
    }

    @Override protected GraphSnapshot.Node desc(Node n) {
        Projection proj = n instanceof ProjNode p
            ? new Projection(n.nIns() == 0 ? 0 : ref(n.in(0)), p._idx) : null;
        Kind kind = n instanceof ScopeNode ? Kind.SCOPE : n.isCFG() ? Kind.CTRL : Kind.DATA;
        String label = n.label();
        return new GraphSnapshot.Node(n._nid, label == null ? n.getClass().getSimpleName() : label,
                                      n._type == null ? null : n._type.toString(), kind, edges(n), proj);
    }
}
