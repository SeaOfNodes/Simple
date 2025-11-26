package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.util.Utils;

public class OrIRISC extends ImmRISC {
    public OrIRISC( Node and, int imm) { super(and,imm); }
    @Override int opcode() {  return riscv.OP_IMM; }
    @Override int func3() {  return 6; }
    @Override public String glabel() { return "|"; }
    @Override public String op() { return "ori"; }
}
