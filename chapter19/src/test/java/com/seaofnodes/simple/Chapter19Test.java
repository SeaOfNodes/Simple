package com.seaofnodes.simple;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assert.fail;
import org.junit.Ignore;

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
        CodeGen code = new CodeGen(
"""
struct String {
    u8[] cs;
    int _hashCode;
};

val equals = { String self, String s ->
    if( self == s ) return true;
    if( self.cs# != s.cs# ) return false;
    for( int i=0; i< self.cs#; i++ )
        if( self.cs[i] != s.cs[i] )
            return false;
    return true;
};

val hashCode = { String self ->
    self._hashCode
    ?  self._hashCode
    : (self._hashCode = _hashCodeString(self));
};

val _hashCodeString = { String self ->
    int hash=0;
    if( self.cs ) {
        for( int i=0; i< self.cs#; i++ )
            hash = hash*31 + self.cs[i];
    }
    if( !hash ) hash = 123456789;
    return hash;
};

String !s = new String { cs = new u8[17]; };
s.cs[0] =  67; // C
s.cs[1] = 108; // l
hashCode(s);""");
        code.parse().opto().typeCheck().GCM().localSched();
        assertEquals("Stop[ return Phi(Region,123456789,Phi(Loop,0,(.[]+((Phi_hash<<5)-Phi_hash)))); return Phi(Region,1,0,0,1); ]", code._stop.toString());
        assertEquals("-2449306563677080489", Eval2.eval(code,  2));
    }

    @Test
    public void testBasic0() {
        CodeGen code = new CodeGen("return 0;").parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return 0;", code._stop.toString());
    }

    @Test
    public void testBasic1() {
        CodeGen code = new CodeGen("return arg+1;").parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return (inc,arg);", code._stop.toString());
    }

    @Test
    public void testBasic2() {
        CodeGen code = new CodeGen("return -17;").parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return -17;", code._stop.toString());
    }


    @Test
    public void testBasic3() {
        CodeGen code = new CodeGen("return arg==1;").parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return (set==,(cmp,arg));", code._stop.toString());
    }

    @Test
    public void testBasic4() {
        CodeGen code = new CodeGen("return arg<<1;").parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return (shli,arg);", code._stop.toString());
    }

    @Test
    public void testBasic5() {
        CodeGen code = new CodeGen("return arg >> 1;").parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return (sari,arg);", code._stop.toString());
    }

    @Test
    public void testBasic6() {
        CodeGen code = new CodeGen("return arg >>> 1;").parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return (shri,arg);", code._stop.toString());
    }

    @Test
    public void testBasic7() {
        CodeGen code = new CodeGen("return arg / 2;").parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return (div,arg,2);", code._stop.toString());
    }

    @Test
    public void testBasic8() {
        CodeGen code = new CodeGen("return arg * 6;").parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return (muli,arg);", code._stop.toString());
    }

    @Test
    public void testBasic9() {
        CodeGen code = new CodeGen("return arg & 2;").parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return (andi,arg);", code._stop.toString());
    }

    @Test
    public void testBasic10() {
        CodeGen code = new CodeGen("return arg | 2;").parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return (ori,arg);", code._stop.toString());
    }

    @Test
    public void testBasic11() {
        CodeGen code = new CodeGen("return arg ^ 2;").parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return (xori,arg);", code._stop.toString());
    }

    @Test
    public void testBasic12() {
        CodeGen code = new CodeGen("return arg + 2.0;").parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return (addf,(i2f8,arg),2.0f);", code._stop.toString());
    }

    @Test
    public void testBasic13() {
        CodeGen code = new CodeGen("return arg - 2.0;").parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return (subf,(i2f8,arg),2.0f);", code._stop.toString());
    }

    @Test
    public void testBasic14() {
        CodeGen code = new CodeGen("return arg * 2.0;").parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return (mulf,(i2f8,arg),2.0f);", code._stop.toString());
    }

    @Test
    public void testBasic15() {
        CodeGen code = new CodeGen("return arg / 2.0;").parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return (divf,(i2f8,arg),2.0f);", code._stop.toString());
    }

    @Test
    public void testBasic16() {
        CodeGen code = new CodeGen(
"""
int arg1 =  arg + 1;
return arg1 / arg;""");
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return (div,(inc,arg),arg);", code._stop.toString());
    }

    @Test
    public void testBasic17() {
        CodeGen code = new CodeGen(
"""
int arg1 =  arg + 1;
return arg1 * arg;
""");
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return (mul,(inc,arg),arg);", code._stop.toString());
    }

    @Test
    public void testToFloat() {
        CodeGen code = new CodeGen("""
                int a = arg;
                return a + 2.0;
                """
        ).parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return (addf,(i2f8,arg),2.0f);", code._stop.toString());
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
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
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
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
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
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return Phi(Loop,0,(add,Phi_sum,Phi(Loop,0,(inc,Phi_i))));", code.print());
    }

    @Test
    public void testAlloc1() {
        CodeGen code = new CodeGen(
"""
struct S { int a; S? c; };
return new S;""");
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return S;", code.print());
    }

    @Test
    public void testLea1() {
        CodeGen code = new CodeGen("int x = arg/3; return arg+x+7;");
        code.parse().opto().typeCheck().instSelect("x86_64_v2",  "SystemV").GCM().localSched();
        assertEquals("return (lea,arg,(div,arg,3));", code.print());
    }

    @Test
    public void testLea2() {
        CodeGen code = new CodeGen("int x = arg/3; return arg+x*4+7;");
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return (lea,arg,(div,arg,3));", code.print());
    }

    @Test
    public void testLea3() {
        CodeGen code = new CodeGen("int x = arg/3; return x*4+arg;");
        code.parse().opto().typeCheck().instSelect("x86_64_v2",  "SystemV").GCM().localSched();
        assertEquals("return (lea,arg,(div,arg,3));", code.print());
    }

    @Test
    public void testAlloc2() {
        CodeGen code = new CodeGen("int[] !xs = new int[3]; xs[arg]=1; return xs[arg&1];");
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return .[];", code.print());
    }

    @Test
    public void testAlloc3() {
        CodeGen code = new CodeGen("int[] !xs = new int[3]; xs[arg]=1; return xs[arg&1]+3;");
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
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
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
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
""");
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
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
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return .[];", code.print());
    }

    @Test
    public void testNewton() {
        CodeGen code = new CodeGen(
"""
// Newtons approximation to the square root
val sqrt = { flt x ->
    flt guess = x;
    while( 1 ) {
        flt next = (x/guess + guess)/2;
        if( next == guess ) return guess;
        guess = next;
    }
};
flt farg = arg;
return sqrt(farg);
""");
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return Phi(Loop,(i2f8,arg),(divf,(addf,(divf,i2f8,Phi_guess),Phi_guess),2.0f));", code.print());
    };


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
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return [int];", code.print());
        //assertEquals("int[ 2,3,5,7,11,13,17,19]",Eval2.eval(code, 20));
    }


    @Test
    public void testFcn1() {
        CodeGen code = new CodeGen(
"""
val fcn = arg ? { int x -> x*x; } : { int x -> x+x; };
return fcn(2)*10 + fcn(3);
""");
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("Stop[ return (add,Phi(Region,{ int -> int #1},{ int -> int #2})( 3),(muli,Phi_( 2))); return (mul,Parm_x($fun,int,3,2),x); return (shli,Parm_x($fun,int,3,2)); ]", code.print());
    }

    @Test
    public void testFcn2() {
        CodeGen code = new CodeGen(
"""
val sq = { int x -> x*x; };
return sq(arg) + sq(3);
""");
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("Stop[ return (add,sq(),sq()); return (mul,Parm_x(sq,int,3,arg),x); ]", code.print());
    }
}
