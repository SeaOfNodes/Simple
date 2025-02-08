package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.RegMask;
import com.seaofnodes.simple.node.ParmNode;
import com.seaofnodes.simple.node.MachNode;
import java.io.ByteArrayOutputStream;

public class ParmX86 extends ParmNode implements MachNode {
    final RegMask _rmask;
    ParmX86( ParmNode parm ) {
        super(parm);
        // default to int
        _rmask = x86_64_v2.callInMaskInt(_idx);
    }

    // Register mask allowed on input i.  0 for no register.
    @Override public RegMask regmap(int i) { return RegMask.EMPTY; }
    // Register mask allowed as a result.  Calling convention register
    @Override public RegMask outregmap() { return _rmask; }

    // Encoding is appended into the byte array.  Returns size
    @Override public int encoding(ByteArrayOutputStream bytes) { return 0; }
}
