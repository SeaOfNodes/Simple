package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class Chapter11Test {

    @Test
    public void testPrimes() {
        CodeGen code = new CodeGen(
"""
if( arg < 2 ) return 0;
int primeCount = 1;
int prime = 3;
while( prime <= arg ) {
    int isPrime = 1;
    // Check for even case, so the next loop need only check odds
    if( (prime/2)*2 == prime )
        continue;
    // Check odds up to sqrt of prime
    int j = 3;
    while( j*j <= prime ) {
        if( (prime/j)*j == prime ) {
            isPrime = 0;
            break;
        }
        j = j + 2;
    }
    if( isPrime )
        primeCount = primeCount + 1;
    prime = prime + 2;
}
return primeCount;
""");
        code.parse().opto();
        assertEquals("return Phi(Region,0,Phi(Loop,1,Phi(Region,Phi_primeCount,Phi_primeCount,(Phi_primeCount+1))));", code.print());
        assertEquals("0", Eval2.eval(code,  1)); // No primes 1 or below
        assertEquals("1", Eval2.eval(code,  2)); // 2
        assertEquals("2", Eval2.eval(code,  3)); // 2, 3
        assertEquals("2", Eval2.eval(code,  4)); // 2, 3
        assertEquals("3", Eval2.eval(code,  5)); // 2, 3, 5
        assertEquals("4", Eval2.eval(code, 10)); // 2, 3, 5, 7
    }

    @Test
    public void testAntiDeps1() {
        CodeGen code = new CodeGen(
"""
struct S { int f; };
S !v=new S;
v.f = 2;
int i=new S.f;
i=v.f;
if (arg) v.f=1;
return i;
""");
        code.parse().opto();
        assertEquals("return 2;", code.print());
        assertEquals("2", Eval2.eval(code, 0));
        assertEquals("2", Eval2.eval(code, 1));
    }

    @Test
    public void testAntiDeps2() {
        CodeGen code = new CodeGen(
"""
struct S { int f; };
S !v = new S;
v.f = arg;
S !t = new S;
int i = 0;
if (arg) {
    if (arg+1) v = t;
    i = v.f;
} else {
    v.f = 2;
}
return i;
""");
        code.parse().opto();
        assertEquals("return Phi(Region,.f,0);", code.print());
    }

    @Test
    public void testAntiDeps3() {
        CodeGen code = new CodeGen(
"""
struct S { int f; };
S !v0 = new S;
S? v1;
if (arg) v1 = new S;
if (v1) {
    v0.f = v1.f;
} else {
    v0.f = 2;
}
return v0;
""");
        code.parse().opto();
        assertEquals("return S;", code.print());
    }


    @Test
    public void testAntiDeps4() {
        CodeGen code = new CodeGen(
"""
struct S { int f; };
S !v = new S;
v.f = arg;
S t = new S;
int i = v.f;
if (arg+1) arg= 0;
while (arg) v.f = 2;
return i;
""");
        code.parse().opto();
        assertEquals("return arg;", code.print());
    }

    @Test
    public void testAntiDeps5() {
        CodeGen code = new CodeGen(
"""
struct S { int f; };
S !v = new S;
while(1) {
    while(arg+1) { arg=arg-1; }
    if (arg) break;
    v.f = 2;
}
return v;
""");
        code.parse().opto();
        assertEquals("return S;", code.print());
    }

    @Test
    public void testAntiDeps6() {
        CodeGen code = new CodeGen(
"""
struct s { int v; };
s !ptr=new s;
while( -arg )
  ptr = new s;
while(1)
  arg = arg+ptr.v;
""");
        code.parse().opto();
        assertEquals("return Top;", code.print());
    }

    @Test
    public void testAntiDeps7() {
        CodeGen code = new CodeGen(
"""
struct S { int f; };
S !v = new S;
S t = new S;
int i = v.f;
while (arg) {
    v.f = arg;
    arg = arg-1;
}
return i;
""");
        code.parse().opto();
        assertEquals("return 0;", code.print());
    }

    @Test
    public void testAntiDeps8() {
        CodeGen code = new CodeGen(
"""
struct S { int f; };
S !v = new S;
S t = new S;
while(arg) {
    arg=arg-1;
    int f = v.f;
    v.f = 2;
    if (arg) arg = f;
    else v.f = 3;
}
return arg;
""");
        code.parse().opto();
        assertEquals("return 0;", code.print());
    }

    @Test
    public void testAntiDeps9() {
        CodeGen code = new CodeGen(
"""
struct S { int f; };
S !v = new S;
S t = new S;
if (arg) {
    v.f=2;
    int i=t.f;
    v.f=i;
}
return v;
""");
        code.parse().opto();
        assertEquals("return S;", code.print());
    }

    @Test
    public void testExample2() {
        CodeGen code = new CodeGen(
"""
struct S { int f; };
S !v = new S;
int i = arg;
while (arg > 0) {
    int j = i/3;
    if (arg == 5)
        v.f = j;
    arg = arg - 1;
}
return v;
                """);
        code.parse().opto();
        //assertEquals("return new S;", code.print());
    }

    @Test
    public void testScheduleUse() {
        CodeGen code = new CodeGen(
"""
int v0=0;
while(0>=0) {
    u1 v1=0;
    v1=v0;
    if(v1*0)
        v0=-v1;
}
return 0;
""");
        code.parse().opto();
        assertEquals("return Top;", code.print());
    }

    @Test
    public void testLoopCarriedDep() {
        CodeGen code = new CodeGen(
"""
u32 v0=0;
{
    int v1=0;
    while(v1) {
        v1=1>>>v0!=0;
        v0=v1/0;
    }
    return v1;
}
""");
        code.parse().opto();
        assertEquals("return 0;", code.print());
    }

}
