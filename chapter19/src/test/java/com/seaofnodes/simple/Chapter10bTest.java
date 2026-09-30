package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class Chapter10bTest {
    @Test public void testIndependentField() {
        var code = new CodeGen("""
            struct S { int x; int y; };
            S !s = new S;
            s.x = 42;
            while (arg > 0) { s.y = s.y + arg; arg = arg - 1; }
            return s.x;
            """).parse().opto();
        var stop = code._stop;
        assertEquals("return 42;",stop.toString());
        assertEquals("42",Eval2.eval(code,5));
        assertNull(stop.walk(n -> n instanceof LoadNode ? n : null));
    }

    @Test public void testParallelMemoryPhis() {
        var code = new CodeGen("""
            struct S { int x; int y; int z; };
            S !a = new S; S !b = new S;
            S !p = a; if (arg) p = b;
            while (arg > 0) {
                a.x = p.x + arg;
                a.y = p.y + 2;
                a.z = p.z + 3;
                arg = arg - 1;
            }
            return a;
            """).parse().opto();
        var stop = code._stop;
        int[] phis = {0};
        stop.walk(n -> {
            if (n instanceof MemPhiNode) phis[0]++;
            if (n instanceof MemMergeNode m && m.in(1) instanceof BulkMemPhiNode b)
                for(int a=b._aliases.nextSetBit(0); a>=0; a=b._aliases.nextSetBit(a+1))
                    assertTrue(a<m.nIns() && m.in(a)!=null);
            assertFalse(n instanceof PhiNode && n.isMem() &&
                !(n instanceof MemPhiNode) && !(n instanceof BulkMemPhiNode) && !(n instanceof ParmNode));
            return null;
        });
        assertTrue(phis[0]>=3);
        assertEquals("S{x=1,y=2,z=3}",Eval2.eval(code,4));
    }

    @Test public void testLateSliceWorklist() {
        // Seed 97 exposed a store selecting a bulk predecessor without queuing
        // it. Keep both the worklist assertion and the final heap check.
        for (int seed : new int[]{0,97,123,456}) {
            var code = new CodeGen(Chapter10Test.NESTED_MEMORY,com.seaofnodes.simple.type.TypeInteger.BOT,seed).parse();
            code.opto();
            assertEquals("S{x=9,y=25,z=17}",Eval2.eval(code,4));
        }
    }
}
