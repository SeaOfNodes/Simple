package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Chapter16Test {

    @Test
    public void testJig() {
        CodeGen code = new CodeGen(
"""
return 3.14;
""");
        code.parse().opto();
        assertEquals("return 3.14;", code.print());
        assertEquals("3.14", Eval2.eval(code,  0));
    }


    @Test
    public void testMulti0() {
        CodeGen code = new CodeGen(
"""
int x, y;
return x+y;
""");
        code.parse().opto();
        assertEquals("return 0;", code.print());
        assertEquals("0", Eval2.eval(code,  0));
    }
    @Test
    public void testMulti1() {
        CodeGen code = new CodeGen(
"""
int x=2, y=x+1;
return x+y;
""");
        code.parse().opto();
        assertEquals("return 5;", code.print());
        assertEquals("5", Eval2.eval(code,  0));
    }

//    @Test
//    public void testFinal0() {
//        CodeGen code = new CodeGen(
//"""
//int !x=2;
//x=3;
//return x;
//""");
//        try { code.parse().opto(); fail(); }
//        catch( Exception e ) { assertEquals("Cannot reassign final 'x'",e.getMessage()); }
//    }

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
    public void testConstruct0() {
        CodeGen code = new CodeGen("""
struct X { int x=3; };
X z = new X;
return z.x;
""");
        code.parse().opto();
        assertEquals("return 3;", code.print());
        assertEquals("3", Eval2.eval(code,  0));
    }

    @Test
    public void testConstruct1() {
        CodeGen code = new CodeGen("""
struct X { int !x; };
X z = new X { x=3; };
return z.x;
""");
        code.parse().opto();
        assertEquals("return 3;", code.print());
        assertEquals("3", Eval2.eval(code,  0));
    }

    @Test
    public void testConstruct2() {
        CodeGen code = new CodeGen("""
struct X { int x=3; };
X z = new X { x = 4; };
return z.x;
""");
        code.parse().opto();
        assertEquals("return 4;", code.print());
        assertEquals("4", Eval2.eval(code,  0));
    }


    @Test
    public void testStructFinal0() {
        CodeGen code = new CodeGen("""
struct Point { int !x, !y; };
Point p = new Point { x=3; y=4; };
return p;
""");
        code.parse().opto();
        assertEquals("return Point;", code.print());
        assertEquals("Point{x=3,y=4}", Eval2.eval(code,  0));
    }

    @Test
    public void testStructFinal1() {
        CodeGen code = new CodeGen("""
struct Point { int x=3, y=4; };
val p = new Point { x=5; y=6; };
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
val p = new Point;
p.x++;
return p;
""");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Cannot modify final field 'x'",e.getMessage()); }
    }

    @Test
    public void testStructFinal3() {
        for( String fields : new String[] { "var x; var y;", "int[] x;", "int[] !x;" } ) {
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
Point !p = new Point;
p.x++;
return p;
""");
        code.parse().opto();
        assertEquals("return Point;", code.print());
        assertEquals("Point{x=4,y=4}", Eval2.eval(code,  0));
    }

    // Same as the Chapter13 test with the same name, but using the new
    // constructor syntax
    @Test
    public void testLinkedList1() {
        CodeGen code = new CodeGen(
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
        code.parse().opto();
        assertEquals("return Phi(Region,0,1,.i);", code.print());
        assertEquals("0", Eval2.eval(code,  0));
        assertEquals("1", Eval2.eval(code,  1));
        assertEquals("2", Eval2.eval(code,  3));
    }

    @Test
    public void testLinkedList2() {
        CodeGen code = new CodeGen(
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
        code.parse().opto();
        assertEquals("return Phi(Region,0,1,.i);", code.print());
        assertEquals("0", Eval2.eval(code,  0));
        assertEquals("1", Eval2.eval(code,  1));
        assertEquals("2", Eval2.eval(code, 11));
    }

    @Test
    public void testSquare() {
        CodeGen code = new CodeGen(
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
        code.parse().opto();
        assertEquals("return Square;", code.print());
        assertEquals("Square{side=3.0,diag=1.7320508075688772}", Eval2.eval(code,  3));
        assertEquals("Square{side=4.0,diag=2.0}", Eval2.eval(code, 4));
    }
    static final String CONSTRUCTOR_MEMORY = """
        struct S { int x; int y; };
        struct T { int z=arg+40; };
        T !t = new T;
        S !a = new S { x=11; y=7; };
        S !b = new S { x=22; y=9; };
        S !p=a;
        if (arg) p=b;
        int before=p.x;
        S !c = new S {
            x=p.x+1;
            { int i=0; while (i<2) { p.y=p.y+1; i=i+1; } }
            y=p.y;
        };
        p.x=33;
        return before*10000+c.x*100+c.y+t.z;
        """;

    @Test public void testConstructorMemory() {
        var code = new CodeGen(CONSTRUCTOR_MEMORY).parse().opto();
        assertEquals("111249",Eval2.eval(code,0));
        assertEquals("222352",Eval2.eval(code,1));
    }
}
