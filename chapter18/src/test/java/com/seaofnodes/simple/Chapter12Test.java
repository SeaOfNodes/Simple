package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import com.seaofnodes.simple.node.StopNode;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Chapter12Test {
    @Test
    public void testJig() {
        CodeGen code = new CodeGen(
"""
return 3.14;
""");
        code.parse().opto();
        assertEquals("return 3.14;", code._stop.toString());
        assertEquals(3.14, Evaluator.evaluate(code._stop,  0));
    }

    @Test
    public void testFloat() {
        CodeGen code = new CodeGen(
"""
return 3.14;
""");
        code.parse().opto();
        assertEquals("return 3.14;", code._stop.toString());
        assertEquals(3.14, Evaluator.evaluate(code._stop,  0));
    }

    @Test
    public void testSquareRoot() {
        CodeGen code = new CodeGen(
"""
flt guess = arg;
while( 1 ) {
    flt next = (arg/guess + guess)/2;
    if( next == guess ) break;
    guess = next;
}
return guess;
""");
        code.parse().opto();
        assertEquals("return Phi(Loop,(flt)arg,(((ToFloat/Phi_guess)+Phi_guess)/2.0f));", code._stop.toString());
        assertEquals(3.0, Evaluator.evaluate(code._stop,  9));
        assertEquals(1.414213562373095, Evaluator.evaluate(code._stop,  2));
    }

    @Test
    public void testFPOps() {
        CodeGen code = new CodeGen(
"""
flt x = arg;
return x+1==x;
""");
        code.parse().opto();
        assertEquals("return ((flt)arg==(ToFloat+1.0f));", code._stop.toString());
        assertEquals(0L, Evaluator.evaluate(code._stop, 1));
    }
}
