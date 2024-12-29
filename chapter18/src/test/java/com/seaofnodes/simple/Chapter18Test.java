package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import org.junit.Test;
import org.junit.Ignore;

import static org.junit.Assert.*;

public class Chapter18Test {

    @Test
    public void testJig() {
        CodeGen code = new CodeGen("""
return 0;
""");
        code.parse().opto().typeCheck().GCM();
        assertEquals("return 0;", code._stop.toString());
        assertEquals("0", Eval2.eval(code,  0));
    }


    // ---------------------------------------------------------------
    @Test
    public void testType0() {
        CodeGen code = new CodeGen("""
{int -> int}? x2 = null; // null function ptr
return x2;
""");
        code.parse().opto();
        assertEquals("return null;", code._stop.toString());
        assertEquals("null", Eval2.eval(code, 0 ) );
    }

    @Ignore @Test
    public void testFcn0() {
        CodeGen code = new CodeGen("""
{int -> int}? sq = { int x ->
    x*x
};
return sq;
""");
        code.parse().opto();
        assertEquals("return fcn;", code._stop.toString());
        assertEquals("1", Eval2.eval(code, 0));
    }

}
