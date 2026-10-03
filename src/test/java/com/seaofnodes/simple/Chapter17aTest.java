package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import com.seaofnodes.simple.node.StopNode;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Chapter17aTest {

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
    public void testConstruct1() {
        Parser parser = new Parser("""
struct X { int ~x; };
X z = new X { x=3; };
return z.x;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 3;", stop.toString());
        assertEquals(3L, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testStructFinal0() {
        Parser parser = new Parser("""
struct Point { int ~x, ~y; };
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
p.x=p.x+1;
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
p.x=p.x+1;
return p;
""");
        try { parser.parse().iterate(); fail(); }
        catch( Exception e ) { assertEquals("Cannot modify final field 'x'",e.getMessage()); }
    }

    @Test
    public void testStructFinal3() {
        for( String fields : new String[] { "int ~x; int ~y;", "int[] !x;", "int[] !x;" } ) {
            String src = "struct Point { "+fields+" }; Point p = new Point; p.x=p.x+1; return p;";
            try { new Parser(src).parse().iterate(); fail(fields); }
            catch( Exception e ) { assertEquals("'Point' is not fully initialized, field 'x' needs to be set in a constructor",e.getMessage()); }
        }
    }

    @Test
    public void testStructFinal4() {
        Parser parser = new Parser("""
struct Point { int ~x=3; int ~y=4; };
Point p = new Point;
p.x=p.x+1;
return p;
""");
        try { parser.parse().iterate(); fail(); }
        catch( Exception e ) { assertEquals("Cannot modify final field 'x'",e.getMessage()); }
    }

    @Test
    public void testStructFinal5() {
        Parser parser = new Parser("""
struct Point { int x=3; int y=4; };
!Point !p = new Point;
p.x=p.x+1;
return p;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return Point;", stop.toString());
        assertEquals("Obj<Point>{x=4,y=4}", Evaluator.evaluate(stop,  0).toString());
    }

}
