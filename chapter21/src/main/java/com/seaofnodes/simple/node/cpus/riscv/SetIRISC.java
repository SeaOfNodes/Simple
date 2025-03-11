package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.BoolNode;

// corresponds to slt,sltu,slti,sltiu, seqz
public class SetIRISC extends ImmRISC {
    final String _bop;          // One of <,<=,==
    final boolean _unsigned;    // slti vs sltiu
    SetIRISC( BoolNode bool, int unsigned ) {
        super(bool,imm);
        assert !bool.isFloat();
        _bop = bool.op();
        _unsigned = unsigned;
    }
    @Override int opcode() { return 19; }
    @Override int func3() { return 2; }
    @Override public String glabel() { return _bop; }
    @Override public String op() { return "set"+_bop; }
}
