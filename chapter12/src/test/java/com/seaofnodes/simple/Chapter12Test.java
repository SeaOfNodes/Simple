package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import com.seaofnodes.simple.node.StopNode;
import org.junit.Test;


import static org.junit.Assert.assertEquals;

public class Chapter12Test {
    @Test
    public void testFloat() {
        Parser parser = new Parser(
"""
return 3.14;
""");
        StopNode stop = parser.parse(false).iterate(true);
        assertEquals("return 3.14;", stop.toString());
        assertEquals(3.14, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testSquareRoot() {
        Parser parser = new Parser(
"""
flt guess = arg;
while( 1 ) {
    flt next = (arg/guess + guess)/2;
    if( next == guess ) break;
    guess = next;
}
return guess;
""");
        StopNode stop = parser.parse(false).iterate(true);
        assertEquals("return Phi(Loop9,(flt)arg,(((ToFloat/Phi_guess)+Phi_guess)/2.0));", stop.toString());
        assertEquals(3.0, Evaluator.evaluate(stop,  9));
        assertEquals(1.414213562373095, Evaluator.evaluate(stop,  2));
    }

    @Test
    public void testFPOps() {
        Parser parser = new Parser(
"""
flt x = arg;
return x+1==x;
""");
        StopNode stop = parser.parse(false).iterate(true);
        assertEquals("return (((flt)arg+1.0)==ToFloat);", stop.toString());
        assertEquals(0L, Evaluator.evaluate(stop, 1));
    }

}
