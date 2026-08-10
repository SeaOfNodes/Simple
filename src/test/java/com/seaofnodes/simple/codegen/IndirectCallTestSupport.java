package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.*;
import static org.junit.Assert.*;

public class IndirectCallTestSupport {
    public static void checkArmEncoding(CodeGen code, CallNode call) {
        Encoding enc = new Encoding(code) {
            @Override public short reg(Node n) {
                assertSame(call.fptr(),n);
                return 7;
            }
        };
        ((MachNode)call).encoding(enc);
        assertEquals(0xd63f00e0,enc.read4(0)); // BLR X7
    }
}
