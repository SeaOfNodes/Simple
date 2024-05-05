package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import com.seaofnodes.simple.node.StopNode;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SchedulerTest {

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
struct s0 {
    int v0;
    int v1;
}
s0 v4=new s0;
while(new s0.v0) {}
while(v4.v1) {}
return v4.v0;
""");
        StopNode._disablePeephole = true;
        StopNode stop = parser.parse(true);//.iterate(true);
        var eval = new Evaluator(stop);
        assertEquals(0L, eval.evaluate(0, 10));
    }

    @Test
    public void testStoreInIf() {
        Parser parser = new Parser("""
struct S {
    int f;
}
S v0=new S;
if(arg) v0.f=1;
return v0;
""");
        StopNode stop = parser.parse(false).iterate(true);
        var eval = new Evaluator(stop);
        assertObj(eval.evaluate(0, 10), "S", 0L);
        assertObj(eval.evaluate(1, 10), "S", 1L);
    }

}
