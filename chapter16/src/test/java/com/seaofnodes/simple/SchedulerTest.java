package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import com.seaofnodes.simple.node.StopNode;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SchedulerTest {

    @Test public void testMemoryPhiAcrossSiblingBranch() {
        // The inner memory Phi defines the load's memory. The outer Phi's
        // other input comes from a branch not dominated by that definition.
        String src = """
            struct S { int x; }; S a=new S; S b=new S;
            a.x=10; b.x=20; S p=a; if(arg>3) p=b;
            int result=0;
            if(arg>0) {
                if(arg>2) p.x=30; else p.x=40;
                result=a.x;
            }
            return result;
            """;
        StopNode stop = new Parser(src).parse().iterate();
        var eval = new Evaluator(stop);
        long[] expected = {0,40,40,30,10,10};
        for( int arg=0; arg<expected.length; arg++ )
            assertEquals(expected[arg],eval.evaluate(arg,100));
    }


    private static void assertObj(Object obj, String name, Object... fields) {
        assertEquals(Evaluator.Obj.class, obj.getClass());
        var o = (Evaluator.Obj)obj;
        assertEquals(name, o.struct()._name);
        Assert.assertArrayEquals(fields, o.fields());
    }

    @Test
    @Ignore
    public void testJig() {
        Parser parser = new Parser("""
struct S {
    int f;
}
S v=new S;
v.f = 2;
int i=v.f;
if (arg) v.f=1;
return i;
""");
        StopNode stop = parser.parse();//.iterate();
        var eval = new Evaluator(stop);
        assertEquals(2L, eval.evaluate(1, 10));
    }

    @Test
    public void testStoreInIf() {
        Parser parser = new Parser("""
struct S {
    int f;
};
S v0=new S;
if(arg) v0.f=1;
return v0;
""");
        StopNode stop = parser.parse().iterate();
        var eval = new Evaluator(stop);
        assertObj(eval.evaluate(0, 10), "S", 0L);
        assertObj(eval.evaluate(1, 10), "S", 1L);
    }

    @Test
    public void testStoreInIf2() {
        Parser parser = new Parser("""
struct S {
    int f;
};
S v=new S;
v.f = 2;
int i=new S.f;
i=v.f;
if (arg) v.f=1;
return i;
""");
        StopNode stop = parser.parse().iterate();
        var eval = new Evaluator(stop);
        assertEquals(2L, eval.evaluate(0, 10));
        assertEquals(2L, eval.evaluate(1, 10));
    }

}
