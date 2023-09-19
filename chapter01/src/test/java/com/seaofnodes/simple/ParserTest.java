package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ParserTest {

    @Test
    public void testSimpleProgram() {
        Parser parser = new Parser();
        StartNode startNode = parser.parse("return 1;");
        assertNotNull(startNode);
        assertEquals(2, startNode.nOuts());
        for (int i = 0; i < startNode.nOuts(); i++) {
            switch( startNode.out(i) ) {
            case ReturnNode ret:
                assertEquals(2, ret.nIns());
                assertEquals(startNode, ret.in(0));
                assertTrue(ret.in(1) instanceof ConstantNode);
                break;
            case ConstantNode con:
                assertEquals(1, con._value);
                assertEquals(1, con.nIns());
                assertEquals(startNode, con.in(0));
                break;
            default:
                throw new IllegalStateException();
            }
        }
    }

    @Test
    public void testZero() {
        Parser parser = new Parser();
        StartNode start = parser.parse("return 0;");
        for( Node use : start._outputs )
            if( use instanceof ConstantNode con )
                assertEquals(0,con._value);
    }

    @Test
    public void testBad1() {
        try { new Parser().parse("ret"); }
        catch( RuntimeException e ) { assertEquals("syntax error, expected return: ret",e.getMessage()); }
    }

    @Test
    public void testBad2() {
        try { new Parser().parse("return 0123;"); }
        catch( RuntimeException e ) { assertEquals("syntax error: integer values cannot start with '0'",e.getMessage()); }
    }

    @Test
    public void testBad3() {
        try { new Parser().parse("return --12;"); }
        catch( RuntimeException e ) { assertEquals("syntax error, expected integer literal: --",e.getMessage()); }
    }

    @Test
    public void testBad4() {
        try { new Parser().parse("return 100"); }
        catch( RuntimeException e ) { assertTrue(e.getMessage().contains("syntax error, expected ; got :")); }
    }

    // Negative numbers require unary operator support that is not in scope
    @Test
    public void testBad5() {
        try { new Parser().parse("return -100;"); }
        catch( RuntimeException e ) { assertEquals("syntax error, expected integer literal: -", e.getMessage()); }
    }
}
