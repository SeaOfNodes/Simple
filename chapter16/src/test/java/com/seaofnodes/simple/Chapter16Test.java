package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import com.seaofnodes.simple.node.StopNode;
import org.junit.Test;
import org.junit.Ignore;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Chapter16Test {

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

    @Test
    public void testBasicFinal() {
        Parser parser = new Parser(
"""
struct Point { int !x, !y; }
Point p = new P { x=3; y=4; }
return p;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return P;", stop.toString());
        assertEquals(3.14, Evaluator.evaluate(stop,  0));
    }

}
