package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.Node;

// corresponds to slti,sltiu
public class SetIRISC extends ImmRISC {
    final boolean _unsigned;    // slti vs sltiu
    SetIRISC( Node src, int imm12, boolean unsigned ) {
        super(src,imm12);
        _unsigned = unsigned;
    }
    @Override public String op() { return "setlt" + (_unsigned ? "u":""); }
    @Override public String glabel() { return  "<"+ (_unsigned ? "u":""); }
    @Override int opcode() { return 19; }
    @Override int func3() { return 2; }
}
