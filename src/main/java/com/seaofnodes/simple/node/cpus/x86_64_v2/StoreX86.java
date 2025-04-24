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
            int log = _declaredType.log_size();
            x86_64_v2.rexF(-1, ptr, idx, log==3, enc);
            switch( log ) {
            case 0: enc           .add1(0xC6); break;
            case 1: enc.add1(0x66).add1(0xC7); break;
            case 2: enc           .add1(0xC7); break;
            case 3: enc           .add1(0xC7); break;
            }
            x86_64_v2.indirectAdr(_scale, idx, ptr, _off, src, enc);
            switch( log ) {
            case 0: enc.add1(_imm); break;
            case 1: enc.add2(_imm); break;
            case 2: enc.add4(_imm); break;
            case 3: enc.add4(_imm); break; // Limit of a 4 byte immediate
            }
        } else {
            encVal(enc,_declaredType,ptr,idx,src,_off,_scale);
        }
    }

    // Non-immediate encoding
    static void encVal( Encoding enc, Type decl, short ptr, short idx, short src, int off, int scale ) {
        int log = decl.log_size();
        // Float reg being stored
        if( src >= x86_64_v2.XMM_OFFSET ) {
            src -= (short)x86_64_v2.XMM_OFFSET;
            assert log == 2 || log == 3;
            enc.add1( log==2 ? 0xF3 : 0xF2 );
            x86_64_v2.rexF(src,ptr,idx,false,enc);
            enc.add1(0x0F).add1(0x11);
        } else {
            // byte stores from sil, dil, bpl, spl need a rex
            if( log == 0 && src >= x86_64_v2.RSP ) enc.add1(x86_64_v2.rex(src,ptr,idx,false));
            else x86_64_v2.rexF(src,ptr,idx,log==3,enc);
            switch( log ) {
            case 0: enc           .add1(0x88); break;
            case 1: enc.add1(0x66).add1(0x89); break;
            case 2: enc           .add1(0x89); break;
            case 3: enc           .add1(0x89); break;
            }
        }
        x86_64_v2.indirectAdr(scale, idx, ptr, off, src, enc);
    }


    // General form: "stN  [base + idx<<2 + 12],val"
    @Override public void asm(CodeGen code, SB sb) {
        asm_address(code,sb).p(",");
        if( val()==null ) sb.p("#").p(_imm);
        else sb.p(code.reg(val()));
    }
}
