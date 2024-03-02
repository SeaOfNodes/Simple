package com.seaofnodes.simple;

import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.StopNode;
    
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Random;
import java.util.function.IntSupplier;

// Classic worklist, with a fast add/remove, dup removal, random pull.
public abstract class Iterate {

    public static final Work<Node> WORK = new Work<>();

    public static <N extends Node> N add( N n ) { return (N)WORK.push(n); }
    
    // Iterate peepholes to a fixed point
    public static StopNode iterate(StopNode stop) { return iterate(stop,false); }
    public static StopNode iterate(StopNode stop, boolean show) {
        assert progressOnList(stop);
        int cnt=0;
        
        Node n;
        while( (n=WORK.pop()) != null ) {
            if( n.isDead() )  continue;
            cnt++;              // Useful for debugging, searching which peephole broke things
            Node x = n.iter();
            if( x != null ) {
                if( x.isDead() ) continue;
                if( x._type==null ) x._type = x.compute();
                // Neighbors for worklist
                if( x != n || !(x instanceof ConstantNode) ) {
                    // All outputs of n (changing node) not x (prior existing node).
                    for( Node z : n._outputs ) WORK.push(z);
                    // Non-constants get a free "go again" in case they didn't
                    // get made in their final form.
                    if( !(x instanceof ConstantNode) )
                        WORK.push(x);
                    // If the result is not self, revisit all inputs (because
                    // there's a new user), and replace in the graph.
                    if( x != n ) {
                        for( Node z : n. _inputs ) WORK.push(z);
                        n.subsume(x);
                    }
                }
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
                Node m = n.iter();
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
