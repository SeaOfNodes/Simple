package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.FunNode;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.SplitNode;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFloat;
import com.seaofnodes.simple.type.TypeInteger;

public class SplitX86 extends SplitNode {
    SplitX86( String kind, byte round ) { super(kind,round, new Node[2]); }
    @Override public String op() { return "mov"; }
    @Override public RegMask regmap(int i) { return x86_64_v2.SPLIT_MASK; }
    @Override public RegMask outregmap() { return x86_64_v2.SPLIT_MASK; }

    // Need to handle 8 cases: . reg->reg, reg->xmm, reg->flags, xmm->reg, xmm->xmm, xmm->flags, flags->reg, flags->xmm,
    // flags->flags.
    @Override public void encoding( Encoding enc ) {
        // REX.W + 8B /r	MOV r64, r/m64
        short dst = enc.reg(this );
        short src = enc.reg(in(1));
        if( dst == x86_64_v2.FLAGS ) {
            // mov reg, flags
            // push rcx
            // popf (Pop the top of the stack into the FLAGS register)
            // 50+rd	PUSH r64
            x86_64_v2.rexF(0,src,0,false,enc);
            enc.add1(0x50 + src);
            // popf
            enc.add1(0x9D);
            return;
        }
        if( src == x86_64_v2.FLAGS ) {
            // mov flags, reg
            // pushf; pop reg
            enc.add1(0x9C);
            // 58+ rd	POP r64
            x86_64_v2.rexF(0,dst,0,false, enc);
            enc.add1(0x58 + dst);
            return;
        }

        // Flag for being either XMM or stack
        boolean dstX = dst >= x86_64_v2.XMM_OFFSET;
        boolean srcX = src >= x86_64_v2.XMM_OFFSET;

        // Stack spills
        if( dst >= x86_64_v2.MAX_REG ) {
            int off = enc._fun.computeStackOffset(enc._code,dst);
            if( src >= x86_64_v2.MAX_REG ) {
                // Rare stack-stack move.  push [RSP+soff]; pop [RSP+doff]
                int soff = enc._fun.computeStackOffset(enc._code,src);
                enc.add1(0xFF);
                x86_64_v2.indirectAdr(0, (short)-1/*index*/, (short)x86_64_v2.RSP, soff, 6, enc);
                enc.add1(0x8F);
                x86_64_v2.indirectAdr(0, (short)-1/*index*/, (short)x86_64_v2.RSP,  off, 0, enc);
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

        // reg-reg move.  Adjust numbering for GPR vs FPR reg set
        if( dstX ) dst -= (short) x86_64_v2.XMM_OFFSET;
        if( srcX ) src -= (short) x86_64_v2.XMM_OFFSET;

        // 0x66 if moving between register classes
        if( dstX ^ srcX )  enc.add1(0x66);
        if( !dstX && srcX )
            { short tmp=src; src=dst; dst=tmp; }
        enc.add1(x86_64_v2.rex(dst, src, 0));

        // pick opcode based on regs
        if( !dstX && !srcX ) {
            // reg->reg (MOV r64, r/m64)
            enc.add1(0x8B);
        } else if( dstX && srcX ) {
            // xmm->xmm (NP 0F 28 /r MOVAPS xmm1, xmm2/m128)
            enc.add1(0x0F);
            enc.add1(0x28);
        } else if( dstX && !srcX ) {
            // reg->xmm (66 REX.W 0F 6E /r MOVQ xmm, r/m64)
            enc.add1(0x0F);
            enc.add1(0x6E);
        } else if( !dstX && srcX ) {
            // xmm->reg(66 0F 7E /r MOVQ r/m64, xmm)
            enc.add1(0x0F);
            enc.add1(0x7E);
        }

        enc.add1(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, dst, src));
    }

    // General form: "mov  dst = src"
    @Override public void asm(CodeGen code, SB sb) {
        FunNode fun = code._encoding==null ? null : code._encoding._fun;
        sb.p(code.reg(this,fun)).p(" = ").p(code.reg(in(1),fun));
    }
}
