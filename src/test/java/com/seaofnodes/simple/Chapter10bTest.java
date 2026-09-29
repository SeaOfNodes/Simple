package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.evaluator.Evaluator;
import org.junit.Test;
import static org.junit.Assert.*;

public class Chapter10bTest {
    @Test public void testIndependentField() {
        var stop = new Parser("""
            struct S { int x; int y; }
            S s = new S;
            s.x = 42;
            while (arg > 0) { s.y = s.y + arg; arg = arg - 1; }
            return s.x;
            """).parse().iterate();
        assertEquals("return 42;",stop.toString());
        assertEquals(42L,Evaluator.evaluate(stop,5));
        assertNull(stop.walk(n -> n instanceof LoadNode ? n : null));
    }

    @Test public void testParallelMemoryPhis() {
        var stop = new Parser("""
            struct S { int x; int y; int z; }
            S a = new S; S b = new S;
            S p = a; if (arg) p = b;
            while (arg > 0) {
                a.x = p.x + arg;
                a.y = p.y + 2;
                a.z = p.z + 3;
                arg = arg - 1;
            }
            return a;
            """).parse().iterate();
        int[] phis = {0};
        stop.walk(n -> {
            if (n instanceof MemPhiNode) phis[0]++;
            if (n instanceof MemMergeNode m && m.in(1) instanceof BulkMemPhiNode b)
                for(int a=b._aliases.nextSetBit(0); a>=0; a=b._aliases.nextSetBit(a+1))
                    assertTrue(a<m.nIns() && m.in(a)!=null);
            assertFalse(n instanceof PhiNode && n.isMem() &&
                !(n instanceof MemPhiNode) && !(n instanceof BulkMemPhiNode));
            return null;
        });
        assertTrue(phis[0]>=3);
        assertArrayEquals(new Object[]{1L,2L,3L},((Evaluator.Obj)Evaluator.evaluate(stop,4)).fields());
    }

    @Test public void testLateSliceWorklist() throws ReflectiveOperationException {
        // Seed 97 exposed a store selecting a bulk predecessor without queuing
        // it. Keep both the worklist assertion and the final heap check.
        for (int seed : new int[]{0,97,123,456}) {
            var parser = new Parser(Chapter10Test.NESTED_MEMORY);
            var field = IterPeeps.class.getDeclaredField("WORK");
            field.setAccessible(true);
            Object work = field.get(null);
            field = work.getClass().getDeclaredField("_R");
            field.setAccessible(true);
            ((java.util.Random)field.get(work)).setSeed(seed);
            var stop = parser.parse().iterate();
            assertArrayEquals(new Object[]{9L,25L,17L},((Evaluator.Obj)Evaluator.evaluate(stop,4)).fields());
        }
    }
}
