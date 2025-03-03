package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.Node;

public class MulIRISC extends ImmRISC {
    MulIRISC( Node add, int imm ) { super(add,imm); }
    @Override int opcode() { throw Utils.TODO(); }
    @Override public String glabel() { return "*"; }
    @Override public String op() { return "muli"; }
}
