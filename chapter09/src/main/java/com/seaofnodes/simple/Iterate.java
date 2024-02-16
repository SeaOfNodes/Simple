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
                assert progressOnList(stop);
            }
        }
        
        if( show )
            System.out.println(new GraphVisualizer().generateDotOutput(stop,null,null));
        return stop;
    }

    private static boolean progressOnList(Node stop) {
        return stop.walk( n -> WORK.on(n) ? null : n.iter() ) == null;
    }

    public static void reset() {
        WORK.clear();
    }
}
