package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import org.junit.Test;

import static org.junit.Assert.*;

public class Chapter07Test {

    @Test
    public void testExample() {
        Parser parser = new Parser(
                """
                while(arg < 10) {
                    arg = arg + 1;
                    #showGraph;
                }
                return arg;
                """);
        Node._disablePeephole = true;
        StopNode stop = parser.parse(true);
        assertEquals("return Phi(Loop8,arg,(Phi_arg+1));", stop.toString());
        assertTrue(stop.ret().ctrl() instanceof CProjNode);
        Node._disablePeephole = false;
    }

    @Test
    public void testRegression() {
        Parser parser = new Parser(
"""
int a = 1;
if(arg){}else{
    while(a < 10) {
        a = a + 1;
    }
}
return a;
""");
        StopNode stop = parser.parse().iterate(true);
        assertEquals("return Phi(Region26,1,Phi(Loop15,1,(Phi_a+1)));", stop.toString());
    }

    @Test
    public void testWhileNested() {
        Parser parser = new Parser(
"""
int sum = 0;
int i = 0;
while(i < arg) {
    i = i + 1;
    int j = 0;
    while( j < arg ) {
        sum = sum + j;
        j = j + 1;
    }
}
return sum;
""");
        StopNode stop = parser.parse().iterate(true);
        assertEquals("return Phi(Loop10,0,Phi(Loop22,Phi_sum,(Phi_sum+Phi(Loop,0,(Phi_j+1)))));", stop.toString());
        System.out.println(IRPrinter.prettyPrint(stop,99));
    }

    @Test
    public void testWhileScope() {
        Parser parser = new Parser(
"""
int a = 1;
int b = 2;
while(a < 10) {
    if (a == 2) a = 3;
    else b = 4;
}
return b;
""");
        Node._disablePeephole = true;
        StopNode stop = parser.parse(true);
        assertEquals("return Phi(Loop10,2,Phi(Region29,Phi_b,4));", stop.toString());
        assertTrue(stop.ret().ctrl() instanceof CProjNode);
        Node._disablePeephole = false;
        System.out.println(IRPrinter.prettyPrint(stop,99));
    }

    @Test
    public void testWhileNestedIfAndInc() {
        Parser parser = new Parser(
"""
int a = 1;
int b = 2;
while(a < 10) {
    if (a == 2) a = 3;
    else b = 4;
    b = b + 1;
    a = a + 1;
}
return b;
""");
        StopNode stop = parser.parse().iterate(true);
        assertEquals("return Phi(Loop10,2,(Phi(Region29,Phi_b,4)+1));", stop.toString());
        assertTrue(stop.ret().ctrl() instanceof CProjNode);
        System.out.println(IRPrinter.prettyPrint(stop,99));
    }


    @Test
    public void testWhile() {
        Parser parser = new Parser(
"""
int a = 1;
while(a < 10) {
    a = a + 1;
    a = a + 2;
}
return a;
""");
        Node._disablePeephole = true;
        StopNode stop = parser.parse(true);
        assertEquals("return Phi(Loop9,1,((Phi_a+1)+2));", stop.toString());
        assertTrue(stop.ret().ctrl() instanceof CProjNode);
        Node._disablePeephole = false;
    }

    @Test
    public void testWhilePeep() {
        Parser parser = new Parser(
"""
int a = 1;
while(a < 10) {
    a = a + 1;
    a = a + 2;
}
return a;
""");
        StopNode stop = parser.parse().iterate(true);
        assertEquals("return Phi(Loop9,1,(Phi_a+3));", stop.toString());
        assertTrue(stop.ret().ctrl() instanceof CProjNode);
    }

    @Test
    public void testWhile2() {
        Parser parser = new Parser(
"""
int a = 1;
while(arg) a = 2;
return a;
""");
        Node._disablePeephole = true;
        StopNode stop = parser.parse(true);
        assertEquals("return Phi(Loop9,1,2);", stop.toString());
        assertTrue(stop.ret().ctrl() instanceof CProjNode);
        Node._disablePeephole = false;
        System.out.println(IRPrinter.prettyPrint(stop,99));
    }

    @Test
    public void testWhile2Peep() {
        Parser parser = new Parser(
"""
int a = 1;
while(arg) a = 2;
return a;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Loop9,1,2);", stop.toString());
        assertTrue(stop.ret().ctrl() instanceof CProjNode);
        System.out.println(IRPrinter.prettyPrint(stop, 99));
    }

    @Test
    public void testWhile3() {
        Parser parser = new Parser(
"""
int a = 1;
while(a < 10) {
    int b = a + 1;
    a = b + 2;
}
return a;
""");
        Node._disablePeephole = true;
        StopNode stop = parser.parse(true);
        assertEquals("return Phi(Loop9,1,((Phi_a+1)+2));", stop.toString());
        assertTrue(stop.ret().ctrl() instanceof CProjNode);
        Node._disablePeephole = false;
    }

    @Test
    public void testWhile3Peep() {
        Parser parser = new Parser(
"""
int a = 1;
while(a < 10) {
    int b = a + 1;
    a = b + 2;
}
return a;
""");
        StopNode stop = parser.parse().iterate(true);
        assertEquals("return Phi(Loop9,1,(Phi_a+3));", stop.toString());
        assertTrue(stop.ret().ctrl() instanceof CProjNode);
    }

    @Test
    public void testWhile4() {
        Parser parser = new Parser(
"""
int a = 1;
int b = 2;
while(a < 10) {
    int b = a + 1;
    a = b + 2;
}
return a;
""");
        Node._disablePeephole = true;
        StopNode stop = parser.parse(true);
        assertEquals("return Phi(Loop10,1,((Phi_a+1)+2));", stop.toString());
        assertTrue(stop.ret().ctrl() instanceof CProjNode);
        Node._disablePeephole = false;
    }

    @Test
    public void testWhile4Peep() {
        Parser parser = new Parser(
"""
int a = 1;
int b = 2;
while(a < 10) {
    int b = a + 1;
    a = b + 2;
}
return a;
""");
        StopNode stop = parser.parse().iterate(true);
        assertEquals("return Phi(Loop10,1,(Phi_a+3));", stop.toString());
        assertTrue(stop.ret().ctrl() instanceof CProjNode);
    }

}
