package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.BoolNode;
import com.seaofnodes.simple.node.MachConcreteNode;
import com.seaofnodes.simple.node.MachNode;

// corresponds to feq.d = equal
//                fle.d = less than or equal
//                flt.d = less than
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
    @Override public void encoding( Encoding enc ) {

        LRG fset_rg = CodeGen.CODE._regAlloc.lrg(this);

        LRG in1_rg = CodeGen.CODE._regAlloc.lrg(in(1));
        LRG in2_rg = CodeGen.CODE._regAlloc.lrg(in(2));

        short reg = fset_rg.get_reg();
        int reg_in_1 = in1_rg.get_reg();
        int reg_in_2 = in2_rg.get_reg();

        int beforeSize = bytes.size();

        if(_bop.equals( "<")) {
            // Test for zero case
//            if(_imm) {
//                reg_in_2 = riscv.ZERO;
//            }
            int body = riscv.r_type(0x53, reg,  riscv.fsetop("<"), reg_in_1,  reg_in_2, 0);
            riscv.push_4_bytes(body, bytes);
        } else if(_bop.equals("=")) {
            // xor t0, a0, a1  # XOR a0 and a1; result is 0 if they are equal
//
//            if(_imm) {
//                reg_in_2 = riscv.ZERO;
//            }
            int body = riscv.r_type(0x53, reg, riscv.fsetop("="), reg_in_1, reg_in_2, 0);

            riscv.push_4_bytes(body, bytes);
            // need to get reg here from RA

            // seqz t1, t0 = sltiu rd, rs, 1
            // will clobber output reg from first XOR
            int body2 = riscv.i_type(0x53,  reg, riscv.fsetop("="), reg, 1);
            riscv.push_4_bytes(body2, bytes);

        } else if(_bop.equals("<=")) {
            // slt t0, a1, a0  # t0 = (a1 < a0) ? 1 : 0
            //seqz t1, t0     # t1 = (t0 == 0) ? 1 : 0  (t1 = (a0 ≤ a1))
//
//            if(_imm) {
//                reg_in_2 = riscv.ZERO;
//            }
            int body = riscv.r_type(0x53,  reg,  riscv.fsetop("<="), reg_in_1,  reg_in_2, 0);
            riscv.push_4_bytes(body, bytes);

            // will clobber output reg from first XOR
            int body2 = riscv.i_type(0x53, riscv.fsetop("="),  reg, 0x3, reg, 1);
            riscv.push_4_bytes(body2, bytes);
        }

        return bytes.size() - beforeSize;
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(in(1))).p(" ").p(_bop).p(" ").p(code.reg(in(2)));
    }

    @Override public String op() { return "set"+_bop; }
}
