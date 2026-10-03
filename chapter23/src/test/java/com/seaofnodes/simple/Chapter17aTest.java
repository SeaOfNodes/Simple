package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Chapter17aTest {

    @Test
    public void testFinal1() {
        CodeGen code = new CodeGen(
"""
int x=2, y=3;
if( arg ) { int x = y; x = x*x; y=x; } // Shadow final x
return y;
""");
        code.parse().opto();
        assertEquals("return Phi(Region,9,3);", code.print());
        assertEquals("3", Eval2.eval(code, 0));
        assertEquals("9", Eval2.eval(code, 1));
    }

    @Test
    public void testConstruct1() {
        CodeGen code = new CodeGen("""
struct X { int ~x; };
X z = new X { x=3; };
return z.x;
""");
        code.parse().opto();
        assertEquals("return 3;", code.print());
        assertEquals("3", Eval2.eval(code,  0));
    }

    @Test
    public void testStructFinal0() {
        CodeGen code = new CodeGen("""
struct Point { int ~x, ~y; };
Point p = new Point { x=3; y=4; };
return p;
""");
        code.parse().opto();
        assertEquals("return (const)Point;", code.print());
        assertEquals("Point{x=3,y=4}", Eval2.eval(code,  0));
    }

    @Test
    public void testStructFinal1() {
        CodeGen code = new CodeGen("""
struct Point { int x=3, y=4; };
~Point p = new Point { x=5; y=6; };
p.x++;
return p;
""");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Cannot modify final field 'x'",e.getMessage()); }
    }

    @Test
    public void testStructFinal2() {
        CodeGen code = new CodeGen("""
struct Point { int x=3, y=4; };
~Point p = new Point;
p.x++;
return p;
""");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Cannot modify final field 'x'",e.getMessage()); }
    }

    @Test
    public void testStructFinal3() {
        for( String fields : new String[] { "var x; var y;", "int[] !x;", "int[] !x;" } ) {
            String src = "struct Point { "+fields+" }; Point p = new Point; p.x++; return p;";
            try { new CodeGen(src).parse().opto(); fail(fields); }
            catch( Exception e ) { assertEquals("'Point' is not fully initialized, field 'x' needs to be set in a constructor",e.getMessage()); }
        }
    }

    @Test
    public void testStructFinal4() {
        CodeGen code = new CodeGen("""
struct Point { val x=3; val y=4; };
Point p = new Point;
p.x++;
return p;
""");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Cannot modify final field 'x'",e.getMessage()); }
    }

    @Test
    public void testStructFinal5() {
        CodeGen code = new CodeGen("""
struct Point { var x=3; var y=4; };
!Point !p = new Point;
p.x++;
return p;
""");
        code.parse().opto();
        assertEquals("return Point;", code.print());
        assertEquals("Point{x=4,y=4}", Eval2.eval(code,  0));
    }

}
