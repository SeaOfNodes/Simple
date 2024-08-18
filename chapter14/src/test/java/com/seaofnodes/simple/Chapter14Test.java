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
return b;
""");
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return 67;", stop.toString());
        assertEquals(67L, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testU1() {
        Parser parser = new Parser(
"""
bool b = 123;
b = b + 456;// Truncate
u1 c = b;   // No more truncate needed
return c;
""");
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return 1;", stop.toString());
        assertEquals(1L, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testAnd() {
        Parser parser = new Parser(
"""
int b = 123;
b = b+456 & 31;                 // Precedence
return b;
""");
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return 3;", stop.toString());
        assertEquals(3L, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testRefLoad() {
        Parser parser = new Parser(
"""
struct Foo { u1 b; }
Foo f = new Foo;
f.b = 123;
return f.b;
""");
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return 1;", stop.toString());
        assertEquals(1L, Evaluator.evaluate(stop,  0));
    }
}
