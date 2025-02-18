package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.print.ASMPrinter;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

public class Chapter20Test {

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
    public void testBasic1() {
        CodeGen code = new CodeGen("return arg | 2;").parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched().regAlloc();
        assertEquals("return (ori,(mov,arg));", code._stop.toString());
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
return sqrt(farg) + sqrt(farg+2.0);
""");
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched().regAlloc();
        assertEquals("Stop[ return (addf,#2,(mov,#2)); return (mov,Phi(Loop,(mov,(mov,Parm_x(sqrt,flt))),(mulf,(addf,(divf,(mov,mov),mov),mov),0.5f))); ]", code.print());
    };

    @Test
    public void testAlloc2() {
        CodeGen code = new CodeGen("int[] !xs = new int[3]; xs[arg]=1; return xs[arg&1];");
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched().regAlloc();
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
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched().regAlloc();
        assertEquals("return .[];", code.print());
    }

    @Test
    public void testString() {
        CodeGen code = new CodeGen("""
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
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched().regAlloc();
        assertEquals("Stop[ return Phi(Region,123456789,(mov,(mov,Phi(Loop,0,.[])))); return Phi(Region,1,0,0,1); ]", code.print());
    }

    @Test
    public void testCast() {
        CodeGen code = new CodeGen(
    """
        struct Bar { int x; };
        var b = arg ? new Bar;
        return b ? b.x++ + b.x++ : -1;
     """
        );
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched().regAlloc();
        assertEquals("return Phi(Region,(mov,(lea, ---,.x)),-1);", code.print());
    }

}
