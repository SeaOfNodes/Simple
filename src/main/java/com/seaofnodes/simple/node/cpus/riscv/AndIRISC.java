package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.isa.RiscV;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.util.Utils;

public class AndIRISC extends ImmRISC {
    public AndIRISC( Node and, int imm) { super(and,imm); }
    @Override public String op() { return "andi"; }
    @Override public String glabel() { return "&"; }
    @Override int opcode() {  return RiscV.OP_IMM; }
    @Override int func3() {return 7;}
}
