package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.isa.RiscV;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.Node;

public class OrIRISC extends ImmRISC {
    public OrIRISC( Node and, int imm) { super(and,imm); }
    @Override int opcode() {  return RiscV.OP_IMM; }
    @Override int func3() {  return 6; }
    @Override public String glabel() { return "|"; }
    @Override public String op() { return "ori"; }
}
