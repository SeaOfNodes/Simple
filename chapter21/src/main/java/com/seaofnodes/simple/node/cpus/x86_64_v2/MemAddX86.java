package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class MemAddX86 extends MemOpX86 {
    MemAddX86( StoreNode st, Node base, Node idx, int off, int scale, int imm, Node val ) {
        super(st, st, base, idx, off, scale, imm, val );
    }
    @Override public String op() {
        return (_imm == 1 ? "inc" : (_imm == -1 ? "dec" : "add")) + _sz;
    }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return null; }
    @Override public void encoding( Encoding enc ) {
        // add something to memory
        // REX.W + 01 /r | REX.W + 81 /0 id
        // ADD [mem], imm32/reg
        short ptr = enc.reg(ptr());
        short idx = enc.reg(idx());
        short src = enc.reg(val());

        enc.add1(x86_64_v2.rex(src, ptr, idx));
        // opcode
        enc.add1( src == -1 ? 0x81: 0x01);

        // includes modrm
        x86_64_v2.indirectAdr(_scale, idx, ptr, _off, src == -1 ? 0 : src, enc);
        if( src == -1 ) enc.add4(_imm);
    }
    // General form: "add  [base + idx<<2 + 12] += src"
    @Override public void asm(CodeGen code, SB sb) {
        asm_address(code,sb);
        if( val()==null ) {
            if( _imm != 1 && _imm != -1 ) sb.p(" += #").p(_imm);
        } else sb.p(" += ").p(code.reg(val()));
    }
}
