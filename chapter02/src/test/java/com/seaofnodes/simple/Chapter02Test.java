package com.seaofnodes.simple;

import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.ReturnNode;
import com.seaofnodes.simple.node.StartNode;
import com.seaofnodes.simple.type.TypeInteger;
import org.junit.Test;

import static org.junit.Assert.*;

public class Chapter02Test {

    @Test
    public void testChapter2ParseGrammar() {
        Node._disablePeephole = true; // disable peephole so we can observe full graph
        Parser parser = new Parser("return 1+2*3+-5;");
        ReturnNode ret = parser.parse();
        assertEquals("return (1+((2*3)+(-5)));", ret.print());
        GraphVisualizer gv = new GraphVisualizer();
        System.out.println(gv.generateDotOutput(Parser.START));
        Node._disablePeephole = false;
    }

    @Test
    public void testChapter2AddPeephole() {
        Parser parser = new Parser("return 1+2;");
        ReturnNode ret = parser.parse();
        assertEquals("return 3;", ret.print());
    }

    @Test
    public void testChapter2SubPeephole() {
        Parser parser = new Parser("return 1-2;");
        ReturnNode ret = parser.parse();
        assertEquals("return -1;", ret.print());
    }

    @Test
    public void testChapter2MulPeephole() {
        Parser parser = new Parser("return 2*3;");
        ReturnNode ret = parser.parse();
        assertEquals("return 6;", ret.print());
    }

    @Test
    public void testChapter2DivPeephole() {
        Parser parser = new Parser("return 6/3;");
        ReturnNode ret = parser.parse();
        assertEquals("return 2;", ret.print());
    }

    @Test
    public void testChapter2MinusPeephole() {
        Parser parser = new Parser("return 6/-3;");
        ReturnNode ret = parser.parse();
        assertEquals("return -2;", ret.print());
    }

    @Test
    public void testChapter2Example() {
        Parser parser = new Parser("return 1+2*3+-5;");
        ReturnNode ret = parser.parse();
        assertEquals("return 2;", ret.print());
        GraphVisualizer gv = new GraphVisualizer();
        System.out.println(gv.generateDotOutput(Parser.START));
    }

    @Test
    public void testSimpleProgram() {
        Parser parser = new Parser("return 1;");
        ReturnNode ret = parser.parse();
        StartNode start = Parser.START;
        
        assertEquals(start, ret.ctrl());
        Node expr = ret.expr();
        if( expr instanceof ConstantNode con ) {
            assertEquals(start,con.in(0));
            assertEquals(new TypeInteger(1), con._type);
        } else {
            fail();
        }
    }

    @Test
    public void testZero() {
        Parser parser = new Parser("return 0;");
        parser.parse();
        StartNode start = Parser.START;
        for( Node use : start._outputs )
            if( use instanceof ConstantNode con )
                assertEquals(new TypeInteger(0),con._type);
    }

    @Test
    public void testBad1() {
        try { new Parser("ret").parse(); }
        catch( RuntimeException e ) { assertEquals("Syntax error, expected return: ret",e.getMessage()); }
    }

    @Test
    public void testBad2() {
        try { new Parser("return 0123;").parse(); }
        catch( RuntimeException e ) { assertEquals("Syntax error: integer values cannot start with '0'",e.getMessage()); }
    }

    @Test
    public void testBad3() {
        try { new Parser("return --12;").parse(); }
        catch( RuntimeException e ) { assertEquals("Syntax error, expected integer literal: -",e.getMessage()); }
    }

    @Test
    public void testBad4() {
        try { new Parser("return 100").parse(); }
        catch( RuntimeException e ) { assertTrue(e.getMessage().contains("Syntax error, expected ;:")); }
    }

    // Negative numbers require unary operator support that is not in scope
    @Test
    public void testBad5() {
        try { new Parser("return -100;").parse(); }
        catch( RuntimeException e ) { assertEquals("Syntax error, expected integer literal: -", e.getMessage()); }
    }
}
