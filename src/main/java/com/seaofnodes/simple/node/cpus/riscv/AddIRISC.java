package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.Node;

public class AddIRISC extends ImmRISC {
    // Used to inst-selection as a direct match against an ideal Add/Sub
    public AddIRISC( Node add, int imm12, boolean pop ) { super(add,imm12,pop); }
    @Override public String op() { return "addi"; }
    @Override public String glabel() { return "+"; }
    @Override int opcode() {  return riscv.OP_IMM; }
    @Override int func3() {return 0;}
    @Override public AddIRISC copy() {
        // Clone the AddI, using the same inputs-only code used during inst select.
        // Output edges are missing.
        AddIRISC add = new AddIRISC(in(1),_imm12,false);
        // Copys happen when output edges should be valid, so correct missing output edges.
        in(1)._outputs.add(add);
        return add;
    }
}
