package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import java.util.BitSet;
import java.io.ByteArrayOutputStream;

abstract public class ImmRISC extends MachConcreteNode implements MachNode {
    final int _imm;
    ImmRISC( Node add, int imm ) {
        super(add);
        _inputs.pop();
        _imm = imm;
    }

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) { return riscv.RMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return riscv.WMASK; }

    @Override public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        in(1)._print0(sb.append("( "), visited);
        return sb.append(String.format(" %s #%d )",glabel(),_imm));
    }

    abstract int opcode();

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    // General form: "addi  rd = rs1 + imm"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" ").p(glabel()).p(" #").p(_imm);
    }
}
