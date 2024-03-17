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
}
