package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.FunNode;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.SplitNode;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFloat;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;

public class SplitX86 extends SplitNode {
    SplitX86( String kind, byte round ) { super(kind,round, new Node[2]); }
    @Override public String op() { return "mov"; }
    @Override public RegMask regmap(int i) { return x86_64_v2.SPLIT_MASK; }
    @Override public RegMask outregmap() { return x86_64_v2.SPLIT_MASK; }

    // Need to handle 8 cases: . reg->reg, reg->xmm, reg->flags, xmm->reg, xmm->xmm, xmm->flags, flags->reg, flags->xmm,
    // flags->flags.
    @Override public void encoding( Encoding enc ) {
        short dst=enc.reg(this), src=enc.reg(in(1));
        if( dst==x86_64_v2.FLAGS ) { X86.flags(enc,src,true); return; }
        if( src==x86_64_v2.FLAGS ) { X86.flags(enc,dst,false); return; }
        boolean dstX=dst>=x86_64_v2.XMM_OFFSET, srcX=src>=x86_64_v2.XMM_OFFSET;
        // Stack spills
        if( dst >= x86_64_v2.MAX_REG ) {
            int off = enc._fun.computeStackOffset(enc._code,dst);
            if( src >= x86_64_v2.MAX_REG ) {
                // Rare stack-stack move.  push [RSP+soff]; pop [RSP+doff]
                int soff = enc._fun.computeStackOffset(enc._code,src);
                X86.stackCopy(enc,soff,off);
                return;
            }
            StoreX86.encVal(enc, srcX ? TypeFloat.F64 : TypeInteger.BOT, (short)x86_64_v2.RSP, (short)-1/*index*/, src, off, 0);
            return;
        }
        if( src >= x86_64_v2.MAX_REG ) {
            int off = enc._fun.computeStackOffset(enc._code,src);
            LoadX86.enc(enc, dstX ? TypeFloat.F64 : TypeInteger.BOT, dst, (short)x86_64_v2.RSP, (short)-1, off, 0);
            return;
        }

        X86.move(enc,dstX,srcX,dstX ? dst-x86_64_v2.XMM_OFFSET : dst,
                 srcX ? src-x86_64_v2.XMM_OFFSET : src);
    }

    // General form: "mov  dst = src"
    @Override public void asm(CodeGen code, SB sb) {
        FunNode fun = code._encoding==null ? null : code._encoding._fun;
        sb.p(code.reg(this,fun)).p(" = ").p(code.reg(in(1),fun));
    }
}
