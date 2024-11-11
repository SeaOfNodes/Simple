package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import com.seaofnodes.simple.node.StopNode;
import org.junit.Test;
import org.junit.Ignore;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Chapter17Test {

    @Test
    public void testJig() {
        Parser parser = new Parser("""
return 3.14;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 3.14;", stop.toString());
        assertEquals(3.14, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testInc0() {
        Parser parser = new Parser("""
return arg++;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return arg;", stop.toString());
        assertEquals(0L, Evaluator.evaluate(stop, 0));
    }

    @Test
    public void testInc1() {
        Parser parser = new Parser("""
return arg+++arg++;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return ((arg*2)+1);", stop.toString());
        assertEquals(1L, Evaluator.evaluate(stop, 0));
    }

    @Test
    public void testInc2() {
        Parser parser = new Parser("""
//   -(arg--)-(arg--)
return -arg---arg--;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return (-((arg*2)+-1));", stop.toString());
        assertEquals(1L, Evaluator.evaluate(stop, 0));
    }

    @Test
    public void testInc3() {
        Parser parser = new Parser("""
int[] xs = new int[arg];
xs[0]++;
xs[1]++;
return xs[0];
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 1;", stop.toString());
        assertEquals(1L, Evaluator.evaluate(stop, 2));
    }

    @Test
    public void testInc4() {
        Parser parser = new Parser("""
u8[] xs = new u8[1];
xs[0]--;
return xs[0];
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 255;", stop.toString());
        assertEquals(255L, Evaluator.evaluate(stop, 2));
    }

    @Test
    public void testInc5() {
        Parser parser = new Parser("""
struct S { u16 x; };
S s = new S;
s.x--;
return s.x;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 65535;", stop.toString());
        assertEquals(65535L, Evaluator.evaluate(stop, 2));
    }

    @Test
    public void testInc6() {
        Parser parser = new Parser("""
return --arg;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return (arg+-1);", stop.toString());
        assertEquals(-1L, Evaluator.evaluate(stop, 0));
    }


    @Test
    public void testVar0() {
        Parser parser = new Parser("""
var d;
return d;
""");
        try { parser.parse().iterate(); fail(); }
        catch( Exception e ) { assertEquals("Syntax error, expected expression: ;",e.getMessage()); }
    }


    @Test
    public void testTrinary0() {
        Parser parser = new Parser("""
return arg ? 1 : 2;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Region,1,2);", stop.toString());
        assertEquals(2L, Evaluator.evaluate(stop, 0));
        assertEquals(1L, Evaluator.evaluate(stop, 1));
    }

    @Test
    public void testTrinary1() {
        Parser parser = new Parser("""
return arg ? 0 : arg;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 0;", stop.toString());
        assertEquals(0L, Evaluator.evaluate(stop, 0));
    }

    @Test
    public void testTrinary2() {
        Parser parser = new Parser("""
struct Bar { int x; };
var b = arg ? new Bar : null;
return b ? b.x++ + b.x++ : -1;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Region,1,-1);", stop.toString());
        assertEquals(-1L, Evaluator.evaluate(stop, 0));
        assertEquals( 1L, Evaluator.evaluate(stop, 1));
    }

    @Test
    public void testTrinary3() {
        Parser parser = new Parser("""
struct Bar { int x; };
var b = arg ? new Bar;
return b ? b.x++ + b.x++ : -1;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Region,1,-1);", stop.toString());
        assertEquals(-1L, Evaluator.evaluate(stop, 0));
        assertEquals( 1L, Evaluator.evaluate(stop, 1));
    }

    @Test
    public void testTrinary4() {
        // This test case will benefit from an unzipping transformation
        Parser parser = new Parser("""
struct Bar { Bar? next; int x; };
var b = arg ? new Bar { next = (arg==2) ? new Bar{x=2;}; x=1; };
return b ? b.next ? b.next.x : b.x; // parses "b ? (b.next ? b.next.x : b.x) : 0"
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Region,Phi(Region,.x,.x),0);", stop.toString());
        assertEquals( 0L, Evaluator.evaluate(stop, 0));
        assertEquals( 1L, Evaluator.evaluate(stop, 1));
        assertEquals( 2L, Evaluator.evaluate(stop, 2));
    }


    @Test
    public void testFor0() {
        Parser parser = new Parser("""
int sum=0;
for( int i=0; i<arg; i++ )
    sum = sum + i;
return sum;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Loop,0,(Phi_sum+Phi(Loop,0,(Phi_i+1))));", stop.toString());
        assertEquals(3L, Evaluator.evaluate(stop, 3));
        assertEquals(45L, Evaluator.evaluate(stop, 10));
    }
    @Test
    public void testFor1() {
        Parser parser = new Parser("""
int sum=0, i=0;
for( ; i<arg; i++ )
    sum = sum + i;
return sum;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Loop,0,(Phi_sum+Phi(Loop,0,(Phi_i+1))));", stop.toString());
        assertEquals(3L, Evaluator.evaluate(stop, 3));
        assertEquals(45L, Evaluator.evaluate(stop, 10));
    }
    @Test
    public void testFor2() {
        Parser parser = new Parser("""
int sum=0;
for( int i=0; ; i++ ) {
    if( i>=arg ) break;
    sum = sum + i;
}
return sum;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Loop,0,(Phi_sum+Phi(Loop,0,(Phi_i+1))));", stop.toString());
        assertEquals(3L, Evaluator.evaluate(stop, 3));
        assertEquals(45L, Evaluator.evaluate(stop, 10));
    }
    @Test
    public void testFor3() {
        Parser parser = new Parser("""
int sum=0;
for( int i=0; i<arg; )
    sum = sum + i++;
return sum;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Loop,0,(Phi_sum+Phi(Loop,0,(Phi_i+1))));", stop.toString());
        assertEquals(3L, Evaluator.evaluate(stop, 3));
        assertEquals(45L, Evaluator.evaluate(stop, 10));
    }
    @Test
    public void testFor4() {
        Parser parser = new Parser("""
int sum=0;
for( int i=0; i<arg; i++ )
    sum = sum + i;
return i;
""");
        try { parser.parse().iterate(); fail(); }
        catch( Exception e ) { assertEquals("Undefined name 'i'",e.getMessage()); }
    }


    @Test
    public void testLinkedList2() {
        Parser parser = new Parser("""
struct LLI { LLI? next; int i; };
LLI? head = null;
while( arg-- )
    head = new LLI { next=head; i=arg; };
int sum=0;
for( ; head; head = head.next )
    sum = sum + head.i;
return sum;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Loop,0,(Phi_sum+.i));", stop.toString());
        assertEquals(45L, Evaluator.evaluate(stop, 10));
    }

    @Test
    public void sieveOfEratosthenes() {
        Parser parser = new Parser(
"""
val ary = new bool[arg], primes = new int[arg];
var nprimes=0, p=0;
// Find primes while p^2 < arg
for( p=2; p*p < arg; p++ ) {
    // skip marked non-primes
    while( ary[p] ) p++;
    // p is now a prime
    primes[nprimes++] = p;
    // Mark out the rest non-primes
    for( int i = p + p; i < ary#; i = i + p )
        ary[i] = true;
}
// Now just collect the remaining primes, no more marking
for( ; p < arg; p++ )
    if( !ary[p] )
        primes[nprimes++] = p;
// Copy/shrink the result array
var rez = new int[nprimes];
for( int j=0; j<nprimes; j++ )
    rez[j] = primes[j];
return rez;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return [int];", stop.toString());
        Evaluator.Obj obj = (Evaluator.Obj)Evaluator.evaluate(stop, 20);
        assertEquals("[int] {  # :int;   [] :int; }",obj.struct().toString());
        long nprimes = (Long)obj.fields()[0];
        long[] primes = new long[]{2,3,5,7,11,13,17,19};
        for( int i=0; i<nprimes; i++ )
            assertEquals(primes[i],(long)(Long)obj.fields()[i+1]);
    }

}
