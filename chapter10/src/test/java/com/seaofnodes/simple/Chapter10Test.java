package com.seaofnodes.simple;

import com.seaofnodes.simple.node.StopNode;
import com.seaofnodes.simple.type.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Chapter10Test {


    @Test
    public void testStruct() {
        Parser parser = new Parser(
                """
struct Bar {
    int a;
    int b;
}
struct Foo {
    int x;
}
Foo foo = null;
Bar bar = new Bar;
bar.a = 1;
bar.a = 2;
return bar.a;
                """);
        StopNode stop = parser.parse(true).iterate(true);
        System.out.println(IRPrinter.prettyPrint(stop, 99, true));
        assertEquals("return .a;", stop.toString());
    }

    @Test
    public void testExample() {
        Parser parser = new Parser(
                """
struct Vector2D { int x; int y; }

Vector2D v = new Vector2D;
v.x = 1;
if (arg)
    v.y = 2;
else
    v.y = 3;
return v;
                """);
        StopNode stop = parser.parse(true).iterate(true);
        System.out.println(IRPrinter.prettyPrint(stop, 99, true));
        assertEquals("return new Ptr(Vector2D);", stop.toString());
    }

    @Test
    public void testBug() {
        Parser parser = new Parser(
                """
struct s0 {
    int v0;
}
s0 v1=null;
int v3=v1.zAicm;
                """);
        try {
            StopNode stop = parser.parse(true);
            fail();
        }
        catch (RuntimeException e) {
            assertEquals("Attempt to access 'zAicm' from null reference",e.getMessage());
        }
    }

    @Test
    public void testBug2() {
        Parser parser = new Parser(
                """
struct s0 {
    int v0;
}
arg=0+new s0.0;
                """);
        try {
            StopNode stop = parser.parse(true);
            fail();
        }
        catch (RuntimeException e) {
            assertEquals("Expected an identifier, found 'null'",e.getMessage());
        }
    }

    @Test
    public void testLoop() {
        Parser parser = new Parser(
                """
struct Bar {
    int a;
}
Bar bar = new Bar;
while (arg) {
    bar.a = bar.a + 2;
    arg = arg + 1;
}
return bar.a;                
                """);

        StopNode stop = parser.parse(true).iterate(true);
        assertEquals("return .a;", stop.toString());
    }

    @Test
    public void testIf() {
        Parser parser = new Parser(
                """
struct Bar {
    int a;
}
Bar bar = new Bar;
if (arg) bar = null;
bar.a = 1;
return bar.a;             
                """);
        StopNode stop = parser.parse(true).iterate(true);
        assertEquals("return .a;", stop.toString());
    }

    @Test
    public void testIf2() {
        Parser parser = new Parser(
                """
struct Bar {
    int a;
}
Bar bar = null;
if (arg) bar = new Bar;
bar.a = 1;
return bar.a;             
                """);
        StopNode stop = parser.parse(true).iterate(true);
        assertEquals("return .a;", stop.toString());
    }

    @Test
    public void testIf3() {
        Parser parser = new Parser(
                """
struct Bar {
    int a;
}
Bar bar = null;
if (arg) bar = null;
bar.a = 1;
return bar.a;             
                """);
        try {
            StopNode stop = parser.parse(true);
            fail();
        }
        catch (RuntimeException e) {
            assertEquals("Attempt to access 'a' from null reference", e.getMessage());
        }
    }

    @Test
    public void testWhileWithNullInside() {
        Parser parser = new Parser(
                """
struct s0 {int v0;}
s0 v0 = new s0;
int ret = 0;
while(arg) {
    ret = v0.v0;
    v0 = null;
    arg = arg - 1;
}
return ret;     
                """);
        StopNode stop = parser.parse(true).iterate(true);
        assertEquals("return Phi(Loop11,0,.v0);", stop.toString());
    }

    @Test
    public void testRedeclareStruct() {
        Parser parser = new Parser(
                """
struct s0 {
    int v0;
}
s0 v1=new s0;
s0 v1;
v1=new s0;  
                """);
        try {
            StopNode stop = parser.parse(true);
            fail();
        }
        catch (RuntimeException e) {
            assertEquals("Redefining name 'v1'", e.getMessage());
        }
    }

    @Test
    public void test1() {
        Parser parser = new Parser(
                """
struct s0 {int v0;}
s0 ret = new s0;
while(arg) {
    s0 v0 = new s0;
    v0.v0 = arg;
    arg = arg-1;
    if (arg==5) ret=v0;
    #showGraph;
}
return ret;                
                """);
        StopNode stop = parser.parse(true).iterate(true);
        System.out.println(IRPrinter.prettyPrint(stop, 99, true));
        assertEquals("return Phi(Loop10,new Ptr(s0),Phi(Region31,new Ptr(s0),Phi_ret));", stop.toString());
    }

    @Test
    public void test2() {
        Parser parser = new Parser(
                """
struct s0 {int v0;}
s0 ret = new s0;
s0 v0 = new s0;
while(arg) {
    v0.v0 = arg;
    arg = arg-1;
    if (arg==5) ret=v0;
        #showGraph;
}
return ret;          
                """);
        StopNode stop = parser.parse(true).iterate(true);
        System.out.println(IRPrinter.prettyPrint(stop, 99, true));
        assertEquals("return Phi(Loop13,new Ptr(s0),Phi(Region32,new Ptr(s0),Phi_ret));", stop.toString());
    }


    @Test
    public void test3() {
        Parser parser = new Parser(
                """
struct s0 {int v0;}
s0 ret = new s0;
while(arg < 10) {
    s0 v0 = new s0;
    if (arg == 5) ret=v0;
    arg = arg + 1;
}
return ret;                
                """);
        StopNode stop = parser.parse(true).iterate(true);
        System.out.println(IRPrinter.prettyPrint(stop, 99, true));
        assertEquals("return Phi(Loop10,new Ptr(s0),Phi(Region30,new Ptr(s0),Phi_ret));", stop.toString());
    }

    @Test
    public void testBug3() {
        Parser parser = new Parser(
                """
struct s0 {
    int f0;
}
if(0>=0) return new s0;
return new s0;
int v0=null.f0;            
                """);
        try {
            StopNode stop = parser.parse(true);
            fail();
        }
        catch (Exception e) {
            assertEquals("Attempt to access 'f0' from null reference", e.getMessage());
        }
    }

    @Test
    public void testBug4() {
        Parser parser = new Parser(
                """
if(0) {
    while(0) if(arg) continue;
    int v0=0;
    while(1) {
        int arg=-arg;
        v0=arg;
    }
}         
                   """);
        StopNode stop = parser.parse(true).iterate(true);
        assertEquals("Stop[ ]", stop.toString());
    }

    @Test
    public void testBug5() {
        Parser parser = new Parser(
                """
struct s0 {
    int f0;
}
if(0) return 0;
else return new s0;
if(new s0.f0) return 0;                   
                    """);
        StopNode stop = parser.parse(true).iterate(true);
        assertEquals("return new Ptr(s0);", stop.toString());
    }

    @Test
    public void testBug6MissedWorklist() {
        Parser parser = new Parser(
                """
while(0) {}
int v4=0;
while(0<arg) {
    v4=v4+1;
    while(1) v4=-v4;
    while(0) arg=-1;
}
return 0;                
                    """);
        StopNode stop = parser.parse().iterate(true);
    }

    @Test
    public void testBug7() {
        Parser parser = new Parser(
                """
struct s0 {
    int f0;
}
s0 v0=new s0;
while(v0.f0) {}
s0 v1=v0;          
                    """);
        StopNode stop = parser.parse().iterate(true);
        assertEquals("Stop[ ]", stop.toString());
    }

    @Test
    public void testTypeLattice() {
        TypeStruct s1 = TypeStruct.make("s1", Arrays.asList(
                            new StructField(null, TypeInteger.BOT, "a", 0),
                            new StructField(null, TypeInteger.BOT, "b", 0)));
        TypeStruct s2 = TypeStruct.make("s2", Arrays.asList(
                            new StructField(null, TypeInteger.BOT, "a", 0),
                            new StructField(null, TypeInteger.BOT, "b", 0)));
        Assert.assertEquals(s1, s1.glb());
        Assert.assertEquals(s1, s1.dual());
        Assert.assertEquals(s1, s1.glb().dual());

        TypeMem m1 = TypeMem.make(s1.getField("a"));
        TypeMem m2 = TypeMem.make(s1.getField("b"));
        TypeMem m3 = TypeMem.make(s2.getField("a"));
        TypeMem m4 = TypeMem.make(s2.getField("b"));

        Assert.assertNotEquals(m1, m2);
        Assert.assertNotEquals(m2, m3);
        Assert.assertNotEquals(m3, m4);

        Assert.assertEquals(Type.BOTTOM, s1.meet(s2));
        Assert.assertEquals(Type.BOTTOM, m1.meet(m2));
        Assert.assertEquals(Type.BOTTOM, m2.meet(m3));
        Assert.assertEquals(Type.BOTTOM, m3.meet(m4));

        Assert.assertEquals(m1, m1.glb());
        Assert.assertEquals(m1, m1.dual());
        Assert.assertEquals(m1, m1.glb().dual());

        TypeMemPtr ptr1 = TypeMemPtr.make(s1);
        Assert.assertEquals(s1, ptr1.structType());
        TypeMemPtr ptr2 = TypeMemPtr.make(s2);
        Assert.assertEquals(s2, ptr2.structType());

        TypeMemPtr ptr1opt = TypeMemPtr.make(s1, true);
        Assert.assertEquals(s1, ptr1opt.structType());
        Assert.assertTrue(ptr1opt.maybeNull());
        Assert.assertFalse(ptr1opt.isNull());
        TypeMemPtr ptr2opt = TypeMemPtr.make(s2, true);
        Assert.assertEquals(s2, ptr2opt.structType());

        Assert.assertNotEquals(ptr1, ptr2);
        Assert.assertNotEquals(ptr1, ptr1.glb());
        Assert.assertEquals(ptr1opt, ptr1.glb());

        Assert.assertEquals(TypeMemPtr.TOP, TypeMemPtr.BOT.dual());
        Assert.assertEquals(TypeMemPtr.BOT, TypeMemPtr.TOP.dual());
        Assert.assertEquals(ptr1, ptr1.dual());
        Assert.assertEquals(ptr1.glb(), ptr1.glb().dual());
        Assert.assertEquals(TypeMemPtr.BOT, ptr1.meet(ptr2));
        Assert.assertEquals(ptr1.glb(), ptr1.meet(TypeMemPtr.NULLPTR));
    }

}
