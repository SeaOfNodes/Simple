package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;

public class IRPrinter {

    // Inspect raw edges so a partly built/dead node is still useful to print.
    private static String label(Node n) {
        if( n._inputs==null ) return n.getClass().getSimpleName();
        try { return n.label(); }
        catch( RuntimeException | AssertionError ex ) { return n.getClass().getSimpleName(); }
    }

    private static String type(Node n) {
        if( n._type==null ) return "";
        try { return n._type.toString(); }
        catch( RuntimeException | AssertionError ex ) { return "<incomplete type>"; }
    }

    public static void _printLine(Node n, StringBuilder sb) {
        if( n==null ) return;
        sb.append("%4d %-7.7s ".formatted(n._nid,label(n)));
        if( n._inputs==null ) { sb.append("DEAD\n"); return; }
        for( Node def : n._inputs )
            sb.append(def==null ? "____ " : "%4d ".formatted(def._nid));
        for( int i=n._inputs.size(); i<4; i++ ) sb.append("     ");
        sb.append(" [[  ");
        if( n._outputs!=null ) for( Node use : n._outputs )
            sb.append(use==null ? "____ " : "%4d ".formatted(use._nid));
        sb.append(" ]]  ").append(type(n)).append('\n');
    }

    public static String prettyPrint(Node node, int depth) {
        StringBuilder sb = new StringBuilder();
        for( Node n : DebugSchedule.schedule(node,depth) ) {
            if( DebugSchedule.gap(n) ) sb.append('\n');
            _printLine(n,sb);
        }
        return sb.toString();
    }
}
