package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.type.TypeInteger;

public class AddIRISC extends ImmRISC {
    // Used to inst-selection as a direct match against an ideal Add/Sub
    AddIRISC( Node add, int imm, boolean pop ) { super(add,imm,pop); }
    @Override public String op() { return "addi"; }
    @Override public String glabel() { return "+"; }
    @Override int opcode() {  return riscv.I_TYPE; }
    @Override int func3() {return 0;}
    @Override public AddIRISC copy() { return new AddIRISC(this,_imm12,false); }
}
