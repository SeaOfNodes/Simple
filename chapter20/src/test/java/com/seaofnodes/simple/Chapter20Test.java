package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
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
        assertEquals("return (ori,arg);", code._stop.toString());
    }


}
