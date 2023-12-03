package com.seaofnodes.simple;

import com.seaofnodes.simple.node.LoopNode;
import com.seaofnodes.simple.node.Node;

import java.util.ArrayList;
import java.util.BitSet;

public class IRPrinter {

    // Another bulk pretty-printer.  Makes more effort at basic-block grouping.
    public static String prettyPrint(Node node, int depth) {
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
            if( n.isCFG() || n.isMultiHead() ) {
                if( !gap ) sb.append("\n"); // Blank before multihead
                n._printLine(sb); // Print head
                while( --i >= 0 ) {
                    Node t = rpos.get(i);
                    if( !t.isMultiTail() ) { i++; break; }
                    t._printLine(sb);
                }
                sb.append("\n"); // Blank after multitail
                gap = true;
            } else {
                n._printLine( sb );
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
        if( n.isCFG() ) {
            for( Node use : n._outputs )
                if( use.isCFG() && use.nOuts()>=1 && !(use._outputs.get(0) instanceof LoopNode) )
                    postOrd(use, rpos,visit,bfs);
            for( Node use : n._outputs )
                if( use.isCFG() )
                    postOrd(use,rpos,visit,bfs);
        }
        for( Node use : n._outputs )
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
            Node n = Utils.del(_bfs, idx);
            _bs.clear(n._nid);
        }
        boolean any_visited( Node n ) {
            for( Node def : n._inputs )
                if( def!=null && _bs.get(def._nid) )
                    return true;
            return false;
        }
    }
}
