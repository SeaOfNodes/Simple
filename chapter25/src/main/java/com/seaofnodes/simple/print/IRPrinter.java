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
        // Loop-tree for nodeOrder
        CFGNode.LoopTree old = code._start._ltree;
        if( old==null )
            code._start.buildLoopTree(code._start,code._stop);
        Ary<Node> nodes = Serialize.nodeOrder(code);
        if( old == null )
            code._start._ltree = null;
        SB sb = new SB();
        Node prior=null;
        for( Node n : nodes ) {
            if( n instanceof FunNode fun ) {
                sb.nl().p("--- ");
                fun.sig().print(sb.p(fun._name==null ? "" : fun._name).p(" "));
                sb.p("----------------------\n");
            }
            if( n instanceof MultiNode || n instanceof RegionNode || n instanceof CallNode ||
                (isMultiChild(prior) && !isMultiChild(n)) )
                sb.nl();
            printLine(n,sb);
            if( n instanceof ReturnNode ret )
                sb.p("--- ").p(ret._fun._name==null ? "" : ret._fun._name).p(" ----------------------\n");
            prior = n;
        }
        return sb.toString();
    }

    private static boolean isMultiHead(Node n) {
        return n instanceof RegionNode || n instanceof MultiNode;
    }

    private static boolean isMultiChild(Node n) {
        return n!=null && n.nIns() > 0 && n.in(0) != null && isMultiHead(n.in(0));
    }


    // ----------------------------------------
    // Another bulk pretty-printer.  Makes more effort at basic-block grouping.
    public static String prettyPrint(Node node, int depth) {
        // Convert just that set to a post-order
        Ary<Node> post = new Ary<>(Node.class);
        Ary<Node> rets = new Ary<>(Node.class);
        var visit = new IdentityHashMap<Node,Integer>();
        assert depth < 5000;
        postOrd( node, 0, depth, visit, post, rets);
        // Function calls all print independent
        while( rets.size()>0 )
            postOrd( rets.pop(), 0, depth, visit, post, rets );


        // Reverse the post-order walk
        SB sb = new SB();
        for( int i=0; i<post._len; i++ ) {
            Node n = post.at(i);
            // Split ahead of a Region/MultiNode
            if( n instanceof RegionNode || n instanceof MultiNode ||
                (i>0 && isMultiChild(post.at(i-1)) && !isMultiChild(n)) )
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


    // keep the largest PO depth?; probably want *shortest*
    private static void postOrd( Node n, int d, int cutoff, IdentityHashMap<Node,Integer> visit, Ary<Node> post, Ary<Node> rets ) {
        if( n==null ) return;
        if( d >= cutoff ) return; // Too deep
        Integer depth = visit.get(n);
        if( depth!=null ) return; // Been there, done that

        // Not a multi-child (Phi of a Region or Proj of a MultiNode)
        if( !isMultiHead(n) && !isMultiChild(n) ) {
            visit.put(n,d);             // Visit node N and depth D
            for( Node def : n._inputs ) // Children before visit
                postOrd(def, d+1, cutoff, visit, post, rets);
            post.add(n);        // Post-order add
            return;
        }

        // Multi-child; print the parent and all children together
        Node multi = isMultiHead(n) ? n : n.in(0);
        // Multi + children are visited all at once
        visit.put(multi,d+1);
        for( Node out : multi.outs() )
            visit.put(out,d);

        // Order to visit all the children
        Node[] outs = multi.outs().asAry();
        Arrays.sort(outs, Comparator.comparingInt( IRPrinter::sortOrder ) );

        // Do not walk out of Functions, this makes functions stand-alone
        // but otherwise unordered
        if( !(multi instanceof FunNode fun && fun._folding) ) {
            // Visit all children inputs all at once
            for( Node out : outs ) {
                // A FunNode is both a multi-head AND a multi-child from Start
                if( !isMultiHead(out) && out != null ) {
                    for( Node def : out._inputs ) {
                        postOrd(def, d+1, cutoff, visit, post, rets);
                    }
                }
            }
            // Visit multi inputs
            for( Node def : multi._inputs ) {
                if( multi instanceof CallEndNode && def instanceof ReturnNode ret && !ret._fun._folding ) {
                    rets.add(ret);  // But save the Return for a separate function print
                } else {
                    postOrd(def, d+2, cutoff, visit, post, rets);
                }
            }
        }

        // Post visit the multi and all children
        post.add(multi);
        for( Node out : outs )
            if( !isMultiHead(out) )
                post.add(out);
    }

    static int sortOrder( Node n ) {
        if( n instanceof ProjNode proj ) return proj._idx;
        if( n instanceof PhiNode phi ) return phi._nid;
        if( n == null ) return 0;
        return n._nid+1000000;
    }

}
