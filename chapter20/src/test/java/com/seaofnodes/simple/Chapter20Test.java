package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
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

    @Ignore @Test
    public void testBasic1() {
        CodeGen code = new CodeGen("return arg | 2;").parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched().regAlloc();
        assertEquals("return (ori,(mov,arg));", code._stop.toString());
    }

    @Ignore @Test
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
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched().regAlloc();
        assertEquals("return (mov,Phi(Loop,(mov,(i2f8,arg)),(mulf,(addf,(mov,(divf,i2f8,mov)),mov),0.5f)));", code.print());
    };

    @Test
    public void testAlloc2() {
        CodeGen code = new CodeGen("int[] !xs = new int[3]; xs[arg]=1; return xs[arg&1];");
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched().regAlloc();
        assertEquals("return .[];", code.print());
    }

}
