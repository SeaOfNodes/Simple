package com.seaofnodes.simple.print;

import com.seaofnodes.graph.GraphAdapter;
import com.seaofnodes.graph.GraphSnapshot.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.Node;

/** Chapter 6's view of the IR; browser, transport and layout are shared. */
public class SimpleGraphAdapter extends GraphAdapter<Node> {

    @Override protected String[] edgeNames(Node n) {
        return n instanceof ScopeNode scope && n.nIns()!=0 ? scope.reverseNames() : null;
    }
    @Override protected int projectionIndex(Node n) { return n instanceof ProjNode p ? p._idx : -1; }

    @Override protected Kind kind(Node n) {
        if( n instanceof StopNode ) return Kind.STOP;
        if( n instanceof ScopeNode ) return Kind.SCOPE;
        if( n instanceof StartNode ) return Kind.CTRL;
        if( n instanceof RegionNode ) return Kind.REGION;
        if( n instanceof PhiNode ) return Kind.PHI;
        return n.isCFG() ? Kind.CTRL : Kind.DATA;
    }

    @Override protected Role role(Node n, int i) {
        if( n instanceof ScopeNode || n instanceof ConstantNode ) return Role.ASSOC;
        if( n instanceof PhiNode ) return i == 0 ? Role.ASSOC : Role.DATA;
        if( n instanceof ProjNode ) return n.isCFG() ? Role.CTRL : Role.DATA;
        if( n instanceof RegionNode && i == 0 ) return Role.ASSOC;
        if( i == 0 || n instanceof RegionNode || n instanceof StopNode ) return Role.CTRL;
        return Role.DATA;
    }
}
