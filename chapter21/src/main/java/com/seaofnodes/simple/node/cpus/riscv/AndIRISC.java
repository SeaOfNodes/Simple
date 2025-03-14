package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.Node;

public class AndIRISC extends ImmRISC {
    AndIRISC( Node and, int imm) { super(and,imm); }
    @Override int opcode() {  return riscv.I_TYPE; }
    @Override int func3() {return 7;}
    @Override public String glabel() { return "&"; }
    @Override public String op() { return "andi"; }
}
