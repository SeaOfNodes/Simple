package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.BoolNode;
import com.seaofnodes.simple.node.MachConcreteNode;
import com.seaofnodes.simple.node.MachNode;
import java.io.ByteArrayOutputStream;

// corresponds to slt,sltu,slti,sltiu, seqz
public class SetFRISC extends MachConcreteNode implements MachNode {
    final String _bop;          // One of <,<=,==
    SetFRISC( BoolNode bool ) {
        super(bool);
        assert bool.isFloat();
        _bop = bool.op();
    }
    @Override public RegMask regmap(int i) { return riscv.FMASK; }
    @Override public RegMask outregmap() { return riscv.WMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(in(1))).p(" ").p(_bop).p(" ").p(code.reg(in(2)));
    }

    @Override public String op() { return "set"+_bop; }
}
