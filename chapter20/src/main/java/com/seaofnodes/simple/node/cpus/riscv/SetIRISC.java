package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.BoolNode;

// corresponds to slt,sltu,slti,sltiu, seqz
public class SetIRISC extends ImmRISC {
    final String _bop;          // One of <,<=,==
    SetIRISC( BoolNode bool, int imm ) {
        super(bool,imm);
        assert !bool.isFloat();
        _bop = bool.op();
    }
    @Override int opcode() { throw Utils.TODO(); }
    @Override int func3() { throw Utils.TODO(); }
    @Override public String glabel() { return _bop; }
    @Override public String op() { return "set"+_bop; }
}
