package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.Node;

public class AddIRISC extends ImmRISC {
    AddIRISC( Node add, int imm ) { super(add,imm); }
    @Override int opcode() {  return riscv.I_TYPE; }
    @Override int func3() {return 0;}
    @Override public String glabel() { return "+"; }
    @Override public String op() { return "addi"; }
}
