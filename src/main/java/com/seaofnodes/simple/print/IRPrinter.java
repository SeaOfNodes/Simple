package com.seaofnodes.simple.print;

import com.seaofnodes.print.IRAdapter;
import com.seaofnodes.simple.node.*;
import java.util.ArrayList;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.codegen.CodeGen;

/** This chapter's read-only IR facts and the familiar debugger entry points. */
public final class IRPrinter extends IRAdapter<Node> {
    private static final com.seaofnodes.print.IRPrinter<Node> PRINT =
        new com.seaofnodes.print.IRPrinter<>(new IRPrinter());
    @Override public boolean global(Node n) { return n.isConst(); }
    @Override public String type(Node n) { return n._type==null ? "" : n._type.str(); }
    @Override public String inputMark(Node n, Node def) { return n instanceof MemMergeNode && def instanceof MemMergeNode ? "^" : " "; }
    @Override public Kind kind(Node n) {
        if( n instanceof StartCUNode ) return Kind.UNIT;
        if( n instanceof StopCUNode ) return Kind.UNIT;
        if( n instanceof StartNode ) return Kind.START;
        if( n instanceof StopNode ) return Kind.STOP;
        if( n instanceof FunNode ) return Kind.FUN;
        if( n instanceof LoopNode ) return Kind.LOOP;
        if( n instanceof RegionNode ) return Kind.REGION;
        if( n instanceof ParmNode ) return Kind.PARM;
        if( n instanceof PhiNode ) return Kind.PHI;
        if( n instanceof CallEndNode ) return Kind.CALL_END;
        if( n instanceof CallNode ) return Kind.CALL;
        if( n instanceof ReturnNode ) return Kind.RETURN;
        if( n instanceof ConstantNode ) return Kind.CONSTANT;
        if( n instanceof CProjNode ) return Kind.CPROJ;
        if( n instanceof ProjNode ) return n instanceof CFGNode ? Kind.CPROJ : Kind.PROJ;
        if( n instanceof CFGNode ) return Kind.CTRL;
        if( n instanceof MultiNode ) return Kind.MULTI;
        return Kind.DATA;
    }
    @Override public int index(Node n) {
        if( n instanceof ProjNode p ) return p._idx;
        if( n instanceof CProjNode p ) return p._idx;
        return n._nid;
    }
    @Override public String functionName(Node n) { return ((FunNode)n)._name==null ? "" : ((FunNode)n)._name; }
    @Override public String signature(Node n) {
        FunNode fun=(FunNode)n;
        return (fun._name==null ? "" : fun._name)+" "+fun.sig().str();
    }

    public static String prettyPrint(Node n, int depth) { return PRINT.prettyPrint(n,depth); }
    public static SB printLine(Node n, SB sb) { return sb.p(PRINT.line(n)); }

    public static String prettyPrint(CodeGen code) {
        var units=new ArrayList<com.seaofnodes.print.IRPrinter.Unit<Node>>();
        for( var cu : code._compunits.values() ) {
            var funs=new ArrayList<Node>();
            for( FunNode fun : code._linker )
                if( fun!=null && fun._compunit==cu ) funs.add(fun);
            units.add(new com.seaofnodes.print.IRPrinter.Unit<>(cu._cname,cu._start,cu._stop,funs));
        }
        return PRINT.program(code._start,code._stop,units);
    }
}
