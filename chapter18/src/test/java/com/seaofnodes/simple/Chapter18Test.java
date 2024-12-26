package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import com.seaofnodes.simple.node.StopNode;
import org.junit.Test;
import org.junit.Ignore;

import static org.junit.Assert.*;

public class Chapter18Test {

    @Test
    public void testJig() {
        CodeGen code = new CodeGen(
"""
0 ;
""");
        code.parse().opto();
        assertEquals("return 0;", code._stop.toString());
        assertEquals(0L, Evaluator.evaluate(code._stop,  0));
    }

        /*
        */

    // ---------------------------------------------------------------
    @Test
    public void testType0() {
        CodeGen code = new CodeGen("""
{int -> int}? x2 = null; // null function ptr
return x2;
""");
        code.parse().opto();
        assertEquals("return null;", code._stop.toString());
        assertNull( Evaluator.evaluate( code._stop, 0 ) );
    }

    @Ignore @Test
    public void testFcn0() {
        CodeGen code = new CodeGen("""
{int -> int}? sq = { int x ->
    x*x
};
return sq;
""");
        code.parse().opto();
        assertEquals("return fcn;", code._stop.toString());
        assertEquals(1L, Evaluator.evaluate(code._stop, 0));
    }

}
