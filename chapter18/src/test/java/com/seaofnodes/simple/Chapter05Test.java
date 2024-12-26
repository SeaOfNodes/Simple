package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import org.junit.Test;

import static org.junit.Assert.*;

public class Chapter05Test {

    @Test
    public void testIfStmt() {
        CodeGen code = new CodeGen(
"""
int a = 1;
if (arg == 1)
    a = arg+2;
else {
    a = arg-3;
}
return a;
""");
        code.parse().opto();
        assertEquals("return Phi(Region,(arg+2),(arg-3));", code._stop.toString());
    }

    @Test
    public void testTest() {
        CodeGen code = new CodeGen(
"""
int c = 3;
int b = 2;
if (arg == 1) {
    b = 3;
    c = 4;
}
return c;
""");
        code.parse().opto();
        assertEquals("return Phi(Region,4,3);", code._stop.toString());
    }

    @Test
    public void testReturn2() {
        CodeGen code = new CodeGen(
"""
if( arg==1 )
    return 3;
else
    return 4;
""");
        code.parse();
        assertEquals("return Phi(Region,3,4);", code._stop.expr().in(0).in(1).toString());
    }

    @Test
    public void testIfMergeB() {
        CodeGen code = new CodeGen(
"""
int a=arg+1;
int b=0;
if( arg==1 )
    b=a;
else
    b=a+1;
return a+b;""");
        code.parse().opto();
        assertEquals("return ((arg*2)+Phi(Region,2,3));", code._stop.toString());
    }

    @Test
    public void testIfMerge2() {
        CodeGen code = new CodeGen(
"""
int a=arg+1;
int b=arg+2;
if( arg==1 )
    b=b+a;
else
    a=b+1;
return a+b;""");
        code.parse().opto();
        assertEquals("return ((Phi(Region,(arg*2),arg)+arg)+Phi(Region,4,5));", code._stop.toString());
    }

    @Test
    public void testMerge3() {
        CodeGen code = new CodeGen(
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
""");
        code.parse().opto();
        assertEquals("return Phi(Region,Phi(Region,2,3),Phi(Region,4,5));", code._stop.toString());
    }

    @Test
    public void testMerge4() {
        CodeGen code = new CodeGen(
"""
int a=0;
int b=0;
if( arg )
    a=1;
if( arg==0 )
    b=2;
return arg+a+b;
""");
        code.parse().opto();
        assertEquals("return ((arg+Phi(Region,1,0))+Phi(Region,2,0));", code._stop.toString());
    }

    @Test
    public void testMerge5() {
        CodeGen code = new CodeGen(
"""
int a=arg==2;
if( arg==1 )
{
    a=arg==3;
}
return a;
""");
        code.parse().opto();
        assertEquals("return (arg==Phi(Region,3,2));", code._stop.toString());
    }

    @Test
    public void testTrue() {
      StopNode stop = new Parser("return true;").parse();
      assertEquals("return 1;",stop.expr().in(0).in(1).toString());
    }

    @Test
    public void testHalfDef() {
        try {
            new Parser("if( arg==1 ) int b=2; return b;").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("Cannot define a new name on one arm of an if",e.getMessage());
        }
    }

    @Test
    public void testHalfDef2() {
        try {
            new Parser("if( arg==1 ) { int b=2; } else { int b=3; } return b;").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("Undefined name 'b'",e.getMessage());
        }
    }

    @Test
    public void testRegress1() {
        try {
            new Parser("if(arg==2) int a=1; else int b=2; return a;").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("Cannot define a new name on one arm of an if",e.getMessage());
        }
    }


    @Test
    public void testBadNum() {
        try {
            new Parser("return 1-;").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("Syntax error, expected an identifier or expression: ;",e.getMessage());
        }
    }

    @Test
    public void testKeyword1() {
        try {
            new Parser("int true=0; return true;").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("Expected an identifier, found 'true'",e.getMessage());
        }
    }

    @Test
    public void testKeyword2() {
        try {
            new Parser("int else=arg; if(else) else=2; else else=1; return else;").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("Expected an identifier, found 'else'",e.getMessage());
        }
    }

    @Test
    public void testKeyword3() {
        try {
            new Parser("int a=1; ififif(arg)inta=2;return a;").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("Undefined name 'ififif'",e.getMessage());
        }
    }

}
