package com.seaofnodes.simple;

import com.seaofnodes.simple.node.StopNode;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class Chapter10Test {


    @Test
    public void testStruct() {
        Parser parser = new Parser(
                """
struct T {
    int a;
    int b;
}
struct Foo {
    int x;
}
return 0;
                """);
        StopNode stop = parser.parse(true);
    }

}
