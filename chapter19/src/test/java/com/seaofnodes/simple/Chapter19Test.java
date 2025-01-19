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
}
