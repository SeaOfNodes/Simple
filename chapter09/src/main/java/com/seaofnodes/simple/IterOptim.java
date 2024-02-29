package com.seaofnodes.simple;

import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.StopNode;

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
public abstract class IterOptim {

    public static final WorkList<Node> WORK = new WorkList<>();

    public static <N extends Node> N add( N n ) { return (N)WORK.push(n); }

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
                for( Node z : n. _inputs ) WORK.push(z);
                if( x != n )
                    n.subsume(x);
                if( x.isDead() ) continue;
                if( x._type==null ) x._type = x.compute();
                WORK.push(x);
                for( Node z : x. _inputs ) WORK.push(z);
                for( Node z : x._outputs ) WORK.push(z);
                n.depsClear();
                assert progressOnList(stop); // Very expensive assert
            }
        }
        
        if( show )
            System.out.println(new GraphVisualizer().generateDotOutput(stop,null,null));
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
        Node changed = stop.walk( n -> {
                if( WORK.on(n) ) return null;
                Node m = n.peepholeOpt();
                if( m==null ) return null;
                System.err.println("BREAK HERE FOR BUG");
                return m;
            });
        MID_ASSERT = false;
        return changed==null;
    }

    public static void reset() {
        WORK.clear();
    }
}
