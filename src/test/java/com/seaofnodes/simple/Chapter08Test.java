package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import com.seaofnodes.simple.node.*;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class Chapter08Test {

    @Test
    public void testEx6() {
        Parser parser = new Parser(
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
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Region45,Phi(Region32,Phi(Loop10,arg,(Phi_arg+1)),Add),Add);", stop.toString());
        assertTrue(stop.ret().ctrl() instanceof RegionNode);
        Assert.assertEquals(5L, Evaluator.evaluate(stop, 1));
        Assert.assertEquals(10L, Evaluator.evaluate(stop, 6));
    }


    @Test
    public void testEx5() {
        Parser parser = new Parser(
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
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Loop11,1,Phi(Region52,Phi_a,(Phi_a+1)));", stop.toString());
        assertTrue(stop.ret().ctrl() instanceof CProjNode);
    }


    @Test
    public void testEx4() {
        Parser parser = new Parser(
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
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Region43,Phi(Loop10,arg,(Phi_arg+1)),Add);", stop.toString());
        assertTrue(stop.ret().ctrl() instanceof RegionNode);
    }

    @Test
    public void testEx3() {
        Parser parser = new Parser(
                """
while(arg < 10) {
    arg = arg + 1;
    if (arg == 6)
        break;
}
return arg;
                """);
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Region32,Phi(Loop10,arg,(Phi_arg+1)),Add);", stop.toString());
        assertTrue(stop.ret().ctrl() instanceof RegionNode);
    }


    @Test
    public void testEx2() {
        Parser parser = new Parser(
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
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Loop10,arg,(Phi_arg+1));", stop.toString());
        assertTrue(stop.ret().ctrl() instanceof CProjNode);
    }


    @Test
    public void testEx1() {
        Parser parser = new Parser(
                """
while(arg < 10) {
    arg = arg + 1;
    if (arg == 5)
        continue;
}
return arg;
                """);
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Loop10,arg,(Phi_arg+1));", stop.toString());
        assertTrue(stop.ret().ctrl() instanceof CProjNode);
    }

    @Test
    public void testRegress1() {
        Parser parser = new Parser(
                """
while( arg < 10 ) {
    int a = arg+2;
    if( a > 4 )
        break;
}
return arg;
                """);
        StopNode stop = parser.parse().iterate();
        assertEquals("return arg;", stop.toString());
    }

    @Test
    public void testRegress2() {
        Parser parser = new Parser("if(1) return 0;  else while(arg>--arg) arg=arg+1; return 0;");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 0;", stop.toString());
    }

    @Test
    public void testBreakOutsideLoop() {
        try {
            new Parser("""
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
        Parser parser = new Parser("""
while(arg < 10) {
    break;
}
return arg;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return arg;", stop.toString());
    }

    @Test
    public void testRegress4() {
        Parser parser = new Parser("""
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
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Region35,Phi(Loop11,1,(Phi_a+1)),Add);", stop.toString());
        assertTrue(stop.ret().ctrl() instanceof RegionNode);
    }

    @Test
    public void testRegress5() {
        Parser parser = new Parser("""
int a = 1;
while(1) {
    a = a + 1;
    if (a<10) continue;
    break;
}
return a;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return (Phi(Loop11,1,Add)+1);", stop.toString());
        assertTrue(stop.ret().ctrl() instanceof CProjNode prj && prj._idx==1 );
    }

}
