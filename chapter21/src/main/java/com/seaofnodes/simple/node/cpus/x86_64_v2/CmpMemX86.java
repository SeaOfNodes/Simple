package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class CmpMemX86 extends MemOpX86 {
    final boolean _invert;      // Op switched LHS, RHS
    CmpMemX86( BoolNode bool, LoadNode ld, Node base, Node idx, int off, int scale, int imm, Node val, boolean invert ) {
        super(bool,ld, base, idx, off, scale, imm, val );
        _invert = invert;
    }
    @Override public String op() { return ((val()==null && _imm==0) ? "test" : "cmp") + _sz; }
    @Override public RegMask outregmap() { return x86_64_v2.FLAGS_MASK; }
    @Override public void encoding( Encoding enc ) {
        // REX.W + 81 /7 id	CMP r/m64, imm32 | REX.W + 39 /r	CMP r/m64,r64
        // CMP [mem], imm32
        short ptr = enc.reg(ptr());
        short idx = enc.reg(idx());
        short src = enc.reg(val());

        enc.add1(x86_64_v2.rex(src, ptr, idx));
        // opcode varies by immediate
        enc.add1( src == -1 ? 0x39 : 0x81 );

        // includes modrm
        x86_64_v2.indirectAdr(_scale, idx, ptr, _off, src == -1 ? 7 : src, enc);

        if( src == -1 ) enc.add4(_imm);
    }

    // General form: "cmp  dst = src, [base + idx<<2 + 12]"
    @Override public void asm(CodeGen code, SB sb) {
        String dst = code.reg(this);
        if( dst!="flags" )  sb.p(dst).p(" = ");
        if( _invert ) asm_address(code,sb).p(",");
        if( val()==null ) {
            if( _imm!=0 )  sb.p("#").p(_imm);
        } else {
            sb.p(code.reg(val()));
        }
        if( !_invert ) asm_address(code,sb.p(", "));
    }
}
