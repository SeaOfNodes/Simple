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
        Parser parser = new Parser("return 1;");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 1;", stop.toString());
        assertEquals(1L, Evaluator.evaluate(stop,  0));
    }


    // ---------------------------------------------------------------
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
int[] !xs = new int[arg];
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
u8[] !xs = new u8[1];
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
S !s = new S;
s.x--;
return s.x;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 65535;", stop.toString());
        assertEquals(65535L, Evaluator.evaluate(stop, 2));
    }

    @Test
    public void testInc6() {
        Parser parser = new Parser("return --arg;");
        StopNode stop = parser.parse().iterate();
        assertEquals("return (arg+-1);", stop.toString());
        assertEquals(-1L, Evaluator.evaluate(stop, 0));
    }

    @Test
    public void testInc7() {
        Parser parser = new Parser("u8 x=0; return --x;");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 255;", stop.toString());
        assertEquals(255L, Evaluator.evaluate(stop, 0));
    }

    @Test
    public void testInc8() {
        Parser parser = new Parser("int x; x+=2; return x+=3;");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 5;", stop.toString());
        assertEquals(5L, Evaluator.evaluate(stop, 0));
    }

    // ---------------------------------------------------------------
    @Test public void testVar0() {
        Parser parser = new Parser("var d; return d;");
        try { parser.parse().iterate(); fail(); }
        catch( Exception e ) { assertEquals("Syntax error, expected =expression: ;",e.getMessage()); }
    }

    @Test public void testVar1() {
        Parser parser = new Parser("val d; return d;");
        try { parser.parse().iterate(); fail(); }
        catch( Exception e ) { assertEquals("Syntax error, expected =expression: ;",e.getMessage()); }
    }

    @Test public void testVar2() {
        Parser parser = new Parser("int x; x=3; x++; return x; // Ok, no initializer so x is mutable ");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 4;", stop.toString());
        assertEquals(4L, Evaluator.evaluate(stop, 0));
    }

    @Test public void testVar3() {
        Parser parser = new Parser("int x=3; x++; return x; // Ok, primitive so x is mutable despite initializer ");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 4;", stop.toString());
        assertEquals(4L, Evaluator.evaluate(stop, 0));
    }

    @Test public void testVar4() {
        Parser parser = new Parser("struct S{int x;}; S s; s=new S; s.x++; return s.x; // Ok, no initializer so x is mutable ");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 1;", stop.toString());
        assertEquals(1L, Evaluator.evaluate(stop, 0));
    }

    @Test public void testVar5() {
        Parser parser = new Parser("struct S{int x;}; S s; s=new S{x=3;}; s.x++; return s.x; // Ok, no initializer so x is mutable ");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 4;", stop.toString());
        assertEquals(4L, Evaluator.evaluate(stop, 0));
    }

    @Test public void testVar6() {
        Parser parser = new Parser("struct S{int x;}; S s=new S; s.x++; return s.x; // Error initializer so x is immutable ");
        try { parser.parse().iterate(); fail(); }
        catch( Exception e ) { assertEquals("Cannot modify final field 'x'",e.getMessage()); }
    }

    @Test public void testVar7() {
        Parser parser = new Parser("struct S{int x;}; S s=new S{x=3;}; s.x++; return s.x; // Error initializer so x is immutable ");
        try { parser.parse().iterate(); fail(); }
        catch( Exception e ) { assertEquals("Cannot modify final field 'x'",e.getMessage()); }
    }

    @Test public void testVar8() {
        Parser parser = new Parser("struct S{int x;}; S !s=new S; s.x++; return s.x; // Ok, has '!' so s.x is mutable ");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 1;", stop.toString());
        assertEquals(1L, Evaluator.evaluate(stop, 0));
    }

    @Test public void testVar9() {
        Parser parser = new Parser("struct S{int x;}; var s=new S; s.x++; return s.x; // Ok, has var so s.x is mutable ");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 1;", stop.toString());
        assertEquals(1L, Evaluator.evaluate(stop, 0));
    }

    @Test public void testVar10() {
        Parser parser = new Parser("struct S{int x;}; val s=new S; s.x++; return s.x; // Error, has val so x is immutable ");
        try { parser.parse().iterate(); fail(); }
        catch( Exception e ) { assertEquals("Cannot modify final field 'x'",e.getMessage()); }
    }

    @Test public void testVar11() {
        Parser parser = new Parser("""
struct Bar { int x; };
Bar !bar = new Bar;
bar.x = 3; // Ok, bar is mutable

struct Foo { Bar? !bar; int y; };
Foo !foo = new Foo { bar = bar; };
foo.bar = bar; // Ok foo is mutable
foo.bar.x++;   // Ok foo and foo.bar and foo.bar.x are all mutable

val xfoo = foo; // Throw away mutability
xfoo.bar.x++;   // Error, cannot mutate through xfoo
 """);
        try { parser.parse().iterate(); fail(); }
        catch( Exception e ) { assertEquals("Cannot modify final field 'x'",e.getMessage()); }
    }

    @Test public void testVar12() {
        Parser parser = new Parser("""
struct Bar { int x; };
Bar !bar = new Bar;
bar.x = 3; // Ok, bar is mutable

struct Foo { Bar? !bar; int y; };
Foo !foo = new Foo;
foo.bar = bar; // Ok bar is mutable
foo.bar.x++;   // Ok foo and foo.bar and foo.bar.x are all mutable

val xfoo = foo;        // Throw away mutability
int x4 = xfoo.bar.x;   // Ok to read through xfoo, gets 4
foo.bar.x++;           // Bumps to 5
int x5 = xfoo.bar.x;   // Ok to read through xfoo, gets 5
return x4*10+x5;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 45;", stop.toString());
        assertEquals(45L, Evaluator.evaluate(stop, 0));
    }

    @Test
    public void testVar13() {
        Parser parser = new Parser("""
int i,i++;
""");
        try { parser.parse().iterate(); fail(); }
        catch( Exception e ) { assertEquals("Redefining name 'i'",e.getMessage()); }
    }


    @Test
    public void testVar14() {
        Parser parser = new Parser("""
struct B {};
struct A { B b; };
A x = new A {
    return b; // read before init
    b = new B;
};
""");
        try { parser.parse().iterate(); fail(); }
        catch( Exception e ) { assertEquals("Cannot read uninitialized field 'b'",e.getMessage()); }
    }

    @Test
    public void testVar15() {
        Parser parser = new Parser("""
struct B {};
struct A { B b; };
return new A {
    if (arg) b = new B; // Constructor ends with partial init of b
}.b;
""");
        try { parser.parse().iterate(); fail(); }
        catch( Exception e ) { assertEquals("'A' is not fully initialized, field 'b' needs to be set in a constructor",e.getMessage()); }
    }

    // ---------------------------------------------------------------
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
    public void testTrinary5() {
        Parser parser = new Parser("""
flt f=arg?1:1.2;
return f;   // missing widening
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Region,1.0,1.2);", stop.toString());
        assertEquals(1.2, Evaluator.evaluate(stop,  0));
    }

    // ---------------------------------------------------------------
    @Test
    public void testFor0() {
        Parser parser = new Parser("""
int sum=0;
for( int i=0; i<arg; i++ )
    sum += i;
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
    sum += i;
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
    sum += i;
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
    sum += i++;
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
    sum += i;
return i;
""");
        try { parser.parse().iterate(); fail(); }
        catch( Exception e ) { assertEquals("Undefined name 'i'",e.getMessage()); }
    }
    @Test
    public void testFor5() {
        Parser parser = new Parser("""
for(;;arg++;) {}
""");
        try { parser.parse().iterate(); fail(); }
        catch( Exception e ) { assertEquals("Syntax error, expected Unexpected code after expression: ;",e.getMessage()); }
    }



    // ---------------------------------------------------------------
    @Test
    public void testForward0() {
        Parser parser = new Parser("""
struct A{
    B? f1;
    B? f2;
};
return new A;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return A;", stop.toString());
        assertEquals("Obj<A>{f1=null,f2=null}", Evaluator.evaluate(stop).toString());
    }

    @Test
    public void testForward1() {
        Parser parser = new Parser("""
struct A{
    B?[]? nil_array_of_b;
    B?[]  not_array_of_b = new B?[0];
};
return new A.not_array_of_b;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return (const)[*B?];", stop.toString());
        assertEquals("Obj<[*B?]>{#=0,[]=[]}", Evaluator.evaluate(stop).toString());
    }

    // ---------------------------------------------------------------
    @Test
    public void testLinkedList2() {
        Parser parser = new Parser("""
struct LLI { LLI? next; int i; };
LLI? !head = null;
while( arg-- )
    head = new LLI { next=head; i=arg; };
int sum=0;
var ptr = head; // A read-only ptr, to be assigned from read-only next fields
for( ; ptr; ptr = ptr.next )
    sum += ptr.i;
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
var ary = new bool[arg], primes = new int[arg];
var nprimes=0, p=0;
// Find primes while p^2 < arg
for( p=2; p*p < arg; p++ ) {
    // skip marked non-primes
    while( ary[p] ) p++;
    // p is now a prime
    primes[nprimes++] = p;
    // Mark out the rest non-primes
    for( int i = p + p; i < ary#; i += p )
        ary[i] = true;
}
// Now just collect the remaining primes, no more marking
for( ; p < arg; p++ )
    if( !ary[p] )
        primes[nprimes++] = p;
// Copy/shrink the result array
var !rez = new int[nprimes];
for( int j=0; j<nprimes; j++ )
    rez[j] = primes[j];
return rez;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return [int];", stop.toString());
        Evaluator.Obj obj = (Evaluator.Obj)Evaluator.evaluate(stop, 20);
        assertEquals("[int] {int #; int ![]; }",obj.struct().toString());
        long nprimes = (Long)obj.fields()[0];
        long[] primes = new long[]{2,3,5,7,11,13,17,19};
        for( int i=0; i<nprimes; i++ )
            assertEquals(primes[i],(long)(Long)obj.fields()[i+1]);
    }

}
