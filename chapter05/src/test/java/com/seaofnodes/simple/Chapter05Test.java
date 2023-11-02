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
#showGraph;
return a;
""");
        StopNode ret = parser.parse(true);
        assertEquals("return Phi(Region17,(arg+2),(arg-3));", ret.toString());
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
        StopNode ret = parser.parse();
        assertEquals("return Phi(Region16,4,3);", ret.toString());
    }
    
    @Test
    public void testChapter5Return2() {
        Parser parser = new Parser("if( arg==1 ) return 3; else return 4; #showGraph;", TypeInteger.BOT);
        StopNode stop = parser.parse();
        assertEquals("Stop[ return 3; return 4; ]", stop.toString());
    }
    
    @Test
    public void testChapter5IfMergeB() {
        Parser parser = new Parser("int a=arg+1; int b=0; if( arg==1 ) b=a; else b=a+1; return a+b;");
        StopNode ret = parser.parse(true);
        assertEquals("return ((arg*2)+Phi(Region20,2,3));", ret.toString());
    }

    @Test
    public void testChapter5IfMerge2() {
        Parser parser = new Parser("int a=arg+1; int b=arg+2; if( arg==1 ) b=b+a; else a=b+1; return a+b;");
        StopNode ret = parser.parse(true);
        assertEquals("return ((Phi(Region31,(arg*2),arg)+arg)+Phi(Region31,4,5));", ret.toString());
    }

    @Test
    public void testChapter5Merge3() {
        Parser parser = new Parser("int a=1; if( arg==1 ) if( arg==2 ) a=2; else a=3; else if( arg==3 ) a=4; else a=5; return a; #showGraph;", TypeInteger.BOT);
        StopNode stop = parser.parse();
        assertEquals("return Phi(Region33,Phi(Region21,2,3),Phi(Region31,4,5));", stop.toString());
    }

    @Test
    public void testChapter5Merge4() {
        Parser parser = new Parser("int a=0;int b=0;if(arg)a=1;if(arg==0)b=2;return arg+a+b;#showGraph;", TypeInteger.BOT);
        StopNode stop = parser.parse();
        assertEquals("return ((arg+Phi(Region13,1,0))+Phi(Region22,2,0));", stop.toString());
    }

    @Test
    public void testChapter5Merge5() {
        Parser parser = new Parser("int a=arg==2;if(arg==1)a=arg==3;return a;");
        StopNode ret = parser.parse(true);
        assertEquals("return (arg==Phi(Region16,3,2));", ret.toString());
    }

    @Test
    public void testChapter5True() {
      StopNode stop = new Parser("return true;").parse();
      assertEquals("return 1;",stop.toString());
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
    public void testChapter5BadNum() {
        try { 
            new Parser("return 1-;").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("Syntax error, expected an identifier or expression: ;",e.getMessage());
        }
    }
      
    @Test
    public void testChapter5Keyword1() {
        try { 
            new Parser("int true=0; return true;").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("Expected an identifier, found 'true'",e.getMessage());
        }
    }
      
    @Test
    public void testChapter5Keyword2() {
        try { 
            new Parser("int else=arg; if(else) else=2; else else=1; return else;").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("Expected an identifier, found 'else'",e.getMessage());
        }
    }

    @Test
        public void testChapter5Keyword3() {
        try { 
            new Parser("int a=1; ififif(arg)inta=2;return a;").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("Syntax error, expected =: (",e.getMessage());
        }
    }
    
    @Test
    public void testChapter4Peephole() {
        Parser parser = new Parser("return 1+arg+2; #showGraph;");
        StopNode ret = parser.parse();
        assertEquals("return (arg+3);", ret.print());
    }

    @Test
    public void testChapter4Peephole2() {
        Parser parser = new Parser("return (1+arg)+2;");
        StopNode ret = parser.parse();
        assertEquals("return (arg+3);", ret.print());
    }

    @Test
    public void testChapter4Add0() {
        Parser parser = new Parser("return 0+arg;");
        StopNode ret = parser.parse();
        assertEquals("return arg;", ret.print());
    }

    @Test
    public void testChapter4AddAddMul() {
        Parser parser = new Parser("return arg+0+arg;");
        StopNode ret = parser.parse();
        assertEquals("return (arg*2);", ret.print());
    }
  
    @Test
    public void testChapter4Peephole3() {
        Parser parser = new Parser("return 1+arg+2+arg+3; #showGraph;");
        StopNode ret = parser.parse();
        assertEquals("return ((arg*2)+6);", ret.print());
    }

    @Test
    public void testChapter4Mul1() {
        Parser parser = new Parser("return 1*arg;");
        StopNode ret = parser.parse();
        assertEquals("return arg;", ret.print());
    }
  
    @Test
    public void testChapter4VarArg() {
        Parser parser = new Parser("return arg; #showGraph;");
        StopNode stop = parser.parse();
        ReturnNode ret = stop.ret();
        assertTrue(ret.in(0) instanceof ProjNode);
        assertTrue(ret.in(1) instanceof ProjNode);
    }

    @Test
    public void testChapter4ConstantArg() {
        Parser parser = new Parser("return arg; #showGraph;", TypeInteger.constant(2));
        StopNode ret = parser.parse();
        assertEquals("return 2;", ret.print());
    }

    @Test
    public void testChapter4CompEq() {
        Parser parser = new Parser("return 3==3; #showGraph;");
        StopNode ret = parser.parse();
        assertEquals("return 1;", ret.print());
    }

    @Test
    public void testChapter4CompEq2() {
        Parser parser = new Parser("return 3==4; #showGraph;");
        StopNode ret = parser.parse();
        assertEquals("return 0;", ret.print());
    }

    @Test
    public void testChapter4CompNEq() {
        Parser parser = new Parser("return 3!=3; #showGraph;");
        StopNode ret = parser.parse();
        assertEquals("return 0;", ret.print());
    }

    @Test
    public void testChapter4CompNEq2() {
        Parser parser = new Parser("return 3!=4; #showGraph;");
        StopNode ret = parser.parse();
        assertEquals("return 1;", ret.print());
    }

    @Test
    public void testChapter4Bug1() {
        Parser parser = new Parser("int a=arg+1; int b=a; b=1; return a+2; #showGraph;");
        StopNode ret = parser.parse();
        assertEquals("return (arg+3);", ret.print());
    }

    @Test
    public void testChapter4Bug2() {
        Parser parser = new Parser("int a=arg+1; a=a; return a; #showGraph;");
        StopNode ret = parser.parse();
        assertEquals("return (arg+1);", ret.print());
    }

    @Test
    public void testChapter4Bug3() {
        try { 
            new Parser("inta=1; return a;").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("Undefined name 'inta'",e.getMessage());
        }
    }

    @Test
    public void testChapter4Bug4() {
        Parser parser = new Parser("return -arg;");
        StopNode ret = parser.parse();
        assertEquals("return (-arg);", ret.print());
    }
    
    @Test
    public void testVarDecl() {
        Parser parser = new Parser("int a=1; return a;");
        StopNode ret = parser.parse();
        assertEquals("return 1;", ret.print());
    }

    @Test
    public void testVarAdd() {
        Parser parser = new Parser("int a=1; int b=2; return a+b;");
        StopNode ret = parser.parse();
        assertEquals("return 3;", ret.print());
    }

    @Test
    public void testVarScope() {
        Parser parser = new Parser("int a=1; int b=2; int c=0; { int b=3; c=a+b; } return c;");
        StopNode ret = parser.parse();
        assertEquals("return 4;", ret.print());
    }

    @Test
    public void testVarScopeNoPeephole() {
        Node._disablePeephole = true;
        Parser parser = new Parser("int a=1; int b=2; int c=0; { int b=3; c=a+b; #showGraph; } return c; #showGraph;");
        StopNode ret = parser.parse();
        Node._disablePeephole = false;
        assertEquals("return (1+3);", ret.print());
    }

    @Test
    public void testVarDist() {
        Parser parser = new Parser("int x0=1; int y0=2; int x1=3; int y1=4; return (x0-x1)*(x0-x1) + (y0-y1)*(y0-y1); #showGraph;");
        StopNode ret = parser.parse();
        assertEquals("return 8;", ret.print());
    }

    @Test
    public void testSelfAssign() {
        try { 
            new Parser("int a=a; return a;").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("Undefined name 'a'",e.getMessage());
        }
    }

    @Test
    public void testChapter2ParseGrammar() {
        Node._disablePeephole = true; // disable peephole so we can observe full graph
        Parser parser = new Parser("return 1+2*3+-5;");
        StopNode ret = parser.parse();
        assertEquals("return (1+((2*3)+(-5)));", ret.print());
        GraphVisualizer gv = new GraphVisualizer();
        System.out.println(gv.generateDotOutput(parser));
        Node._disablePeephole = false;
    }

    @Test
    public void testChapter2AddPeephole() {
        Parser parser = new Parser("return 1+2;");
        StopNode ret = parser.parse();
        assertEquals("return 3;", ret.print());
    }

    @Test
    public void testChapter2SubPeephole() {
        Parser parser = new Parser("return 1-2;");
        StopNode ret = parser.parse();
        assertEquals("return -1;", ret.print());
    }

    @Test
    public void testChapter2MulPeephole() {
        Parser parser = new Parser("return 2*3;");
        StopNode ret = parser.parse();
        assertEquals("return 6;", ret.print());
    }

    @Test
    public void testChapter2DivPeephole() {
        Parser parser = new Parser("return 6/3;");
        StopNode ret = parser.parse();
        assertEquals("return 2;", ret.print());
    }

    @Test
    public void testChapter2MinusPeephole() {
        Parser parser = new Parser("return 6/-3;");
        StopNode ret = parser.parse();
        assertEquals("return -2;", ret.print());
    }

    @Test
    public void testChapter2Example() {
        Parser parser = new Parser("return 1+2*3+-5;");
        StopNode ret = parser.parse();
        assertEquals("return 2;", ret.print());
        GraphVisualizer gv = new GraphVisualizer();
        System.out.println(gv.generateDotOutput(parser));
    }

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

    @Test
    public void testBad6() {
        try {
            new Parser("int a=1; int b=2; int c=0; { int b=3; c=a+b;").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("Syntax error, expected }: ",e.getMessage());
        }
    }
}
