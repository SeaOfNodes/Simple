package com.seaofnodes.simple.print;

import com.seaofnodes.simple.Ary;
import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeFunPtr;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;

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


    public static String prettyPrint(Node node, int depth) {
        return CodeGen.CODE._phase.ordinal() > CodeGen.Phase.Schedule.ordinal()
            ? _prettyPrintScheduled( node, depth )
            : _prettyPrint( node, depth );
    }

    // Another bulk pretty-printer.  Makes more effort at basic-block grouping.
    private static String _prettyPrint( Node node, int depth ) {
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
                    fun.sig().print(sb.p("--- ").p(fun._name==null ? "" : fun._name).p(" "),false).p("----------------------\n");
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
                        fun.sig().print(sb.p(fun._name==null ? "" : fun._name).p(" "),false);
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

    public static String _prettyPrint( CodeGen code ) {
        SB sb = new SB();
        // Print the Start "block"
        printLine(code._start,sb);
        for( Node n : code._start._outputs )
            printLine(n,sb);
        sb.nl();

        // Skip start, stop
        for( int i=1; i<code._cfg._len-1; i++ ) {
            CFGNode blk = code._cfg.at(i);
            if( blk.blockHead() && (!(blk instanceof CProjNode) || (blk.cfg0() instanceof IfNode )) ) {
                if( blk instanceof FunNode fun )
                    sb.p("--- ").p(fun.label()).p(" ---------------------------").nl();
                // Print block header
                sb.p("%-13.13s".formatted(label(blk)+":"));
                sb.p( "     ".repeat(4) ).p(" [[  ");
                if( blk instanceof RegionNode )
                    for( int j=1; j<blk.nIns(); j++ )
                        label(sb,blk.cfg(j));
                else
                    label(sb,blk.cfg(0));
                sb.p(" ]]  \n");
            }
            printLine(blk,sb);
            if( blk instanceof ReturnNode ret )
                sb.p("--- ").p(ret.fun().label()).p(" ---------------------------").nl();

            // Block contents
            for( Node n : blk._outputs ) {
                if( n instanceof CFGNode cfg ) continue;
                printLine(n,sb);
                if( !(n instanceof CFGNode) && n instanceof MultiNode )
                    for( Node use : n._outputs )
                        if( use instanceof ProjNode )
                            printLine(use,sb);
            }
        }

        printLine(code._stop,sb);
        return sb.toString();
    }

    // Bulk pretty printer, knowing scheduling information is available
    private static String _prettyPrintScheduled( Node node, int depth ) {
        // Backwards DFS walk to depth.
        HashMap<Integer,Integer> ds = new HashMap<>();
        Ary<Node> ns = new Ary<>(Node.class);
        _walk(ds,ns,node,depth);
        // Remove data projections, these are force-printed behind their multinode head
        for( int i=0; i<ns.size(); i++ ) {
            if( ns.get(i) instanceof ProjNode proj && !(proj.in(0) instanceof CFGNode) ) {
                ns.del(i--);
                ds.remove(proj._nid);
            }
        }
        // Print by block with least idepth
        SB sb = new SB();
        Ary<Node> bns = new Ary<>(Node.class);
        while( !ds.isEmpty() ) {
            CFGNode blk = null;
            for( Node n : ns ) {
                CFGNode cfg = n instanceof CFGNode cfg0 && cfg0.blockHead() ? cfg0 : n.cfg0();
                if( blk==null || cfg.idepth() < blk.idepth() )
                    blk = cfg;
            }
            Integer d = ds.remove(blk._nid);
            ns.del(ns.find(blk));

            // Print block header
            sb.p("%-13.13s".formatted(label(blk)+":"));
            sb.p( "     ".repeat(4) ).p(" [[  ");
            if( blk instanceof StartNode ) ;
            else if( blk instanceof RegionNode || blk instanceof StopNode )
                for( int i=(blk instanceof StopNode ? 3 : 1); i<blk.nIns(); i++ )
                    label(sb,blk.cfg(i));
            else
                label(sb,blk.cfg(0));
            sb.p(" ]]  \n");
            printLine(blk,sb);

            // Collect block contents that are in the depth limit
            bns.clear();
            int xd = Integer.MAX_VALUE;
            for( Node use : blk._outputs ) {
                Integer i = ds.get(use._nid);
                if( i!=null && !(use instanceof CFGNode cfg && cfg.blockHead()) ) {
                    if( bns.find(use)==-1 )
                        bns.add(use);
                    xd = Math.min(xd,i);
                }
            }
            // Print Phis up front, if any
            for( int i=0; i<bns.size(); i++ )
                if( bns.get(i) instanceof PhiNode phi )
                    printLine( phi, sb,bns,i--,ds,ns);

            // Print block contents in depth order, bumping depth until whole block printed
            for( ; !bns.isEmpty(); xd++ )
                for( int i=0; i<bns.size(); i++ ) {
                    Node n = bns.get(i);
                    if( ds.get(n._nid)==xd ) {
                        printLine( n, sb, bns, i--, ds,ns );
                        if( n instanceof MultiNode && !(n instanceof CFGNode) ) {
                            for( Node use : n._outputs ) {
                                if( use instanceof ProjNode )
                                    printLine(use,sb,bns,bns.indexOf(use),ds,ns);
                            }
                        }
                    }
                }
            sb.p("\n");
        }
        return sb.toString();
    }

    private static void _walk( HashMap<Integer,Integer> ds, Ary<Node> ns, Node node, int d ) {
        Integer nd = ds.get(node._nid);
        if( nd!=null && d <= nd ) return; // Been there, done that
        Integer old = ds.put(node._nid,d) ;
        if( old == null )
          ns.add(node);
        if( d == 0 ) return;    // Depth cutoff
        for( Node def : node._inputs )
            if( def != null &&
                !(node instanceof LoopNode loop && loop.back()==def) &&
                // Don't walk into or out of functions
                !(node instanceof CallEndNode && def instanceof ReturnNode) &&
                !(node instanceof FunNode && def instanceof CallNode) &&
                !(node instanceof ParmNode && !(def instanceof FunNode))
            )
                _walk(ds,ns,def,d-1);
    }

    static String label( CFGNode blk ) {
        if( blk instanceof StartNode ) return "START";
        return (blk instanceof LoopNode ? "LOOP" : "L")+blk._nid;
    }
    static void label( SB sb, CFGNode blk ) {
        if( !blk.blockHead() ) blk = blk.cfg(0);
        sb.p( "%-9.9s ".formatted( label( blk ) ) );
    }
    static void printLine( Node n, SB sb, Ary<Node> bns, int i, HashMap<Integer,Integer> ds, Ary<Node> ns ) {
        printLine( n, sb );
        if( i != -1 ) bns.del(i);
        ds.remove(n._nid);
        int idx = ns.find(n);
        if( idx!=-1 ) ns.del(idx);
    }

}
