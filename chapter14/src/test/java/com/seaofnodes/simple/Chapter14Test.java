package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import com.seaofnodes.simple.node.StopNode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Chapter14Test {

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
    public void testRange() {
        Parser parser = new Parser(
"""
int b;
if( arg ) b=1; else b=0;
int c = 99;
if( b < 0 ) c = -1;
if( b > 2 ) c =  1;
return c;
""");
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return 99;", stop.toString());
        assertEquals(99L, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testU8() {
        Parser parser = new Parser(
"""
u8 b = 123;
b = b + 456;// Truncate
return 67;
""");
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return 67;", stop.toString());
        assertEquals(67, Evaluator.evaluate(stop,  0));
    }

}
