package com.seaofnodes.simple;

import org.junit.Test;

import static org.junit.Assert.*;

public class Chapter03Test {

    @Test
    public void testVarDecl() {
        CodeGen code = new CodeGen("int a=1; return a;");
        code.parse(); assertEquals("return 1;", code.print());
    }

    @Test
    public void testVarAdd() {
        CodeGen code = new CodeGen("int a=1; int b=2; return a+b;");
        code.parse();
        assertEquals("return 3;", code.print());
    }

    @Test
    public void testVarScope() {
        CodeGen code = new CodeGen("int a=1; int b=2; int c=0; { int b=3; c=a+b; } return c;");
        code.parse();
        assertEquals("return 4;", code.print());
    }

    @Test
    public void testVarScopeNoPeephole() {
        CodeGen code = new CodeGen("int a=1; int b=2; int !c=0; { int b=3; c=a+b;  } return c; ");
        code.parse(true);
        assertEquals("return (1+3);", code.print());
    }

    @Test
    public void testVarDist() {
        CodeGen code = new CodeGen("int x0=1; int y0=2; int x1=3; int y1=4; return (x0-x1)*(x0-x1) + (y0-y1)*(y0-y1); ");
        code.parse();
        assertEquals("return 8;", code.print());
    }

    @Test
    public void testSelfAssign() {
        try {
            new CodeGen("int a=a; return a;").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("Undefined name 'a'",e.getMessage());
        }
    }

    @Test
    public void testBad1() {
        try {
            new CodeGen("int a=1; int b=2; int !c=0; { int b=3; c=a+b;").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("Syntax error, expected }: ",e.getMessage());
        }
    }

}
