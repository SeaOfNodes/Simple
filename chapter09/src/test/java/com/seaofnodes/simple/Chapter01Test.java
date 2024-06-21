package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import org.junit.Assert;
import org.junit.Test;
import org.junit.Ignore;

import static org.junit.Assert.*;

public class Chapter01Test {

    @Test
    public void testSimpleProgram() {
        Parser parser = new Parser("return 1;");
        StopNode stop = parser.parse();
        StartNode start = Parser.START;
        ReturnNode ret = (ReturnNode)stop.in(0);

        assertTrue(ret.ctrl() instanceof ProjNode);
        Node expr = ret.expr();
        if( expr instanceof ConstantNode con ) {
            assertEquals(start,con.in(0));
            assertEquals(TypeInteger.constant(1), con._type);
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
            if( use instanceof ConstantNode con && con._type instanceof TypeInteger )
                assertEquals(TypeInteger.constant(0),con._type);
    }


    @Test
    public void testBad2() {
        try {
            new Parser("return 0123;").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("Syntax error: integer values cannot start with '0'",e.getMessage());
        }
    }
}
