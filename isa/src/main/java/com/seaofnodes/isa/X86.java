package com.seaofnodes.isa;

/** x86-64 instruction bytes. Registers are hardware numbers 0..15, in the chosen bank. */
public final class X86 {
    private X86() {}
    public enum MOD { INDIRECT, INDIRECT_disp8, INDIRECT_disp32, DIRECT }

    public static int modrm(MOD mod, int reg, int rm) {
        if( reg==-1 ) reg=0;
        assert 0<=reg && reg<16;
        assert 0<=rm && rm<16;
        return (mod.ordinal()<<6) | ((reg&7)<<3) | (rm&7);
    }
    public static int sib(int scale, int index, int base) {
        assert 0<=scale && scale<=3;
        assert 0<=index && index<16;
        assert 0<=base && base<16;
        return (scale<<6) | ((index&7)<<3) | (base&7);
    }
    public static int rex(int reg, int base, int index, boolean wide) {
        assert -1<=reg && reg<16;
        assert -1<=base && base<16;
        assert -1<=index && index<16;
        return (wide ? 0x48 : 0x40) | (reg>=8 ? 4 : 0) |
            (index>=8 ? 2 : 0) | (base>=8 ? 1 : 0);
    }
    public static int rex(int reg, int base, int index) { return rex(reg,base,index,true); }

    /** Omit a redundant REX; byte-register instructions sometimes need it explicitly. */
    public static byte rexF(int reg, int base, int index, boolean wide, CodeSink out) {
        int bits=rex(reg,base,index,wide);
        if( bits==0x40 ) return 0;
        out.add1(bits);
        return 1;
    }

