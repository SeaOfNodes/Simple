package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import com.seaofnodes.simple.node.StopNode;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Chapter16Test {

    @Test
    public void testJig() {
        Parser parser = new Parser(
"""
return 3.14;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 3.14;", stop.toString());
        assertEquals(3.14, Evaluator.evaluate(stop,  0));
    }


    @Test
    public void testMulti0() {
        Parser parser = new Parser(
"""
int x, y;
return x+y;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 0;", stop.toString());
        assertEquals(0L, Evaluator.evaluate(stop,  0));
    }
    @Test
    public void testMulti1() {
        Parser parser = new Parser(
"""
int x=2, y=x+1;
return x+y;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 5;", stop.toString());
        assertEquals(5L, Evaluator.evaluate(stop,  0));
    }

//    @Test
//    public void testFinal0() {
//        Parser parser = new Parser(
//"""
//int !x=2;
//x=3;
//return x;
//""");
//        try { parser.parse().iterate(); fail(); }
//        catch( Exception e ) { assertEquals("Cannot reassign final 'x'",e.getMessage()); }
//    }

    @Test
    public void testFinal1() {
        Parser parser = new Parser(
"""
int x=2, y=3;
if( arg ) { int x = y; x = x*x; y=x; } // Shadow final x
return y;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Region,9,3);", stop.toString());
        assertEquals(3L, Evaluator.evaluate(stop, 0));
        assertEquals(9L, Evaluator.evaluate(stop, 1));
    }

    @Test
    public void testConstruct0() {
        Parser parser = new Parser("""
struct X { int x=3; };
X z = new X;
return z.x;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 3;", stop.toString());
        assertEquals(3L, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testConstruct1() {
        Parser parser = new Parser("""
struct X { int !x; };
X z = new X { x=3; };
return z.x;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 3;", stop.toString());
        assertEquals(3L, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testConstruct2() {
        Parser parser = new Parser("""
struct X { int x=3; };
X z = new X { x = 4; };
return z.x;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 4;", stop.toString());
        assertEquals(4L, Evaluator.evaluate(stop,  0));
    }


    @Test
    public void testStructFinal0() {
        Parser parser = new Parser("""
struct Point { int !x, !y; };
Point p = new Point { x=3; y=4; };
return p;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return (const)Point;", stop.toString());
        assertEquals("Obj<Point>{x=3,y=4}", Evaluator.evaluate(stop,  0).toString());
    }

    @Test
    public void testStructFinal1() {
        Parser parser = new Parser("""
struct Point { int x=3, y=4; };
Point p = new Point { x=5; y=6; };
p.x++;
return p;
""");
        try { parser.parse().iterate(); fail(); }
        catch( Exception e ) { assertEquals("Cannot modify final field 'x'",e.getMessage()); }
    }

    @Test
    public void testStructFinal2() {
        Parser parser = new Parser("""
struct Point { int x=3, y=4; };
Point p = new Point;
p.x++;
return p;
""");
        try { parser.parse().iterate(); fail(); }
        catch( Exception e ) { assertEquals("Cannot modify final field 'x'",e.getMessage()); }
    }

    @Test
    public void testStructFinal3() {
        Parser parser = new Parser("""
struct Point { var x; var y; };
Point p = new Point;
p.x++;
return p;
""");
        try { parser.parse().iterate(); fail(); }
        catch( Exception e ) { assertEquals("Syntax error, expected expression: ;",e.getMessage()); }
    }

    @Test
    public void testStructFinal4() {
        Parser parser = new Parser("""
struct Point { val x=3; val y=4; };
Point p = new Point;
p.x++;
return p;
""");
        try { parser.parse().iterate(); fail(); }
        catch( Exception e ) { assertEquals("Cannot reassign final 'x'",e.getMessage()); }
    }

    @Test
    public void testStructFinal5() {
        Parser parser = new Parser("""
struct Point { var x=3; var y=4; };
Point !p = new Point;
p.x++;
return p;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return Point;", stop.toString());
        assertEquals("Obj<Point>{x=4,y=4}", Evaluator.evaluate(stop,  0).toString());
    }

    // Same as the Chapter13 test with the same name, but using the new
    // constructor syntax
    @Test
    public void testLinkedList1() {
        Parser parser = new Parser(
"""
struct LLI { LLI? next; int i; };
LLI? !head = null;
while( arg ) {
    head = new LLI { next=head; i=arg; };
    arg = arg-1;
}
if( !head ) return 0;
LLI? next = head.next;
if( !next ) return 1;
return next.i;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("Stop[ return 0; return 1; return .i; ]", stop.toString());
        assertEquals(0L, Evaluator.evaluate(stop,  0));
        assertEquals(1L, Evaluator.evaluate(stop,  1));
        assertEquals(2L, Evaluator.evaluate(stop,  3));
    }

    @Test
    public void testLinkedList2() {
        Parser parser = new Parser(
"""
struct LLI { LLI? next; int i; };
LLI? !head = null;
while( arg ) {
    head = new LLI {
        next=head;
        // Any old code in the constructor
        int !tmp=arg;
        while( arg > 10 ) {
            tmp = tmp + arg;
            arg = arg - 1;
        }
        i=tmp;
    };
    arg = arg-1;
}
if( !head ) return 0;
LLI? next = head.next;
if( !next ) return 1;
return next.i;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("Stop[ return 0; return 1; return .i; ]", stop.toString());
        assertEquals(0L, Evaluator.evaluate(stop,  0));
        assertEquals(1L, Evaluator.evaluate(stop,  1));
        assertEquals(2L, Evaluator.evaluate(stop, 11));
    }

    @Test
    public void testSquare() {
        Parser parser = new Parser(
"""
struct Square {
    flt !side = arg;
    // Newtons approximation to the square root, computed in a constructor.
    // The actual allocation will copy in this result as the initial
    // value for 'diag'.
    flt !diag = arg*arg/2;
    while( 1 ) {
        flt next = (side/diag + diag)/2;
        if( next == diag ) break;
        diag = next;
    }
};
return new Square;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return Square;", stop.toString());
        assertEquals("Obj<Square>{side=3.0,diag=1.7320508075688772}", Evaluator.evaluate(stop,  3).toString());
        assertEquals("Obj<Square>{side=4.0,diag=2.0}", Evaluator.evaluate(stop, 4).toString());
    }
}
