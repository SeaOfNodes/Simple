package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
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
    abstract int func3();

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        int beforeSize = bytes.size();

        LRG rg_1 = CodeGen.CODE._regAlloc.lrg(this);
        LRG rg_2 = CodeGen.CODE._regAlloc.lrg(in(1));

        short rd = rg_1.get_reg();
        short reg_1 = rg_2.get_reg();

        switch (this) {
            case AddIRISC addIRISC-> {
                int body = riscv.i_type(opcode(), rd, func3(), reg_1, _imm);
                riscv.push_4_bytes(body, bytes);
                return  bytes.size() - beforeSize;
            }
            case AndIRISC andIRISC -> {
                int body = riscv.i_type(opcode(), rd, func3(), reg_1, _imm);
                riscv.push_4_bytes(body, bytes);
                return bytes.size() - beforeSize;
            }

            case OrIRISC orIRISC -> {
                int body = riscv.i_type(opcode(), rd, func3(), reg_1, _imm);
                riscv.push_4_bytes(body, bytes);
                return bytes.size() - beforeSize;
            }

            case XorIRISC xorIRISC -> {
                int body = riscv.i_type(opcode(), rd, func3(), reg_1, _imm);
                riscv.push_4_bytes(body, bytes);
                return bytes.size() - beforeSize;
            }

            // todo
            case SetIRISC setIRISC -> {
                int body = riscv.i_type(opcode(), rd, func3(), reg_1, _imm);
                riscv.push_4_bytes(body, bytes);
                return bytes.size() - beforeSize;
            }

            case SllIRISC sllIRISC -> {
                int body = riscv.i_type(opcode(), rd, func3(), reg_1, _imm);
                riscv.push_4_bytes(body, bytes);
                return bytes.size() - beforeSize;
            }

            case SraIRISC sraIIRISC -> {
                int body = riscv.i_type(opcode(), rd, func3(), reg_1, _imm);
                riscv.push_4_bytes(body, bytes);
                return bytes.size() - beforeSize;
            }

            case SrlIRISC srlIIRISC -> {
                int body = riscv.i_type(opcode(), rd, func3(), reg_1, _imm);
                riscv.push_4_bytes(body, bytes);
                return bytes.size() - beforeSize;
            }

            default -> {
            }
        }
        throw Utils.TODO();
    }

    // General form: "addi  rd = rs1 + imm"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" ").p(glabel()).p(" #").p(_imm);
    }
}
