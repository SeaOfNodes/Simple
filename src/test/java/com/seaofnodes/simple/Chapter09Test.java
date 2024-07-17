package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import com.seaofnodes.simple.node.*;
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
        Parser parser = new Parser("""
int v0=0;
arg=0;
while(v0) {
        while(1) if(arg*arg*0==0) {}
                while(0) {}
    arg=1;
}
return 0;
                """);
        StopNode stop = parser.parse().iterate();
    }

    @Test
    public void testGVN1() {
        Parser parser = new Parser(
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
        StopNode stop = parser.parse();
        assertEquals("Stop[ return (arg*2); return (Mul+1); ]", stop.toString());
        Assert.assertEquals(2L, Evaluator.evaluate(stop, 1));
        Assert.assertEquals(23L, Evaluator.evaluate(stop, 11));
    }

    @Test
    public void testGVN2() {
        Parser parser = new Parser(
                """
return arg*arg-arg*arg;
                """);
        StopNode stop = parser.parse();
        assertEquals("return 0;", stop.toString());
        Assert.assertEquals(0L, Evaluator.evaluate(stop, 1));
    }

    @Test
    public void testWorklist1() {
        Parser parser = new Parser(
                """
int step = 1;
while (arg < 10) {
    arg = arg + step + 1;
}
return arg;
                """);
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Loop9,arg,(Phi_arg+2));", stop.toString());
        Assert.assertEquals(11L, Evaluator.evaluate(stop, 1));
    }

    @Test
    public void testWorklist2() {
        Parser parser = new Parser(
                """
int cond = 0;
int one = 1;
while (arg < 10) {
    if (cond) one = 2;
    arg = arg + one*3 + 1;
}
return arg;
                """);
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Loop10,arg,(Phi_arg+4));", stop.toString());
        Assert.assertEquals(13L, Evaluator.evaluate(stop, 1));
    }

    @Test
    public void testWorklist3() {
        Parser parser = new Parser(
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
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Loop16,arg,(Phi_arg+1));", stop.toString());
    }

    @Test
    public void testRegionPeepBug() {
        Parser parser = new Parser(
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
                """);
        StopNode stop = parser.parse().iterate();
        assertEquals("Stop[ return 0; return 0; ]", stop.toString());
    }

    @Test
    public void testWhile0() {
        Parser parser = new Parser("while(0) continue; if(0) arg=0;");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 0;", stop.toString());
    }

    @Test
    public void testWhile1() {
        Parser parser = new Parser("""
if(0) while(0) {
    int arg=arg;
    while(0) {}
}
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 0;", stop.toString());
    }


    @Test
    public void testPrecedence() {
        Parser parser = new Parser("return 3-1+2;");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 4;", stop.toString());
    }

    @Test
    public void testSwap2() {
        Parser parser = new Parser("return 1+(1+1);");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 3;", stop.toString());
    }


    @Test
    public void testFuzz0() {
        Parser parser = new Parser("""
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
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Loop11,0,(-(Phi_a+3)));", stop.toString());
    }

    @Test
    public void testFuzz1() {
        Parser parser = new Parser("""
while(1) {}
while(arg) break;
while(arg) arg=0;
arg=0;
int v0=0!=0<-0;
return -0+0+0;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 0;", stop.toString());
    }


    @Test
    public void testFuzz2() {
        Parser parser = new Parser("return 0+-0;");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 0;", stop.toString());
    }

    @Test
    public void testFuzz3() {
        Parser parser = new Parser("int v0=0; while(0==69) while(v0) return 0;");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 0;", stop.toString());
    }

    @Test
    public void testFuzz4() {
        Parser parser = new Parser("""
while(1) {
    arg=0<=0;
    if(1<0) while(arg==-0) arg=arg-arg;
}
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 0;", stop.toString());
    }

    @Test
    public void testFuzz5() {
        Parser parser = new Parser("""
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
        StopNode stop = parser.parse().iterate();
        assertEquals("return 0;", stop.toString());
    }

    @Test
    public void testFuzz6() {
        Parser parser = new Parser(
"""
int v0=0;
while(0==1) while(v0)
        v0=1+v0;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 0;", stop.toString());
    }

    @Test
    public void testFuzz7() {
        Parser parser = new Parser("""
while(1) {}
int v0=0;
while(v0)
    {}
int v1=0;
while(1)
        v1=1;
return v1+v0;
                                   """);
        StopNode stop = parser.parse().iterate();
        assertEquals("return 0;", stop.toString());
    }

    @Test
    public void testFuzz8() {
        Parser parser = new Parser("while(arg) arg = arg - 1;  return arg;");
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Loop8,arg,(Phi_arg-1));", stop.toString());
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
