package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import com.seaofnodes.simple.node.StopNode;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Chapter13Test {
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
    public void testLinkedList0() {
        Parser parser = new Parser(
"""
struct LLI { LLI? next; int i; }
LLI? head = null;
while( arg ) {
    LLI x = new LLI;
    x.next = head;
    x.i = arg;
    head = x;
    arg = arg-1;
}
return head.next.i;
""");
        try { parser.parse().iterate(); fail(); }
        catch( Exception e ) { assertEquals("Might be null accessing 'next'",e.getMessage()); }
    }

    @Test
    public void testLinkedList1() {
        Parser parser = new Parser(
"""
struct LLI { LLI? next; int i; }
LLI? head = null;
while( arg ) {
    LLI x = new LLI;
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
        StopNode stop = parser.parse().iterate();
        assertEquals("Stop[ return 0; return 1; return .i; ]", stop.toString());
        assertEquals(2L, Evaluator.evaluate(stop,  3));
    }

    @Test
    public void testCoRecur() {
        Parser parser = new Parser(
"""
struct int0 { int i; flt0? f; }
struct flt0 { flt f; int0? i; }
int0 i0 = new int0;
i0.i = 17;
flt0 f0 = new flt0;
f0.f = 3.14;
i0.f = f0;
f0.i = i0;
return f0.i.f.i.i;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 17;", stop.toString());
    }

    @Test
    public void testNullRef0() {
        Parser parser = new Parser(
"""
struct N { N next; int i; }
N n = new N;
return n.next;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return null;", stop.toString());
    }

    @Test
    public void testNullRef1() {
        Parser parser = new Parser(
"""
struct N { N next; int i; }
N n = new N;
n.next = new N;
return n.next;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return N;", stop.toString());
    }

    @Test
    public void testNullRef2() {
        Parser parser = new Parser(
"""
struct N { N next; int i; }
N n = new N;
n.next = null;
return n.next;
""");
        try { parser.parse().iterate(); fail(); }
        catch( Exception e ) { assertEquals("Cannot store null into field *N next",e.getMessage()); }
    }

    @Test
    public void testNullRef3() {
        Parser parser = new Parser(
"""
struct N { N next; int i; }
N n = new N;
n.i = 3.14;
return n.i;
""");
        try { parser.parse().iterate(); fail(); }
        catch( Exception e ) { assertEquals("Cannot store 3.14 into field int i",e.getMessage()); }
    }

    @Test
    public void testEmpty() {
        Parser parser = new Parser(
"""
struct S{};
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 0;", stop.toString());
        assertEquals(0L, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testForwardRef0() {
        Parser parser = new Parser(
"""
struct S1 { S2 s; }
return new S2;
""");
        try { parser.parse().iterate(); fail(); }
        catch( Exception e ) { assertEquals("Unknown struct type 'S2'",e.getMessage()); }
    }

    @Test
    public void testForwardRef1() {
        Parser parser = new Parser(
"""
struct S1 { S2? s; }
struct S2 { int x; }
return new S1.s=new S2;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return S2;", stop.toString());
    }
}
