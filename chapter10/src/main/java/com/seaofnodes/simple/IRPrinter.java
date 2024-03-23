package com.seaofnodes.simple;

import com.seaofnodes.simple.node.LoopNode;
import com.seaofnodes.simple.node.Node;

import java.util.ArrayList;
import java.util.BitSet;

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
        for( int i = n._inputs.size(); i<3; i++ )
            sb.append("     ");
        sb.append(" [[  ");
        for( Node use : n._outputs )
            sb.append(use==null ? "____ " : "%4d ".formatted(use._nid));
        int lim = 5 - Math.max(n._inputs.size(),3);
        for( int i = n._outputs.size(); i<lim; i++ )
            sb.append("     ");
        sb.append(" ]]  ");
        if( n._type!= null ) n._type._print(sb);
        sb.append("\n");
    }

    // Print a node on 1 line, format is inspired by LLVM
    // %id: TYPE = NODE(inputs ....)
    // Nodes as referred to as %id
    public static void _printLineLlvmFormat( Node n, StringBuilder sb ) {
        sb.append("%%%d: ".formatted(n._nid));
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
            sb.append(def == null ? "_" : "%%%d".formatted(def._nid));
        }
        sb.append(")").append("\n");
    }

    public static void printLine( Node n, StringBuilder sb, boolean llvmFormat ) {
        if (llvmFormat) _printLineLlvmFormat( n, sb );
        else            _printLine          ( n, sb );
    }

    public static String prettyPrint(Node node, int depth) {
        return prettyPrint( node, depth, false );
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
            if( n.isCFG() || n.isMultiHead() ) {
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
        if( n.isCFG() ) {
            for( Node use : n._outputs )
                if( use!=null && use.isCFG() && use.nOuts()>=1 && !(use._outputs.get(0) instanceof LoopNode) )
                    postOrd(use, rpos,visit,bfs);
            for( Node use : n._outputs )
                if( use!=null && use.isCFG() )
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
