package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.*;

public class Chapter05Test {

    @Test
    public void testChapter5IfStmt() {
        Parser parser = new Parser("""
int a = 1;
if (arg == 1)
    a = arg+2;
else {
    a = arg-3;
    #showGraph;
}
return a;
#showGraph;
""");
        ReturnNode ret = parser.parse();
        assertEquals("return Phi(Region16,(arg4+2),(arg4-3));", ret.toString());
    }
  
    @Test
    public void testChapter5Test() {
        Parser parser = new Parser("""
int c = 3;
int b = 2;
if (arg == 1) {
    b = 3;
    c = 4;
}
return c;
                """, TypeInteger.BOT);
        ReturnNode ret = parser.parse();
        assertEquals("return Phi(Region15,4,3);", ret.toString());
    }
    
    @Test
    public void testChapter5IfMergeB() {
        Parser parser = new Parser("int a=arg+1; int b=0; if( arg==1 ) b=a; else b=a+1; return a+b; #showGraph;");
        ReturnNode ret = parser.parse();
        assertEquals("return ((arg4+1)+Phi(Region19,(arg4+1),(arg4+2)));", ret.toString());
    }

    @Test
    public void testChapter5IfMerge2() {
        Parser parser = new Parser("int a=arg+1; int b=arg+2; if( arg==1 ) b=b+a; else a=b+1; return a+b; #showGraph;");
        ReturnNode ret = parser.parse();
        assertEquals("return (Phi(Region30,(arg4+1),(arg4+3))+Phi(Region30,((arg4*2)+3),(arg4+2)));", ret.toString());
    }
      
    @Test
    public void testChapter5HalfDef() {
        try { 
            new Parser("if( arg==1 ) int b=2; return b;").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("Cannot define a new name on one arm of an if",e.getMessage());
        }
    }
    
    @Test
    public void testChapter5HalfDef2() {
        try { 
            new Parser("if( arg==1 ) { int b=2; } else { int b=3; } return b;").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("Undefined name 'b'",e.getMessage());
        }
    }
      
    @Test
    public void testChapter4Peephole() {
        Parser parser = new Parser("return 1+arg+2; #showGraph;");
        ReturnNode ret = parser.parse();
        assertEquals("return (arg4+3);", ret.print());
    }

    @Test
    public void testChapter4Peephole2() {
        Parser parser = new Parser("return (1+arg)+2;");
        ReturnNode ret = parser.parse();
        assertEquals("return (arg4+3);", ret.print());
    }

    @Test
    public void testChapter4Add0() {
        Parser parser = new Parser("return 0+arg;");
        ReturnNode ret = parser.parse();
        assertEquals("return arg4;", ret.print());
    }

    @Test
    public void testChapter4AddAddMul() {
        Parser parser = new Parser("return arg+0+arg;");
        ReturnNode ret = parser.parse();
        assertEquals("return (arg4*2);", ret.print());
    }
  
    @Test
    public void testChapter4Peephole3() {
        Parser parser = new Parser("return 1+arg+2+arg+3; #showGraph;");
        ReturnNode ret = parser.parse();
        assertEquals("return ((arg4*2)+6);", ret.print());
    }

    @Test
    public void testChapter4Mul1() {
        Parser parser = new Parser("return 1*arg;");
        ReturnNode ret = parser.parse();
        assertEquals("return arg4;", ret.print());
    }
  
    @Test
    public void testChapter4VarArg() {
        Parser parser = new Parser("return arg; #showGraph;");
        ReturnNode ret = parser.parse();
        assertTrue(ret.in(0) instanceof ProjNode);
        assertTrue(ret.in(1) instanceof ProjNode);
    }

    @Test
    public void testChapter4ConstantArg() {
        Parser parser = new Parser("return arg; #showGraph;", TypeInteger.constant(2));
        ReturnNode ret = parser.parse();
        assertEquals("return 2;", ret.print());
    }

    @Test
    public void testChapter4CompEq() {
        Parser parser = new Parser("return 3==3; #showGraph;");
        ReturnNode ret = parser.parse();
        assertEquals("return 1;", ret.print());
    }

    @Test
    public void testChapter4CompEq2() {
        Parser parser = new Parser("return 3==4; #showGraph;");
        ReturnNode ret = parser.parse();
        assertEquals("return 0;", ret.print());
    }

    @Test
    public void testChapter4CompNEq() {
        Parser parser = new Parser("return 3!=3; #showGraph;");
        ReturnNode ret = parser.parse();
        assertEquals("return 0;", ret.print());
    }

