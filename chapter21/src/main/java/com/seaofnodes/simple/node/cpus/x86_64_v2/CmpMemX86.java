package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFloat;

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

        if(src == -1) {
            int log = _declaredType.log_size();
            x86_64_v2.rexF(-1, ptr, idx, log==3, enc);
            switch(log) {
                case 0: enc.add1(0x80); break;
                case 1: enc.add1(0x66).add1(0x81); break;
                case 2: enc.add1(0x81); break;
                case 3: enc.add1(0x81); break;
            }
            x86_64_v2.indirectAdr(_scale, idx, ptr, _off,  7, enc);
            switch(log) {
                case 0: enc.add1(_imm); break;
                case 1: enc.add2(_imm); break;
                case 2: enc.add4(_imm); break;
                case 3: enc.add8(_imm); break;
            }
        } else {
            encVal(enc, _declaredType, ptr, idx, src, _off, _scale);
        }
    }

    static void encVal(Encoding enc, Type decl, short ptr, short idx, short src, int off, int scale) {
        if(decl instanceof TypeFloat) {
            src -= (short)x86_64_v2.XMM_OFFSET;
            enc.add1(decl==TypeFloat.F32 ? 0xF3 : 0xF2).add1(0x0F).add1(0xC2);
        } else {
            int log = decl.log_size();
            x86_64_v2.rexF(src, ptr, idx, log == 3, enc);
            switch(log) {
                case 0: enc.add1(0x38); break;
                case 1: enc.add1(0x66).add1(0x3B); break;
                case 2: enc.add1(0x3B); break;
                case 3: enc.add1(0x3B); break;
            }
        }
        x86_64_v2.indirectAdr(scale, idx, ptr, off, src, enc);
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
