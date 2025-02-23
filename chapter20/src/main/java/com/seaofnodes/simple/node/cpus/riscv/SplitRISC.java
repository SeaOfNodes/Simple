package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.SplitNode;
import java.io.ByteArrayOutputStream;

public class SplitRISC extends SplitNode {
    SplitRISC( String kind, byte round ) { super(kind,round, new Node[2]); }

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) { return riscv.MEM_MASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return riscv.MEM_MASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }
}
