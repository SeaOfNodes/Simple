package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.node.ParmNode;

public class ParmRISC extends ParmNode implements MachNode {
    final RegMask _rmask;
    ParmRISC(ParmNode parm) {
        super(parm);
        // Assume int
        _rmask = riscv.callInMask(fun().sig(),_idx);
    }

    // Register mask allowed on input i.  0 for no register.
    @Override public RegMask regmap(int i) { return _rmask; }
    // Register mask allowed as a result.  Calling convention register
    @Override public RegMask outregmap() { return _rmask; }

    // Encoding is appended into the byte array.  Returns size
    @Override public void encoding( Encoding enc ) { return 0; }
}
