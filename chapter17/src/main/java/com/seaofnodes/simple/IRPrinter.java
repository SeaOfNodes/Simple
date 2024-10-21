package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import java.lang.StringBuilder;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;

public class IRPrinter {

    // Print a node on 1 line, columnar aligned, as:
    // NNID NNAME DDEF DDEF  [[  UUSE UUSE  ]]  TYPE
    // 1234 sssss 1234 1234 1234 1234 1234 1234 tttttt
    public static void _printLine( Node n, StringBuilder sb ) {
        sb.append("%4d %-7.7s ".formatted(n._nid,n.label()));
        if( n._inputs==null ) {
            sb.append("DEAD\n");
            return;
        }
        for( Node def : n._inputs )
            sb.append(def==null ? "____ " : "%4d ".formatted(def._nid));
        for( int i = n._inputs.size(); i<4; i++ )
            sb.append("     ");
        sb.append(" [[  ");
        for( Node use : n._outputs )
            sb.append(use==null ? "____ " : "%4d ".formatted(use._nid));
        int lim = 6 - Math.max(n._inputs.size(),4);
        for( int i = n._outputs.size(); i<lim; i++ )
            sb.append("     ");
        sb.append(" ]]  ");
        if( n._type!= null ) sb.append(n._type.str());
        sb.append("\n");
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
        nodeId(sb, n).append(": ");
        if( n._inputs==null ) {
            sb.append("DEAD\n");
            return;
        }
        if( n._type!= null ) n._type.typeName(sb);
        sb.append(" = ").append( n.label() ).append( "(" );
        for( int i = 0; i < n._inputs.size(); i++ ) {
            Node def = n.in(i);
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
        return Parser.SCHEDULED
            ? prettyPrintScheduled( node, depth, false )
            : prettyPrint( node, depth, false );
    }

    // Another bulk pretty-printer.  Makes more effort at basic-block grouping.
    public static String prettyPrint( Node node, int depth, boolean llvmFormat ) {
        // First, a Breadth First Search at a fixed depth.
        BFS bfs = new BFS(node,depth);
        // Convert just that set to a post-order
        ArrayList<Node> rpos = new ArrayList<>();
        BitSet visit = new BitSet();
        for( int i=bfs._lim; i< bfs._bfs.size(); i++ )
            postOrd( bfs._bfs.get(i), rpos, visit, bfs._bs);
        // Reverse the post-order walk
        StringBuilder sb = new StringBuilder();
        boolean gap=false;
        for( int i=rpos.size()-1; i>=0; i-- ) {
            Node n = rpos.get(i);
            if( n instanceof CFGNode || n.isMultiHead() ) {
                if( !gap ) sb.append("\n"); // Blank before multihead
                printLine( n, sb, llvmFormat ); // Print head
                while( --i >= 0 ) {
                    Node t = rpos.get(i);
                    if( !t.isMultiTail() ) { i++; break; }
                    printLine( t, sb, llvmFormat );
                }
                sb.append("\n"); // Blank after multitail
                gap = true;
            } else {
                printLine( n, sb, llvmFormat );
                gap = false;
            }
        }
        return sb.toString();
    }

    private static void postOrd(Node n, ArrayList<Node> rpos, BitSet visit, BitSet bfs) {
        if( !bfs.get(n._nid) )
            return;  // Not in the BFS visit
        if( visit.get(n._nid) ) return; // Already post-order walked
        visit.set(n._nid);
        // First walk the CFG, then everything
        if( n instanceof CFGNode ) {
            for( Node use : n._outputs )
                if( use instanceof CFGNode && use.nOuts() >= 1 && !(use._outputs.get( 0 ) instanceof LoopNode) )
                    postOrd(use, rpos,visit,bfs);
            for( Node use : n._outputs )
                if( use instanceof CFGNode )
                    postOrd(use,rpos,visit,bfs);
        }
        for( Node use : n._outputs )
            if( use != null )
                postOrd(use, rpos,visit,bfs);
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
                if( n.isMultiHead() ) idx++;
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

    // Bulk pretty printer, knowing scheduling information is available
    public static String prettyPrintScheduled( Node node, int depth, boolean llvmFormat ) {
        // Backwards DFS walk to depth.
        HashMap<Integer,Integer> ds = new HashMap<>();
        ArrayList<Node> ns = new ArrayList<>();
        _walk(ds,ns,node,depth);
        // Remove data projections, these are force-printed behind their multinode head
        for( int i=0; i<ns.size(); i++ ) {
            if( ns.get(i) instanceof ProjNode proj && !(proj.in(0) instanceof CFGNode) ) {
                Utils.del(ns,i--);
                ds.remove(proj._nid);
            }
        }
        // Print by block with least idepth
        StringBuilder sb = new StringBuilder();
        ArrayList<Node> bns = new ArrayList<>();
        while( !ds.isEmpty() ) {
            CFGNode blk = null;
            for( Node n : ns ) {
                CFGNode cfg = n instanceof CFGNode cfg0 && cfg0.blockHead() ? cfg0 : n.cfg0();
                if( blk==null || cfg.idepth() < blk.idepth() )
                    blk = cfg;
            }
            ds.remove(blk._nid);
            ns.remove(blk);

            // Print block header
            sb.append("%-13.13s".formatted(label(blk)+":"));
            sb.append( "     ".repeat(4) ).append(" [[  ");
            if( blk instanceof RegionNode || blk instanceof StopNode )
                for( int i=(blk instanceof StopNode ? 0 : 1); i<blk.nIns(); i++ )
                    label(sb,blk.cfg(i));
            else if( !(blk instanceof StartNode) )
                label(sb,blk.cfg(0));
            sb.append(" ]]  \n");

            // Collect block contents that are in the depth limit
            bns.clear();
            int xd = Integer.MAX_VALUE;
            for( Node use : blk._outputs ) {
                Integer i = ds.get(use._nid);
                if( i!=null && !(use instanceof CFGNode cfg && cfg.blockHead()) ) {
                    bns.add(use);
                    xd = Math.min(xd,i);
                }
            }
            // Print Phis up front, if any
            for( int i=0; i<bns.size(); i++ )
                if( bns.get(i) instanceof PhiNode phi )
                    printLine( phi, sb, llvmFormat,bns,i--,ds,ns);

            // Print block contents in depth order, bumping depth until whole block printed
            for( ; !bns.isEmpty(); xd++ )
                for( int i=0; i<bns.size(); i++ ) {
                    Node n = bns.get(i);
                    if( ds.get(n._nid)==xd ) {
                        printLine( n, sb, llvmFormat, bns, i--, ds,ns );
                        if( n instanceof MultiNode && !(n instanceof CFGNode) ) {
                            for( Node use : n._outputs ) {
                                printLine(use,sb,llvmFormat,bns,bns.indexOf(use),ds,ns);
                            }
                        }
                    }
                }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static void _walk( HashMap<Integer,Integer> ds, ArrayList<Node> ns, Node node, int d ) {
        Integer nd = ds.get(node._nid);
        if( nd!=null && d <= nd ) return; // Been there, done that
        if( ds.put(node._nid,d) == null )
          ns.add(node);
        if( d == 0 ) return;    // Depth cutoff
        for( Node def : node._inputs )
            if( def != null )
                _walk(ds,ns,def,d-1);
    }

    static String label( CFGNode blk ) {
        if( blk instanceof StartNode ) return "START";
        return (blk instanceof LoopNode ? "LOOP" : "L")+blk._nid;
    }
    static void label( StringBuilder sb, CFGNode blk ) {
        if( !blk.blockHead() ) blk = blk.cfg(0);
        sb.append( "%-9.9s ".formatted( label( blk ) ) );
    }
    static void printLine( Node n, StringBuilder sb, boolean llvmFormat, ArrayList<Node> bns, int i, HashMap<Integer,Integer> ds, ArrayList<Node> ns ) {
        printLine( n, sb, llvmFormat );
        if( i != -1 ) Utils.del(bns,i);
        ds.remove(n._nid);
        ns.remove(n);
    }

}
