package com.seaofnodes.simple.print;

import com.seaofnodes.graph.GraphAdapter;
import com.seaofnodes.graph.GraphSnapshot.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.type.TypeMem;

/** Chapter 13's view of the IR; browser, transport and layout are shared. */
public class SimpleGraphAdapter extends GraphAdapter<Node> {

    @Override protected String[] edgeNames(Node n) {
        return n instanceof ScopeNode scope && n.nIns()!=0 ? scope.reverseNames() : null;
    }
    @Override protected int projectionIndex(Node n) { return n instanceof ProjNode p ? p._idx : n instanceof CProjNode p ? p._idx : -1; }

    @Override protected Kind kind(Node n) {
        if( n instanceof StopNode ) return Kind.STOP;
        if( n instanceof ScopeNode ) return Kind.SCOPE;
        if( n instanceof StartNode ) return Kind.CTRL;
        if( n instanceof LoopNode ) return Kind.LOOP;
        if( n instanceof RegionNode ) return Kind.REGION;
        if( n instanceof PhiNode ) return Kind.PHI;
        return n.isCFG() ? Kind.CTRL : isMem(n) ? Kind.MEM : Kind.DATA;
    }

    @Override protected Role role(Node n, int i) {
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
