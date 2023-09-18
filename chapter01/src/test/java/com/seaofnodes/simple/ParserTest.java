package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import org.junit.Assert;
import org.junit.Test;

public class ParserTest {

    @Test
    public void testSimpleProgram() {
        Parser parser = new Parser(new NodeIDGenerator());
        StartNode startNode = parser.parse("return 1;");
        Assert.assertNotNull(startNode);
        Assert.assertTrue(startNode.isControl());
        Assert.assertEquals(2, startNode.numOutputs());
        for (int i = 0; i < startNode.numOutputs(); i++) {
            Node out = startNode.out(i);
            if (out instanceof ReturnNode) {
                Assert.assertEquals(2, out.numInputs());
                Assert.assertEquals(startNode, out.in(0));
                Assert.assertTrue(out.isControl());
                Assert.assertTrue(out.in(1) instanceof ConstantNode);
            }
            else if (out instanceof ConstantNode cn) {
                Assert.assertEquals(1, cn._value);
                Assert.assertEquals(1, cn.numInputs());
                Assert.assertEquals(startNode, cn.in(0));
            }
            else {
                throw new IllegalStateException();
            }
        }
    }
}
