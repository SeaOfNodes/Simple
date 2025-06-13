package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class SccpTest {
    @Test public void testCallArgumentFlow() {
        var code = new CodeGen("val f={ int x -> x+1; }; return f(1)+f(2);").parse().iter().opto();
        int[] parms = {0};
        code._start.walk(n -> {
            if( n instanceof ParmNode p && "x".equals(p._label) ) {
                parms[0]++;
                TypeInteger t = (TypeInteger)p._type;
                assertEquals(1,t._min);
                assertEquals(2,t._max);
                assertFalse(p.fun().in(1) instanceof StartNode);
            }
            return null;
        });
        assertEquals(1,parms[0]);
        code.driver(CodeGen.Phase.LocalSched);
        assertEquals("5",Eval2.eval(code,0));
    }
}
