package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Chapter13Test {
    @Test
    public void testJig() {
        CodeGen code = new CodeGen(
"""
return 3.14;
""");
        code.parse().opto();
        assertEquals("return 3.14;", code._stop.toString());
        assertEquals(3.14, Evaluator.evaluate(code._stop,  0));
    }

    @Test
    public void testLinkedList0() {
        CodeGen code = new CodeGen(
"""
struct LLI { LLI? next; int i; };
LLI? !head = null;
while( arg ) {
    LLI !x = new LLI;
    x.next = head;
    x.i = arg;
    head = x;
    arg = arg-1;
}
return head.next.i;
""");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Might be null accessing 'i'",e.getMessage()); }
    }

    @Test
    public void testLinkedList1() {
        CodeGen code = new CodeGen(
"""
struct LLI { LLI? next; int i; };
LLI? !head = null;
while( arg ) {
    LLI !x = new LLI;
    x.next = head;
    x.i = arg;
    head = x;
    arg = arg-1;
}
if( !head ) return 0;
LLI? next = head.next;
if( next==null ) return 1;
return next.i;
""");
        code.parse().opto();
        assertEquals("return Phi(Region,0,1,.i);", code._stop.toString());
        assertEquals(2L, Evaluator.evaluate(code._stop,  3));
    }

    @Test
    public void testCoRecur() {
        CodeGen code = new CodeGen(
"""
struct int0 { int i; flt0? f; };
struct flt0 { flt f; int0? i; };
int0 !i0 = new int0;
i0.i = 17;
flt0 !f0 = new flt0;
f0.f = 3.14;
i0.f = f0;
f0.i = i0;
return f0.i.f.i.i;
""");
        code.parse().opto();
        assertEquals("return 17;", code._stop.toString());
    }

    @Test
    public void testNullRef0() {
        CodeGen code = new CodeGen(
"""
struct N { N? next; int i; };
N n = new N;
return n.next;
""");
        code.parse().opto();
        assertEquals("return null;", code._stop.toString());
    }

    @Test
    public void testNullRef1() {
        CodeGen code = new CodeGen(
"""
struct M { int m; };
struct N { M next; int i; };
N n = new N { next = new M; };
return n.next;
""");
        code.parse().opto();
        assertEquals("return (const)M;", code._stop.toString());
    }

    @Test
    public void testNullRef2() {
        CodeGen code = new CodeGen(
"""
struct M { int m; };
struct N { M next; int i; };
N n = new N { next = null; }
return n.next;
""");
        try { code.parse().opto(); fail(); }
        catch( Exception e ) { assertEquals("Type null is not of declared type *M",e.getMessage()); }
    }

    @Test
    public void testNullRef3() {
        CodeGen code = new CodeGen(
"""
struct N { N? next; int i; };
N !n = new N;
n.i = 3.14;
return n.i;
""");
        try { code.parse().opto(); fail(); }
        catch( Exception e ) { assertEquals("Type 3.14 is not of declared type int",e.getMessage()); }
    }

    @Test
    public void testEmpty() {
        CodeGen code = new CodeGen(
"""
struct S{};
""");
        code.parse().opto();
        assertEquals("return 0;", code._stop.toString());
        assertEquals(0L, Evaluator.evaluate(code._stop,  0));
    }

    @Test
    public void testForwardRef0() {
        CodeGen code = new CodeGen(
"""
struct S1 { S2? s; };
return new S2;
""");
        try { code.parse().opto(); fail(); }
        catch( Exception e ) { assertEquals("Unknown struct type 'S2'",e.getMessage()); }
    }

    @Test
    public void testForwardRef1() {
        CodeGen code = new CodeGen(
"""
struct S1 { S2? s; };
struct S2 { int x; };
return new S1.s=new S2;
""");
        code.parse().opto();
        assertEquals("return S2;", code._stop.toString());
    }

    @Test
    public void testcheckNull() {
        CodeGen code = new CodeGen(
"""
struct I {int i;};
struct P { I? pi; };
P !p1 = new P;
P p2 = new P;
p2.pi = new I;
p2.pi.i = 2;
if (arg) p1 = new P;
return p1.pi.i + 1;
""");
        try { code.parse().opto().typeCheck();  fail(); }
        catch( Exception e ) {  assertEquals("Might be null accessing 'i'",e.getMessage());  }
    }

}
