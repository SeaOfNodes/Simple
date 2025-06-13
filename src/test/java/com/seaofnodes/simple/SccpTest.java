package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class SccpTest {
    @Test public void testLoopFixedPoint() {
        var code = new CodeGen("int x=1; while(arg) { x=2-x; arg--; } return x;").parse().iter();
        assertFalse(code.link(code._main).ret().expr()._type.isConstant());
        code.opto();
        assertSame(TypeInteger.TRUE,code.link(code._main).ret().expr()._type);
        code.driver(CodeGen.Phase.LocalSched);
        assertEquals("1",Eval2.eval(code,4));
    }

    @Test public void testConservativeCalls() {
        var code = new CodeGen("val f={ int x -> x+1; }; return f(1)+f(2);").parse().iter().opto();
        int[] parms = {0};
        code._start.walk(n -> {
            if( n instanceof ParmNode p && "x".equals(p._label) ) {
                parms[0]++;
                assertSame(TypeInteger.BOT,p._type);
                assertTrue(p.fun().in(1) instanceof StartNode);
            }
            if( n instanceof CallEndNode cend ) assertEquals(1,cend.nIns());
            return null;
        });
        assertEquals(1,parms[0]);
        code.driver(CodeGen.Phase.LocalSched);
        assertEquals("5",Eval2.eval(code,0));
    }
}
