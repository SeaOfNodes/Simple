package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.StopNode;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Chapter12Test {
    @Test
    public void testJig() {
        CodeGen code = new CodeGen(
"""
return 0;
""");
        code.parse().opto();
        assertEquals("return 0;", code.print());
        assertEquals("0", Eval2.eval(code,  0));
    }

    @Test
    public void testFloat() {
        CodeGen code = new CodeGen(
"""
flt x = 3.14;
return 0;
""");
        code.parse().opto();
        assertEquals("return 0;", code.print());
        assertEquals("0", Eval2.eval(code,  0));
    }

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
""");
        // TODO: One of the problems with <clinit> returning an OS error code,
        // is it chops all results to int.
        code.parse().opto();
        assertEquals("return (!Phi(Loop,(flt)arg,(((ToFloat/Phi_guess)+Phi_guess)*0.5f)));", code.print());
        assertEquals("0", Eval2.eval(code,  9));
        assertEquals("0", Eval2.eval(code,  2));
    }

    @Test
    public void testFPOps() {
        CodeGen code = new CodeGen(
"""
flt x = arg;
return x+1==x;
""");
        code.parse().opto();
        assertEquals("return ((flt)arg==(ToFloat+1.0f));", code.print());
        assertEquals("0", Eval2.eval(code, 1));
    }
}
