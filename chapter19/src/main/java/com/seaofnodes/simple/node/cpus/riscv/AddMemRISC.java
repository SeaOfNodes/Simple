package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.CodeGen;
import com.seaofnodes.simple.RegMask;
import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.cpus.x86_64_v2.x86_64_v2;

public class AddMemRISC extends MemOpRISC{
    AddMemRISC( AddNode add, LoadNode ld , Node base, Node idx, int off, int scale, int imm, Node val ) {
        super(add,ld, base, idx, off, scale, imm, val );
    }

    @Override public String op() { return "add"+_sz; }

    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return riscv.RMASK; }


    // General form: "add  dst = src + [base + idx<<2 + 12]"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ");
        sb.p(val()==null ? "#"+_imm : code.reg(val())).p(" + ");
        asm_address(code,sb);
    }
}
