package com.seaofnodes.simple;

import com.seaofnodes.simple.node.Node;
import org.junit.Assert;
import org.junit.Test;

public class ParserTest {

    @Test
    public void testSimpleProgram() {
        Parser parser = new Parser();
        Node startNode = parser.parse("return 1;");
        Assert.assertNotNull(startNode);
    }
}
