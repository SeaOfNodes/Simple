package com.seaofnodes.simple;

import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.ReturnNode;
import com.seaofnodes.simple.node.StartNode;
import com.seaofnodes.simple.type.TypeInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Chapter03Test {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void testVarDecl() {
        Parser parser = new Parser("int a=1; return a;");
        ReturnNode ret = parser.parse();
        assertEquals("return 1;", ret.print());
    }

    @Test
    public void testVarAdd() {
        Parser parser = new Parser("int a=1; int b=2; return a+b;");
        ReturnNode ret = parser.parse();
        assertEquals("return 3;", ret.print());
    }

    @Test
    public void testVarScope() {
        Parser parser = new Parser("int a=1; int b=2; int c=0; { int b=3; c=a+b; } return c;");
        ReturnNode ret = parser.parse();
        assertEquals("return 4;", ret.print());
    }

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
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("Syntax error, expected =: ");
        new Parser("ret").parse();
    }

    @Test
    public void testBad2() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("Syntax error: integer values cannot start with '0'");
        new Parser("return 0123;").parse();
    }

    @Test
    public void testNotBad3() {
        // this test used to fail in chapter 1
        assertEquals("return 12;", new Parser("return --12;").parse().print());
    }

    @Test
    public void testBad4() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("Syntax error, expected ;:");
        new Parser("return 100").parse();
    }

    @Test
    public void testNotBad5() {
        // this test used to fail in chapter 1
        assertEquals("return -100;", new Parser("return -100;").parse().print());
    }
}
