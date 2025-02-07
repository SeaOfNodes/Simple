package com.seaofnodes.simple;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class SchedulingScratchTest {
    @Test public void testScratchAcrossCompilations() {
        String src = """
            int[] !xs = new int[2];
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
            CodeGen code = new CodeGen(src).driver(CodeGen.Phase.TypeCheck);
            // A small graph after many earlier node allocations.
            if( round==0 ) code._uid += 100_000;
            assertEquals("6",Eval2.eval(code,0));
            assertEquals("0",Eval2.eval(code,1));
            assertEquals("8",Eval2.eval(code,2));
        }
    }
}
