package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

import java.util.BitSet;

abstract public class ImmRISC extends MachConcreteNode implements MachNode {
    final int _imm12;
    ImmRISC( Node add, int imm12 ) {
        super(add);
        _inputs.pop();
        _imm12 = imm12;
    }
    @Override public RegMask regmap(int i) { return riscv.RMASK; }
    @Override public RegMask outregmap() { return riscv.WMASK; }

    @Override public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        in(1)._print0(sb.append("( "), visited);
        return sb.append(String.format(" %s #%d )",glabel(),_imm12));
    }

    abstract int opcode();
    abstract int func3();

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        short dst = enc.reg(this );
        short src = enc.reg(in(1));
        int body = riscv.i_type(opcode(), dst, func3(), src, _imm12);
        enc.add4(body);
    }

    // General form: "addi  rd = rs1 + imm"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" ").p(glabel()).p(" #").p(_imm12);
    }
}
