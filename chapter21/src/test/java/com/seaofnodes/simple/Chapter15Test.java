package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

public class Chapter15Test {

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
    public void testCyclic() {
        CodeGen code = new CodeGen(
"""
struct C { C? l; };
C !c = new C;
c.l = c;
return c;
""");
        code.parse().opto();
        assertEquals("return C;", code.print());
        assertEquals("C{l=$cyclic}", Eval2.eval(code,  0));
    }

    @Test
    public void testSafetyCheck() {
        CodeGen code = new CodeGen(
"""
u8[] old = new u8[0];
u8[] !output = new u8[1];
int i = 0;
while (i < old#) {
    output[i] = old[i];
    i = i + 1;
}
output[i] = 1;
return output;
""");
        code.parse().opto();
        assertEquals("return [u8];", code.print());
        assertEquals("\u0001", Eval2.eval(code,  0));
    }

    @Test
    public void testBasic1() {
        CodeGen code = new CodeGen(
"""
int[] is = new int[2];
return is[1];
""");
        code.parse().opto();
        assertEquals("return 0;", code.print());
        assertEquals("0", Eval2.eval(code,  0));
    }

    @Test
    public void testBasic2() {
        CodeGen code = new CodeGen(
"""
int[] is = new int[2];
int[] is2 = new int[2];
return is[1];
""");
        code.parse().opto();
        assertEquals("return 0;", code.print());
        assertEquals("0", Eval2.eval(code,  0));
    }

    @Test
    public void testBasic3() {
        CodeGen code = new CodeGen(
"""
int[] !a = new int[2];
a[0] = 1;
a[1] = 2;
return a[0];
""");
        code.parse().opto();
        assertEquals("return 1;", code.print());
        assertEquals("1", Eval2.eval(code,  0));
    }

    @Test
    public void testBasic4() {
        CodeGen code = new CodeGen(
"""
struct A { int i; };
A?[] !a = new A?[2];
return a;
""");
        code.parse().opto();
        assertEquals("return [*A?];", code.print());
        assertEquals("*A {int !i; }?[ null,null]", Eval2.eval(code, 0));
    }

    @Test
    public void testBasic5() {
        CodeGen code = new CodeGen(
"""
struct S { int x; flt y; };
// A new S
S !s = new S; s.x=99; s.y = 3.14;

// Double-d array of Ss.  Fill in one row.
S?[]?[] !iss = new S?[]?[2];
iss[0] = new S?[7];
iss[0][2] = s;

// Now pull out the filled-in value, with null checks
flt rez;
S?[]? is = iss[arg];
if( !is ) rez = 1.2;
else {
    S? i = is[2];
    if( !i ) rez = 2.3;
    else rez = i.y;
}
return rez;
""");
        code.parse().opto();
        assertEquals("return Phi(Region,1.2,.y,2.3);", code.print());
        assertEquals("3.14", Eval2.eval(code, 0));
        assertEquals("1.2", Eval2.eval(code, 1));
    }

    @Test
    public void testBasic6() {
        CodeGen code = new CodeGen(
"""
struct S { int x; flt y; };
// A new S
S !s = new S; s.x=99; s.y = 3.14;

// Double-d array of Ss.  Fill in one row.
S?[]?[] !iss = new S?[]?[2];
iss[0] = new S?[7];
iss[0][2] = s;

// Now pull out the filled-in value, with null checks
flt rez = 1.2;
if( iss[arg] )
    if( iss[arg][2] )
        rez = iss[arg][2].y;
return rez;""");
        code.parse().opto().typeCheck();
        assertEquals("return Phi(Region,1.2,1.2,.y);", code.print());
        assertEquals("3.14", Eval2.eval(code,  0));
        try { Eval2.eval(code, 10); fail(); } // Fails AIOOBE in Eval2, no range checks
        catch( Exception e ) { assertEquals("Index 10 out of bounds for length 2",e.getMessage()); }
    }

    @Test
    public void testTree() {
        CodeGen code = new CodeGen(
"""
// Can we define a forward-reference array?
struct Tree { Tree?[]? _kids; };
Tree !root = new Tree;
root._kids = new Tree?[2]; // NO BANG SO ARRAY IS OF IMMUTABLE TREES????
root._kids[0] = new Tree;
return root;
""");
        code.parse().opto();
        assertEquals("return Tree;", code.print());
        assertEquals("Tree{_kids=*Tree?[ Tree{_kids=null},null]}", Eval2.eval(code,  0));
    }

    @Test
    public void testNestedStructAddMemProj() {
        CodeGen code = new CodeGen(
"""
struct S { int a; int[] b; };
return 0;
""");
        code.parse().opto();
        assertEquals("return 0;", code.print());
    }

