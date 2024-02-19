package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
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
// Insert test case here
""");
        StopNode stop = parser.parse().iterate(true);
    }

    @Test
    public void testWhile0() {
        Parser parser = new Parser("while(0) continue; if(0) arg=0;");
        StopNode stop = parser.parse().iterate(true);
        assertEquals("Stop[ ]", stop.toString());
    }

    @Test
    public void testWhile1() {
        Parser parser = new Parser("""
if(0) while(0) {
    int arg=arg;
    while(0) {}
}                                   
""");
        StopNode stop = parser.parse().iterate(true);
        assertEquals("Stop[ ]", stop.toString());
    }


    @Test
    public void testPrecedence() {
        Parser parser = new Parser("return 3-1+2;");
        StopNode stop = parser.parse().iterate(true);
        assertEquals("return 4;", stop.toString());
    }

    @Test
    public void testSwap2() {
        Parser parser = new Parser("return 1+(1+1);");
        StopNode stop = parser.parse().iterate(true);
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
        StopNode stop = parser.parse().iterate(true);
        assertEquals("return Phi(Loop9,0,(-(Phi_a+3)));", stop.toString());
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
        StopNode stop = parser.parse().iterate(true);
        assertEquals("Stop[ ]", stop.toString());
    }


    @Test
    public void testFuzz2() {
        Parser parser = new Parser("return 0+-0;");
        StopNode stop = parser.parse().iterate(true);
        assertEquals("return 0;", stop.toString());
    }
        
    @Test
    public void testFuzz3() {
        Parser parser = new Parser("int v0=0; while(0==69) while(v0) return 0;");
        StopNode stop = parser.parse().iterate(true);
        assertEquals("Stop[ ]", stop.toString());
    }

    @Test
    public void testFuzz4() {
        Parser parser = new Parser("""
while(1) {
    arg=0<=0;
    if(1<0) while(arg==-0) arg=arg-arg;
}
""");
        StopNode stop = parser.parse().iterate(true);
        assertEquals("Stop[ ]", stop.toString());
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
        StopNode stop = parser.parse().iterate(true);
        assertEquals("Stop[ ]", stop.toString());
    }

}
