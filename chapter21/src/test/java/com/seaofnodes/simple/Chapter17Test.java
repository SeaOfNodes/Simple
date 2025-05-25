package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Chapter17Test {

    @Test
    public void testJig() {
        CodeGen code = new CodeGen(
"""
return 1;
""");
        code.parse().opto().typeCheck();
        assertEquals("return 1;", code.print());
        assertEquals("1", Eval2.eval(code,  0));
    }


    // ---------------------------------------------------------------
    @Test
    public void testInc0() {
        CodeGen code = new CodeGen("""
return arg++;
""");
        code.parse().opto();
        assertEquals("return arg;", code.print());
        assertEquals("0", Eval2.eval(code, 0));
    }

    @Test
    public void testInc1() {
        CodeGen code = new CodeGen("""
return arg+++arg++;
""");
        code.parse().opto();
        assertEquals("return ((arg<<1)+1);", code.print());
        assertEquals("1", Eval2.eval(code, 0));
    }

    @Test
    public void testInc2() {
        CodeGen code = new CodeGen("""
//   -(arg--)-(arg--)
return -arg---arg--;
""");
        code.parse().opto();
        assertEquals("return (-((arg<<1)+-1));", code.print());
        assertEquals("1", Eval2.eval(code, 0));
    }

    @Test
    public void testInc3() {
        CodeGen code = new CodeGen("""
int[] !xs = new int[arg];
xs[0]++;
xs[1]++;
return xs[0];
""");
        code.parse().opto();
        assertEquals("return 1;", code.print());
        assertEquals("1", Eval2.eval(code, 2));
    }

    @Test
    public void testInc4() {
        CodeGen code = new CodeGen("""
u8[] !xs = new u8[1];
xs[0]--;
return xs[0];
""");
        code.parse().opto();
        assertEquals("return 255;", code.print());
        assertEquals("255", Eval2.eval(code, 2));
    }

    @Test
    public void testInc5() {
        CodeGen code = new CodeGen("""
struct S { u16 x; };
S !s = new S;
s.x--;
return s.x;
""");
        code.parse().opto();
        assertEquals("return 65535;", code.print());
        assertEquals("65535", Eval2.eval(code, 2));
    }

    @Test
    public void testInc6() {
        CodeGen code = new CodeGen("return --arg;");
        code.parse().opto();
        assertEquals("return (arg+-1);", code.print());
        assertEquals("-1", Eval2.eval(code, 0));
    }

    @Test
    public void testInc7() {
        CodeGen code = new CodeGen("u8 x=0; return --x;");
        code.parse().opto();
        assertEquals("return 255;", code.print());
        assertEquals("255", Eval2.eval(code, 0));
    }

    @Test
    public void testInc8() {
        CodeGen code = new CodeGen("int x; x+=2; return x+=3;");
        code.parse().opto();
        assertEquals("return 5;", code.print());
        assertEquals("5", Eval2.eval(code, 0));
    }

    @Test
    public void testInc9() {
        CodeGen code = new CodeGen("flt x = 1.5; x++; return x;");
        code.parse().opto();
        assertEquals("return 2.5f;", code.print());
        assertEquals("2.5", Eval2.eval(code, 0));
    }

    @Test public void testInt10() {
        CodeGen code = new CodeGen("flt x = 1.5; x++; int i = x; return i;");
        try { code.parse().opto(); fail(); }
        catch( Exception e ) { assertEquals("Type 2.5f is not of declared type int",e.getMessage()); }
    }


    // ---------------------------------------------------------------
    @Test public void testVar0() {
        CodeGen code = new CodeGen("var d; return d;");
        try { code.parse().opto(); fail(); }
        catch( Exception e ) { assertEquals("Syntax error, expected =expression: ;",e.getMessage()); }
    }

    @Test public void testVar1() {
        CodeGen code = new CodeGen("val d; return d;");
        try { code.parse().opto(); fail(); }
        catch( Exception e ) { assertEquals("Syntax error, expected =expression: ;",e.getMessage()); }
    }

    @Test public void testVar2() {
        CodeGen code = new CodeGen("int x; x=3; x++; return x; // Ok, no initializer so x is mutable ");
        code.parse().opto();
        assertEquals("return 4;", code.print());
        assertEquals("4", Eval2.eval(code, 0));
    }

    @Test public void testVar3() {
        CodeGen code = new CodeGen("int x=3; x++; return x; // Ok, primitive so x is mutable despite initializer ");
        code.parse().opto();
        assertEquals("return 4;", code.print());
        assertEquals("4", Eval2.eval(code, 0));
    }

    @Test public void testVar4() {
        CodeGen code = new CodeGen("struct S{int x;}; S? s; s=new S; s.x++; return s.x; // Ok, no initializer so x is mutable ");
        code.parse().opto();
        assertEquals("return 1;", code.print());
        assertEquals("1", Eval2.eval(code, 0));
    }

    @Test public void testVar5() {
        CodeGen code = new CodeGen("struct S{int x;}; S? s; s=new S{x=3;}; s.x++; return s.x; // Ok, no initializer so x is mutable ");
        code.parse().opto();
        assertEquals("return 4;", code.print());
        assertEquals("4", Eval2.eval(code, 0));
    }

    @Test public void testVar6() {
        CodeGen code = new CodeGen("struct S{int x;}; S s=new S; s.x++; return s.x; // Error initializer so x is immutable ");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Cannot modify final field 'x'",e.getMessage()); }
    }

    @Test public void testVar7() {
        CodeGen code = new CodeGen("struct S{int x;}; S s=new S{x=3;}; s.x++; return s.x; // Error initializer so x is immutable ");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Cannot modify final field 'x'",e.getMessage()); }
    }

    @Test public void testVar8() {
        CodeGen code = new CodeGen("struct S{int x;}; S !s=new S; s.x++; return s.x; // Ok, has '!' so s.x is mutable ");
        code.parse().opto();
        assertEquals("return 1;", code.print());
        assertEquals("1", Eval2.eval(code, 0));
    }

    @Test public void testVar9() {
        CodeGen code = new CodeGen("struct S{int x;}; var s=new S; s.x++; return s.x; // Ok, has var so s.x is mutable ");
        code.parse().opto();
        assertEquals("return 1;", code.print());
        assertEquals("1", Eval2.eval(code, 0));
    }

    @Test public void testVar10() {
        CodeGen code = new CodeGen("struct S{int x;}; val s=new S; s.x++; return s.x; // Error, has val so x is immutable ");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Cannot modify final field 'x'",e.getMessage()); }
    }

    @Test public void testVar11() {
        CodeGen code = new CodeGen("""
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
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Cannot modify final field 'x'",e.getMessage()); }
    }

    @Test public void testVar12() {
        CodeGen code = new CodeGen("""
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
        code.parse().opto();
        assertEquals("return 45;", code.print());
        assertEquals("45", Eval2.eval(code, 0));
    }

    @Test
    public void testVar13() {
        CodeGen code = new CodeGen("""
int i,i++;
""");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Redefining name 'i'",e.getMessage()); }
    }


    @Test
    public void testVar14() {
        CodeGen code = new CodeGen("""
struct B {};
struct A { B b; };
A x = new A {
    return b; // read before init
    b = new B;
};
""");
        try { code.parse().opto(); fail(); }
        catch( Exception e ) { assertEquals("Cannot read uninitialized field 'b'",e.getMessage()); }
    }

    @Test
    public void testVar15() {
        CodeGen code = new CodeGen("""
struct B {};
struct A { B b; };
return new A {
    if (arg) b = new B; // Constructor ends with partial init of b
}.b;
""");
        try { code.parse().opto(); fail(); }
        catch( Exception e ) { assertEquals("'A' is not fully initialized, field 'b' needs to be set in a constructor",e.getMessage()); }
    }

    @Test
    public void testVar16() {
        CodeGen code = new CodeGen(
"""
struct S{};
val x = 1;
val s = new S{x = 2;};
return x;
"""
);
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Cannot reassign final 'x'",e.getMessage()); }
    }

    @Test
    public void testVar17() {
        CodeGen code = new CodeGen(
"""
struct Person {
    bool focused;
};
val focus = { Person p ->
    p.focused = true;
};
Person me; // Person !me = new Person;
focus(me);
return me;
"""
);
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Syntax error, expected =expression: ;",e.getMessage()); }
    }

    // ---------------------------------------------------------------
    @Test
    public void testTrinary0() {
        CodeGen code = new CodeGen("""
return arg ? 1 : 2;
""");
        code.parse().opto();
        assertEquals("return Phi(Region,1,2);", code.print());
        assertEquals("2", Eval2.eval(code, 0));
        assertEquals("1", Eval2.eval(code, 1));
    }

    @Test
    public void testTrinary1() {
        CodeGen code = new CodeGen("""
return arg ? 0 : arg;
""");
        code.parse().opto();
        assertEquals("return 0;", code.print());
        assertEquals("0", Eval2.eval(code, 0));
    }

    @Test
    public void testTrinary2() {
        CodeGen code = new CodeGen("""
struct Bar { int x; };
var b = arg ? new Bar : null;
return b ? b.x++ + b.x++ : -1;
""");
        code.parse().opto();
        assertEquals("return Phi(Region,((.x<<1)+1),-1);", code.print());
        assertEquals("-1", Eval2.eval(code, 0));
        assertEquals("1", Eval2.eval(code, 1));
    }

    @Test
    public void testTrinary3() {
        CodeGen code = new CodeGen("""
struct Bar { int x; };
var b = arg ? new Bar;
return b ? b.x++ + b.x++ : -1;
""");
        code.parse().opto();
        assertEquals("return Phi(Region,((.x<<1)+1),-1);", code.print());
        assertEquals("-1", Eval2.eval(code, 0));
        assertEquals("1", Eval2.eval(code, 1));
    }

    @Test
    public void testTrinary4() {
        // This test case will benefit from an unzipping transformation
        CodeGen code = new CodeGen("""
struct Bar { Bar? next; int x; };
var b = arg ? new Bar { next = (arg==2) ? new Bar{x=2;}; x=1; };
return b ? b.next ? b.next.x : b.x; // parses "b ? (b.next ? b.next.x : b.x) : 0"
""");
        code.parse().opto();
        assertEquals("return Phi(Region,.x,0);", code.print());
        assertEquals("0", Eval2.eval(code, 0));
        assertEquals("1", Eval2.eval(code, 1));
        assertEquals("2", Eval2.eval(code, 2));
    }

    @Test
    public void testTrinary5() {
        CodeGen code = new CodeGen("""
flt f=arg?1:1.2;
return f;   // missing widening
""");
        code.parse().opto();
        assertEquals("return Phi(Region,1.0f,1.2);", code.print());
        assertEquals("1.2", Eval2.eval(code,  0));
    }


    @Test
    public void testTrinary6() {
        CodeGen code = new CodeGen("""
struct S{};
return arg ? 7 : new S;
""");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("No common type amongst int and reference",e.getMessage()); }
    }

    // ---------------------------------------------------------------
    @Test
    public void testFor0() {
        CodeGen code = new CodeGen("""
int sum=0;
for( int i=0; i<arg; i++ )
    sum += i;
return sum;
""");
        code.parse().opto();
        assertEquals("return Phi(Loop,0,(Phi_sum+Phi(Loop,0,(Phi_i+1))));", code.print());
        assertEquals("3", Eval2.eval(code, 3));
        assertEquals("45", Eval2.eval(code, 10));
    }
    @Test
    public void testFor1() {
        CodeGen code = new CodeGen("""
int sum=0, i=0;
for( ; i<arg; i++ )
    sum += i;
return sum;
""");
        code.parse().opto();
        assertEquals("return Phi(Loop,0,(Phi_sum+Phi(Loop,0,(Phi_i+1))));", code.print());
        assertEquals("3", Eval2.eval(code, 3));
        assertEquals("45", Eval2.eval(code, 10));
    }
    @Test
    public void testFor2() {
        CodeGen code = new CodeGen("""
int sum=0;
for( int i=0; ; i++ ) {
    if( i>=arg ) break;
    sum += i;
}
return sum;
""");
        code.parse().opto();
        assertEquals("return Phi(Loop,0,(Phi_sum+Phi(Loop,0,(Phi_i+1))));", code.print());
        assertEquals("3", Eval2.eval(code, 3));
        assertEquals("45", Eval2.eval(code, 10));
    }
    @Test
    public void testFor3() {
        CodeGen code = new CodeGen("""
int sum=0;
for( int i=0; i<arg; )
    sum += i++;
return sum;
""");
        code.parse().opto();
        assertEquals("return Phi(Loop,0,(Phi_sum+Phi(Loop,0,(Phi_i+1))));", code.print());
        assertEquals("3", Eval2.eval(code, 3));
        assertEquals("45", Eval2.eval(code, 10));
    }
    @Test
    public void testFor4() {
        CodeGen code = new CodeGen("""
int sum=0;
for( int i=0; i<arg; i++ )
    sum += i;
return i;
""");
        try { code.parse().opto(); fail(); }
        catch( Exception e ) { assertEquals("Undefined name 'i'",e.getMessage()); }
    }
    @Test
    public void testFor5() {
        CodeGen code = new CodeGen("""
for(;;arg++;) {}
""");
        try { code.parse().opto(); fail(); }
        catch( Exception e ) { assertEquals("Syntax error, expected Unexpected code after expression: ;",e.getMessage()); }
    }



    // ---------------------------------------------------------------
    @Test
    public void testForward0() {
        CodeGen code = new CodeGen("""
struct A{
    B? f1;
    B? f2;
};
return new A;
""");
        code.parse().opto();
        assertEquals("return A;", code.print());
        assertEquals("A{f1=null,f2=null}", Eval2.eval(code,0));
    }

    @Test
    public void testForward1() {
        CodeGen code = new CodeGen("""
struct A{
    B?[]? nil_array_of_b;
    B?[]  not_array_of_b = new B?[0];
};
return new A.not_array_of_b;
""");
        code.parse().opto();
        assertEquals("return (const)[*B?];", code.print());
        assertEquals("*B?[]", Eval2.eval(code,0));
    }

    // ---------------------------------------------------------------
    @Test
    public void testLinkedList2() {
        CodeGen code = new CodeGen("""
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
        code.parse().opto();
        assertEquals("return Phi(Loop,0,(Phi_sum+.i));", code.print());
        assertEquals("45", Eval2.eval(code, 10));
    }

    @Test
    public void sieveOfEratosthenes() {
        CodeGen code = new CodeGen(
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
        code.parse().opto().typeCheck().GCM();
        assertEquals("return [int];", code.print());
        assertEquals("int[ 2,3,5,7,11,13,17,19]",Eval2.eval(code, 20));
    }

}
