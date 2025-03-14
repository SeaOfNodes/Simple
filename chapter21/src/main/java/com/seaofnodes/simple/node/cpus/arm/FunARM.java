package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.FunNode;
import com.seaofnodes.simple.node.MachNode;

public class FunARM  extends FunNode implements MachNode {
    FunARM(FunNode fun) { super(fun); }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return null; }
    @Override public void encoding( Encoding enc ) { }
}
