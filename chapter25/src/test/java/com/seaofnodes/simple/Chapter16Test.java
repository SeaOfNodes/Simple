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
struct _X { int x=3; };
_X z = new _X;
return z.x;
""");
        code.parse().opto();
        assertEquals("return 3;", code.print());
        assertEquals("3", Eval2.eval(code,  0));
    }

    @Test
    public void testConstruct2() {
        CodeGen code = new CodeGen("""
struct _X { int x=3; new _X = { int xx -> x=xx; }; };
_X z = new _X(4);
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
struct _LLI { !_LLI? !next; int i; new _LLI = { !_LLI? !n, int ii -> next=n; i=ii; }; };
!_LLI? !head = null;
while( arg ) {
    head = new _LLI(head,arg);
    arg = arg-1;
}
if( !head ) return 0;
_LLI? next = head.next;
if( !next ) return 1;
return next.i;
""");
        code.parse().opto();
        assertEquals("return Phi(Region,1,.i,0);", code.print());
        assertEquals("0", Eval2.eval(code,  0));
        assertEquals("1", Eval2.eval(code,  1));
        assertEquals("2", Eval2.eval(code,  3));
    }

    @Test
    public void testLinkedList2() {
        CodeGen code = new CodeGen(
"""
struct _LLI { !_LLI? !next; int i; new _LLI = { !_LLI? !n, int a ->
    next=n;
    int !tmp=a;
    while( a > 10 ) {
        tmp = tmp + a;
        a = a - 1;
    }
    i=tmp;
}; };
!_LLI? !head = null;
while( arg ) {
    head = new _LLI(head,arg);
    arg = arg-1;
}
if( !head ) return 0;
_LLI? next = head.next;
if( !next ) return 1;
return next.i;
""");
        code.parse().opto();
        assertEquals("return Phi(Region,1,.i,0);", code.print());
        assertEquals("0", Eval2.eval(code,  0));
        assertEquals("1", Eval2.eval(code,  1));
        assertEquals("2", Eval2.eval(code, 11));
    }

    @Test
    public void testSquare() {
        CodeGen code = new CodeGen(
"""
struct _Square {
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
return new _Square;
""");
        code.parse().opto();
        assertEquals("return Test._Square;", code.print());
        assertEquals("Test._Square{side=3.0,diag=1.7320508075688772}", Eval2.eval(code,  3));
        assertEquals("Test._Square{side=4.0,diag=2.0}", Eval2.eval(code, 4));
    }
}
