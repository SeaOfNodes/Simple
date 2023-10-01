package com.seaofnodes.simple.print;

import com.seaofnodes.graph.GraphAdapter;
import com.seaofnodes.graph.GraphSnapshot.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.Node;

/** Chapter 3's view of the IR; browser, transport and layout are shared. */
public class SimpleGraphAdapter extends GraphAdapter<Node> {

    @Override protected String[] edgeNames(Node n) {
        return n instanceof ScopeNode scope && n.nIns()!=0 ? scope.reverseNames() : null;
    }

    @Override protected Kind kind(Node n) {
        if( n instanceof ScopeNode ) return Kind.SCOPE;
        if( n instanceof StartNode ) return Kind.CTRL;
        return n.isCFG() ? Kind.CTRL : Kind.DATA;
    }

    @Override protected Role role(Node n, int i) {
        if( n instanceof ScopeNode || n instanceof ConstantNode ) return Role.ASSOC;
        if( i == 0 ) return Role.CTRL;
        return Role.DATA;
    }
}
