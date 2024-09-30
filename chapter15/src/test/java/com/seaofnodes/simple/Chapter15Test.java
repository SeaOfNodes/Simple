package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import com.seaofnodes.simple.node.StopNode;
import org.junit.Test;
import org.junit.Ignore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Chapter15Test {

    @Test
    public void testJig() {
        Parser parser = new Parser(
"""
return 3.14;
""");
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return 3.14;", stop.toString());
        assertEquals(3.14, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testBasic() {
        Parser parser = new Parser(
"""
int[] is = new int[2];
return is[1];
""");
        StopNode stop = parser.parse(false).iterate(true);
        assertEquals("return 0;", stop.toString());
        assertEquals(0L, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testBasic2() {
        Parser parser = new Parser(
"""
int[] is = new int[2];
int[] is2 = new int[2];
return is[1];
""");
        StopNode stop = parser.parse(false).iterate(true);
        assertEquals("return 0;", stop.toString());
        assertEquals(0L, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testRollingSum() {
        Parser parser = new Parser(
"""
int[] ary = new int[arg];
// Fill [0,1,2,3,4,...]
int i=0;
while( i < ary# ) {
    ary[i] = i;
    i = i+1;
}
// Fill [0,1,3,6,10,...]
i=0;
while( i < ary# - 1 ) {
    ary[i+1] = ary[i+1] + ary[i];
    i = i+1;
}
return ary[1] * 1000 + ary[3]; // 1 * 1000 + 6
""");
        StopNode stop = parser.parse(false).iterate(true);
        assertEquals("return (.[]+(.[]*1000));", stop.toString());
        assertEquals(1006L, Evaluator.evaluate(stop,  4));
    }

    @Test
    public void testBad0() {
        Parser parser = new Parser(
"""
return new flt;
""");
        try { parser.parse(false).iterate(false); fail(); }
        catch( Exception e ) { assertEquals("Cannot allocate a FltBot",e.getMessage()); }
    }

    @Test
    public void testBad1() {
        Parser parser = new Parser(
"""
int is = new int[2];
""");
        try { parser.parse(false).iterate(false); fail(); }
        catch( Exception e ) { assertEquals("Type *[]int is not of declared type int",e.getMessage()); }
    }
}
