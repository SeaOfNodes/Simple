package com.seaofnodes.simple.print;

import com.seaofnodes.simple.codegen.CodeGen;
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
        if( code._start._ltree==null )
            code._start.buildLoopTree(code._stop);
        BitSet visit = code.visit();
        SB sb = new SB();
        printLine(code._start,sb);

        // All the global constants
        for( Node n : code._start._outputs )
            if( !(n instanceof FunNode) )
                printLine(n,sb);
        sb.nl();

        // All the functions
        Ary<Node> rpo = new Ary<>(Node.class);
        for( Node n : code._start._outputs )
            if( n instanceof FunNode fun )
                _funWalk(fun,sb,rpo,visit);

        visit.clear();
        return sb.toString();
    }

    // Walk and print whole functions at a time, in CG order
    private static void _funWalk( FunNode fun, SB sb, Ary<Node> rpo, BitSet visit) {
        if( fun._ltree._par._head instanceof FunNode fun2 )
            _funWalk(fun2,sb,rpo,visit);
        if( visit.get(fun._nid) ) return;
        _funRPO(fun,rpo,visit);

        sb.p("--- ");
        fun.sig().print(sb.p(fun._name==null ? "" : fun._name).p(" "));
        sb.p("----------------------\n");

        boolean gap=false;
        for( int i=rpo._len-1; i>=0; i-- ) {
            Node n = rpo.at(i);
            if( (n instanceof MultiNode && !(n instanceof CallEndNode)) || (n instanceof RegionNode && !(n instanceof FunNode)) || n instanceof CallNode )
                gap=true;
            if( gap ) { sb.nl(); gap=false; }
            printLine(n,sb);
            if( !(n instanceof CallNode) && multiChild(n) && i>0 && !multiChild(rpo.at(i-1)) )
                gap=true;
        }

        sb.p("--- ").p(fun._name==null ? "" : fun._name).p(" ----------------------\n\n");
        rpo.clear();
    }

    // Walk and gather RPO nodes.
    private static void _funRPO(Node n, Ary<Node> rpo, BitSet visit) {
        if( visit.get(n._nid) ) return; // Been there, done that
        visit.set(n._nid);              // Stop recursion
        // Walk outputs ordered
        if( !(n instanceof ReturnNode) ) {
            if( n instanceof CFGNode ) {
                // If nodes walk outer loops before inner, so they hit the
                // Return first, so Return is at the bottom of the RPO.
                if( n instanceof IfNode iff ) {
                    CProjNode c0 = iff.cproj(0);
                    CProjNode c1 = iff.cproj(1);
                    if( c0._ltree.depth() > c1._ltree.depth() )
                        { c0 = c1; c1 = iff.cproj(0); }
                    _funRPO(c0,rpo,visit);
                    _funRPO(c1,rpo,visit);
                } else {
                    // CFG to CFG first
                    for( Node use : n._outputs )
                        if( use instanceof CFGNode && !(use instanceof FunNode) ) // Do not walk from a CallNode to a FunNode
                            _funRPO(use,rpo,visit);
                }
                // Walk CFG to data eventually
                for( Node use : n._outputs )
                    if( !(use instanceof CFGNode) )
                        _funRPO(use,rpo,visit);
            } else {
                // Do not walk from a non-CFG to a CFG; CFGs walk to CFGs to preserve CFG order.
                // Do not walk *into* Phis; wait for the Region to walk the Phis
                for( Node use : n._outputs ) {
                    if( !(use instanceof CFGNode) && !(use instanceof PhiNode) )
                        _funRPO(use,rpo,visit);
                }
            }
        }

        // If parent is a Multi, do not add (yet).
        // If self   is a Multi, add self and children.
        if( multiChild(n) || n instanceof CallEndNode ) {
            // nothing
        } else if( n instanceof MultiNode ) {
            // Now lump all multi/projections together
            printMulti(n,rpo);
        } else if( n instanceof CallNode call ) {
            printMulti(call.cend(),rpo);
            rpo.add(call);
        } else if( n instanceof RegionNode ) {
            // Now lump all Phis together
            int old = rpo._len;
            for( Node use : n._outputs )
                if( use instanceof PhiNode )
                    rpo.add(use);
            // Sort by label?  Want Phis before control outputs
            Arrays.sort( rpo._es, old, rpo._len, (x,y) -> y.label().compareTo(x.label()) );
            rpo.add(n);
        } else {
            rpo.add(n);         // Post-order add
        }
    }

    private static boolean multiChild(Node n) {
        return n instanceof Proj || n instanceof PhiNode;
    }

    private static void printMulti(Node n, Ary<Node> rpo) {
        // Now lump all multi/projections together
        for( Node use : n._outputs )
            rpo.add(use);
        // Sort by projection order
        Arrays.sort( rpo._es,rpo._len-n.nOuts(),rpo._len, (x,y) -> ((Proj)y).idx() - ((Proj)x).idx() );
        rpo.add(n);
    }



    // ----------------------------------------
    // Another bulk pretty-printer.  Makes more effort at basic-block grouping.
    public static String prettyPrint(Node node, int depth) {
        // First, a Breadth First Search at a fixed depth.
        BFS bfs = new BFS(node,depth);
        // Convert just that set to a post-order
        ArrayList<Node> rpos = new ArrayList<>();
        BitSet visit = new BitSet();
        for( int i=bfs._lim; i< bfs._bfs.size(); i++ )
            postOrd( bfs._bfs.get(i), null, rpos, visit, bfs._bs);
        // Reverse the post-order walk
        SB sb = new SB();
        boolean gap=false;
        for( int i=rpos.size()-1; i>=0; i-- ) {
            Node n = rpos.get(i);
            if( n instanceof CFGNode || n instanceof MultiNode ) {
                if( !gap ) sb.p("\n"); // Blank before multihead
                if( n instanceof FunNode fun )
                    fun.sig().print(sb.p("--- ").p(fun._name==null ? "" : fun._name).p(" ")).p("----------------------\n");
                printLine( n, sb );         // Print head
                while( --i >= 0 ) {
                    Node t = rpos.get(i);
                    if( !(t.in(0) instanceof MultiNode) ) { i++; break; }
                    printLine( t, sb );
                }
                if( n instanceof ReturnNode ret ) {
                    FunNode fun = ret.fun();
                    sb.p("--- ");
                    if( fun != null )
                        fun.sig().print(sb.p(fun._name==null ? "" : fun._name).p(" "));
                    sb.p("----------------------\n");
                }
                if( !(n instanceof CallNode) ) {
                    sb.p("\n"); // Blank after multitail
                    gap = true;
                }
            } else {
                printLine( n, sb );
                gap = false;
            }
        }
        return sb.toString();
    }

    private static void postOrd(Node n, Node prior, ArrayList<Node> rpos, BitSet visit, BitSet bfs) {
        if( !bfs.get(n._nid) )
            return;  // Not in the BFS visit
        if( n instanceof FunNode && !(prior instanceof StartNode) )
            return;                     // Only visit Fun from Start
        if( visit.get(n._nid) ) return; // Already post-order walked
        visit.set(n._nid);
        // First walk the CFG, then everything
        if( n instanceof CFGNode ) {
            for( Node use : n._outputs )
                // Follow CFG, not across call/function borders, and not around backedges
                if( use instanceof CFGNode && !(n instanceof CallNode && use instanceof FunNode) &&
                    use.nOuts() >= 1 &&  !(use._outputs.get(0) instanceof LoopNode) )
                    postOrd(use, n, rpos,visit,bfs);
            for( Node use : n._outputs )
                // Follow CFG, not across call/function borders
                if( use instanceof CFGNode && !(n instanceof CallNode && use instanceof FunNode) )
                    postOrd(use,n,rpos,visit,bfs);
        }
        // Follow all outputs
        for( Node use : n._outputs )
            if( use != null &&
                !(n instanceof CallNode && use instanceof FunNode) &&
                (n instanceof FunNode || !(use instanceof ParmNode)) )
                postOrd(use, n, rpos,visit,bfs);
        // Post-order
        rpos.add(n);
    }

    // Breadth-first search, broken out in a class to keep in more independent.
    // Maintains a root-set of Nodes at the limit (or past by 1 if MultiHead).
    public static class BFS {
        // A breadth first search, plus MultiHeads for any MultiTails
        public final ArrayList<Node> _bfs;
        public final BitSet _bs; // Visited members by node id
        public final int _depth; // Depth limit
        public final int _lim; // From here to _bfs._len can be roots for a reverse search
        public BFS( Node base, int d ) {
            _depth = d;
            _bfs = new ArrayList<>();
            _bs = new BitSet();

            add(base);                 // Prime the pump
            int idx=0, lim=1;          // Limit is where depth counter changes
            while( idx < _bfs.size() ) { // Ran out of nodes below depth
                Node n = _bfs.get(idx++);
                for( Node def : n._inputs )
                    if( def!=null && !_bs.get(def._nid) )
                        add(def);
                if( idx==lim ) {    // Depth counter changes at limit
                    if( --d < 0 )
                        break;      // Ran out of depth
                    lim = _bfs.size();  // New depth limit
                }
            }
            // Toss things past the limit except multi-heads
            while( idx < _bfs.size() ) {
                Node n = _bfs.get(idx);
                if( n instanceof MultiNode ) idx++;
                else del(idx);
            }
            // Root set is any node with no inputs in the visited set
            lim = _bfs.size();
            for( int i=_bfs.size()-1; i>=0; i-- )
                if( !any_visited(_bfs.get(i)) )
                    swap( i,--lim);
            _lim = lim;
        }
        void swap( int x, int y ) {
            if( x==y ) return;
            Node tx = _bfs.get(x);
            Node ty = _bfs.get(y);
            _bfs.set(x,ty);
            _bfs.set(y,tx);
        }
        void add(Node n) {
            _bfs.add(n);
            _bs.set(n._nid);
        }
        void del(int idx) {
            _bs.clear(_bfs.get(idx)._nid);
            Utils.del(_bfs, idx);
        }
        boolean any_visited( Node n ) {
            for( Node def : n._inputs )
                if( def!=null && _bs.get(def._nid) )
                    return true;
            return false;
        }
    }

    static String label( CFGNode blk ) {
        if( blk instanceof StartNode ) return "START";
        return (blk instanceof LoopNode ? "LOOP" : "L")+blk._nid;
    }

}
