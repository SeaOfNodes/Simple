package com.seaofnodes.simple;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assert.fail;
import org.junit.Ignore;

public class Chapter19Test {

    @Test
    public void testJig() {
        CodeGen code = new CodeGen(
"""
return 0;
""");
        code.parse().opto().typeCheck().GCM().localSched();
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
hashCode(s);
""");
        code.parse().opto().typeCheck().GCM().localSched();
        assertEquals("Stop[ return Phi(Region,123456789,Phi(Loop,0,(.[]+((Phi_hash<<5)-Phi_hash)))); return Phi(Region,1,0,0,1); ]", code._stop.toString());
        assertEquals("-2449306563677080489", Eval2.eval(code,  2));
    }

    @Test
    public void testBasic0() {
        CodeGen code = new CodeGen("return 0;").parse().opto().typeCheck().instSelect("x86_64_v2").GCM().localSched();
        System.out.println(code.asm());
        assertEquals("return 0;", code._stop.toString());
    }

    @Test
    public void testBasic1() {
        CodeGen code = new CodeGen("return arg+1;").parse().opto().typeCheck().instSelect("x86_64_v2").GCM().localSched();
        System.out.println(code.asm());
        assertEquals("return (addi,arg);", code._stop.toString());
    }

    @Test
    public void testBasic2() {
        CodeGen code = new CodeGen("return -17;").parse().opto().typeCheck().instSelect("x86_64_v2").GCM().localSched();
        System.out.println(code.asm());
        assertEquals("return -17;", code._stop.toString());
    }


    @Test
    public void testBasic3() {
        CodeGen code = new CodeGen("return arg==1;").parse().opto().typeCheck().instSelect("x86_64_v2").GCM().localSched();
        System.out.println(code.asm());
        assertEquals("return (set==,(cmp,arg));", code._stop.toString());
    }

    @Test
    public void testBasic4() {
        CodeGen code = new CodeGen("return arg<<1;").parse().opto().typeCheck().instSelect("x86_64_v2").GCM().localSched();
        System.out.println(code.asm());
        assertEquals("return (shli,arg);", code._stop.toString());
    }

    @Test
    public void testBasic5() {
        CodeGen code = new CodeGen("return arg >> 1;").parse().opto().typeCheck().instSelect("x86_64_v2").GCM().localSched();;
        System.out.println(code.asm());
        assertEquals("return (sari,arg);", code._stop.toString());
    }

    @Test
    public void testBasic6() {
        CodeGen code = new CodeGen("return arg >>> 1;").parse().opto().typeCheck().instSelect("x86_64_v2").GCM().localSched();
        System.out.println(code.asm());
        assertEquals("return (shri,arg);", code._stop.toString());
    }

    @Test
    public void testBasic7() {
        CodeGen code = new CodeGen("return arg / 2;").parse().opto().typeCheck().instSelect("x86_64_v2").GCM().localSched();
        System.out.println(code.asm());
        assertEquals("return (divi,arg);", code._stop.toString());
    }

    @Test
    public void testBasic8() {
        CodeGen code = new CodeGen("return arg * 2;").parse().opto().typeCheck().instSelect("x86_64_v2").GCM().localSched();
        System.out.println(code.asm());
        assertEquals("return (muli,arg);", code._stop.toString());
    }

    @Test
    public void testBasic9() {
        CodeGen code = new CodeGen("return arg & 2;").parse().opto().typeCheck().instSelect("x86_64_v2").GCM().localSched();
        System.out.println(code.asm());
        assertEquals("return (andi,arg);", code._stop.toString());
    }

    @Test
    public void testBasic10() {
        CodeGen code = new CodeGen("return arg | 2;").parse().opto().typeCheck().instSelect("x86_64_v2").GCM().localSched();
        System.out.println(code.asm());
        assertEquals("return (ori,arg);", code._stop.toString());
    }

    @Test
    public void testBasic11() {
        CodeGen code = new CodeGen("return arg ^ 2;").parse().opto().typeCheck().instSelect("x86_64_v2").GCM().localSched();
        System.out.println(code.asm());
        assertEquals("return (xori,arg);", code._stop.toString());
    }

    @Test
    public void testBasic12() {
        CodeGen code = new CodeGen("return arg + 2.0;").parse().opto().typeCheck().instSelect("x86_64_v2").GCM().localSched();
        System.out.println(code.asm());
        assertEquals("return (addss,(fildi));", code._stop.toString());
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
return a;
""");
        code.parse().opto().typeCheck().instSelect("x86_64_v2").GCM().localSched();
        System.out.println(code.asm());
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
        code.parse().opto().typeCheck().instSelect("x86_64_v2").GCM().localSched();
        System.out.println(code.asm());
        assertEquals("return (add,(add,Phi(Region,(shli,arg),arg),arg),Phi(Region,4,5));", code.print());
    }
}
