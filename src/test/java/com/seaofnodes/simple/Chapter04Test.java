package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import org.junit.Test;
import org.junit.Ignore;

import static org.junit.Assert.*;

public class Chapter04Test {

    @Test
    public void testPeephole() {
        CodeGen code = new CodeGen("return 1+arg+2; ");
        code.parse();
        assertEquals("return (arg+3);", code.print());
    }

    @Test
    public void testPeephole2() {
        CodeGen code = new CodeGen("return (1+arg)+2;");
        code.parse();
        assertEquals("return (arg+3);", code.print());
    }

    @Test
    public void testAdd0() {
        CodeGen code = new CodeGen("return 0+arg;");
        code.parse();
        assertEquals("return arg;", code.print());
    }

    @Test
    public void testAddAddMul() {
        CodeGen code = new CodeGen("return arg+0+arg;");
        code.parse();
        assertEquals("return (arg<<1);", code.print());
    }

    @Test
    public void testPeephole3() {
        CodeGen code = new CodeGen("return 1+arg+2+arg+3; ");
        code.parse();
        assertEquals("return ((arg<<1)+6);", code.print());
    }

    @Test
    public void testMul1() {
        CodeGen code = new CodeGen("return 1*arg;");
        code.parse();
        assertEquals("return arg;", code.print());
    }

    @Test
    public void testVarArg() {
        CodeGen code = new CodeGen("return arg; ");
        code.parse();
        assertTrue(code.ctrl() instanceof  FunNode);
        assertTrue(code.expr() instanceof ParmNode);
    }

    // Adding functions means `main` does not constant fold the incoming arg value
    @Ignore @Test
    public void testConstantArg() {
        CodeGen code = new CodeGen("return arg; ", TypeInteger.constant(2), 123L, true).parse();
        assertEquals("return 2;", code.expr().in(1).toString());
    }

    @Test
    public void testCompEq() {
        CodeGen code = new CodeGen("return 3==3; ");
        code.parse();
        assertEquals("return 1;", code.print());
    }

    @Test
    public void testCompEq2() {
        CodeGen code = new CodeGen("return 3==4; ");
        code.parse();
        assertEquals("return 0;", code.print());
    }

    @Test
    public void testCompNEq() {
        CodeGen code = new CodeGen("return 3!=3; ");
        code.parse();
        assertEquals("return 0;", code.print());
    }

    @Test
    public void testCompNEq2() {
        CodeGen code = new CodeGen("return 3!=4; ");
        code.parse();
        assertEquals("return 1;", code.print());
    }

    @Test
    public void testBug1() {
        CodeGen code = new CodeGen("int a=arg+1; int !b=a; b=1; return a+2; ");
        code.parse();
        assertEquals("return (arg+3);", code.print());
    }

    @Test
    public void testBug2() {
        CodeGen code = new CodeGen("int !a=arg+1; a=a; return a; ");
        code.parse();
        assertEquals("return (arg+1);", code.print());
    }

    @Test
    public void testBug3() {
        try { new CodeGen("inta=1; return a;").parse(); fail(); }
        catch( RuntimeException e ) { assertEquals("Undefined name 'inta'",e.getMessage()); }
    }

    @Test
    public void testBug4() {
        CodeGen code = new CodeGen("return -arg;");
        code.parse();
        assertEquals("return (-arg);", code.print());
    }

    @Test
    public void testBug5() {
        CodeGen code = new CodeGen("return arg--2;");
        code.parse();
        assertEquals("return (arg--2);", code.print());
    }
}
