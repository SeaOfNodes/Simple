package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.FunNode;
import com.seaofnodes.simple.node.MachNode;

public class FunX86 extends FunNode implements MachNode {
    FunX86( FunNode fun ) { super(fun); }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return null; }
    @Override public void encoding( Encoding enc ) { }
    @Override public void postSelect(CodeGen code) { code.link(this);  }
}
