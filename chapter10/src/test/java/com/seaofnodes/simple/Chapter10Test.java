package com.seaofnodes.simple;

import com.seaofnodes.simple.node.StopNode;
import org.junit.Test;

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
        StopNode stop = parser.parse(true);
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
        StopNode stop = parser.parse(true);
        System.out.println(IRPrinter.prettyPrint(stop, 99, true));
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

        StopNode stop = parser.parse(true);
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
        StopNode stop = parser.parse(true);
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
        StopNode stop = parser.parse(true);
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
        StopNode stop = parser.parse(true);
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
        StopNode stop = parser.parse(true);
        System.out.println(IRPrinter.prettyPrint(stop, 99, true));
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
        StopNode stop = parser.parse(true);
        System.out.println(IRPrinter.prettyPrint(stop, 99, true));
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
        StopNode stop = parser.parse(true);
        System.out.println(IRPrinter.prettyPrint(stop, 99, true));
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
        StopNode stop = parser.parse(true);
    }

}