    /** ModRM/SIB/displacement for [base + index*2^scale + offset]; -1 means no index. */
    public static void indirectAdr(int scale, int index, int base, int offset, int reg, CodeSink out) {
        assert 0<=base && base<16;
        assert -1<=index && index<16 && index!=4;
        MOD mod=offset==0 ? MOD.INDIRECT : imm8(offset) ? MOD.INDIRECT_disp8 : MOD.INDIRECT_disp32;
        // The no-displacement r/m=5 form is RIP-relative, even with REX.B.
        if( mod==MOD.INDIRECT && (base==5 || base==13) ) mod=MOD.INDIRECT_disp8;
        // r/m=4 always introduces SIB. Index=4 with no REX.X means no index.
        if( index==-1 && (base==4 || base==12) ) index=4;
        if( index==-1 ) out.add1(modrm(mod,reg,base));
        else out.add1(modrm(mod,reg,4)).add1(sib(scale,index,base));
        if( mod==MOD.INDIRECT_disp8 ) out.add1(offset);
        if( mod==MOD.INDIRECT_disp32 ) out.add4(offset);
    }
    private static boolean imm8(int value) { return -128<=value && value<=127; }
    public static void unary(CodeSink out, int op, int digit, int dst) {
        out.add1(rex(0,dst,-1)).add1(op).add1(modrm(MOD.DIRECT,digit,dst));
    }
    public static void shift(CodeSink out, int digit, int dst, int count) {
        unary(out,0xC1,digit,dst);
        out.add1(count); // A shift always takes imm8; the processor masks its count.
    }
    public static void divide(CodeSink out, int src) {
        out.add1(0x48).add1(0x99); // CQO: sign-extend rax into rdx:rax.
        unary(out,0xF7,7,src);
    }
    public static void sse(CodeSink out, int prefix, int op, boolean wide, int dst, int src) {
        if( prefix!=0 ) out.add1(prefix);
        rexF(dst,src,-1,wide,out);
        opcode(out,op);
        out.add1(modrm(MOD.DIRECT,dst,src));
    }
    public static void memory(CodeSink out, int prefix, int op, boolean wide,
                              int reg, int base, int index, int offset, int scale) {
        if( prefix!=0 ) out.add1(prefix);
        rexF(reg,base,index,wide,out);
        opcode(out,op);
        indirectAdr(scale,index,base,offset,reg,out);
    }
    public static void cmpMem(CodeSink out, int logSize, int reg, int base, int index,
                             int offset, int scale, boolean swap) {
        if( logSize==1 ) out.add1(0x66);
        if( logSize==0 && reg>=4 ) out.add1(rex(reg,base,index,false));
        else rexF(reg,base,index,logSize==3,out);
        out.add1((logSize==0 ? 0x38 : 0x39)+(swap ? 2 : 0));
        indirectAdr(scale,index,base,offset,reg,out);
    }
    public static void addMem(CodeSink out, int src, int value, int base, int index, int offset, int scale) {
        memory(out,0,src==-1 ? (value==1 ? 0xFF : 0x81) : 0x01,true,
               src==-1 ? 0 : src,base,index,offset,scale);
        if( src==-1 && value!=1 ) out.add4(value);
    }
    public static void extend(CodeSink out, int bits, boolean signed, int dst, int src) {
        out.add1(rex(dst,src,-1,signed || bits<32));
        opcode(out,bits==32 ? (signed ? 0x63 : 0x8B) : (signed ? 0x0FBE : 0x0FB6)+(bits==16 ? 1 : 0));
        out.add1(modrm(MOD.DIRECT,dst,src));
    }
    /** SETcc dstb; MOVZX dst32,dstb. Condition is the low four bits of Jcc/SETcc. */
    public static void set(CodeSink out, int condition, int dst) {
        if( dst>=4 ) out.add1(rex(0,dst,-1,false));
        out.add1(0x0F).add1(0x90|condition).add1(modrm(MOD.DIRECT,0,dst));
        if( dst>=4 ) out.add1(rex(dst,dst,-1,false));
        out.add1(0x0F).add1(0xB6).add1(modrm(MOD.DIRECT,dst,dst));
    }
    public static void constant(CodeSink out, int dst, long value) {
        if( value==0 ) {
            if( dst>=8 ) out.add1(rex(dst,dst,-1));
            out.add1(0x33).add1(modrm(MOD.DIRECT,dst,dst));
        } else if( Integer.MIN_VALUE<=value && value<0 ) {
            unary(out,0xC7,0,dst);
            out.add4((int)value);
        } else if( 0<=value && value<=0xFFFFFFFFL ) {
            rexF(0,dst,-1,false,out);
            out.add1(0xB8+(dst&7)).add4((int)value);
        } else {
            out.add1(rex(0,dst,-1)).add1(0xB8+(dst&7)).add8(value);
        }
    }
    /** RIP-relative operand; returns length so the chapter can record its relocation. */
    public static byte rip(CodeSink out, int prefix, int op, boolean wide, int dst) {
        int size=4+1+(op>255 ? 2 : 1);
        if( prefix!=0 ) { out.add1(prefix); size++; }
        size+=rexF(dst,-1,-1,wide,out);
        opcode(out,op);
        out.add1(modrm(MOD.INDIRECT,dst,5)).add4(0);
        return (byte)size;
    }
    public static void call(CodeSink out) { out.add1(0xE8).add4(0); }
    public static void callRegister(CodeSink out, int src) {
        rexF(0,src,-1,false,out);
        out.add1(0xFF).add1(modrm(MOD.DIRECT,2,src));
    }
    public static void ret(CodeSink out) { out.add1(0xC3); }
    public static void branch(CodeSink out, int condition) {
        out.add1(condition<0 ? 0xEB : 0x70|condition).add1(0);
    }
    public static byte branchSize(int delta, boolean conditional) {
        return (byte)(imm8(delta-2) ? 2 : conditional ? 6 : 5);
    }
    public static void patchBranch(byte[] bytes, int start, int size, int delta, int condition) {
        delta-=size;
        if( size==2 ) {
            assert (byte)delta==delta;
            bytes[start+1]=(byte)delta;
        } else {
            int pos=start;
            if( condition<0 ) bytes[pos++]=(byte)0xE9;
            else { bytes[pos++]=0x0F; bytes[pos++]=(byte)(0x80|condition); }
            for( int i=0; i<4; i++ ) bytes[pos+i]=(byte)(delta>>(8*i));
        }
    }
    public static void flags(CodeSink out, int reg, boolean toFlags) {
        if( !toFlags ) out.add1(0x9C); // PUSHF
        rexF(0,reg,-1,false,out);
        out.add1((toFlags ? 0x50 : 0x58)+(reg&7));
        if( toFlags ) out.add1(0x9D); // POPF
    }
    public static void stackCopy(CodeSink out, int srcOffset, int dstOffset) {
        memory(out,0,0xFF,false,6,4,-1,srcOffset,0); // PUSH [rsp+srcOffset]
        memory(out,0,0x8F,false,0,4,-1,dstOffset,0); // POP uses the restored rsp.
    }
    public static void move(CodeSink out, boolean dstXmm, boolean srcXmm, int dst, int src) {
        if( dstXmm ^ srcXmm ) out.add1(0x66);
        if( !dstXmm && srcXmm ) { int tmp=dst; dst=src; src=tmp; }
        reg(out,!dstXmm && !srcXmm ? 0x8B : dstXmm && srcXmm ? 0x0F28 : dstXmm ? 0x0F6E : 0x0F7E,dst,src);
    }
    private static void opcode(CodeSink out, int op) {
        if( op>255 ) out.add1(op>>8);
        out.add1(op);
    }

