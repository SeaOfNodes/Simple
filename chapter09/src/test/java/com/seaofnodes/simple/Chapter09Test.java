package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import org.junit.Assert;
import org.junit.Test;
import org.junit.Ignore;

import static org.junit.Assert.*;

public class Chapter09Test {
    
    @Test
    @Ignore
    public void testJig() {
        Parser parser = new Parser("""
// Insert test case here
""");
        StopNode stop = parser.parse(true).iterate();
    }

    @Test
    public void testChapter9Precedence() {
        Parser parser = new Parser("return 3-1+2;");
        StopNode stop = parser.parse(true).iterate();
        assertEquals("return 4;", stop.toString());
    }

    @Test
    public void testChapter9Swap() {
        Parser parser = new Parser("int v0=1+1+1; return v0;");
        StopNode stop = parser.parse(true).iterate();
        assertEquals("return 3;", stop.toString());
    }
}
