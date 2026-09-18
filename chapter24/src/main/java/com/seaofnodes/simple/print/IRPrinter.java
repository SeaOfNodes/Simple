package com.seaofnodes.simple.print;

import com.seaofnodes.simple.node.*;
import java.util.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.util.SB;

public class IRPrinter {

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

    public static String prettyPrint(Node node, int depth) {
        StringBuilder sb = new StringBuilder();
        for( Node n : DebugSchedule.schedule(node,depth) ) {
            if( DebugSchedule.gap(n) ) sb.append('\n');
            if( n instanceof FunNode fun )
                sb.append("--- ").append(label(fun)).append(' ').append(signature(fun)).append(" ----------------------\n");
            _printLine(n,sb);
        }
        return sb.toString();
    }

    public static String _prettyPrint(CodeGen code) {
        return prettyPrint(code);
    }

    // Print complete functions separately, so linked callees/callers do not
    // appear in the middle of one another's bodies.
    public static String prettyPrint(CodeGen code) {
        if( code._start==null ) return prettyPrint(code._stop,9999);
        ArrayList<Node> nodes = new ArrayList<>();
        IdentityHashMap<Node,Boolean> seen = new IdentityHashMap<>();
        nodes.add(code._start);
        seen.put(code._start,Boolean.TRUE);
        if( code._stop!=null && seen.put(code._stop,Boolean.TRUE)==null ) nodes.add(code._stop);
        ArrayList<FunNode> funs = new ArrayList<>();
        for( int i=0; i<nodes.size(); i++ ) {
            Node n = nodes.get(i);
            if( n instanceof FunNode fun ) funs.add(fun);
            if( n._inputs!=null ) for( Node def : n._inputs )
                if( def!=null && seen.put(def,Boolean.TRUE)==null ) nodes.add(def);
            if( n._outputs!=null ) for( Node use : n._outputs )
                if( use!=null && seen.put(use,Boolean.TRUE)==null ) nodes.add(use);
        }
        StringBuilder sb = new StringBuilder();
        sb.append('\n');
        _printLine(code._start,sb);
        for( Node proj : DebugSchedule.children(code._start) ) _printLine(proj,sb);
        ArrayList<Node> globals = new ArrayList<>();
        for( Node n : nodes )
            if( DebugSchedule.input0(n)==code._start &&
                n instanceof ConstantNode ) globals.add(n);
        globals.sort(Comparator.comparingInt(n -> n._nid));
        for( Node n : globals ) _printLine(n,sb);
        funs.sort(Comparator.comparingInt(n -> n._nid));
        for( FunNode fun : funs ) {
            sb.append("\n--- ").append(label(fun)).append(' ').append(signature(fun)).append(" ----------------------\n");
            for( Node n : DebugSchedule.function(fun) ) {
                if( DebugSchedule.gap(n) ) sb.append('\n');
                _printLine(n,sb);
            }
            sb.append("--- ").append(label(fun)).append(" ----------------------\n");
        }
        sb.append('\n');
        _printLine(code._stop,sb);
        return sb.toString();
    }
}
