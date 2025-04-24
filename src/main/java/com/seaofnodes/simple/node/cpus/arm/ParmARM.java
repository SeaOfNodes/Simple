package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.node.ParmNode;

public class ParmARM extends ParmNode implements MachNode {
    final RegMask _rmask;
    ParmARM(ParmNode parm) {
        super(parm);
        _rmask = arm.callInMask(fun().sig(),_idx,0);
    }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return _rmask; }
    @Override public void encoding( Encoding enc ) { }
}
