package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import org.junit.Test;

import static org.junit.Assert.*;

public class Chapter02Test {

    @Test
    public void testParseGrammar() {
        Parser parser = new Parser("return 1+2*3+-5;");
        Node._disablePeephole = true; // disable peephole so we can observe full graph
        StopNode ret = parser.parse();
        assertEquals("return (1+((2*3)+(-5)));", ret.print());
        Node._disablePeephole = false;
    }

    @Test
    public void testAddPeephole() {
        Parser parser = new Parser("return 1+2;");
        StopNode ret = parser.parse();
        assertEquals("return 3;", ret.print());
    }

    @Test
    public void testSubPeephole() {
        Parser parser = new Parser("return 1-2;");
        StopNode ret = parser.parse();
        assertEquals("return -1;", ret.print());
    }

    @Test
    public void testMulPeephole() {
        Parser parser = new Parser("return 2*3;");
        StopNode ret = parser.parse();
        assertEquals("return 6;", ret.print());
    }

    @Test
    public void testDivPeephole() {
        Parser parser = new Parser("return 6/3;");
        StopNode ret = parser.parse();
        assertEquals("return 2;", ret.print());
    }

    @Test
    public void testMinusPeephole() {
        Parser parser = new Parser("return 6/-3;");
        StopNode ret = parser.parse();
        assertEquals("return -2;", ret.print());
    }

    @Test
    public void testExample() {
        Parser parser = new Parser("return 1+2*3+-5;");
        StopNode ret = parser.parse();
        assertEquals("return 2;", ret.print());
    }

}
