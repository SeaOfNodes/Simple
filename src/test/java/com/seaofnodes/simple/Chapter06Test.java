package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import org.junit.Test;

import static org.junit.Assert.*;

public class Chapter06Test {

    @Test
    public void testPeepholeReturn() {
        Parser parser = new Parser(
"""
if( true ) return 2;
return 1;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 2;", stop.toString());
        assertTrue(stop.ret().ctrl() instanceof ProjNode);
    }

    @Test
    public void testPeepholeRotate() {
        Parser parser = new Parser(
"""
int a = 1;
if (arg)
    a = 2;
return (arg < a) < 3;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return ((arg<Phi(Region12,2,1))<3);", stop.toString());
    }

    @Test
    public void testPeepholeCFG() {
        Parser parser = new Parser(
"""
int a=1;
if( true )
  a=2;
else
  a=3;
return a;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 2;", stop.toString());
        assertTrue(stop.ret().ctrl() instanceof ProjNode);
    }

    @Test
    public void testIfIf() {
        Parser parser = new Parser(
"""
int a=1;
if( arg!=1 )
    a=2;
else
    a=3;
int b=4;
if( a==2 )
    b=42;
else
    b=5;
return b;""", TypeInteger.BOT);
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Region32,42,5);", stop.toString());
    }

    @Test
    public void testIfArgIf() {
        Parser parser = new Parser(
"""
int a=1;
if( 1==1 )
    a=2;
else
    a=3;
int b=4;
if( arg==2 )
    b=a;
else
    b=5;
return b;""", TypeInteger.BOT);
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Region29,2,5);", stop.toString());
    }

    @Test
    public void testMerge3With2() {
        Parser parser = new Parser(
"""
int a=1;
if( arg==1 )
    if( arg==2 )
        a=2;
    else
        a=3;
else if( arg==3 )
    a=4;
else
    a=5;
return a;
""", TypeInteger.constant(2));
        StopNode stop = parser.parse();
        assertEquals("return 5;", stop.toString());
    }

    @Test
    public void testMerge3With1() {
        Parser parser = new Parser(
"""
int a=1;
if( arg==1 )
    if( arg==2 )
        a=2;
    else
        a=3;
else if( arg==3 )
    a=4;
else
    a=5;
return a;
""", TypeInteger.constant(1));
        StopNode stop = parser.parse().iterate();
        assertEquals("return 3;", stop.toString());
    }

    @Test
    public void testMerge3Peephole() {
        Parser parser = new Parser(
"""
int a=1;
if( arg==1 )
    if( 1==2 )
        a=2;
    else
        a=3;
else if( arg==3 )
    a=4;
else
    a=5;
return a;
""", TypeInteger.BOT);
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Region37,3,Phi(Region35,4,5));", stop.toString());
    }

    @Test
    public void testMerge3Peephole1() {
        Parser parser = new Parser(
"""
int a=1;
if( arg==1 )
    if( 1==2 )
        a=2;
    else
        a=3;
else if( arg==3 )
    a=4;
else
    a=5;
return a;
""", TypeInteger.constant(1));
        StopNode stop = parser.parse().iterate();
        assertEquals("return 3;", stop.toString());
    }

    @Test
    public void testMerge3Peephole3() {
        Parser parser = new Parser(
"""
int a=1;
if( arg==1 )
    if( 1==2 )
        a=2;
    else
        a=3;
else if( arg==3 )
    a=4;
else
    a=5;
return a;
""", TypeInteger.constant(3));
        StopNode stop = parser.parse().iterate();
        assertEquals("return 4;", stop.toString());
    }

    @Test
    public void testDemo1NonConst() {
        Parser parser = new Parser("""
int a = 0;
int b = 1;
if( arg ) {
    a = 2;
    if( arg ) { b = 2; }
    else b = 3;
}
return a+b;
""");
        StopNode ret = parser.parse().iterate();
        assertEquals("return Phi(Region24,4,1);", ret.toString());
    }


    @Test
    public void testDemo1True() {
        Parser parser = new Parser("""
int a = 0;
int b = 1;
if( arg ) {
    a = 2;
    if( arg ) { b = 2; }
    else b = 3;
}
return a+b;
""", TypeInteger.constant(1));
        StopNode ret = parser.parse().iterate();
        assertEquals("return 4;", ret.toString());
    }

    @Test
    public void testDemo1False() {
        Parser parser = new Parser("""
int a = 0;
int b = 1;
if( arg ) {
    a = 2;
    if( arg ) { b = 2; }
    else b = 3;
}
return a+b;
""", TypeInteger.constant(0));
        StopNode ret = parser.parse().iterate();
        assertEquals("return 1;", ret.toString());
    }

    @Test
    public void testDemo2NonConst() {
        Parser parser = new Parser("""
int a = 0;
int b = 1;
int c = 0;
if( arg ) {
    a = 1;
    if( arg==2 ) { c=2; } else { c=3; }
    if( arg ) { b = 2; }
    else b = 3;
}
return a+b+c;
""");
        StopNode ret = parser.parse().iterate();
        assertEquals("return (Phi(Region35,Phi(Region22,2,3),0)+Phi(Region,3,1));", ret.toString());
    }


    @Test
    public void testDemo2True() {
        Parser parser = new Parser("""
int a = 0;
int b = 1;
int c = 0;
if( arg ) {
    a = 1;
    if( arg==2 ) { c=2; } else { c=3; }
    if( arg ) { b = 2; }
    else b = 3;
}
return a+b+c;
""", TypeInteger.constant(1));
        StopNode ret = parser.parse().iterate();
        assertEquals("return 6;", ret.toString());
    }

    @Test
    public void testDemo2arg2() {
        Parser parser = new Parser("""
int a = 0;
int b = 1;
int c = 0;
if( arg ) {
    a = 1;
    if( arg==2 ) { c=2; } else { c=3; }
    if( arg ) { b = 2; }
    else b = 3;
}
return a+b+c;
""", TypeInteger.constant(2));
        StopNode ret = parser.parse().iterate();
        assertEquals("return 5;", ret.toString());
    }

}
