package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.FunNode;
import com.seaofnodes.simple.node.MachNode;

public class FunRISC extends FunNode implements MachNode {
    FunRISC(FunNode fun) {super(fun);}
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return null; }
    @Override public void encoding( Encoding enc ) { }
    @Override public void postSelect(CodeGen code) { code.link(this);  }
}
