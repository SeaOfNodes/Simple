package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SchedulerTest {

    @Test public void testMemoryPhiAcrossSiblingBranch() {
        // The inner memory Phi defines the load's memory. The outer Phi's
        // other input comes from a branch not dominated by that definition.
        String src = """
            struct S { int x; }; !S !a=new S; !S !b=new S;
            a.x=10; b.x=20; !S !p=a; if(arg>3) p=b;
            int result=0;
            if(arg>0) {
                if(arg>2) p.x=30; else p.x=40;
                result=a.x;
            }
            return result;
            """;
        var code = new CodeGen(src).driver(CodeGen.Phase.LocalSched);
        long[] expected = {0,40,40,30,10,10};
        for( int arg=0; arg<expected.length; arg++ )
            assertEquals(Long.toString(expected[arg]),Eval2.eval(code,arg));
    }


    @Test
    @Ignore
    public void testJig() {
        CodeGen code = new CodeGen("""
struct S {
    int f;
}
!S !v=new S;
v.f = 2;
int i=v.f;
if (arg) v.f=1;
return i;
""");
        code.parse();//.iter().finalizeIR();
        assertEquals("2", Eval2.eval(code, 1, 10));
    }

    @Test
    public void testStoreInIf() {
        CodeGen code = new CodeGen("""
struct S {
    int f;
};
!S !v0=new S;
if(arg) v0.f=1;
return v0;
""");
        code.parse().iter().finalizeIR();
        assertEquals("S{f=0}", Eval2.eval(code, 0, 10));
        assertEquals("S{f=1}", Eval2.eval(code, 1, 10));
    }

    @Test
    public void testStoreInIf2() {
        CodeGen code = new CodeGen("""
struct S {
    int f;
};
!S !v=new S;
v.f = 2;
int i=new S.f;
i=v.f;
if (arg) v.f=1;
return i;
""");
        code.parse().iter().finalizeIR();
        assertEquals("2", Eval2.eval(code, 0, 10));
        assertEquals("2", Eval2.eval(code, 1, 10));
    }

}
