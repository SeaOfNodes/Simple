package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import com.seaofnodes.simple.node.StopNode;
import org.junit.Test;
import org.junit.Ignore;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
   struct S {
     int x;     // default init of 0
     S? x;      // default init of null
     int !x;    // final   init required in constructor
     S x;       //         init required in constructor
     int !x=17; // final   init done now, not in constructor
     S  x = ...;//         init done now, not in constructor
   };

 */

public class Chapter16Test {

    @Test
    public void testJig() {
        Parser parser = new Parser("""
return 3.14;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 3.14;", stop.toString());
        assertEquals(3.14, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testMulti0() {
        Parser parser = new Parser(
"""
int x, y;
return x+y;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 0;", stop.toString());
        assertEquals(0L, Evaluator.evaluate(stop,  0));
    }
    @Test
    public void testMulti1() {
        Parser parser = new Parser(
"""
int x=2, y=x+1;
return x+y;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 5;", stop.toString());
        assertEquals(5L, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testFinal0() {
        Parser parser = new Parser(
"""
int !x=2;
x=3;
return x;
""");
        try { parser.parse().iterate(); fail(); }
        catch( Exception e ) { assertEquals("Cannot reassign final 'x'",e.getMessage()); }
    }

    @Test
    public void testFinal1() {
        Parser parser = new Parser(
"""
int !x=2, y=3;
if( arg ) { int x = y; x = x*x; y=x; } // Shadow final x
return y;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Region18,9,3);", stop.toString());
        assertEquals(3L, Evaluator.evaluate(stop, 0));
        assertEquals(9L, Evaluator.evaluate(stop, 1));
    }


    @Test
    public void testStructFinal() {
        Parser parser = new Parser("""
struct Point { int !x, !y; }
Point p = new Point { x=3; y=4; }
return p;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return P;", stop.toString());
        assertEquals(3.14, Evaluator.evaluate(stop,  0));
    }

}
