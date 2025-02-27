package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.SplitNode;
import java.io.ByteArrayOutputStream;

public class SplitARM extends SplitNode {
    SplitARM(String kind, byte round) { super(kind,round,new Node[2]);}

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) { return arm.MEM_MASK; }

    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return arm.MEM_MASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }
}
