package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import com.seaofnodes.simple.node.StopNode;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Chapter12Test {
    @Test
    public void testJig() {
        Parser parser = new Parser(
"""
return 3.14;
""");
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return 3.14;", stop.toString());
        assertEquals(3.14, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testFloat() {
        Parser parser = new Parser(
"""
return 3.14;
""");
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return 3.14;", stop.toString());
        assertEquals(3.14, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testSquareRoot() {
        Parser parser = new Parser(
"""
flt guess = arg;
while( 1 ) {
    flt next = (arg/guess + guess)/2;
    if( next == guess ) break;
    guess = next;
}
return guess;
""");
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return Phi(Loop9,(flt)arg,(((ToFloat/Phi_guess)+Phi_guess)/2.0));", stop.toString());
        assertEquals(3.0, Evaluator.evaluate(stop,  9));
        assertEquals(1.414213562373095, Evaluator.evaluate(stop,  2));
    }

    @Test
    public void testFPOps() {
        Parser parser = new Parser(
"""
flt x = arg;
return x+1==x;
""");
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return ((flt)arg==(ToFloat+1.0));", stop.toString());
        assertEquals(0L, Evaluator.evaluate(stop, 1));
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
        try { parser.parse(true).iterate(true); fail(); }
        catch( Exception e ) { assertEquals("Might be null accessing 'i'",e.getMessage()); }
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
        StopNode stop = parser.parse(false).iterate(false);
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
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return 17;", stop.toString());
    }

}
