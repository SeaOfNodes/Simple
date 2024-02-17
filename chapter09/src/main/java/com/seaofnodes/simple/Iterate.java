package com.seaofnodes.simple;

import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.StopNode;
    
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Random;
import java.util.function.IntSupplier;

// Classic worklist, with a fast add/remove, dup removal, random pull.
public abstract class Iterate {

    private static final Work<Node> WORK = new Work<>();

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
                if( x != n )
                    n.subsume(x);
                for( Node z : x. _inputs ) WORK.push(z);
                for( Node z : x._outputs ) WORK.push(z);
                n.depsClear(WORK);
                assert progressOnList(stop);
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
