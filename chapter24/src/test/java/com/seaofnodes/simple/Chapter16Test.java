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
    public void testLinkedList1() {
        CodeGen code = new CodeGen(
"""
struct LLI { !LLI? !next; int i; };
!LLI? !head = null;
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
struct LLI { !LLI? !next; int i; };
!LLI? !head = null;
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
        !T !t = new T;
        !S !a = new S { x=11; y=7; };
        !S !b = new S { x=22; y=9; };
        !S !p=a;
        if (arg) p=b;
        int before=p.x;
        !S !c = new S {
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
