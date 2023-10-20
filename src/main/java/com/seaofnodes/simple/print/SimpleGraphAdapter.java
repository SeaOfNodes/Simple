package com.seaofnodes.simple.print;

import com.seaofnodes.graph.GraphAdapter;
import com.seaofnodes.graph.GraphSnapshot.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.Node;

/** Chapter 4's view of the IR; no knowledge of browser, transport or layout. */
public class SimpleGraphAdapter extends GraphAdapter<Node> {

    @Override protected String[] edgeNames(Node n) {
        return n instanceof ScopeNode scope && n.nIns()!=0 ? scope.reverseNames() : null;
    }
    @Override protected int projectionIndex(Node n) { return n instanceof ProjNode p ? p._idx : -1; }

    @Override protected Kind kind(Node n) { return n instanceof ScopeNode ? Kind.SCOPE : n.isCFG() ? Kind.CTRL : Kind.DATA; }
    @Override protected Role role(Node n, int i) {
        return n instanceof ScopeNode || n instanceof ConstantNode ? Role.ASSOC
            : n instanceof ProjNode ? (n.isCFG() ? Role.CTRL : Role.DATA)
            : i==0 ? Role.CTRL : Role.DATA;
    }
}
