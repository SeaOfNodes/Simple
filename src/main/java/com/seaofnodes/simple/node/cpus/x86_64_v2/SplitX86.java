package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.SplitNode;
import java.io.ByteArrayOutputStream;

public class SplitX86 extends SplitNode {
    SplitX86( String kind, byte round ) { super(kind,round, new Node[2]); }

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) { return x86_64_v2.SPLIT_MASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return x86_64_v2.SPLIT_MASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }
}
