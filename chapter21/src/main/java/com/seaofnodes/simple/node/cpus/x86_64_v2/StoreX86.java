package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.StoreNode;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFloat;
import java.util.BitSet;

public class StoreX86 extends MemOpX86 {
    StoreX86( StoreNode st, Node base, Node idx, int off, int scale, int imm, Node val ) {
        super(st,st, base, idx, off, scale, imm, val);
    }
    @Override public String op() { return "st"+_sz; }
    @Override public StringBuilder _printMach(StringBuilder sb, BitSet visited) {
        Node val = val();
        sb.append(".").append(_name).append("=");
        if( val==null ) sb.append(_imm);
        else val._print0(sb,visited);
        return sb.append(";");
    }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return null; }
    @Override public void encoding( Encoding enc ) {
        // REX.W + C7 /0 id	MOV r/m64, imm32 |
        // REX.W + 89 /r        MOV r/m64, r64
        short ptr = enc.reg(ptr());
        short idx = enc.reg(idx());
        short src = enc.reg(val());

        if( src == -1 ) {
            // return opcode for optimised immediate store
            if( x86_64_v2.imm8(_imm) ) enc.add1(0xC6);
            else if( x86_64_v2.imm32(_imm) ) enc.add1(0xC7);
            else enc.add1(x86_64_v2.rex(-1, ptr, idx)).add1(0xC7);
            x86_64_v2.indirectAdr(_scale, idx, ptr, _off, src, enc);
            switch (x86_64_v2.imm_size(_imm)) {
            case  8: enc.add1(_imm); break;
            case 16: enc.add2(_imm); break;
            case 32: enc.add4(_imm); break;
            case 64: enc.add8(_imm); break;
            }
        } else {
            encVal(enc,_declaredType,ptr,idx,src,_off,_scale);
        }
    }

    // Non-immediate encoding
    static void encVal( Encoding enc, Type decl, short ptr, short idx, short src, int off, int scale ) {
        if( decl instanceof TypeFloat ) {
            src -= (short)x86_64_v2.XMM_OFFSET;
            enc.add1( decl==TypeFloat.F32 ? 0xF3 : 0xF2 ).add1(0x0F).add1(0x11);
        }
        else if( decl.log_size() == 0 ) enc.add1(0x88);
        else if( decl.log_size() == 1 ) enc.add1(0x89);
        else if( decl.log_size() == 2 ) enc.add1(0x89);
        else if( decl.log_size() == 3 ) enc.add1(x86_64_v2.rex(src, ptr, idx)).add1(0x89);

        x86_64_v2.indirectAdr(scale, idx, ptr, off, src, enc);
    }


    // General form: "stN  [base + idx<<2 + 12],val"
    @Override public void asm(CodeGen code, SB sb) {
        asm_address(code,sb).p(",");
        if( val()==null ) sb.p("#").p(_imm);
        else sb.p(code.reg(val()));
    }
}
