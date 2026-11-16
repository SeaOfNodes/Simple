package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import org.junit.Test;

import static org.junit.Assert.*;

public class Chapter05Test {

    @Test
    public void testIfStmt() {
        Parser parser = new Parser(
"""
int a = 1;
if (arg == 1)
    a = arg+2;
else {
    a = arg-3;

}

return a;""");
        StopNode ret = parser.parse();
        assertEquals("return Phi(Region17,(arg+2),(arg-3));", ret.toString());
    }

    @Test
    public void testTest() {
        Parser parser = new Parser(
"""
int c = 3;
int b = 2;
if (arg == 1) {
    b = 3;
    c = 4;
}
return c;""", TypeInteger.BOT);
        StopNode ret = parser.parse();
        assertEquals("return Phi(Region16,4,3);", ret.toString());
    }

    @Test
    public void testReturn2() {
        Parser parser = new Parser(
"""
if( arg==1 )
    return 3;
else
    return 4;
""", TypeInteger.BOT);
        StopNode stop = parser.parse();
        assertEquals("Stop[ return 3; return 4; ]", stop.toString());
    }

    @Test
    public void testIfMergeB() {
        Parser parser = new Parser(
"""
int a=arg+1;
int b=0;
if( arg==1 )
    b=a;
else
    b=a+1;
return a+b;""");
        StopNode ret = parser.parse();
        assertEquals("return ((arg*2)+Phi(Region20,2,3));", ret.toString());
    }

    @Test
    public void testIfMerge2() {
        Parser parser = new Parser(
"""
int a=arg+1;
int b=arg+2;
if( arg==1 )
    b=b+a;
else
    a=b+1;
return a+b;""");
        StopNode ret = parser.parse();
        assertEquals("return ((Phi(Region31,(arg*2),arg)+arg)+Phi(Region,4,5));", ret.toString());
    }

    @Test
    public void testMerge3() {
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
""", TypeInteger.BOT);
        StopNode stop = parser.parse();
        assertEquals("return Phi(Region33,Phi(Region21,2,3),Phi(Region31,4,5));", stop.toString());
    }

    @Test
    public void testMerge4() {
        Parser parser = new Parser(
"""
int a=0;
int b=0;
if( arg )
    a=1;
if( arg==0 )
    b=2;
return arg+a+b;
""", TypeInteger.BOT);
        StopNode stop = parser.parse();
        assertEquals("return ((arg+Phi(Region13,1,0))+Phi(Region22,2,0));", stop.toString());
    }

    @Test
    public void testMerge5() {
        Parser parser = new Parser(
"""
int a=arg==2;
if( arg==1 )
{
    a=arg==3;
}
return a;""");
        StopNode ret = parser.parse();
        assertEquals("return (arg==Phi(Region16,3,2));", ret.toString());
    }

    @Test
    public void testMergeSameCon() {
        Parser parser = new Parser(
"""
int a=arg;
if( arg==1 )
    a=3;
else
    a=3;
return a;
""");
        StopNode ret = parser.parse();
        assertEquals("return 3;", ret.toString());
    }

    @Test
    public void testTrue() {
      StopNode stop = new Parser("return true;").parse();
      assertEquals("return 1;",stop.toString());
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
            assertEquals("Syntax error, expected =: (",e.getMessage());
        }
    }

    private static <N extends Node> N init(N n) { n._type=n.compute(); return n; }

    @Test public void testDoublingBeforeAndAfterStrengthReduction() {
        for( boolean reduced : new boolean[]{false,true} )
            for( boolean reverse : new boolean[]{false,true} ) {
                new Parser("");
                Node ctrl = init(new ProjNode(Parser.START,0,"Ctrl"));
                RegionNode region = init(new RegionNode(new Node[]{null,ctrl,ctrl}));
                Node p = init(new ConstantNode(TypeInteger.BOT));
                Node i = init(new MinusNode(p));
                Node twice = reduced ? init(new MulNode(p,init(new ConstantNode(TypeInteger.constant(2))))) : init(new AddNode(p,p));
                Node sum = init(new AddNode(i,p));
                PhiNode phi = init(new PhiNode("sum",region,reverse ? sum : twice,reverse ? twice : sum));
                phi.keep();
                Node factored = phi.idealize();
                assertTrue(factored instanceof AddNode);
                assertSame(p,factored.in(2));
                assertTrue(factored.in(1) instanceof PhiNode);
                assertSame(reverse ? i : p,factored.in(1).in(1));
                assertSame(reverse ? p : i,factored.in(1).in(2));
                phi.unkeep();
            }
    }
}
