package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
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
        CodeGen code = new CodeGen("""
struct S {
    int f;
}
S !v=new S;
v.f = 2;
int i=v.f;
if (arg) v.f=1;
return i;
""");
        code.parse();//.opto();
        var eval = new Evaluator(code._stop);
        assertEquals(2L, eval.evaluate(1, 10));
    }

    @Test
    public void testStoreInIf() {
        CodeGen code = new CodeGen("""
struct S {
    int f;
};
S !v0=new S;
if(arg) v0.f=1;
return v0;
""");
        code.parse().opto();
        var eval = new Evaluator(code._stop);
        assertObj(eval.evaluate(0, 10), "S", 0L);
        assertObj(eval.evaluate(1, 10), "S", 1L);
    }

    @Test
    public void testStoreInIf2() {
        CodeGen code = new CodeGen("""
struct S {
    int f;
};
S !v=new S;
v.f = 2;
int i=new S.f;
i=v.f;
if (arg) v.f=1;
return i;
""");
        code.parse().opto();
        var eval = new Evaluator(code._stop);
        assertEquals(2L, eval.evaluate(0, 10));
        assertEquals(2L, eval.evaluate(1, 10));
    }

}
