package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.node.ProjNode;

public class ProjX86 extends ProjNode implements MachNode {
    ProjX86( ProjNode p ) { super(p); }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() {
        return ((MachNode)in(0)).outregmap(_idx);
    }
    @Override public void encoding( Encoding enc ) {}
}
