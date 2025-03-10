package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.Node;

public class XorIRISC extends ImmRISC {
    XorIRISC( Node and, int imm) { super(and,imm); }
    @Override int opcode() { throw Utils.TODO(); }
    @Override public String glabel() { return "^"; }
    @Override public String op() { return "xori"; }
}
