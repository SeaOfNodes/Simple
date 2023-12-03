package com.seaofnodes.simple;

import com.seaofnodes.simple.node.LoopNode;
import com.seaofnodes.simple.node.Node;

import java.util.BitSet;

public class IRPrinter {

    // Another bulk pretty-printer.  Makes more effort at basic-block grouping.
    public String p(Node node, int depth) {
        // First, a Breadth First Search at a fixed depth.
        BFS bfs = new BFS(node,depth);
        // Convert just that set to a post-order
        Ary<Node> rpos = new Ary<>(Node.class);
        BitSet visit = new BitSet();
        for( int i=bfs._lim; i< bfs._bfs._len; i++ )
            postOrd( bfs._bfs.at(i), rpos, visit, bfs._bs);
        // Reverse the post-order walk
        StringBuilder sb = new StringBuilder();
        boolean gap=false;
        for( int i=rpos._len-1; i>=0; i-- ) {
            Node n = rpos.at(i);
            if( n.isCFG() || n.isMultiHead() ) {
                if( !gap ) sb.append("\n"); // Blank before multihead
                n._print_line(sb); // Print head
                while( --i >= 0 ) {
                    Node t = rpos.at(i);
                    if( !t.isMultiTail() ) { i++; break; }
                    t._print_line(sb);
                }
                sb.append("\n"); // Blank after multitail
                gap = true;
            } else {
                n._print_line( sb );
                gap = false;
            }
        }
        return sb.toString();
    }
    private void postOrd(Node n, Ary<Node> rpos, BitSet visit, BitSet bfs) {
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
        rpos.push(n);
    }

    // Breadth-first search, broken out in a class to keep in more independent.
    // Maintains a root-set of Nodes at the limit (or past by 1 if MultiHead).
    public static class BFS {
        // A breadth first search, plus MultiHeads for any MultiTails
        public final Ary<Node> _bfs;
        public final BitSet _bs; // Visited members by node id
        public final int _depth; // Depth limit
        public final int _lim; // From here to _bfs._len can be roots for a reverse search
        public BFS( Node base, int d ) {
            _depth = d;
            _bfs = new Ary<>(Node.class);
            _bs = new BitSet();

            add(base);                 // Prime the pump
            int idx=0, lim=1;          // Limit is where depth counter changes
            while( idx < _bfs._len ) { // Ran out of nodes below depth
                Node n = _bfs.at(idx++);
                for( Node def : n._inputs )
                    if( def!=null && !_bs.get(def._nid) )
                        add(def);
                if( idx==lim ) {    // Depth counter changes at limit
                    if( --d < 0 )
                        break;      // Ran out of depth
                    lim = _bfs._len;  // New depth limit
                }
            }
            // Toss things past the limit except multi-heads
            while( idx < _bfs._len ) {
                Node n = _bfs.at(idx);
                if( n.isMultiHead() ) idx++;
                else del(idx);
            }
            // Root set is any node with no inputs in the visited set
            lim = _bfs._len;
            for( int i=_bfs._len-1; i>=0; i-- )
                if( !any_visited(_bfs.at(i)) )
                    swap( i,--lim);
            _lim = lim;
        }
        void swap( int x, int y ) {
            if( x==y ) return;
            Node tx = _bfs.at(x);
            Node ty = _bfs.at(y);
            _bfs.set(x,ty);
            _bfs.set(y,tx);
        }
        void add(Node n) {
            _bfs.push(n);
            _bs.set(n._nid);
        }
        void del(int idx) {
            Node n = _bfs.del(idx);
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
