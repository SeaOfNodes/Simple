package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import org.junit.Test;

import static org.junit.Assert.*;

public class Chapter07Test {

    @Test
    public void testExample() {
        CodeGen code = new CodeGen(
"""
while(arg < 10) {
    arg = arg + 1;
}
return arg;
""");
        code.parse();
        assertEquals("return Phi(Loop,arg,(Phi_arg+1));", code.print());
        assertTrue(code.ctrl() instanceof CProjNode);
    }

    @Test
    public void testRegression() {
        CodeGen code = new CodeGen(
"""
int a = 1;
if(arg){}else{
    while(a < 10) {
        a = a + 1;
    }
}
return a;
""");
        code.parse().opto();
        assertEquals("return Phi(Region,1,Phi(Loop,1,(Phi_a+1)));", code.print());
    }

    @Test
    public void testWhileNested() {
        CodeGen code = new CodeGen(
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
        code.parse().opto();
        assertEquals("return Phi(Loop,0,Phi(Loop,Phi_sum,(Phi_sum+Phi(Loop,0,(Phi_j+1)))));", code.print());
    }

    @Test
    public void testWhileScope() {
        CodeGen code = new CodeGen(
"""
int a = 1;
int b = 2;
while(a < 10) {
    if (a == 2) a = 3;
    else b = 4;
}
return b;
""");
        code.parse();
        assertEquals("return Phi(Loop,2,Phi(Region,Phi_b,4));", code.print());
        assertTrue(code.ctrl() instanceof CProjNode);
    }

    @Test
    public void testWhileNestedIfAndInc() {
        CodeGen code = new CodeGen(
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
        code.parse().opto();
        assertEquals("return Phi(Loop,2,(Phi(Region,Phi_b,4)+1));", code.print());
        assertTrue(code.ctrl() instanceof CProjNode);
    }


    @Test
    public void testWhile() {
        CodeGen code = new CodeGen(
"""
int a = 1;
while(a < 10) {
    a = a + 1;
    a = a + 2;
}
return a;
""");
        code.parse();
        assertEquals("return Phi(Loop,1,(Phi_a+3));", code.print());
        assertTrue(code.ctrl() instanceof CProjNode);
    }

    @Test
    public void testWhilePeep() {
        CodeGen code = new CodeGen(
"""
int a = 1;
while(a < 10) {
    a = a + 1;
    a = a + 2;
}
return a;
""");
        code.parse().opto();
        assertEquals("return Phi(Loop,1,(Phi_a+3));", code.print());
        assertTrue(code.ctrl() instanceof CProjNode);
    }

    @Test
    public void testWhile2() {
        CodeGen code = new CodeGen(
"""
int a = 1;
while(arg) a = 2;
return a;
""");
        code.parse();
        assertEquals("return Phi(Loop,1,2);", code.print());
        assertTrue(code.ctrl() instanceof CProjNode);
    }

    @Test
    public void testWhile2Peep() {
        CodeGen code = new CodeGen(
"""
int a = 1;
while(arg) a = 2;
return a;
""");
        code.parse().opto();
        assertEquals("return Phi(Loop,1,2);", code.print());
        assertTrue(code.ctrl() instanceof CProjNode);
    }

    @Test
    public void testWhile3() {
        CodeGen code = new CodeGen(
"""
int a = 1;
while(a < 10) {
    int b = a + 1;
    a = b + 2;
}
return a;
""");
        code.parse();
        assertEquals("return Phi(Loop,1,(Phi_a+3));", code.print());
        assertTrue(code.ctrl() instanceof CProjNode);
    }

    @Test
    public void testWhile3Peep() {
        CodeGen code = new CodeGen(
"""
int a = 1;
while(a < 10) {
    int b = a + 1;
    a = b + 2;
}
return a;
""");
        code.parse().opto();
        assertEquals("return Phi(Loop,1,(Phi_a+3));", code.print());
        assertTrue(code.ctrl() instanceof CProjNode);
    }

    @Test
    public void testWhile4() {
        CodeGen code = new CodeGen(
"""
int a = 1;
int b = 2;
while(a < 10) {
    int b = a + 1;
    a = b + 2;
}
return a;
""");
        code.parse();
        assertEquals("return Phi(Loop,1,(Phi_a+3));", code.print());
        assertTrue(code.ctrl() instanceof CProjNode);
    }

    @Test
    public void testWhile4Peep() {
        CodeGen code = new CodeGen(
"""
int a = 1;
int b = 2;
while(a < 10) {
    int b = a + 1;
    a = b + 2;
}
return a;
""");
        code.parse().opto();
        assertEquals("return Phi(Loop,1,(Phi_a+3));", code.print());
        assertTrue(code.ctrl() instanceof CProjNode);
    }

}