    @Test
    public void testRollingSum() {
        CodeGen code = new CodeGen(
"""
int[] !ary = new int[arg];
// Fill [0,1,2,3,4,...]
int i=0;
while( i < ary# ) {
    ary[i] = i;
    i = i+1;
}
// Fill [0,1,3,6,10,...]
i=0;
while( i < ary# - 1 ) {
    ary[i+1] = ary[i+1] + ary[i];
    i = i+1;
}
return ary[1] * 1000 + ary[3]; // 1 * 1000 + 6
""");
        code.parse().opto();
        assertEquals("return (.[]+(.[]*1000));", code.print());
        assertEquals("1006", Eval2.eval(code,  4));
    }

    @Test
    public void sieveOEratosthenes() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/sieve.smp"));
        CodeGen code = new CodeGen(src+"return sieve(arg);");
        code.parse().opto();
        assertEquals("return [u32];", code.print());
        assertEquals("u32[ 2,3,5,7,11,13,17,19]",Eval2.eval(code, 20));
    }

    @Test
    public void testNewNodeInit() {
        CodeGen code = new CodeGen(
"""
struct S {int i; flt f;};
S !s1 = new S;
S !s2 = new S;
s2.i = 3;
s2.f = 2.0;
if (arg) s1 = new S;
return s1.i + s1.f;
""");
        code.parse().opto();
        assertEquals("return ((flt).i+.f);", code.print());
        assertEquals("0.0", Eval2.eval(code,  0));
    }


    @Test
    public void testBad0() {
        CodeGen code = new CodeGen(
"""
return new flt;
""");
        try { code.parse().opto(); fail(); }
        catch( Exception e ) { assertEquals("Cannot allocate a flt",e.getMessage()); }
    }

    @Test
    public void testBad1() {
        CodeGen code = new CodeGen(
"""
int is = new int[2];
""");
        try { code.parse().opto(); fail(); }
        catch( Exception e ) { assertEquals("Type *[int] is not of declared type int",e.getMessage()); }
    }

    @Test
    public void testBad2() {
        CodeGen code = new CodeGen(
"""
int[] is = new int[3.14];
return is[1];
""");
        try { code.parse().opto(); fail(); }
        catch( Exception e ) { assertEquals("Cannot allocate an array with length 3.14",e.getMessage()); }
    }

    @Test
    public void testBad3() {
        CodeGen code = new CodeGen("""
int[] is = new int[arg];
return is[1];
""");
        code.parse().opto();
        assertEquals("return 0;", code.print());
        assertEquals("0", Eval2.eval(code,  4));
        try { Eval2.eval(code,-1); fail(); }
        catch( NegativeArraySizeException e ) { assertEquals("-1",e.getMessage()); }
        // This test will not pass (i.e., failed range-check) until we have range checks.
        // Without the range check, the load `is[1]` optimizes against the `new int[]`
        // and assumes it loads a zero then folds away, and the Eval2 never sees an OOB load.
        //try { Eval2.eval(code,0); fail(); }
        //catch( ArrayIndexOutOfBoundsException e ) { assertEquals("Array index 1 out of bounds for array length 0",e.getMessage()); }
    }

    @Test
    public void testProgress() {
        CodeGen code = new CodeGen("""
i8 v1=0&0;
u8 v2=0;
byte v4=0;
if(0) {}
while(v2<0) {
    v4=0-v1;
    break;
}
int v5=0&0;
while(v5+(0&0)) {
    int v7=0&0;
    while(v7)
        v4=0>>>v5;
    while(v1)
        return 0;
}
return v1;
""");
        code.parse().opto();
        assertEquals("return 0;", code.print());
        assertEquals("0", Eval2.eval(code,  0));
    }

    @Test
    public void testSharpNot() {
        CodeGen code = new CodeGen(
"""
if(0>>0) {}
while(0) {}
u32 v7=0;
int v8=0;
while(0<--1>>>---(v7*0==v8)) {}
""");
        code.parse().opto();
        assertEquals("return 0;", code.print());
    }


    @Test
    public void testProgress2() {
        CodeGen code = new CodeGen(
"""
if(1) {}
else {
        while(arg>>>0&0>>>0) {}
    byte v3=0>>>0;
                while(0) {}
        int v7=0>>>0;
        while(v7<0>>>0) {
                    while(0+v7<=0) if(1) arg=-12;
            if(arg) {
                v3=arg+v3+0;
                arg=0;
            }
        }
}
""");
        code.parse().opto();
        assertEquals("return 0;", code.print());
    }
}
