package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import org.junit.Test;

import static org.junit.Assert.*;

public class Chapter02Test {

    @Test
    public void testParseGrammar() {
        CodeGen code = new CodeGen("return 1+2*3+-5;");
        code.parse(); // No more disable peepholes
        assertEquals("return 2;", code.print());
    }

    @Test
    public void testAddPeephole() {
        CodeGen code = new CodeGen("return 1+2;");
        code.parse();
        assertEquals("return 3;", code.print());
    }

    @Test
    public void testSubPeephole() {
        CodeGen code = new CodeGen("return 1-2;");
        code.parse();
        assertEquals("return -1;", code.print());
    }

    @Test
    public void testMulPeephole() {
        CodeGen code = new CodeGen("return 2*3;");
        code.parse();
        assertEquals("return 6;", code.print());
    }

    @Test
    public void testDivPeephole() {
        CodeGen code = new CodeGen("return 6/3;");
        code.parse();
        assertEquals("return 2;", code.print());
    }

    @Test
    public void testMinusPeephole() {
        CodeGen code = new CodeGen("return 6/-3;");
        code.parse();
        assertEquals("return -2;", code.print());
    }

    @Test
    public void testExample() {
        CodeGen code = new CodeGen("return 1+2*3+-5;");
        code.parse();
        assertEquals("return 2;", code.print());
    }

}
