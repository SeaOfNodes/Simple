package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

// Conditional branch such as: BEQ, BLT, BLE
public class CBranchRISC extends IfNode implements MachNode{
    final String _bop;
    // label is obtained implicitly
    CBranchRISC( IfNode iff, String bop ) {
        super(iff);
        _bop = bop;
    }

    @Override public String label() { return op(); }

    @Override public void postSelect() {
        Node set = in(1);
        Node cmp = set.in(1);
        // Bypass an expected Set and just reference the cmp directly
        if( set instanceof SetRISC)
            _inputs.set(1,cmp);
        else
            throw Utils.TODO();
    }

    @Override public RegMask regmap(int i) { assert i==1; return riscv.FLAGS_MASK; }
    @Override public RegMask outregmap() { return null; }

    // Encoding is appended into the byte array; size is returned

    @Override public int encoding(ByteArrayOutputStream bytes) {
        // e.g beq Branch == B 1100011 0x0 if(rs1 == rs2) PC += imm
        // It compares
        //two operands stored in registers and branch to a destination address relative
        //to the current Program Counter value

        int beforeSize = bytes.size();

        LRG cb_rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));
        LRG cb_rg_2 = CodeGen.CODE._regAlloc.lrg(in(2));

        short reg1 = cb_rg_1.get_reg();
        short reg2 = cb_rg_2.get_reg();
        if(_bop.equals("=")) {
            // encode pseudo instruction BLE
            // ble rs, rt, offset bge rt, rs, offset
            // swap operands for bge
            int body = riscv.b_type(riscv.jumpop(_bop), 0, 0,  reg2, reg1, 0);

        }
        // Todo:" object file fixes up these offsets
        int body = riscv.b_type(riscv.jumpop(_bop), 0, 0,  reg1, reg2, 0);
        riscv.push_4_bytes(body, bytes);
        return bytes.size() - beforeSize;
    }

    @Override public void asm(CodeGen code, SB sb) {
        String src = code.reg(in(1));
        if( src!="flags" )  sb.p(src);
    }

    @Override public String op() { return "b"+_bop; }

    @Override public String comment() {
        return "L"+cproj(1)._nid+", L"+cproj(0)._nid;
    }
}
