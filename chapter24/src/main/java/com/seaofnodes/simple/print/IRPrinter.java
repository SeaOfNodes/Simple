package com.seaofnodes.simple.print;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.Serialize;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;

import java.util.*;

public abstract class IRPrinter {

    // Print a node on 1 line, columnar aligned, as:
    // NNID NNAME DDEF DDEF  [[  UUSE UUSE  ]]  TYPE
    // 1234 sssss 1234 1234 1234 1234 1234 1234 tttttt
    public static SB printLine( Node n, SB sb ) {
        if( n==null ) return sb;
        sb.p("%4d %-7.7s ".formatted(n._nid,n.label()));
        if( n._inputs==null )
            return sb.p("DEAD\n");
        for( Node def : n._inputs )
            sb.p(def==null ? "____" : "%4d".formatted(def._nid))
                // Lazy Phi indicator
                .p(n instanceof MemMergeNode && def instanceof MemMergeNode ? "^" : " ");
        for( int i = n._inputs.size(); i<4; i++ )
            sb.p("     ");
        sb.p(" [[  ");
        for( Node use : n._outputs )
            sb.p(use==null ? "____ " : "%4d ".formatted(use._nid));
        int lim = 6 - Math.max(n._inputs.size(),4);
        for( int i = n._outputs.size(); i<lim; i++ )
            sb.p("     ");
        sb.p(" ]]  ");
        if( n._type!= null ) sb.p(n._type.str());
        return sb.p("\n");
    }


    // Bulk whole program pretty print
    public static String prettyPrint( CodeGen code ) {
        Ary<Node> nodes = Serialize.nodeOrder(code);
        SB sb = new SB();
        Node prior=null;
        for( Node n : nodes ) {
            if( n instanceof FunNode fun ) {
                sb.nl().p("--- ");
                fun.sig().print(sb.p(fun._name==null ? "" : fun._name).p(" "));
                sb.p("----------------------\n");
            }
            if( n instanceof MultiNode || n instanceof RegionNode || n instanceof CallNode ||
                (multiChild(prior) && !multiChild(n)) )
                sb.nl();
            printLine(n,sb);
            if( n instanceof ReturnNode ret )
                sb.p("--- ").p(ret._fun._name==null ? "" : ret._fun._name).p(" ----------------------\n");
            prior = n;
        }
        return sb.toString();
    }

    private static boolean multiChild(Node n) {
        return n instanceof Proj || n instanceof PhiNode;
    }


    // ----------------------------------------
    // Another bulk pretty-printer.  Makes more effort at basic-block grouping.
    public static String prettyPrint(Node node, int depth) {
        // Convert just that set to a post-order
        Ary<Node> post = new Ary<>(Node.class);
        var visit = new IdentityHashMap<Node,Integer>();
        postOrd( node, 0, depth, visit, post);

        // Reverse the post-order walk
        SB sb = new SB();
        for( int i=0; i<post._len; i++ ) {
            Node n = post.at(i);
            if( n instanceof RegionNode || n instanceof MultiNode ||
                (i>0 && multiChild(post.at(i-1)) && !multiChild(n)) )
                sb.nl();
            if( n instanceof FunNode fun )
                fun.sig().print(sb.p("--- ").p(fun._name==null ? "" : fun._name).p(" ")).p("----------------------\n");
            printLine( n, sb );         // Print head
            if( n instanceof ReturnNode ret ) {
                FunNode fun = ret.fun();
                sb.p("--- ").p(fun==null ? "" : fun._name).p("----------------------\n");
            }
        }
        return sb.toString();
    }


    private static void postOrd( Node n, int d, int cutoff, IdentityHashMap<Node,Integer> visit, Ary<Node> post ) {
        Integer depth = visit.get(n);
        if( depth!=null && depth >= d ) return; // Been there, done that
        if( d >= cutoff && !multiChild(n) )
            return;               // Too deep, except get all the multi-childs
        visit.put(n,d);
        for( Node def : n._inputs ) {
            if( def != null &&
                // Do not walk across linked function boundaries
                !(n instanceof CallEndNode && def instanceof ReturnNode) )
                postOrd(def, d+1, cutoff, visit, post);
        }
        post.add(n);
    }

}
