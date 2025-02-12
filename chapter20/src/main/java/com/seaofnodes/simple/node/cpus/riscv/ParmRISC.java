package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.node.ParmNode;
import java.io.ByteArrayOutputStream;

public class ParmRISC extends ParmNode implements MachNode {
    final RegMask _rmask;
    ParmRISC(ParmNode parm) {
        super(parm);
        // Assume int
        _rmask = riscv.callInMaskInt(_idx);
    }

    // Register mask allowed on input i.  0 for no register.
    @Override public RegMask regmap(int i) { return null; }
    // Register mask allowed as a result.  Calling convention register
    @Override public RegMask outregmap() { return _rmask; }

    // Encoding is appended into the byte array.  Returns size
    @Override public int encoding(ByteArrayOutputStream bytes) { return 0; }
}
