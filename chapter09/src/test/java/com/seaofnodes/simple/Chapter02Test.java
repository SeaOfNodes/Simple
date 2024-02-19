package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import org.junit.Assert;
import org.junit.Test;
import org.junit.Ignore;

import static org.junit.Assert.*;

public class Chapter02Test {
    
    @Test
    public void testParseGrammar() {
        Parser parser = new Parser("return 1+2*3+-5;");
        Node._disablePeephole = true; // disable peephole so we can observe full graph
        StopNode ret = parser.parse();
        assertEquals("return ((1+(2*3))+(-5));", ret.print());
        GraphVisualizer gv = new GraphVisualizer();
        System.out.println(gv.generateDotOutput(parser));
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
        GraphVisualizer gv = new GraphVisualizer();
        System.out.println(gv.generateDotOutput(parser));
    }
    
    @Test
    public void testBad1() {
        try { 
            new Parser("ret").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("Syntax error, expected =: ",e.getMessage());
        }
    }
    @Test
    public void testNotBad3() {
        // this test used to fail in chapter 1
        assertEquals("return 12;", new Parser("return --12;").parse().print());
    }

    @Test
    public void testBad4() {
        try { 
            new Parser("return 100").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("Syntax error, expected ;: ",e.getMessage());
        }
    }

    @Test
    public void testNotBad5() {
        // this test used to fail in chapter 1
        assertEquals("return -100;", new Parser("return -100;").parse().print());
    }

}
