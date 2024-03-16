package com.seaofnodes.simple;

import com.seaofnodes.simple.node.StopNode;
import org.junit.Test;

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

}
