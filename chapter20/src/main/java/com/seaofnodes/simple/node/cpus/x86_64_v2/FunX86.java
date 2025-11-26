package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.FunNode;
import com.seaofnodes.simple.node.MachNode;
import java.io.ByteArrayOutputStream;

public class FunX86 extends FunNode implements MachNode {
    FunX86( FunNode fun ) { super(fun); }

    // Register mask allowed on input i.  0 for no register.
    @Override public RegMask regmap(int i) { return null; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return null; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        return 0;
    }
}
