package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen.Phase;
import com.seaofnodes.simple.codegen.CodeGen;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class Chapter19Test {

    @Test
    public void testJig() {
        CodeGen code = new CodeGen("""
return 0;
""");
        code.parse().opto().typeCheck();
        assertEquals("return 0;", code._stop.toString());
        assertEquals("0", Eval2.eval(code,  2));
    }


    @Test
    public void testString() {
        String src =
"""
struct String {
    u8[~] cs;
    int _hashCode;
};

// Compare two Strings
val equals = { String self, String s ->
    if( self == s ) return true;
    if( self.cs# != s.cs# ) return false;
    for( int i=0; i< self.cs#; i++ )
        if( self.cs[i] != s.cs[i] )
            return false;
    return true;
};


val _hashCodeString = { String self ->
    int hash=0;
    for( int i=0; i< self.cs#; i++ )
        hash = hash*31 + self.cs[i];
    if( !hash ) hash = 123456789;
    return hash;
};

// Return the String hashCode (cached, and never 0)
val hashCode = { String self ->
    self._hashCode
    ?  self._hashCode
    : (self._hashCode = _hashCodeString(self));
};

hashCode(new String{cs="Hello, World!";});
""";
        CodeGen code = new CodeGen(src).driver(Phase.LocalSched);
        assertEquals("Stop[ return MEM[ 2:___ 3:___ 4:.cs=(*[]u8)Bot; 5:#-5:0]; return Phi(Region,0,1,0,1); return Phi(Region,._hashCode,Phi(Region,123456789,Phi(Loop,0,(.[]+((Phi_hash<<5)-Phi_hash))))); return #2; ]", code._stop.toString());
        assertEquals("4029215624828139541", Eval2.eval(code,  2));
    }

    @Test
    public void testBasic0() {
        CodeGen code = new CodeGen("return 0;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return 0;", code._stop.toString());
    }

    @Test
    public void testBasic1() {
        CodeGen code = new CodeGen("return arg+1;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (inc,arg);", code._stop.toString());
    }

    @Test
    public void testBasic2() {
        CodeGen code = new CodeGen("return -17;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return -17;", code._stop.toString());
    }


    @Test
    public void testBasic3() {
        CodeGen code = new CodeGen("return arg==1;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (set==,(cmp,arg));", code._stop.toString());
    }

    @Test
    public void testBasic4() {
        CodeGen code = new CodeGen("return arg<<1;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (shli,arg);", code._stop.toString());
    }

    @Test
    public void testBasic5() {
        CodeGen code = new CodeGen("return arg >> 1;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (sari,arg);", code._stop.toString());
    }

    @Test
    public void testBasic6() {
        CodeGen code = new CodeGen("return arg >>> 1;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (shri,arg);", code._stop.toString());
    }

    @Test
    public void testBasic7() {
        CodeGen code = new CodeGen("return arg / 2;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (div,arg,2);", code._stop.toString());
    }

    @Test
    public void testBasic8() {
        CodeGen code = new CodeGen("return arg * 6;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (muli,arg);", code._stop.toString());
    }

    @Test
    public void testBasic9() {
        CodeGen code = new CodeGen("return arg & 2;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (andi,arg);", code._stop.toString());
    }

    @Test
    public void testBasic10() {
        CodeGen code = new CodeGen("return arg | 2;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (ori,arg);", code._stop.toString());
    }

    @Test
    public void testBasic11() {
        CodeGen code = new CodeGen("return arg ^ 2;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (xori,arg);", code._stop.toString());
    }

    @Test
    public void testBasic12() {
        CodeGen code = new CodeGen("return arg + 2.0;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (addf,(cvtf,arg),2.0f);", code._stop.toString());
    }

    @Test
    public void testBasic13() {
        CodeGen code = new CodeGen("return arg - 2.0;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (subf,(cvtf,arg),2.0f);", code._stop.toString());
    }

    @Test
    public void testBasic14() {
        CodeGen code = new CodeGen("return arg * 2.0;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (mulf,(cvtf,arg),2.0f);", code._stop.toString());
    }

    @Test
    public void testBasic15() {
        CodeGen code = new CodeGen("return arg / 2.0;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (mulf,(cvtf,arg),0.5f);", code._stop.toString());
    }

    @Test
    public void testBasic16() {
        CodeGen code = new CodeGen(
"""
int arg1 =  arg + 1;
return arg1 / arg;""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (div,(inc,arg),arg);", code._stop.toString());
    }

    @Test
    public void testBasic17() {
        CodeGen code = new CodeGen(
"""
int arg1 =  arg + 1;
return arg1 * arg;
""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (mul,(inc,arg),arg);", code._stop.toString());
    }

    @Test
    public void testToFloat() {
        CodeGen code = new CodeGen("""
int a = arg;
return a + 2.0;
"""
        ).driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (addf,(cvtf,arg),2.0f);", code._stop.toString());
    }

    @Test
    public void testIfStmt() {
        CodeGen code = new CodeGen(
"""
int a = 1;
if (arg == 1)
    a = arg+2;
else {
    a = arg-3;
}
return a;""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return Phi(Region,(addi,arg),(addi,arg));", code.print());
    }

    @Test
    public void testIfMerge2() {
        CodeGen code = new CodeGen(
"""
int a=arg+1;
int b=arg+2;
if( arg==1 )
    b=b+a;
else
    a=b+1;
return a+b;""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (add,(add,Phi(Region,(shli,arg),arg),arg),Phi(Region,4,5));", code.print());
    }

    @Test
    public void testLoop() {
        CodeGen code = new CodeGen(
"""
int sum=0;
for( int i=0; i<arg; i++ )
    sum += i;
return sum;""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return Phi(Loop,0,(add,Phi_sum,Phi(Loop,0,(inc,Phi_i))));", code.print());
    }

    @Test
    public void testAlloc1() {
        CodeGen code = new CodeGen(
"""
struct _S { int a; _S? c; };
return new _S;""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return Test._S;", code.print());
    }

    @Test
    public void testLea1() {
        CodeGen code = new CodeGen("int x = arg/3; return arg+x+7;");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (lea,arg,(div,arg,3));", code.print());
    }

    @Test
    public void testLea2() {
        CodeGen code = new CodeGen("int x = arg/3; return arg+x*4+7;");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (lea,arg,(div,arg,3));", code.print());
    }

    @Test
    public void testLea3() {
        CodeGen code = new CodeGen("int x = arg/3; return x*4+arg;");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (lea,arg,(div,arg,3));", code.print());
    }

    @Test
    public void testAlloc2() {
        CodeGen code = new CodeGen("int[] !xs = new int[3]; xs[arg]=1; return xs[arg&1];");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return .[];", code.print());
    }

    @Test
    public void testAlloc3() {
        CodeGen code = new CodeGen("int[] !xs = new int[3]; xs[arg]=1; return xs[arg&1]+3;");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return .[];", code.print());
    }

    @Test
    public void testArray1() {
        CodeGen code = new CodeGen(
"""
int[] !ary = new int[arg];
// Fill [0,1,2,3,4,...]
for( int i=0; i<ary#; i++ )
    ary[i] = i;
// Fill [0,1,3,6,10,...]
for( int i=0; i<ary#-1; i++ )
    ary[i+1] += ary[i];
return ary[1] * 1000 + ary[3]; // 1 * 1000 + 6
""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return .[];", code.print());
    }

    @Test
    public void testArray2() {
        CodeGen code = new CodeGen(
"""
flt[] !A = new flt[arg], !B = new flt[arg];
// Fill [0,1,2,3,4,...]
for( int i=0; i<A#; i++ )
    A[i] = i;
for( int i=0; i<A#; i++ )
    B[i] += A[i];
return 0;
""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return 0;", code.print());
    }

    @Test
    public void testArray3() {
        CodeGen code = new CodeGen(
"""
byte[] !A = new byte[arg];
for( int i=0; i<A#; i++ )
    A[i]++;
return A[1];
""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return .[];", code.print());
    }

    @Test
    public void testNewton() throws IOException {
        String src =
"""
val _test_sqrt = { flt x ->
    flt epsilon = 1e-15;
    flt guess = x;
    while( 1 ) {
        flt next = (x/guess + guess)/2;
        if( guess-epsilon <= next & next <= guess+epsilon ) return guess;
        //if( guess==next ) return guess;
        guess = next;
    }
};
flt farg = arg;
return _test_sqrt(farg);
""";
        CodeGen code = new CodeGen(src).driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return Phi(Loop,(cvtf,arg),(mulf,(addf,(divf,cvtf,Phi_guess),Phi_guess),0.5f));", code.print());
    };


    @Test
    public void sieveOfEratosthenes() throws IOException {
        String src =
"""
val sieve = { int N ->
    // The main Sieve array
    bool[] !ary = new bool[N];
    // The primes less than N
    u32[] !primes = new u32[N>>1];
    // Number of primes so far, searching at index p
    int nprimes = 0, p=2;
    // Find primes while p^2 < N
    while( p*p < N ) {
        // skip marked non-primes
        while( ary[p] ) p++;
        // p is now a prime
        primes[nprimes++] = p;
        // Mark out the rest non-primes
        for( int i = p + p; i < ary#; i+= p )
            ary[i] = true;
        p++;
    }

    // Now just collect the remaining primes, no more marking
    for( ; p < N; p++ )
        if( !ary[p] )
            primes[nprimes++] = p;

    // Copy/shrink the result array
    u32[] !rez = new u32[nprimes];
    for( int j=0; j < nprimes; j++ )
        rez[j] = primes[j];
    return rez;
};
""";
        CodeGen code = new CodeGen(src).driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("Stop[ return []u32; return { sieve}; ]", code.print());
        //assertEquals("u32[ 2,3,5,7,11,13,17,19]",Eval2.eval(code, 20));
    }


    @Test
    public void testFcn1() {
        CodeGen code = new CodeGen(
"""
val fcn = arg ? { int x -> x*x; } : { int x -> x+x; };
return fcn(2)*10 + fcn(3);
""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (add,#2,(muli,#2));", code.print());
    }

    @Test
    public void testFcn2() {
        CodeGen code = new CodeGen(
"""
val _sq = { int x -> x*x; };
return _sq(arg) + _sq(3);
""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (addi,(mul,arg,arg));", code.print());
    }
}
