package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.node.ParmNode;

public class ParmRISC extends ParmNode implements MachNode {
    final RegMask _rmask;
    ParmRISC(ParmNode parm) {
        super(parm);
        _rmask = riscv.callInMask(fun().sig(),_idx,0);
    }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return _rmask; }
    @Override public void encoding( Encoding enc ) { }
}
