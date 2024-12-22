package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import org.junit.Test;

import static org.junit.Assert.*;

public class Chapter03Test {

    @Test
    public void testVarDecl() {
        Parser parser = new Parser("int a=1; return a;");
        StopNode ret = parser.parse();
        assertEquals("return 1;", ret.print());
    }

    @Test
    public void testVarAdd() {
        Parser parser = new Parser("int a=1; int b=2; return a+b;");
        StopNode ret = parser.parse();
        assertEquals("return 3;", ret.print());
    }

    @Test
    public void testVarScope() {
        Parser parser = new Parser("int a=1; int b=2; int c=0; { int b=3; c=a+b; } return c;");
        StopNode ret = parser.parse();
        assertEquals("return 4;", ret.print());
    }

    @Test
    public void testVarScopeNoPeephole() {
        Parser parser = new Parser("int a=1; int b=2; int !c=0; { int b=3; c=a+b;  } return c; ");
        Node._disablePeephole = true;
        StopNode ret = parser.parse();
        Node._disablePeephole = false;
        assertEquals("return (1+3);", ret.print());
    }

    @Test
    public void testVarDist() {
        Parser parser = new Parser("int x0=1; int y0=2; int x1=3; int y1=4; return (x0-x1)*(x0-x1) + (y0-y1)*(y0-y1); ");
        StopNode ret = parser.parse();
        assertEquals("return 8;", ret.print());
    }

    @Test
    public void testSelfAssign() {
        try {
            new Parser("int a=a; return a;").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("Undefined name 'a'",e.getMessage());
        }
    }

    @Test
    public void testBad1() {
        try {
            new Parser("int a=1; int b=2; int !c=0; { int b=3; c=a+b;").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("Syntax error, expected }: ",e.getMessage());
        }
    }

}
