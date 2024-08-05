package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import com.seaofnodes.simple.node.StopNode;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class SchedulingScratchTest {
    @Test public void testScratchAcrossCompilations() {
        String src = """
            struct Box { int x; };
            Box b = new Box;
            b.x = arg;
            int sum = 0;
            int i = 0;
            while( i<5 ) {
                int old = b.x;
                b.x = i;
                sum = sum + old;
                i = i + 1;
            }
            return sum;
            """;
        // Each fresh compilation reuses node IDs and the scheduler's scratch.
        for( int round=0; round<3; round++ ) {
            StopNode stop = new Parser(src).parse().iterate();
            assertEquals(6L,Evaluator.evaluate(stop,0));
            assertEquals(7L,Evaluator.evaluate(stop,1));
            assertEquals(8L,Evaluator.evaluate(stop,2));
        }
    }
}
