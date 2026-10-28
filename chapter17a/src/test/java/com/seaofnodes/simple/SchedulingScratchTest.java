package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import com.seaofnodes.simple.node.StopNode;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class SchedulingScratchTest {
    @Test public void testScratchAcrossCompilations() {
        String src = """
            int[] xs = new int[2];
            xs[0] = arg;
            int sum = 0;
            int i = 0;
            while( i<5 ) {
                int old = xs[arg&1];
                xs[0] = i;
                sum = sum + old;
                i = i + 1;
            }
            return sum;
            """;
        // Each fresh compilation reuses node IDs and the scheduler's scratch.
        for( int round=0; round<3; round++ ) {
            StopNode stop = new Parser(src).parse().iterate();
            assertEquals(6L,Evaluator.evaluate(stop,0));
            assertEquals(0L,Evaluator.evaluate(stop,1));
            assertEquals(8L,Evaluator.evaluate(stop,2));
        }
    }
}
