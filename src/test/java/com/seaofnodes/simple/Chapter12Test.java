package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import com.seaofnodes.simple.node.StopNode;
import com.seaofnodes.simple.type.TypeStruct;
import com.seaofnodes.simple.type.TypeMemPtr;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class Chapter12Test {

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
struct Left { int i; Right? f; }
struct Right { int f; Left? i; }
Left left = new Left;
left.i = 17;
Right right = new Right;
right.f = 314;
left.f = right;
right.i = left;
return right.i.f.i.i;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 17;", stop.toString());
    }

    @Test
    public void testNullReright() {
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
        assertEquals("return new N;", stop.toString());
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
    public void testNullRef4() {
        Parser parser = new Parser("-null-5/null-5");
        try { parser.parse().iterate(); fail(); }
        catch( Exception e ) { assertEquals("Expected an identifier, found 'null'",e.getMessage()); }
    }

    @Test public void testNullRef5() {
        Parser parser = new Parser("return null+42;");
        try { parser.parse().iterate(); fail(); }
        catch( Exception e ) { assertEquals("Cannot 'Add' null",e.getMessage()); }
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
    public void testForwardReright() {
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
        assertEquals("return new S1;", stop.toString());
    }

    @Test
    public void testcheckNull() {
        Parser parser = new Parser(
"""
struct I {int i;}
struct P {I pi;}
P p1 = new P;
P p2 = new P;
p2.pi = new I;
p2.pi.i = 2;
if (arg) p1 = new P;
return p1.pi.i + 1;
""");
        try { parser.parse().iterate();  fail(); }
        catch( Exception e ) {  assertEquals("Might be null accessing 'i'",e.getMessage());  }
    }


    @Test
    public void testShallowRecursiveType() {
        new Parser("struct LLI { LLI? next; int i; } return 0;").parse().iterate();
        TypeStruct l1 = (TypeStruct)Parser.TYPES.get("LLI");
        TypeStruct l0 = ((TypeMemPtr)l1._fields[0]._type)._obj;
        assertEquals("LLI", l0._name);
        assertNull(l0._fields);
        assertEquals(2, l1._fields.length);
        assertSame(l0, l1.meet(l0));
        assertSame(l0, l0.dual());
        assertSame(l0, l0.glb());
    }

    @Test
    public void testUnusedForwardReference() {
        StopNode stop = new Parser("struct Holder { Missing? ref; } Holder h = new Holder; return h.ref;")
            .parse().iterate();
        assertEquals("return null;", stop.toString());
        assertNull(((TypeStruct)Parser.TYPES.get("Missing"))._fields);
    }
}
