package com.seaofnodes.simple;

import com.seaofnodes.print.IRAdapter;
import com.seaofnodes.simple.node.*;

/** This chapter's read-only IR facts and the familiar debugger entry points. */
public final class IRPrinter extends IRAdapter<Node> {
    private static final com.seaofnodes.print.IRPrinter<Node> PRINT =
        new com.seaofnodes.print.IRPrinter<>(new IRPrinter());
    @Override public String type(Node n) { return n._type==null ? "" : n._type.str(); }
    @Override public int inputColumns() { return 3; }
    @Override public String inputMark(Node n, Node def) { return n instanceof MemMergeNode && def instanceof MemMergeNode ? "^" : " "; }
    @Override public Kind kind(Node n) {
        if( n instanceof StartNode ) return Kind.START;
        if( n instanceof StopNode ) return Kind.STOP;
        if( n instanceof LoopNode ) return Kind.LOOP;
        if( n instanceof RegionNode ) return Kind.REGION;
        if( n instanceof PhiNode ) return Kind.PHI;
        if( n instanceof ReturnNode ) return Kind.RETURN;
        if( n instanceof ConstantNode ) return Kind.CONSTANT;
        if( n instanceof ProjNode ) return n.isCFG() ? Kind.CPROJ : Kind.PROJ;
        if( n.isCFG() ) return Kind.CTRL;
        if( n instanceof MultiNode ) return Kind.MULTI;
        return Kind.DATA;
    }
    @Override public int index(Node n) {
        if( n instanceof ProjNode p ) return p._idx;
        return n._nid;
    }

    public static String prettyPrint(Node n, int depth) { return PRINT.prettyPrint(n,depth); }
}
