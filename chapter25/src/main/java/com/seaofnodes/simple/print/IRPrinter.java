package com.seaofnodes.simple.print;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.CompUnit;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.util.SB;

import java.util.*;

public abstract class IRPrinter {

    // Print a node on 1 line, columnar aligned, as:
    // NNID NNAME DDEF DDEF  [[  UUSE UUSE  ]]  TYPE
    // 1234 sssss 1234 1234 1234 1234 1234 1234 tttttt
    // Inspect raw edges so a partly built/dead node is still useful to print.
    private static String label(Node n) {
        if( n._inputs==null ) return n.getClass().getSimpleName();
        try { return n.label(); }
        catch( RuntimeException | AssertionError ex ) { return n.getClass().getSimpleName(); }
    }

    private static String type(Node n) {
        if( n._type==null ) return "";
        try { return n._type.str(); }
        catch( RuntimeException | AssertionError ex ) { return "<incomplete type>"; }
    }

    private static String signature(FunNode fun) {
        try { return fun.sig()==null ? "" : fun.sig().toString(); }
        catch( RuntimeException | AssertionError ex ) { return "<incomplete signature>"; }
    }

    public static void _printLine(Node n, StringBuilder sb) {
        if( n==null ) return;
        sb.append("%4d %-7.7s ".formatted(n._nid,label(n)));
        if( n._inputs==null ) { sb.append("DEAD\n"); return; }
        for( Node def : n._inputs )
            sb.append(def==null ? "____" : "%4d".formatted(def._nid))
                .append(n instanceof MemMergeNode && def instanceof MemMergeNode ? '^' : ' ');
        for( int i=n._inputs.size(); i<4; i++ ) sb.append("     ");
        sb.append(" [[  ");
        if( n._outputs!=null ) for( Node use : n._outputs )
            sb.append(use==null ? "____ " : "%4d ".formatted(use._nid));
        sb.append(" ]]  ").append(type(n)).append('\n');
    }

    public static SB printLine(Node n, SB sb) {
        StringBuilder line = new StringBuilder();
        _printLine(n,line);
        return sb.p(line.toString());
    }

    // Bulk whole program pretty print
    public static String prettyPrint( CodeGen code ) {
        SB sb = new SB();
        sb.nl();
        printLine(code._start,sb);
        for( Node proj : DebugSchedule.children(code._start) ) printLine(proj,sb);

        // Constants are shared program-wide.  Print only actual Start children
        // here; derived values belong to the functions which use them.
        ArrayList<Node> globals = new ArrayList<>();
        if( code._start!=null && code._start._outputs!=null ) for( Node n : code._start._outputs )
            if( n instanceof ConstantNode ) globals.add(n);
        globals.sort(Comparator.comparingInt(n -> n._nid));
        for( Node n : globals ) printLine(n,sb);

        ArrayList<CompUnit> cus = new ArrayList<>(code._compunits.values());
        cus.sort(Comparator.comparing(cu -> cu._cname==null ? "" : cu._cname));
        for( CompUnit cu : cus ) {
            if( cu._start==null || cu._start._inputs==null ) continue;
            sb.nl().p("=== ").p(cu._cname==null ? "" : cu._cname).p(" ===\n");
            sb.nl();
            printLine(cu._start,sb);
            ArrayList<Node> projs = DebugSchedule.children(cu._start);
            for( Node proj : projs ) printLine(proj,sb);

            ArrayList<FunNode> funs = new ArrayList<>();
            for( FunNode fun : code._linker )
                if( fun!=null && fun._inputs!=null && fun._compunit==cu )
                    funs.add(fun);
            funs.sort(Comparator.comparingInt(n -> n._nid));
            for( FunNode fun : funs ) printFunction(fun,sb);

            sb.nl();
            printLine(cu._stop,sb);
        }
        sb.nl();
        printLine(code._stop,sb);
        return sb.toString();
    }

    /** Print one function without traversing call-graph linkage. */
    private static void printFunction(FunNode fun, SB sb) {
        sb.nl().p("--- ").p(fun._name==null ? "" : fun._name).p(" ").p(signature(fun)).p(" ----------------------\n");
        for( Node n : DebugSchedule.function(fun) ) {
            if( DebugSchedule.gap(n) ) sb.nl();
            printLine(n,sb);
        }
        sb.p("--- ").p(fun._name==null ? "" : fun._name).p(" ----------------------\n");
    }

    // ----------------------------------------
    // Another bulk pretty-printer.  Makes more effort at basic-block grouping.
    public static String prettyPrint(Node node, int depth) {
        SB sb = new SB();
        for( Node n : DebugSchedule.schedule(node,depth) ) {
            if( DebugSchedule.gap(n) ) sb.nl();
            printLine(n,sb);
        }
        return sb.toString();
    }

}
