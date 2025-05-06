package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.util.Utils;

public class SraIRISC extends ImmRISC {
    public SraIRISC( Node and, int imm) {
        super(and,imm | (0x20 << 5));
    }
    @Override int opcode() {  return riscv.OP_IMM; }
    @Override int func3() {  return 5;}
    @Override public String glabel() { return ">>"; }
    @Override public String op() { return "srai"; }
}
