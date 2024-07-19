package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import com.seaofnodes.simple.node.StopNode;
import org.junit.Test;


import static org.junit.Assert.assertEquals;

public class Chapter12Test {
    // A placeholder test used to rapidly rotate through fuzzer produced issues
    @Test
    public void testFuzzer() {
        Parser parser = new Parser(
"""
return 0;
""");
        StopNode stop = parser.parse(false).iterate(true);
        assertEquals("return 0;", stop.toString());
        assertEquals(0L, Evaluator.evaluate(stop,  0));
    }

}
