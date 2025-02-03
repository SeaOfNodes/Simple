package com.seaofnodes.simple;

import com.seaofnodes.simple.node.StopNode;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assert.fail;
import org.junit.Ignore;

public class Chapter19Test {

    @Test
    public void testJig() {
        CodeGen code = new CodeGen("""
                return 0;""");
        code.parse().opto().typeCheck().GCM().localSched();
        assertEquals("return 0;", code._stop.toString());
        assertEquals("0", Eval2.eval(code, 2));
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
        assertEquals("-2449306563677080489", Eval2.eval(code, 2));
    }

    @Test
    public void testBasic0() {
        CodeGen code = new CodeGen("return 0;").parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return 0;", code._stop.toString());
    }

    @Test
    public void testBasic1() {
        CodeGen code = new CodeGen("return arg+1;").parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return (addi,arg);", code._stop.toString());
    }

    @Test
    public void testBasic2() {
        CodeGen code = new CodeGen("return -17;").parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return -17;", code._stop.toString());
    }


    @Test
    public void testBasic3() {
        CodeGen code = new CodeGen("return arg==1;").parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return (set==,(cmp));", code._stop.toString());
    }

    @Test
    public void testBasic4() {
        CodeGen code = new CodeGen("return arg<<1;").parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return (shli,arg);", code._stop.toString());
    }

    @Test
    public void testBasic5() {
        CodeGen code = new CodeGen("return arg >> 1;").parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        ;
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
        assertEquals("return (div,arg);", code._stop.toString());
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
        assertEquals("return (addsd,(i2f8));", code._stop.toString());
    }

    @Test
    public void testBasic13() {
        CodeGen code = new CodeGen("return arg - 2.0;").parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return (subsd,(i2f8));", code._stop.toString());
    }

    @Test
    public void testBasic14() {
        CodeGen code = new CodeGen("return arg * 2.0;").parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return (mulsd,(i2f8));", code._stop.toString());
    }

    @Test
    public void testBasic15() {
        CodeGen code = new CodeGen("return arg / 2.0;").parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return (divsd,(i2f8));", code._stop.toString());
    }

    @Test
    public void testBasic16() {
        CodeGen code = new CodeGen(
"""
int arg1 =  arg + 1;
return arg1 / arg;""");
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return (div,(addi,arg));", code._stop.toString());
    }

    @Test
    public void testBasic17() {
        CodeGen code = new CodeGen(
"""
int arg1 =  arg + 1;
return arg1 * arg;
""");
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return (mul,(addi,arg));", code._stop.toString());
    }

    @Test
    public void testToFloat() {
        CodeGen code = new CodeGen("""
                int a = arg;
                return a + 2.0;
                """
        ).parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return (addsd,(i2f8));", code._stop.toString());
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
        assertEquals("return Phi(Loop,0,(add,Phi_sum,Phi(Loop,0,(addi,Phi_i))));", code.print());
    }

    @Test
    public void testLoopBasic() {
        CodeGen code = new CodeGen(
                """
                while(arg < 10) {
                    arg = arg + 1;
                }
                return arg;""");
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        System.out.print(code.asm());
//        assertEquals("return Phi(Loop,0,(add,Phi_sum,Phi(Loop,0,(addi,Phi_i))));", code.print());
    }
    @Test
    public void testAlloc() {
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
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        System.out.print(code.asm());
        // not enough context?
        assertEquals("return (lea,arg);", code.print());
    }

    @Test
    public void testLea2() {
        CodeGen code = new CodeGen("int x = arg/3; return arg+x*4+7;");
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        // more context?
        assertEquals("return (lea,arg);", code.print());
    }

    @Test
    public void testLea3() {
        CodeGen code = new CodeGen("int x = arg/3; return x*4+arg;");
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched();
        assertEquals("return (lea,arg);", code.print());
    }

    @Ignore
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
        assertEquals("int[ 2,3,5,7,11,13,17,19]", Eval2.eval(code, 20));
    }

    // Todo: better error message when guess is not defined
    // when its int it doesnt have this issue
    @Test
    public void testSquareRoot() {
        CodeGen code = new CodeGen(
                """
                        flt guess = arg;
                        while( 1 ) {
                            flt next = (arg/guess + guess)/2;
                            if( next == guess ) break;
                            guess = next;
                         }
                        return guess;
                        """
        );

        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched().print();
        System.out.print(code.asm());
        //assertEquals("return Phi(Loop,(i2f8),(divf,(addf,(divf,i2f8))));", code.print());
    }

    @Test
    public void testFloat() {
        CodeGen code = new CodeGen(
                """
flt x = arg;
return x+1==x;
"""
        );
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched().print();
        System.out.print(code.asm());
    }
    @Ignore
    @Test
    public void testFunc() {
        CodeGen code = new CodeGen(
                """         
                var sq = { int x ->
                 x*x;
               };
            return sq(arg)+sq(3);
        """);

        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched().print();
        System.out.print(code.asm());
    }
}
