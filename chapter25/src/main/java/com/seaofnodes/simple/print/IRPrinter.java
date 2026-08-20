package com.seaofnodes.simple.print;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.CompUnit;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.simple.util.SB;

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
        SB sb = new SB();
        printLine(code._start,sb);

        // Constants are shared program-wide.  Print only actual Start children
        // here; derived values belong to the functions which use them.
        ArrayList<Node> globals = new ArrayList<>();
        for( Node n : code._start._outputs )
            if( n instanceof ConstantNode ) globals.add(n);
        globals.sort(Comparator.comparingInt(n -> n._nid));
        for( Node n : globals ) printLine(n,sb);

        ArrayList<CompUnit> cus = new ArrayList<>(code._compunits.values());
        cus.sort(Comparator.comparing(cu -> cu._cname==null ? "" : cu._cname));
        for( CompUnit cu : cus ) {
            if( cu._start==null || cu._start._inputs==null ) continue;
            sb.nl().p("=== ").p(cu._cname==null ? "" : cu._cname).p(" ===\n");
            printLine(cu._start,sb);
            ArrayList<Node> projs = projections(cu._start);
            for( Node proj : projs ) printLine(proj,sb);

            ArrayList<FunNode> funs = new ArrayList<>();
            for( FunNode fun : code._linker )
                if( fun!=null && fun._inputs!=null && fun._compunit==cu )
                    funs.add(fun);
            funs.sort(Comparator.comparingInt(n -> n._nid));
            for( FunNode fun : funs ) printFunction(fun,sb);

            printLine(cu._stop,sb);
        }
        printLine(code._stop,sb);
        return sb.toString();
    }

    /** Print one function without ever traversing a call-graph linkage. */
    private static void printFunction(FunNode fun, SB sb) {
        sb.nl().p("--- ");
        fun.sig().print(sb.p(fun._name==null ? "" : fun._name).p(" "));
        sb.p("----------------------\n");

        ArrayList<Node> post = new ArrayList<>();
        IdentityHashMap<Node,Boolean> visit = new IdentityHashMap<>();
        functionPost(fun,fun,visit,post);
        Collections.reverse(post);

        Node prior = null;
        IdentityHashMap<Node,Boolean> emitted = new IdentityHashMap<>();
        for( Node n : post ) {
            if( emitted.containsKey(n) ) continue;
            if( n instanceof RegionNode || n instanceof MultiNode || n instanceof CallNode ||
                (isMultiChild(prior) && !isMultiChild(n)) )
                sb.nl();
            // A Region/Loop and its Phis are one block header.  In particular,
            // do not pull loop-carried Phi inputs up between the header and
            // the Phi; those values belong down in the loop body/backedge.
            if( !isMultiChild(n) )
                emitLocalDefs(n,visit,emitted,sb);
            printLine(n,sb);
            emitted.put(n,Boolean.TRUE);
            prior = n;
        }
        sb.p("--- ").p(fun._name==null ? "" : fun._name).p(" ----------------------\n");
    }

    private static void emitLocalDefs(Node n,
                                      IdentityHashMap<Node,Boolean> owned,
                                      IdentityHashMap<Node,Boolean> emitted,
                                      SB sb) {
        if( n._inputs==null ) return;
        for( Node def : n._inputs ) {
            if( def==null || def instanceof CFGNode || def instanceof ConstantNode ||
                def instanceof PhiNode || isMultiChild(def) ||
                !owned.containsKey(def) || emitted.containsKey(def) )
                continue;
            emitLocalDefs(def,owned,emitted,sb);
            printLine(def,sb);
            emitted.put(def,Boolean.TRUE);
        }
    }

    // Printer-private RPO.  This deliberately uses raw edge arrays and local
    // identity state: no Node visit bits, cached idoms, or loop-tree metadata.
    private static void functionPost(Node n, FunNode owner,
                                     IdentityHashMap<Node,Boolean> visit,
                                     ArrayList<Node> post) {
        if( n==null || n._inputs==null || visit.put(n,Boolean.TRUE)!=null ) return;
        if( n instanceof FunNode fun && fun!=owner ) return; // Linked callee
        if( n instanceof ParmNode &&
            input0(n)!=owner ) return;                       // Callee parameter
        if( n instanceof StopCUNode || n instanceof StopNode || n instanceof StartCUNode ) return;
        if( n instanceof CallEndNode && input0(n) instanceof CallNode call &&
            !visit.containsKey(call) ) return;              // Foreign caller

        ArrayList<Node> uses = new ArrayList<>();
        // A Return is terminal inside its function.  All of its graph users
        // are ownership/linkage hooks (StopCU, CallEnds, FunPtrs), never CFG
        // continuation in the function being printed.
        if( !(n instanceof ReturnNode) ) for( Node use : n._outputs ) {
            if( use==null || use._inputs==null ) continue;
            if( n instanceof CallNode && use instanceof FunNode ) continue;
            if( use instanceof FunNode fun && fun!=owner ) continue;
            if( use instanceof ParmNode &&
                input0(use)!=owner ) continue;
            uses.add(use);
        }
        // CFG first produces an RPO-like block order.  Stable nid ordering
        // keeps malformed/multiply-connected graphs deterministic.
        uses.sort(Comparator
                  .comparingInt((Node use) -> use instanceof CFGNode ? 0 : 1)
                  .thenComparingInt(use -> use._nid));
        for( Node use : uses ) functionPost(use,owner,visit,post);

        // Keep a multi-head and its projections contiguous.
        if( isMultiChild(n) ) return;
        if( isMultiHead(n) ) {
            ArrayList<Node> ps = projections(n);
            for( Node p : ps ) visit.put(p,Boolean.TRUE);
            for( int i=ps.size()-1; i>=0; i-- ) post.add(ps.get(i));
        }
        post.add(n);
    }

    private static ArrayList<Node> projections(Node multi) {
        ArrayList<Node> ps = new ArrayList<>();
        for( Node use : multi._outputs )
            if( use!=null && use._inputs!=null && isMultiChild(use) ) ps.add(use);
        ps.sort(Comparator.comparingInt(IRPrinter::sortOrder));
        return ps;
    }

    private static boolean isMultiHead(Node n) {
        return n instanceof RegionNode || n instanceof MultiNode;
    }

    private static boolean isMultiChild(Node n) {
        Node head = input0(n);
        // Regions group their Phis, and tuple-producing MultiNodes group their
        // projections.  Merely being controlled by a Region/Loop does not
        // make an ordinary CFG node a grouped child.
        return (n instanceof PhiNode && head instanceof RegionNode) ||
               (n instanceof Proj    && head instanceof MultiNode);
    }

    // Raw, bounds-safe graph inspection for the debugger.  In particular do
    // not call an accessor which might assert, sharpen, cache, or lazily kill.
    private static Node input0(Node n) {
        return n==null || n._inputs==null || n._inputs.isEmpty()
            ? null : n._inputs.at(0);
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
        if( n instanceof Proj proj ) return proj.idx();
        if( n instanceof PhiNode phi ) return phi._nid;
        if( n == null ) return 0;
        return n._nid+1000000;
    }

}
