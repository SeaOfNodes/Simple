package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.*;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class Chapter08Test {

    @Test
    public void testEx6() {
        CodeGen code = new CodeGen(
                """
while(arg < 10) {
    arg = arg + 1;
    if (arg == 5)
        break;
    if (arg == 6)
        break;
}
return arg;
                """);
        code.parse().opto();
        assertEquals("return Phi(Region,(Phi(Loop,arg,Add)+1),Add,Phi_arg);", code.print());
        assertTrue(code.ctrl() instanceof RegionNode);
        Assert.assertEquals( "5", Eval2.eval(code, 1));
        Assert.assertEquals("10", Eval2.eval(code, 6));
    }


    @Test
    public void testEx5() {
        CodeGen code = new CodeGen(
                """
int a = 1;
while(arg < 10) {
    arg = arg + 1;
    if (arg == 5)
        continue;
    if (arg == 7)
        continue;
    a = a + 1;
}
return a;
                """);
        code.parse().opto();
        assertEquals("return Phi(Loop,1,Phi(Region,Phi_a,(Phi_a+1),Phi_a));", code.print());
        assertTrue(code.ctrl() instanceof CProjNode);
    }


    @Test
    public void testEx4() {
        CodeGen code = new CodeGen(
                """
while(arg < 10) {
    arg = arg + 1;
    if (arg == 5)
        continue;
    if (arg == 6)
        break;
}
return arg;
                """);
        code.parse().opto();
        assertEquals("return Phi(Region,Phi(Loop,arg,(Phi_arg+1)),Add);", code.print());
        assertTrue(code.ctrl() instanceof RegionNode);
    }

    @Test
    public void testEx3() {
        CodeGen code = new CodeGen(
                """
while(arg < 10) {
    arg = arg + 1;
    if (arg == 6)
        break;
}
return arg;
                """);
        code.parse().opto();
        assertEquals("return Phi(Region,Phi(Loop,arg,(Phi_arg+1)),Add);", code.print());
        assertTrue(code.ctrl() instanceof RegionNode);
    }


    @Test
    public void testEx2() {
        CodeGen code = new CodeGen(
                """
while(arg < 10) {
    arg = arg + 1;
    if (arg == 5)
        continue;
    if (arg == 6)
        continue;
}
return arg;
                """);
        code.parse().opto();
        assertEquals("return Phi(Loop,arg,(Phi_arg+1));", code.print());
        assertTrue(code.ctrl() instanceof CProjNode);
    }


    @Test
    public void testEx1() {
        CodeGen code = new CodeGen(
                """
while(arg < 10) {
    arg = arg + 1;
    if (arg == 5)
        continue;
}
return arg;
                """);
        code.parse().opto();
        assertEquals("return Phi(Loop,arg,(Phi_arg+1));", code.print());
        assertTrue(code.ctrl() instanceof CProjNode);
    }

    @Test
    public void testRegress1() {
        CodeGen code = new CodeGen(
                """
while( arg < 10 ) {
    int a = arg+2;
    if( a > 4 )
        break;
}
return arg;
                """);
        code.parse().opto();
        assertEquals("return arg;", code.print());
    }

    @Test
    public void testRegress2() {
        CodeGen code = new CodeGen("if(1) return 0;  else while(arg>- -arg) arg=arg+1; return 0;");
        code.parse().opto();
        assertEquals("return 0;", code.print());
    }

    @Test
    public void testBreakOutsideLoop() {
        try {
            new CodeGen("""
if(arg <= 10) {
    break;
    arg = arg + 1;
}
return arg;
""").parse();
            fail();
        } catch( RuntimeException e ) {
            assertEquals("No active loop for a break or continue",e.getMessage());
        }
    }

    @Test
    public void testRegress3() {
        CodeGen code = new CodeGen("""
while(arg < 10) {
    break;
}
return arg;
""");
        code.parse().opto();
        assertEquals("return arg;", code.print());
    }

    @Test
    public void testRegress4() {
        CodeGen code = new CodeGen("""
int a = 1;
while(arg < 10) {
    a = a + 1;
    if (arg > 2) {
        int a = 17;
        break;
    }
}
return a;
""");
        code.parse().opto();
        assertEquals("return Phi(Region,Phi(Loop,1,(Phi_a+1)),Add);", code.print());
        assertTrue(code.ctrl() instanceof RegionNode);
    }

    @Test
    public void testRegress5() {
        CodeGen code = new CodeGen("""
int a = 1;
while(1) {
    a = a + 1;
    if (a<10) continue;
    break;
}
return a;
""");
        code.parse().opto();
        assertEquals("return (Phi(Loop,1,Add)+1);", code.print());
        assertTrue(code.ctrl() instanceof CProjNode prj && prj._idx==1 );
    }

}