    /** REX.W + opcode + /r, with the destination in ModRM.reg. */
    public static void reg(CodeSink out, int op, int dst, int src) {
        out.add1(rex(dst,src,-1));
        opcode(out,op);
        out.add1(modrm(MOD.DIRECT,dst,src));
    }
    /** Group-1 arithmetic: 81 /digit id or 83 /digit ib. */
    public static void imm(CodeSink out, int op, int digit, int dst, int value) {
        out.add1(rex(0,dst,-1)).add1(op+(imm8(value) ? 2 : 0));
        out.add1(modrm(MOD.DIRECT,digit,dst));
        if( imm8(value) ) out.add1(value); else out.add4(value);
    }
    public static void imul(CodeSink out, int dst, int src, int value) {
        out.add1(rex(dst,src,-1)).add1(imm8(value) ? 0x6B : 0x69);
        out.add1(modrm(MOD.DIRECT,dst,src));
        if( imm8(value) ) out.add1(value); else out.add4(value);
    }
    public static void not(CodeSink out, int dst, int src) {
        reg(out,0x85,src,src); // TEST src,src
        set(out,4,dst); // SETZ; MOVZX
    }
    public static void lea(CodeSink out, int dst, int base, int index, int offset, int scale) {
        out.add1(rex(dst,base,index)).add1(0x8D);
        if( base==-1 ) {
            assert 0<=index && index<16 && index!=4;
            out.add1(modrm(MOD.INDIRECT,dst,4)).add1(sib(scale,index,5)).add4(offset);
        } else indirectAdr(scale,index,base,offset,dst,out);
    }

    /** Load into a GPR (extending to 64 bits) or an XMM register (scalar 32/64 bits). */
    public static void load(CodeSink out, int logSize, boolean signed, boolean xmm,
                            int dst, int base, int index, int offset, int scale) {
        assert 0<=logSize && logSize<=3;
        if( xmm ) {
            assert logSize==2 || logSize==3;
            out.add1(logSize==2 ? 0xF3 : 0xF2);
            rexF(dst,base,index,false,out);
            out.add1(0x0F).add1(0x10);
        } else {
            rexF(dst,base,index,logSize!=2 || signed,out);
            int op=switch(logSize) {
                case 0 -> signed ? 0x0FBE : 0x0FB6;
                case 1 -> signed ? 0x0FBF : 0x0FB7;
                case 2 -> signed ? 0x63 : 0x8B;
                default -> 0x8B;
            };
            opcode(out,op);
        }
        indirectAdr(scale,index,base,offset,dst,out);
    }
    public static void store(CodeSink out, int logSize, boolean xmm,
                             int src, int base, int index, int offset, int scale) {
        assert 0<=logSize && logSize<=3;
        if( xmm ) {
            assert logSize==2 || logSize==3;
            out.add1(logSize==2 ? 0xF3 : 0xF2);
            rexF(src,base,index,false,out);
            out.add1(0x0F).add1(0x11);
        } else {
            // Legacy prefixes precede REX, which must immediately precede the opcode.
            if( logSize==1 ) out.add1(0x66);
            if( logSize==0 && src>=4 ) out.add1(rex(src,base,index,false));
            else rexF(src,base,index,logSize==3,out);
            out.add1(logSize==0 ? 0x88 : 0x89);
        }
        indirectAdr(scale,index,base,offset,src,out);
    }
    public static void storeImm(CodeSink out, int logSize, int value,
                                int base, int index, int offset, int scale) {
        memImm(out,logSize,0xC6,0xC7,0,value,base,index,offset,scale);
    }
    public static void cmpImm(CodeSink out, int logSize, int value,
                              int base, int index, int offset, int scale) {
        memImm(out,logSize,0x80,0x81,7,value,base,index,offset,scale);
    }
    private static void memImm(CodeSink out, int logSize, int op8, int op, int digit, int value,
                               int base, int index, int offset, int scale) {
        assert 0<=logSize && logSize<=3;
        if( logSize==1 ) out.add1(0x66);
        rexF(-1,base,index,logSize==3,out);
        out.add1(logSize==0 ? op8 : op);
        indirectAdr(scale,index,base,offset,digit,out);
        switch(logSize) {
            case 0 -> out.add1(value);
            case 1 -> out.add2(value);
            default -> out.add4(value); // Even a 64-bit operand takes only imm32.
        }
    }
}
