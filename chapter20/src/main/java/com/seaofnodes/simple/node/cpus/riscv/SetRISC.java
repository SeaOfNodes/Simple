package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

// corresponds to slt,sltu,slti,sltiu, seqz
// RISCV doesn't have rflags.
public class SetRISC extends MachConcreteNode implements MachNode{
    final String _bop;          // One of <,<=,==
    SetRISC( Node cmp, String bop ) {
        super(cmp);
        _inputs.setLen(1);   // Pop the cmp inputs
        // Replace with the matched cmp
        _inputs.push(cmp);
        _bop = bop;
    }
    @Override public RegMask regmap(int i) { assert i==1; return riscv.FLAGS_MASK; }
    @Override public RegMask outregmap() { return riscv.WMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        // rd
        LRG set_rg = CodeGen.CODE._regAlloc.lrg(this);

        LRG in1_rg = CodeGen.CODE._regAlloc.lrg(in(1));
        LRG in2_rg = CodeGen.CODE._regAlloc.lrg(in(2));

        short reg = set_rg.get_reg();
        short reg_in_1 = set_rg.get_reg();
        short reg_in_2 = set_rg.get_reg();

        int beforeSize = bytes.size();
        // handle cases manually
        if(_bop.equals( "<")) {
            int body = riscv.r_type(riscv.setop("<"), reg,  0x2, reg_in_1,  reg_in_2, 0);
            riscv.push_4_bytes(body, bytes);
        } else if(_bop.equals("=")) {
            // xor t0, a0, a1  # XOR a0 and a1; result is 0 if they are equal


            LRG xor_rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));
            LRG xor_rg_2 = CodeGen.CODE._regAlloc.lrg(in(2));

            short reg1 = xor_rg_1.get_reg();
            short reg2 = xor_rg_2.get_reg();

            int body = riscv.r_type(riscv.R_TYPE, reg, 4, reg1, reg2, 0);

            riscv.push_4_bytes(body, bytes);
            // need to get reg here from RA

            // seqz t1, t0 = sltiu rd, rs, 1
            // will clobber output reg from first XOR
            int body2 = riscv.i_type(riscv.setop("="),  reg, 0x3, reg, 1);
            riscv.push_4_bytes(body2, bytes);

        } else if(_bop.equals("<=")) {
            // slt t0, a1, a0  # t0 = (a1 < a0) ? 1 : 0
            //seqz t1, t0     # t1 = (t0 == 0) ? 1 : 0  (t1 = (a0 ≤ a1))

            int body = riscv.r_type(riscv.setop("<"), reg,  0x2, reg_in_1,  reg_in_2, 0);
            riscv.push_4_bytes(body, bytes);

            // will clobber output reg from first XOR
            int body2 = riscv.i_type(riscv.setop("="),  reg, 0x3, reg, 1);
            riscv.push_4_bytes(body2, bytes);
        }

        return bytes.size() - beforeSize;
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this));
        String src = code.reg(in(1));
        if( src!="FLAGS" )  sb.p(" = ").p(src);
    }

    @Override public String op() { return "set"+_bop; }
}
