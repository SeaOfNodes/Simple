package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Random;

/**
 * The IterOptim runs after parsing. It iterates the peepholes to a fixed point
 * so that no more peepholes apply.  This should be linear because peepholes rarely
 * (never?)  increase code size.  The graph should monotonically reduce in some
 * dimension, which is usually size.  It might also reduce in e.g. number of
 * MulNodes or Load/Store nodes, swapping out more "expensive" Nodes for cheaper
 * ones.
 *
 * The theoretical overall worklist is mindless just grabbing the next thing and
 * doing it.  If the graph changes, put the neighbors on the worklist.  Lather,
 * Rinse, Repeat until the worklist runs dry.
 *
 * The main issues we have to deal with:
 *
 * <ul>
 * <li>Nodes have uses; replacing some set of Nodes with another requires more graph
 *   reworking.  Not rocket science, but it can be fiddly.  Its helpful to have a
 *   small set of graph munging utilities, and the strong invariant that the graph
 *   is stable and correct between peepholes.  In our case `Node.subsume` does
 *   most of the munging, building on our prior stable Node utilities.</li>
 *
 * <li>Changing a Node also changes the graph "neighborhood".  The neigbors need to
 *   be checked to see if THEY can also peephole, and so on.  After any peephole
 *   or graph update we put a Nodes uses and defs on the worklist.</li>
 *
 * <li>Our strong invariant is that for all Nodes, either they are on the worklist
 *   OR no peephole applies.  This invariant is easy to check, although expensive.
 *   Basically the normal "iterate peepholes to a fixed point" is linear, and this
 *   check is linear at each peephole step... so quadratic overall.  Its a useful
 *   assert, but one we can disable once the overall algorithm is stable - and
 *   then turn it back on again when some new set of peepholes is misbehaving.
 *   The code for this is turned on in `IterOptim.iterate` as `assert
 *   progressOnList(stop);`</li>
 * </ul>
 */
public abstract class IterPeeps {

    private static final WorkList<Node> WORK = new WorkList<>();

    public static <N extends Node> N add( N n ) {
        return (N)WORK.push(n);
    }

    public static void addAll( ArrayList<Node> ary ) {
        WORK.addAll(ary);
    }

    /**
     * Iterate peepholes to a fixed point
     */
    public static StopNode iterate(StopNode stop, boolean show) {
        assert progressOnList(stop);
        int cnt=0;

        Node n;
        while( (n=WORK.pop()) != null ) {
            if( n.isDead() )  continue;
            cnt++;              // Useful for debugging, searching which peephole broke things
            Node x = n.peepholeOpt();
            if( x != null ) {
                if( x.isDead() ) continue;
                // peepholeOpt can return brand-new nodes, needing an initial type set
                if( x._type==null ) x.setType(x.compute());
                // Changes require neighbors onto the worklist
                if( x != n || !(x instanceof ConstantNode) ) {
                    // All outputs of n (changing node) not x (prior existing node).
                    for( Node z : n._outputs ) WORK.push(z);
                    // Everybody gets a free "go again" in case they didn't get
                    // made in their final form.
                    WORK.push(x);
                    // If the result is not self, revisit all inputs (because
                    // there's a new user), and replace in the graph.
                    if( x != n ) {
                        for( Node z : n. _inputs ) WORK.push(z);
                        n.subsume(x);
                    }
                }
                // If there are distant neighbors, move to worklist
                n.moveDepsToWorklist();
                assert progressOnList(stop); // Very expensive assert
            }
            if( n.isUnused() && !(n instanceof StopNode) )
                n.kill();       // Just plain dead
        }

        if( show )
            System.out.println(new GraphVisualizer().generateDotOutput(stop,null,null));
        GlobalCodeMotion.buildCFG(stop);
        return stop;
    }

    // Visit ALL nodes and confirm the invariant:
    //   Either you are on the WORK worklist OR running `iter()` makes no progress.

    // This invariant ensures that no progress is missed, i.e., when the
    // worklist is empty we have indeed done all that can be done.  To help
    // with debugging, the {@code assert} is broken out in a place where it is easy to
    // stop if a change is found.

    // Also, the normal usage of `iter()` may attempt peepholes with distance
    // neighbors and these should fail, but will then try to add dependencies
    // {@link #Node.addDep} which is a side effect in an assert.  The {@link
    // #midAssert} is used to stop this side effect.
    private static boolean MID_ASSERT;
    public static boolean midAssert() { return MID_ASSERT; }
    private static boolean progressOnList(Node stop) {
        MID_ASSERT = true;
        int old_cnt = Node.ITER_CNT, old_nop = Node.ITER_NOP_CNT;
        Node changed = stop.walk( n -> {
                if( WORK.on(n) ) return null;
                Node m = n.peepholeOpt();
                if( m==null ) return null;
                System.err.println("BREAK HERE FOR BUG");
                return m;
            });
        Node.ITER_CNT = old_cnt;  Node.ITER_NOP_CNT = old_nop;
        MID_ASSERT = false;
        return changed==null;
    }

    public static void reset() {
        WORK.clear();
    }

    /**
     * Classic WorkList, with a fast add/remove, dup removal, random pull.
     * The Node's nid is used to check membership in the worklist.
     */
    public static class WorkList<E extends Node> {

        private Node[] _es;
        private int _len;
        private final BitSet _on;   // Bit set if Node._nid is on WorkList
        private final Random _R;    // For randomizing pull from the WorkList
        private final long _seed;

        /* Useful stat - how many nodes are processed in the post parse iterative opt */
        private long _totalWork = 0;

        WorkList() { this(123); }
        WorkList(long seed) {
            _es = new Node[1];
            _len=0;
            _on = new BitSet();
            _seed = seed;
            _R = new Random();
            _R.setSeed(_seed);
        }

        /**
         * Pushes a Node on the WorkList, ensuring no duplicates
         * If Node is null it will not be added.
         */
        public E push( E x ) {
            if( x==null ) return null;
            int idx = x._nid;
            if( !_on.get(idx) ) {
                _on.set(idx);
                if( _len==_es.length )
                    _es = Arrays.copyOf(_es,_len<<1);
                _es[_len++] = x;
                _totalWork++;
            }
            return x;
        }

        public void addAll( ArrayList<E> ary ) {
            for( E n : ary )
                push(n);
        }

        /**
         * True if Node is on the WorkList
         */
        boolean on( E x ) { return _on.get(x._nid); }

        /**
         * Removes a random Node from the WorkList; null if WorkList is empty
         */
        E pop() {
            if( _len == 0 ) return null;
            int idx = _R.nextInt(_len);
            E x = (E)_es[idx];
            _es[idx] = _es[--_len]; // Compress array
            _on.clear(x._nid);
            return x;
        }

        public void clear() {
            _len = 0;
            _on.clear();
            _R.setSeed(_seed);
            _totalWork = 0;
        }
    }
}
