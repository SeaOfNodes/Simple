package com.seaofnodes.simple.print;

import com.seaofnodes.graph.GraphAdapter;
import com.seaofnodes.graph.GraphSnapshot.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.Node;

/** Chapter 1's view of the IR; browser, transport and layout are shared. */
public class SimpleGraphAdapter extends GraphAdapter<Node> {

    @Override protected Kind kind(Node n) {
        if( n instanceof StartNode ) return Kind.CTRL;
        return n.isCFG() ? Kind.CTRL : Kind.DATA;
    }

    @Override protected Role role(Node n, int i) {
        if( n instanceof ConstantNode ) return Role.ASSOC;
        if( i == 0 ) return Role.CTRL;
        return Role.DATA;
    }
}
