package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import org.junit.Test;

import static org.junit.Assert.*;

public class Chapter06Test {

    @Test
    public void testPeepholeReturn() {
        CodeGen code = new CodeGen("""
if( true ) return 2;
return 1;
""");
        code.parse().opto();
        assertEquals("return 2;", code.print());
        assertTrue(code.ctrl() instanceof FunNode);
    }

    @Test
    public void testPeepholeRotate() {
        CodeGen code = new CodeGen(
"""
int a = 1;
if (arg)
    a = 2;
return (arg < a) < 3; // Because (arg < a) is a bool/uint1/[0-1], its always less than 3
""");
        code.parse().opto();
        assertEquals("return 1;", code.print());
    }

    @Test
    public void testPeepholeCFG() {
        CodeGen code = new CodeGen(
"""
int a=1;
if( true )
  a=2;
else
  a=3;
return a;
""");
        code.parse().opto();
        assertEquals("return 2;", code.print());
        assertTrue(code.ctrl() instanceof FunNode);
    }

    @Test
    public void testIfIf() {
        CodeGen code = new CodeGen(
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
return b;
""");
        code.parse().opto();
        assertEquals("return Phi(Region,42,5);", code.print());
    }

    @Test
    public void testIfArgIf() {
        CodeGen code = new CodeGen(
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
return b;""");
        code.parse().opto();
        assertEquals("return Phi(Region,2,5);", code.print());
    }

    @Test
    public void testMerge3With2() {
        CodeGen code = new CodeGen(
"""
arg=2;
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
""", TypeInteger.constant(2), 123L).parse().opto();
        assertEquals("return 5;", code.print());
    }

    @Test
    public void testMerge3With1() {
        CodeGen code = new CodeGen(
"""
arg=1;
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
""", TypeInteger.constant(1), 123L).parse().opto();
        assertEquals("return 3;", code.print());
    }

    @Test
    public void testMerge3Peephole() {
        CodeGen code = new CodeGen(
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
""");
        code.parse().opto();
        assertEquals("return Phi(Region,3,5,4);", code.print());
    }

    @Test
    public void testMerge3Peephole1() {
        CodeGen code = new CodeGen(
"""
arg=1;
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
""", TypeInteger.constant(1), 123L);
        code.parse().opto();
        assertEquals("return 3;", code.print());
    }

    @Test
    public void testMerge3Peephole3() {
        CodeGen code = new CodeGen(
"""
arg=3;
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
""", TypeInteger.constant(3), 123L);
        code.parse().opto();
        assertEquals("return 4;", code.print());
    }

    @Test
    public void testDemo1NonConst() {
        CodeGen code = new CodeGen("""
int a = 0;
int b = 1;
if( arg ) {
    a = 2;
    if( arg ) { b = 2; }
    else b = 3;
}
return a+b;
""");
        code.parse().opto();
        assertEquals("return Phi(Region,4,1);", code.print());
    }


    @Test
    public void testDemo1True() {
        CodeGen code = new CodeGen(
"""
arg=1;
int a = 0;
int b = 1;
if( arg ) {
    a = 2;
    if( arg ) { b = 2; }
    else b = 3;
}
return a+b;
""", TypeInteger.constant(1), 123L);
        code.parse().opto();
        assertEquals("return 4;", code.print());
    }

    @Test
    public void testDemo1False() {
        CodeGen code = new CodeGen(
"""
arg=0;
int a = 0;
int b = 1;
if( arg ) {
    a = 2;
    if( arg ) { b = 2; }
    else b = 3;
}
return a+b;
""", TypeInteger.constant(0), 123L);
        code.parse().opto();
        assertEquals("return 1;", code.print());
    }

    @Test
    public void testDemo2NonConst() {
        CodeGen code = new CodeGen("""
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
        code.parse().opto();
        assertEquals("return Phi(Region,6,1,5);", code.print());
    }


    @Test
    public void testDemo2True() {
        CodeGen code = new CodeGen(
"""
arg=1;
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
""", TypeInteger.constant(1), 123L);
        code.parse().opto();
        assertEquals("return 6;", code.print());
    }

    @Test
    public void testDemo2arg2() {
        CodeGen code = new CodeGen(
"""
arg=2;
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
""", TypeInteger.constant(2), 123L);
        code.parse().opto();
        assertEquals("return 5;", code.print());
    }

}
