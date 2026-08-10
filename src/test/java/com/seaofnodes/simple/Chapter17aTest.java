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
struct _X { int ~x; new _X = { int xx -> x=xx; }; };
_X z = new _X(3);
return z.x;
""");
        code.parse().opto();
        assertEquals("return 3;", code.print());
        assertEquals("3", Eval2.eval(code,  0));
    }

    @Test
    public void testStructFinal0() {
        CodeGen code = new CodeGen("""
struct _Point { int ~x, ~y; new _Point = { int xx, int yy -> x=xx; y=yy; }; };
_Point p = new _Point(3,4);
return p;
""");
        code.parse().opto();
        assertEquals("return (const)Test._Point;", code.print());
        assertEquals("Test._Point{x=3,y=4}", Eval2.eval(code,  0));
    }

    @Test
    public void testStructFinal1() {
        CodeGen code = new CodeGen("""
struct _Point { int x=3, y=4; new _Point = { int xx, int yy -> x=xx; y=yy; }; };
~_Point p = new _Point(5,6);
p.x++;
return p;
""");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Cannot modify final field 'x'",e.getMessage()); }
    }

    @Test
    public void testStructFinal2() {
        CodeGen code = new CodeGen("""
struct _Point { int x=3, y=4; };
~_Point p = new _Point;
p.x++;
return p;
""");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Cannot modify final field 'x'",e.getMessage()); }
    }

    @Test
    public void testStructFinal3() {
        CodeGen code = new CodeGen("""
struct _Point { var x; var y; };
_Point p = new _Point;
p.x++;
return p;
""");
        try { code.parse().opto(); fail(); }
        catch( Exception e ) { assertEquals("'Test._Point' is not fully initialized, field 'x' needs to be set in a constructor",e.getMessage()); }
    }

    @Test
    public void testStructFinal4() {
        CodeGen code = new CodeGen("""
struct _Point { val x=2; val y=4; };
_Point p = new _Point;
p.x++;
return p;
""");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Cannot modify final field 'x'",e.getMessage()); }
    }

    @Test
    public void testStructFinal5() {
        CodeGen code = new CodeGen("""
struct _Point { var x=3; var y=4; };
!_Point !p = new _Point;
p.x++;
return p;
""");
        code.parse().opto();
        assertEquals("return Test._Point;", code.print());
        assertEquals("Test._Point{x=4,y=4}", Eval2.eval(code,  0));
    }

}
