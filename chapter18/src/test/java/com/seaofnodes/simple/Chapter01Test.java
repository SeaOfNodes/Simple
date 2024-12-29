package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import org.junit.Test;

import static org.junit.Assert.*;

public class Chapter01Test {

    @Test
    public void testSimpleProgram() {
        CodeGen code = new CodeGen("return 1;").parse();
        Node expr = code.expr();
        if( expr instanceof ConstantNode con ) {
            assertEquals(code._start,con.in(0));
            assertEquals(TypeInteger.constant(1), con._type);
        } else {
            fail();
        }
    }

    @Test
    public void testZero() {
        CodeGen code = new CodeGen("return 0;");
        code.parse();
        for( Node use : code._start._outputs )
            if( use instanceof ConstantNode con && con._type instanceof TypeInteger )
                assertEquals(TypeInteger.constant(0),con._type);
    }

    @Test
    public void testBad1() {
        try {
            new CodeGen("ret").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("Undefined name 'ret'",e.getMessage());
        }
    }

    @Test
    public void testBad2() {
        try {
            new CodeGen("return 0123;").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("Syntax error: integer values cannot start with '0'",e.getMessage());
        }
    }

    @Test
    public void testNotBad3() {
        // this test used to fail in chapter 1
        assertEquals("return 12;", new CodeGen("return - -12;").parse()._stop.print());
    }

    @Test
    public void testBad4() {
        try {
            new CodeGen("return 100").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("Syntax error, expected ;: ",e.getMessage());
        }
    }

    @Test
    public void testNotBad5() {
        // this test used to fail in chapter 1
        assertEquals("return -100;", new CodeGen("return -100;").parse()._stop.print());
    }

    @Test
    public void testBad6() {
        try {
            new CodeGen("return100").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("Undefined name 'return100'",e.getMessage());
        }
    }

    @Test
    public void testBad7() {
        try {
            new CodeGen("return 1;}").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("Syntax error, unexpected: }",e.getMessage());
        }
    }

}
