package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.LoadNode;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFloat;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.type.TypeMemPtr;

public class LoadX86 extends MemOpX86 {
    LoadX86( LoadNode ld, Node base, Node idx, int off, int scale ) {
        super(ld,ld, base, idx, off, scale, 0);
    }
    @Override public String op() { return "ld"+_sz; }
    @Override public RegMask outregmap() { return x86_64_v2.MEM_MASK; }
    @Override public void encoding( Encoding enc ) {
        // REX.W + 8B /r	MOV r64, r/m64
        // Zero extension for u8, u16 and u32 but sign extension i8, i16, i32
        // Use movsx and movzx
        short dst = enc.reg(this );
        short ptr = enc.reg(ptr());
        short idx = enc.reg(idx());
        enc(enc, _declaredType, dst, ptr, idx, _off, _scale);
    }

    static void enc( Encoding enc, Type decl, short dst, short ptr, short idx, int off, int scale ) {
        // The chapter chooses the register bank and extension from its own type facts.
        boolean xmm=dst>=x86_64_v2.XMM_OFFSET;
        if( xmm && decl.log_size()==3 ) decl=TypeFloat.F64;
        else if( !xmm && decl instanceof TypeFloat )
            decl=decl==TypeFloat.F32 ? TypeInteger.U32 : TypeInteger.BOT;
        boolean signed=decl.isa(TypeInteger.I8) || decl==TypeInteger.I16 || decl==TypeInteger.I32;
        X86.load(enc,decl.log_size(),signed,xmm,
                 xmm ? dst-x86_64_v2.XMM_OFFSET : dst,ptr,idx,off,scale);
    }


    // General form: "ldN  dst,[base + idx<<2 + 12]"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(",");
        asm_address(code,sb);
    }
}
