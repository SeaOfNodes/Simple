package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import org.junit.Assert;
import org.junit.Test;

public class ParserTest {

    @Test
    public void testSimpleProgram() {
        Parser parser = new Parser();
        StartNode startNode = parser.parse("return 1;");
        Assert.assertNotNull(startNode);
        Assert.assertEquals(2, startNode.nOuts());
        for (int i = 0; i < startNode.nOuts(); i++) {
            switch( startNode.out(i) ) {
            case ReturnNode ret:
                Assert.assertEquals(2, ret.nIns());
                Assert.assertEquals(startNode, ret.in(0));
                Assert.assertTrue(ret.in(1) instanceof ConstantNode);
                break;
            case ConstantNode con:
                Assert.assertEquals(1, con._value);
                Assert.assertEquals(1, con.nIns());
                Assert.assertEquals(startNode, con.in(0));
                break;
            default:
                throw new IllegalStateException();
            }
        }
    }
}
