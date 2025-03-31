package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.Node;

public class SllIRISC extends ImmRISC {
    public SllIRISC( Node and, int imm) { super(and,imm); }
    @Override int opcode() {  return riscv.OP_IMM; }
    @Override int func3() {  return 1; }
    @Override public String glabel() { return "<<"; }
    @Override public String op() { return "slli"; }
}
