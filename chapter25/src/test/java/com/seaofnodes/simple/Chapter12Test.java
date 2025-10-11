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
return 3.14;
""");
        code.parse().opto();
        assertEquals("return 3.14;", code.print());
        assertEquals("3.14", Eval2.eval(code,  0));
    }

    @Test
    public void testFloat() {
        CodeGen code = new CodeGen(
"""
return 3.14;
""");
        code.parse().opto();
        assertEquals("return 3.14;", code.print());
        assertEquals("3.14", Eval2.eval(code,  0));
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
        code.parse().opto();
        assertEquals("return Phi(Loop,(flt)arg,(((ToFloat/Phi_guess)+Phi_guess)*0.5f));", code.print());
        assertEquals("3.0", Eval2.eval(code,  9));
        assertEquals("1.414213562373095", Eval2.eval(code,  2));
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
