package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import org.junit.Test;

import static org.junit.Assert.*;

public class Chapter04Test {

    @Test
    public void testPeephole() {
        Parser parser = new Parser("return 1+arg+2; #showGraph;");
        ReturnNode ret = parser.parse();
        assertEquals("return (arg+3);", ret.print());
    }

    @Test
    public void testPeephole2() {
        Parser parser = new Parser("return (1+arg)+2;");
        ReturnNode ret = parser.parse();
        assertEquals("return (arg+3);", ret.print());
    }

    @Test
    public void testAdd0() {
        Parser parser = new Parser("return 0+arg;");
        ReturnNode ret = parser.parse();
        assertEquals("return arg;", ret.print());
    }

    @Test
    public void testAddAddMul() {
        Parser parser = new Parser("return arg+0+arg;");
        ReturnNode ret = parser.parse();
        assertEquals("return (arg*2);", ret.print());
    }

    @Test
    public void testPeephole3() {
        Parser parser = new Parser("return 1+arg+2+arg+3; #showGraph;");
        ReturnNode ret = parser.parse();
        assertEquals("return ((arg*2)+6);", ret.print());
    }

    @Test
    public void testMul1() {
        Parser parser = new Parser("return 1*arg;");
        ReturnNode ret = parser.parse();
        assertEquals("return arg;", ret.print());
    }

    @Test
    public void testVarArg() {
        Parser parser = new Parser("return arg; #showGraph;", TypeInteger.BOT);
        ReturnNode ret = parser.parse();
        assertTrue(ret.in(0) instanceof ProjNode);
        assertTrue(ret.in(1) instanceof ProjNode);
    }

    @Test
    public void testConstantArg() {
        Parser parser = new Parser("return arg; #showGraph;", TypeInteger.constant(2));
        ReturnNode ret = parser.parse();
        assertEquals("return 2;", ret.print());
    }

    @Test
    public void testCompEq() {
        Parser parser = new Parser("return 3==3; #showGraph;");
        ReturnNode ret = parser.parse();
        assertEquals("return 1;", ret.print());
    }

    @Test
    public void testCompEq2() {
        Parser parser = new Parser("return 3==4; #showGraph;");
        ReturnNode ret = parser.parse();
        assertEquals("return 0;", ret.print());
    }

    @Test
    public void testCompNEq() {
        Parser parser = new Parser("return 3!=3; #showGraph;");
        ReturnNode ret = parser.parse();
        assertEquals("return 0;", ret.print());
    }

    @Test
    public void testCompNEq2() {
        Parser parser = new Parser("return 3!=4; #showGraph;");
        ReturnNode ret = parser.parse();
        assertEquals("return 1;", ret.print());
    }

    @Test
    public void testBug1() {
        Parser parser = new Parser("int a=arg+1; int b=a; b=1; return a+2; #showGraph;");
        ReturnNode ret = parser.parse();
        assertEquals("return (arg+3);", ret.print());
    }

    @Test
    public void testBug2() {
        Parser parser = new Parser("int a=arg+1; a=a; return a; #showGraph;");
        ReturnNode ret = parser.parse();
        assertEquals("return (arg+1);", ret.print());
    }

    @Test
    public void testBug3() {
        try {
            new Parser("inta=1; return a;").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("Undefined name 'inta'",e.getMessage());
        }
    }

    @Test
    public void testBug4() {
        Parser parser = new Parser("return -arg;");
        ReturnNode ret = parser.parse();
        assertEquals("return (-arg);", ret.print());
    }

    @Test
    public void testBug5() {
        Parser parser = new Parser("return arg--2;");
        ReturnNode ret = parser.parse();
        assertEquals("return (arg--2);", ret.print());
    }
}
