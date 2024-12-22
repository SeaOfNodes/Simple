package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import com.seaofnodes.simple.node.StopNode;
import org.junit.Test;
import org.junit.Ignore;

import static org.junit.Assert.*;

public class Chapter18Test {

    @Test
    public void testJig() {
        Parser parser = new Parser(
"""
return 3.14;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 3.14;", stop.toString());
        assertEquals(3.14, Evaluator.evaluate(stop,  0));
    }


    // ---------------------------------------------------------------
    @Test
    public void testType0() {
        Parser parser = new Parser("""
{int -> int}? x2 = null; // null function ptr
return x2;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return null;", stop.toString());
        assertNull( Evaluator.evaluate( stop, 0 ) );
    }

    @Ignore @Test
    public void testFcn0() {
        Parser parser = new Parser("""
{int -> int}? sq = { int x ->
    x*x
};
return sq;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return fcn;", stop.toString());
        assertEquals(1L, Evaluator.evaluate(stop, 0));
    }

}
