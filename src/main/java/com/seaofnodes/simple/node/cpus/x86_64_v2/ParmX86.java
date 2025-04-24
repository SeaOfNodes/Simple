package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.node.ParmNode;

public class ParmX86 extends ParmNode implements MachNode {
    final RegMask _rmask;
    ParmX86( ParmNode parm ) {
        super(parm);
        _rmask = x86_64_v2.callInMask(fun().sig(),_idx,1/*RPC*/);
    }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return _rmask; }
    @Override public void encoding( Encoding enc ) { }
}
