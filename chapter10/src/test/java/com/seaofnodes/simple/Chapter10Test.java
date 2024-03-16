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
return 0;
                """);
        StopNode stop = parser.parse(true);
    }

}
