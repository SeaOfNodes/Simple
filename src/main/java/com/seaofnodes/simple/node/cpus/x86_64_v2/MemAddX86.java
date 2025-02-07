package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.CodeGen;
import com.seaofnodes.simple.RegMask;
import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.node.*;

public class MemAddX86 extends MemOpX86 {
    MemAddX86( StoreNode st, Node base, Node idx, int off, int scale, int imm, Node val ) {
        super(st, st, base, idx, off, scale, imm, val );
    }

    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return RegMask.EMPTY; }


    // General form: "add  [base + idx<<2 + 12] += src"
    @Override public void asm(CodeGen code, SB sb) {
        asm_address(code,sb);
        if( val()==null ) {
            if( _imm != 1 && _imm != -1 ) sb.p(" += #").p(_imm);
        } else sb.p(" += ").p(code.reg(val()));
    }

    @Override public String op() {
        return (_imm == 1 ? "inc" : (_imm == -1 ? "dec" : "add")) + _sz;
    }
}
