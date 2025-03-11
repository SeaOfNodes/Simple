package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.node.ProjNode;

public class ProjRISC extends ProjNode implements MachNode {
    ProjRISC(ProjNode p) { super(p); }

    // Register mask allowed on input i.  0 for no register.
    @Override public RegMask regmap(int i) { return null; }
    // Register mask allowed as a result.
    @Override public RegMask outregmap() {
        return ((MachNode)in(0)).outregmap(_idx);
    }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        return 0;
    }
}
