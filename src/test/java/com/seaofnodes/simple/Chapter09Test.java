package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import org.junit.Assert;
import org.junit.Test;
import org.junit.Ignore;

import static org.junit.Assert.*;

public class Chapter09Test {

    @Test
    @Ignore
    public void testJig() {
        CodeGen code = new CodeGen("""
int v0=0;
arg=0;
while(v0) {
        while(1) if(arg*arg*0==0) {}
                while(0) {}
    arg=1;
}
return 0;
                """);
        code.parse().opto();
    }

    @Test
    public void testGVN1() {
        CodeGen code = new CodeGen(
                """
int x = arg + arg;
if(arg < 10) {
    return arg + arg;
}
else {
    x = x + 1;
}
return x;
                """);
        code.parse().opto();
        assertEquals("return Phi(Region,(arg<<1),(Shl+1));", code.print());
        Assert.assertEquals( "2", Eval2.eval(code,  1));
        Assert.assertEquals("23", Eval2.eval(code, 11));
    }

    @Test
    public void testGVN2() {
        CodeGen code = new CodeGen("return arg*arg-arg*arg;");
        code.parse().opto();
        assertEquals("return 0;", code.print());
        Assert.assertEquals("0", Eval2.eval(code,  1, 1));
    }

    @Test
    public void testWorklist1() {
        CodeGen code = new CodeGen("""
int step = 1;
while (arg < 10) {
    arg = arg + step + 1;
}
return arg;
""");
        code.parse().opto();
        assertEquals("return Phi(Loop,arg,(Phi_arg+2));", code.print());
        Assert.assertEquals("11", Eval2.eval(code,  1));
    }

    @Test
    public void testWorklist2() {
        CodeGen code = new CodeGen(
                """
int cond = 0;
int one = 1;
while (arg < 10) {
    if (cond) one = 2;
    arg = arg + one*3 + 1;
}
return arg;
                """);
        code.parse().opto();
        assertEquals("return Phi(Loop,arg,(Phi_arg+4));", code.print());
        Assert.assertEquals("13", Eval2.eval(code,  1));
    }

    @Test
    public void testWorklist3() {
        CodeGen code = new CodeGen(
                """
int v1 = 0;
int v2 = 0;
int v3 = 0;
int v4 = 0;
int v5 = 0;
int v6 = 0;
int v7 = 0;
int v8 = 0;
while (arg) {
    if (v1) v2 = 1;
    if (v2) v3 = 1;
    if (v3) v4 = 1;
    if (v4) v5 = 1;
    if (v5) v6 = 1;
    if (v6) v7 = 1;
    if (v7) v8 = 1;
    arg = arg + v8 + 1;
}
return arg;
                """);
        code.parse().opto();
        assertEquals("return 0;", code.print());
    }

    @Test
    public void testRegionPeepBug() {
        CodeGen code = new CodeGen(
                """
int v0=0;
int v1=0;
while(v1+arg) {
    arg=0;
    int v2=v0;
    while(arg+1) {}
    v0=1;
    v1=v2;
}
return 0;
                """);
        code.parse().opto();
        assertEquals("return 0;", code.print());
    }

    @Test
    public void testWhile0() {
        CodeGen code = new CodeGen("while(0) continue; if(0) arg=0; return arg;");
        code.parse().opto();
        assertEquals("return arg;", code.print());
    }

    @Test
    public void testWhile1() {
        CodeGen code = new CodeGen("""
if(0) while(0) {
    int arg=arg;
    while(0) {}
}
return 0;
""");
        code.parse().opto();
        assertEquals("return 0;", code.print());
    }


    @Test
    public void testPrecedence() {
        CodeGen code = new CodeGen("return 3-1+2;");
        code.parse().opto();
        assertEquals("return 4;", code.print());
    }

    @Test
    public void testSwap2() {
        CodeGen code = new CodeGen("return 1+(1+1);");
        code.parse().opto();
        assertEquals("return 3;", code.print());
    }


    @Test
    public void testFuzz0() {
        CodeGen code = new CodeGen("""
int one = 1;
int a = 0;
int zero = 0;
while(arg) {
    a = -(one + a + 2);
    arg = arg + 1;
    one = one + zero;
}
return a;
""");
        code.parse().opto();
        assertEquals("return Phi(Loop,0,(-(Phi_a+3)));", code.print());
    }

    @Test
    public void testFuzz1() {
        CodeGen code = new CodeGen("""
while(1) {}
while(arg) break;
while(arg) arg=0;
arg=0;
int v0=0!=0<-0;
return -0+0+0;
""");
        code.parse().opto();
        assertEquals("return Top;", code.print());
    }


    @Test
    public void testFuzz2() {
        CodeGen code = new CodeGen("return 0+-0;");
        code.parse().opto();
        assertEquals("return 0;", code.print());
    }

    @Test
    public void testFuzz3() {
        CodeGen code = new CodeGen("int v0=0; while(0==69) while(v0) return 0; return 0;");
        code.parse().opto();
        assertEquals("return 0;", code.print());
    }

    @Test
    public void testFuzz4() {
        CodeGen code = new CodeGen("""
while(1) {
    arg=0<=0;
    if(1<0) while(arg==-0) arg=arg-arg;
}
""");
        code.parse().opto();
        assertEquals("return Top;", code.print());
    }

    @Test
    public void testFuzz5() {
        CodeGen code = new CodeGen("""
{
    int v0=0;
    while(1)
            int v1=0--0;
    while(v0)
        break;
    while(-v0) {
        while(0+0+v0) continue;
        break;
    }
    if(-0!=-0+0+v0) while(0+0+0+0)
                break;
}
return 0!=0;
""");
        code.parse().opto();
        assertEquals("return Top;", code.print());
    }

    @Test
    public void testFuzz6() {
        CodeGen code = new CodeGen(
"""
int v0=0;
while(0==1) while(v0)
        v0=1+v0;
return 0;        
""");
        code.parse().opto();
        assertEquals("return 0;", code.print());
    }

    @Test
    public void testFuzz7() {
        CodeGen code = new CodeGen("""
while(1) {}
int v0=0;
while(v0)
    {}
int v1=0;
while(1)
        v1=1;
return v1+v0;
                                   """);
        code.parse().opto();
        assertEquals("return Top;", code.print());
    }

    @Test
    public void testFuzz8() {
        CodeGen code = new CodeGen("while(arg) arg = arg - 1;  return arg;");
        code.parse().opto();
        assertEquals("return 0;", code.print());
    }

    @Test
    public void testMeet() {
        Type t1 = Type.TOP;
        Type t2 = TypeInteger.TOP;
        Assert.assertEquals(TypeInteger.TOP, t1.meet(t2));
        Assert.assertEquals(TypeInteger.TOP, t2.meet(t1));
        t1 = Type.BOTTOM;
        t2 = TypeInteger.BOT;
        Assert.assertEquals(Type.BOTTOM, t1.meet(t2));
        Assert.assertEquals(Type.BOTTOM, t2.meet(t1));
    }

}
