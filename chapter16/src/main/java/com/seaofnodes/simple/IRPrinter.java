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
        try { return n._type.str(); }
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

    private static StringBuilder nodeId(StringBuilder sb, Node n) {
        sb.append("%%%d".formatted(n._nid));
        if (n instanceof ProjNode proj) {
            sb.append(".").append(proj._idx);
        }
        return sb;
    }

    // Print a node on 1 line, format is inspired by LLVM
    // %id: TYPE = NODE(inputs ....)
    // Nodes as referred to as %id
    public static void _printLineLlvmFormat( Node n, StringBuilder sb ) {
        if( n==null ) return;
        nodeId(sb, n).append(": ");
        if( n._inputs==null ) {
            sb.append("DEAD\n");
            return;
        }
        try { if( n._type!=null ) n._type.typeName(sb); }
        catch( RuntimeException | AssertionError ex ) { sb.append("<incomplete type>"); }
        sb.append(" = ").append( label(n) ).append( "(" );
        for( int i = 0; i < n._inputs.size(); i++ ) {
            Node def = n._inputs.get(i);
            if (i > 0)
                sb.append(", ");
            if (def == null) sb.append("_");
            else             nodeId(sb, def);
        }
        sb.append(")").append("\n");
    }

    public static void printLine( Node n, StringBuilder sb, boolean llvmFormat ) {
        if (llvmFormat) _printLineLlvmFormat( n, sb );
        else            _printLine          ( n, sb );
    }

    public static String prettyPrint(Node node, int depth) {
        return prettyPrint(node,depth,false);
    }

    public static String prettyPrint(Node node, int depth, boolean llvmFormat) {
        StringBuilder sb = new StringBuilder();
        for( Node n : DebugSchedule.schedule(node,depth) ) {
            if( DebugSchedule.gap(n) ) sb.append('\n');
            printLine(n,sb,llvmFormat);
        }
        return sb.toString();
    }

    public static String prettyPrintScheduled(Node node, int depth, boolean llvmFormat) {
        return prettyPrint(node,depth,llvmFormat);
    }
}