    @Test
    public void testChapter4CompNEq2() {
        Parser parser = new Parser("return 3!=4; #showGraph;");
        ReturnNode ret = parser.parse();
        assertEquals("return 1;", ret.print());
    }

    @Test
    public void testChapter4Bug1() {
        Parser parser = new Parser("int a=arg+1; int b=a; b=1; return a+2; #showGraph;");
        ReturnNode ret = parser.parse();
        assertEquals("return (arg4+3);", ret.print());
    }

    @Test
    public void testChapter4Bug2() {
        Parser parser = new Parser("int a=arg+1; a=a; return a; #showGraph;");
        ReturnNode ret = parser.parse();
        assertEquals("return (arg4+1);", ret.print());
    }

    @Test
    public void testVarDecl() {
        Parser parser = new Parser("int a=1; return a;");
        ReturnNode ret = parser.parse();
        assertEquals("return 1;", ret.print());
    }

    @Test
    public void testVarAdd() {
        Parser parser = new Parser("int a=1; int b=2; return a+b;");
        ReturnNode ret = parser.parse();
        assertEquals("return 3;", ret.print());
    }

    @Test
    public void testVarScope() {
        Parser parser = new Parser("int a=1; int b=2; int c=0; { int b=3; c=a+b; } return c;");
        ReturnNode ret = parser.parse();
        assertEquals("return 4;", ret.print());
    }

    @Test
    public void testVarScopeNoPeephole() {
        Node._disablePeephole = true;
        Parser parser = new Parser("int a=1; int b=2; int c=0; { int b=3; c=a+b; #showGraph; } return c; #showGraph;");
        ReturnNode ret = parser.parse();
        Node._disablePeephole = false;
        assertEquals("return (1+3);", ret.print());
    }

    @Test
    public void testVarDist() {
        Parser parser = new Parser("int x0=1; int y0=2; int x1=3; int y1=4; return (x0-x1)*(x0-x1) + (y0-y1)*(y0-y1); #showGraph;");
        ReturnNode ret = parser.parse();
        assertEquals("return 8;", ret.print());
    }

    @Test
    public void testSelfAssign() {
        try { 
            new Parser("int a=a; return a;").parse();
        } catch( RuntimeException e ) {
            assertEquals("Undefined name 'a'",e.getMessage());
        }
    }


    @Test
    public void testChapter2ParseGrammar() {
        Node._disablePeephole = true; // disable peephole so we can observe full graph
        Parser parser = new Parser("return 1+2*3+-5;");
        ReturnNode ret = parser.parse();
        assertEquals("return (1+((2*3)+(-5)));", ret.print());
        GraphVisualizer gv = new GraphVisualizer();
        System.out.println(gv.generateDotOutput(parser));
        Node._disablePeephole = false;
    }

    @Test
    public void testChapter2AddPeephole() {
        Parser parser = new Parser("return 1+2;");
        ReturnNode ret = parser.parse();
        assertEquals("return 3;", ret.print());
    }

    @Test
    public void testChapter2SubPeephole() {
        Parser parser = new Parser("return 1-2;");
        ReturnNode ret = parser.parse();
        assertEquals("return -1;", ret.print());
    }

    @Test
    public void testChapter2MulPeephole() {
        Parser parser = new Parser("return 2*3;");
        ReturnNode ret = parser.parse();
        assertEquals("return 6;", ret.print());
    }

    @Test
    public void testChapter2DivPeephole() {
        Parser parser = new Parser("return 6/3;");
        ReturnNode ret = parser.parse();
        assertEquals("return 2;", ret.print());
    }

    @Test
    public void testChapter2MinusPeephole() {
        Parser parser = new Parser("return 6/-3;");
        ReturnNode ret = parser.parse();
        assertEquals("return -2;", ret.print());
    }

    @Test
    public void testChapter2Example() {
        Parser parser = new Parser("return 1+2*3+-5;");
        ReturnNode ret = parser.parse();
        assertEquals("return 2;", ret.print());
        GraphVisualizer gv = new GraphVisualizer();
        System.out.println(gv.generateDotOutput(parser));
    }

    @Test
    public void testSimpleProgram() {
        Parser parser = new Parser("return 1;");
        ReturnNode ret = parser.parse();
        StartNode start = Parser.START;
        
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
            if( use instanceof ConstantNode con )
                assertEquals(TypeInteger.constant(0),con._type);
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
    public void testBad2() {
        try { 
            new Parser("return 0123;").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("Syntax error: integer values cannot start with '0'",e.getMessage());
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
