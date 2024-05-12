package com.seaofnodes.simple;

import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.StopNode;
import com.seaofnodes.simple.type.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Chapter11Test {

    // A placeholder test used to rapidly rotate through fuzzer produced issues
    @Test
    public void testFuzzer() {
        Parser parser = new Parser(
"""
return 0;
""");
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return 0;", stop.toString());
    }


}
