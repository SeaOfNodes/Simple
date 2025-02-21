package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.Machine;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;

import java.io.ByteArrayOutputStream;

public class x86_64_v2 extends Machine {
    // X86-64 V2.  Includes e.g. SSE4.2 and POPCNT.
    @Override public String name() { return "x86_64_v2"; }

    public static int RAX =  0, RCX =  1, RDX =  2, RBX =  3, RSP =  4, RBP =  5, RSI =  6, RDI =  7;
    public static int R08 =  8, R09 =  9, R10 = 10, R11 = 11, R12 = 12, R13 = 13, R14 = 14, R15 = 15;

    public static int XMM0  = 16, XMM1  = 17, XMM2  = 18, XMM3  = 19, XMM4  = 20, XMM5  = 21, XMM6  = 22, XMM7  = 23;
    public static int XMM8  = 24, XMM9  = 25, XMM10 = 26, XMM11 = 27, XMM12 = 28, XMM13 = 29, XMM14 = 30, XMM15 = 31;
    public static int FLAGS = 32;
    public static int RPC = 33;

    public static int XMM_OFFSET = 16;

    // General purpose register mask: pointers and ints, not floats
    public static RegMask RMASK = new RegMask(0b1111111111111111);
    // No RSP in the *write* general set.
    public static RegMask WMASK = new RegMask(0b1111111111101111);
    // No RDX for division in inputs
    public static RegMask DMASK = new RegMask(0b1111111111111011);
    // Xmm register mask
    public static RegMask XMASK = new RegMask( 0b1111111111111111L << XMM0);

    public static RegMask FLAGS_MASK = new RegMask(1L<<FLAGS);
    public static RegMask SPLIT_MASK = new RegMask(0b1111111111101111L | (0b1111111111111111L << XMM0) | (1L<<FLAGS) );

    // Load/store mask; both GPR and FPR
    public static RegMask MEM_MASK = new RegMask(0b11111111111111111111111111111010L | (0b11111111111111111111111111111111L<<XMM0));
    // Return single int/ptr register
    public static RegMask RET_MASK = new RegMask(1L<<RAX);
    public static RegMask RET_FMASK= new RegMask(1L<<XMM0);
    public static RegMask RPC_MASK = new RegMask(1L<<RPC);


    public static RegMask RAX_MASK = new RegMask(1L<<RAX);
    public static RegMask RCX_MASK = new RegMask(1L<<RCX);
    public static RegMask RDX_MASK = new RegMask(1L<<RDX);
    public static RegMask RDI_MASK = new RegMask(1L<<RDI);
    public static RegMask R08_MASK = new RegMask(1L<<R08);
    public static RegMask R09_MASK = new RegMask(1L<<R09);
    public static RegMask RSI_MASK = new RegMask(1L<<RSI);

    public static RegMask XMM0_MASK = new RegMask(1L<<XMM0);
    public static RegMask XMM1_MASK = new RegMask(1L<<XMM1);
    public static RegMask XMM2_MASK = new RegMask(1L<<XMM2);
    public static RegMask XMM3_MASK = new RegMask(1L<<XMM3);
    public static RegMask XMM4_MASK = new RegMask(1L<<XMM4);
    public static RegMask XMM5_MASK = new RegMask(1L<<XMM5);
    public static RegMask XMM6_MASK = new RegMask(1L<<XMM6);
    public static RegMask XMM7_MASK = new RegMask(1L<<XMM7);
    public static RegMask[] XMMS = new RegMask[]{
        XMM0_MASK, XMM1_MASK, XMM2_MASK, XMM3_MASK,
        XMM4_MASK, XMM5_MASK, XMM6_MASK, XMM7_MASK,
    };

    // Encoding
    public static int REX_W = 0x48;
    public static int REX_WR = 0x4C;
    public static int REX_WRB = 0x4D;
    public static int REX_WB = 0x49;

    public enum MOD {INDIRECT, //  [mem]
                    INDIRECT_disp8, // [mem + 0x12]
                    INDIRECT_disp32,// [mem + 0x12345678]
                    DIRECT,          // mem
    };

    // size =  size in bits
    static public int imm(int imm, int size, ByteArrayOutputStream bytes) {
        if (size == 8) {
            bytes.write(imm & 0xFF);
        } else if (size == 16) {
            bytes.write(imm & 0xFF);
            bytes.write((imm >> 8) & 0xFF);
        } else if (size == 32) {
            bytes.write(imm & 0xFF);
            bytes.write((imm >> 8) & 0xFF);
            bytes.write((imm >> 16) & 0xFF);
            bytes.write((imm >> 24) & 0xFF);
        }
        return size;
    }

    // 0F (returned_value)
    // 0F 94	SETE r/m8
    // 0F 9C	SETL r/m8
    // 0F 9E	SETLE r/m8
    static public int setop(String op) {
        return switch(op) {
            case "==" -> 0x94;
            case "<"  -> 0x9C;
            case "<="  -> 0X9E;
            default  ->  throw new IllegalArgumentException("Too many arguments");
        };
    }


    // opcode included here
    // 74 cb	JE rel8
    // 7C  cb	J   L rel8
    // 7E cb	JLE rel8
    static public int jumpop(String op) {
        return switch(op) {
            case "==" -> 0x74cb;
            case "<"  -> 0x7Ccb;
            case "<="  -> 0X7Ecb;
            default  ->  throw new IllegalArgumentException("Too many arguments");
        };
    }

    // Clear bits
    // e.g before using setl
    // setl sets low 8 bits the other bits are still the same from before
    // Clear them
    public static void clear_bits(short reg1, short reg2, ByteArrayOutputStream bytes) {
        // use xor to clear them
        bytes.write(x86_64_v2.REX_W);
        bytes.write(0x33); // opcode
        bytes.write(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, reg1, reg2));
    }
    public static void zero_extend(short reg1, short reg2, ByteArrayOutputStream bytes) {
        bytes.write(x86_64_v2.REX_W);
        bytes.write(0x0F); // opcode
        bytes.write(0xB6); // opcode

        bytes.write(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, reg1, reg2));
    }

     public static void print_as_hex(ByteArrayOutputStream outputStream) {

        byte[] encodedBytes = outputStream.toByteArray();

        for (byte b : encodedBytes) {

            System.out.print(String.format("%02X ", b));
        }

        System.out.println();
    }

    public static int modrm(MOD mod, int reg, int m_r) {
        // combine all the bits

        return (mod.ordinal() << 6) | ((reg & 0x07) << 3) | m_r & 0x07;
    }

    // 00 000 000
    // same bit-layout as modrm
    public static int sib(int scale, int index, int base) {
        return (scale<< 6) | ((index & 0x07) << 3) | base & 0x07;
    }
    // reg1 is reg(R)
    // reg 2 is  r/mem(B)
    // reg3 is X(index)
    // reg4 is X(base)

    // 0 denotes no direct register
    public static int rex(int reg, int base_rm, int index) {
        // assuming 64 bit by default so: 0100 1000
        boolean firstHigh = (reg >= 8 && reg <= 15);
        boolean secondHigh = (base_rm >= 8 && base_rm <= 15);
        boolean thirdHigh = (index >= 8 && index <= 15);

        int rex = x86_64_v2.REX_W; // Default REX.W

        if (firstHigh) rex |= 0b00000100; // REX.R
        if (secondHigh) rex |= 0b00000001; // REX.B
        if (thirdHigh) rex |= 0b00000010; // REX.X
        return rex;
    }

    // -1 denotes empty value, not set - note 0 is different from -1 as it can represent rax.
    // Looks for best mod locally
    public static void sibAdr(int scale, short index, short base, int offset, int reg, ByteArrayOutputStream bytes) {
        // Assume indirect
        MOD mod = MOD.INDIRECT;
        MOD disp = MOD.INDIRECT;
        // is 1 byte enough or need more?
        if(offset != 0) {
            if (offset >= -128 && offset <= 127) {
                disp = MOD.INDIRECT_disp8;
            }
            else {
                disp = MOD.INDIRECT_disp32;
            }
        }

        // needs to pick optimal displacement mod if we want to encode base
        if(base == RBP || base == R13) {
            mod = disp;
        }

        // if there is displacement but base is one of these, switch mod
        if((base == RSI || base == R08 || base == R09 || base == R10 || base == R11 || base == R12) && offset != 0) {
            mod = disp;
        }

        // rsp is hard-coded here(0x04)
        if(disp != mod) mod = disp;
        // special encoding for [base +offset]
        if(index == -1) {
            bytes.write(x86_64_v2.modrm(mod, 0, base));
            imm(offset, 32, bytes);
            return;
        }

        bytes.write(x86_64_v2.modrm(mod, reg, 0x04));
        bytes.write(x86_64_v2.sib(scale,  index, base));

        if(mod == MOD.INDIRECT_disp8) {
            x86_64_v2.imm(offset, 8, bytes);
        } else if(mod == MOD.INDIRECT_disp32) {
            x86_64_v2.imm(offset, 32, bytes);
        }
        // no offset if just MOD.INDIRECT

    }
    // Calling conv metadata
    public int GPR_COUNT_CONV_WIN64 = 4; // RCX, RDX, R9, R9
    public int XMM_COUNT_CONV_WIN64 = 4; // XMM0L, XMM1L, XMM2L, XMM3L

    public int GPR_COUNT_CONV_SYSTEM_V = 6; // RDI, RSI, RDX, RCX, R8, R9
    public int XMM_COUNT_CONV_SYSTEM_V = 4; // XMM0, XMM1, XMM2, XMM3 ....
    // Human-readable name for a register number, e.g. "RAX".
    // Hard crash for bad register number, fix yer bugs!
    public static final String[] REGS = new String[] {
        "rax" , "rcx" , "rdx"  , "rbx"  , "rsp"  , "rbp"  , "rsi"  , "rdi"  ,
        "r8"  , "r9"  , "r10"  , "r11"  , "r12"  , "r13"  , "r14"  , "r15"  ,
        "xmm0", "xmm1", "xmm2" , "xmm3" , "xmm4" , "xmm5" , "xmm6" , "xmm7" ,
        "xmm8", "xmm9", "xmm10", "xmm11", "xmm12", "xmm13", "xmm14", "xmm15",
        "flags","[rsp-4]",
    };
    @Override public String reg( int reg ) {
        return reg < REGS.length ? REGS[reg] : "[rsp+"+(reg-REGS.length)*4+"]";
    }

    // WIN64(param passing)
    static RegMask[] CALLINMASK_WIN64 = new RegMask[] {
        RPC_MASK,
        null,
        RCX_MASK,
        RDX_MASK,
        R08_MASK,
        R09_MASK,
    };

    // caller saved(win64)
    public static final long WIN64_ABI_CALLER_SAVED =
        (1L << RAX) | (1L << RCX) | (1L << RDX) | (1L << R08) | (1L << R09) | (1L << R10) | (1L << R11);

    // callee saved(win64)
    public static final long WIN64_ABI_CALLEE_SAVED = ~WIN64_ABI_CALLER_SAVED;

    // SystemV(param passing)
    static RegMask[] CALLINMASK_SYSTEMV = new RegMask[] {
        RPC_MASK,
        null,   // Memory, no register
        RDI_MASK,
        RSI_MASK,
        RDX_MASK,
        RCX_MASK,
        R08_MASK,
        R09_MASK,
    };

    // caller saved(systemv)
    // caller saved(win64)
    public static final long SYSTEMV_ABI_CALLER_SAVED =
        (1L << RAX) | (1L << RDI) | (1L << RSI) | (1L << RCX) | (1L << RDX) | (1L << R08) | (1L << R09) << (1L << R10) << (1L << R11);
    // callee saved(systemv)
    public static final long SYSTEMV_ABI_CALLE_SAVED = ~SYSTEMV_ABI_CALLER_SAVED;


    static RegMask callInMask( TypeFunPtr tfp, int idx ) {
        RegMask[] cargs = switch( CodeGen.CODE._callingConv ) {
        case CodeGen.CallingConv.SystemV -> CALLINMASK_SYSTEMV;
        case CodeGen.CallingConv.Win64   -> CALLINMASK_WIN64;
        };
        if( idx >= 2 && tfp.arg(idx-2) instanceof TypeFloat )
            return XMMS[idx-2];
        if( idx >= cargs.length )
            throw Utils.TODO(); // Pass on stack slot
        return cargs[idx];
    }

    // Create a split op; any register to any register, including stack slots
    @Override public Node split() {  return new SplitX86();  }

    // Return a MachNode unconditional branch
    @Override public CFGNode jump() {
        throw Utils.TODO();
    }

    // Break an infinite loop
    @Override public IfNode never( CFGNode ctrl ) {
        throw Utils.TODO();
    }

    // Instruction selection
    @Override public Node instSelect( Node n ) {
        return switch( n ) {
        case AddFNode     addf  -> addf(addf);
        case AddNode      add   -> add(add);
        case AndNode      and   -> and(and);
        case BoolNode     bool  -> cmp(bool);
        case CallEndNode  cend  -> new CallEndX86(cend);
        case CallNode     call  -> call(call);
        case CastNode     cast  -> new CastX86(cast);
        case CProjNode    c     -> new CProjNode(c);
        case ConstantNode con   -> con(con);
        case DivFNode     divf  -> new DivFX86(divf);
        case DivNode      div   -> new DivX86(div);
        case FunNode      fun   -> new FunX86(fun);
        case IfNode       iff   -> jmp(iff);
        case LoadNode     ld    -> ld(ld);
        case MemMergeNode mem   -> new MemMergeNode(mem);
        case MulFNode     mulf  -> new MulFX86(mulf);
        case MulNode      mul   -> mul(mul);
        case NewNode      nnn   -> new NewX86(nnn);
        case OrNode       or   ->  or(or);
        case ParmNode     parm  -> new ParmX86(parm);
        case PhiNode      phi   -> new PhiNode(phi);
        case ProjNode     prj   -> prj(prj);
        case ReadOnlyNode read  -> new ReadOnlyNode(read);
        case ReturnNode   ret   -> new RetX86(ret,ret.fun());
        case SarNode      sar   -> sar(sar);
        case ShlNode      shl   -> shl(shl);
        case ShrNode      shr   -> shr(shr);
        case StartNode    start -> new StartNode(start);
        case StopNode     stop  -> new StopNode(stop);
        case StoreNode    st    -> st(st);
        case SubFNode     subf  -> new SubFX86(subf);
        case SubNode      sub   -> sub(sub);
        case ToFloatNode  tfn   -> i2f8(tfn);
        case XorNode      xor   -> xor(xor);

        case LoopNode     loop  -> new LoopNode(loop);
        case RegionNode   region-> new RegionNode(region);
        default -> throw Utils.TODO();
        };
    }


    // Attempt a full LEA-style break down.
    private Node add( AddNode add ) {
        Node lhs = add.in(1);
        Node rhs = add.in(2);
        if( lhs instanceof LoadNode ld && ld.nOuts()==1 )
            return new AddMemX86(add,address(ld),ld.ptr(),idx,off,scale, imm(rhs),val);

        if( rhs instanceof LoadNode ld && ld.nOuts()==1 )
            throw Utils.TODO(); // Swap load sides

        // Attempt a full LEA-style break down.
        // Returns one of AddX86, AddIX86, LeaX86, or LHS
        if( rhs instanceof ConstantNode off && off._con instanceof TypeInteger toff ) {
            if( lhs instanceof AddNode ladd )
                // ((base + (idx << scale)) + off)
                return _lea(add,ladd.in(1),ladd.in(2),toff.value());
            if( lhs instanceof ShlNode shift )
                // (idx << scale) + off; no base
                return _lea(add,null,shift,toff.value());

            // lhs + rhs1
            if( toff.value()==0 ) return add;
            return new AddIX86(add, toff);
        }
        return _lea(add,lhs,rhs,0);
    }


    private Node addf( AddFNode addf ) {
        if( addf.in(1) instanceof LoadNode ld && ld.nOuts()==1 )
            return new AddFMemX86(addf,address(ld),ld.ptr(),idx,off,scale, addf.in(2));

        if( addf.in(2) instanceof LoadNode ld && ld.nOuts()==1 )
            throw Utils.TODO(); // Swap load sides

        return new AddFX86(addf);
    }


    private Node _lea( Node add, Node base, Node idx, long off ) {
        int scale = 0;
        if( base instanceof ShlNode && !(idx instanceof ShlNode) ) throw Utils.TODO(); // Bug in canonicalization, should on RHS
        if( idx instanceof ShlNode shift && shift.in(2) instanceof ConstantNode shfcon &&
            shfcon._con instanceof TypeInteger tscale && 0 <= tscale.value() && tscale.value() <= 3 ) {
            idx = shift.in(1);
            scale = ((int)tscale.value());
        }

        // (base + idx) + off
        return off==0 && scale==1
            ? new AddX86(add)
            : new LeaX86(add,base,idx,scale,off);
    }


    private Node and(AndNode and) {
        if( and.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti )
            return new AndIX86(and, ti);
        return new AndX86(and);
    }

    private Node call(CallNode call) {
        if( call.fptr() instanceof ConstantNode con && con._con instanceof TypeFunPtr tfp )
            return new CallX86(call, tfp);
        return new CallRX86( call );
    }

    // Because X86 flags, a normal ideal Bool is 2 X86 ops: a "cmp" and at "setz".
    // Ideal If reading from a setz will skip it and use the "cmp" instead.
    private Node cmp( BoolNode bool ) {
        Node cmp = _cmp(bool);
        return new SetX86(cmp,bool.op());
    }
    private Node _cmp( BoolNode bool ) {
        // Float variant
        if( bool instanceof BoolNode.EQF ||
            bool instanceof BoolNode.LTF ||
            bool instanceof BoolNode.LEF )
            return new CmpFX86(bool);

        Node lhs = bool.in(1);
        Node rhs = bool.in(2);
        if( lhs instanceof LoadNode ld && ld.nOuts()==1 )
            return new CmpMemX86(bool,address(ld),ld.ptr(),idx,off,scale, imm(rhs),val,false);

        if( rhs instanceof LoadNode ld && ld.nOuts()==1 )
            return new CmpMemX86(bool,address(ld),ld.ptr(),idx,off,scale, imm(lhs),val,true);

        // Vs immediate
        if( rhs instanceof ConstantNode con && con._con instanceof TypeInteger ti )
            return new CmpIX86(bool, ti);
        // x vs y
        return new CmpX86(bool);
    }

    private Node con( ConstantNode con ) {
        if( !con._con.isConstant() ) return new ConstantNode( con ); // Default unknown caller inputs
        return switch( con._con ) {
        case TypeInteger ti  -> new IntX86(con);
        case TypeFloat   tf  -> new FltX86(con);
        case TypeFunPtr  tfp -> new TFPX86(con);
        case TypeMemPtr  tmp -> throw Utils.TODO();
        case TypeNil     tn  -> throw Utils.TODO();
        // TOP, BOTTOM, XCtrl, Ctrl, etc.  Never any executable code.
        case Type t -> t==Type.NIL ? new IntX86(con) : new ConstantNode(con);
        };
    }

    private Node i2f8(ToFloatNode tfn) {
        assert tfn.in(1)._type instanceof TypeInteger ti;
        return new I2f8X86(tfn);
    }

    private Node jmp( IfNode iff ) {
        // If/Bool combos will match to a Cmp/Set which sets flags.
        // Most general arith ops will also set flags, which the Jmp needs directly.
        // Loads do not set the flags, and will need an explicit TEST
        if( !(iff.in(1) instanceof BoolNode) )
            iff.setDef(1,new BoolNode.EQ(iff.in(1),new ConstantNode(TypeInteger.ZERO)));
        return new JmpX86(iff, ((BoolNode)iff.in(1)).op());
    }

    private Node ld( LoadNode ld ) {
        return new LoadX86(address(ld),ld.ptr(),idx,off,scale);
    }

    private Node mul(MulNode mul) {
        return mul.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti
            ? new MulIX86(mul, ti)
            : new MulX86(mul);
    }

    private Node or(OrNode or) {
        if( or.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti)
            return new OrIX86(or, ti);
        return new OrX86(or);
    }

    private Node prj( ProjNode prj ) {
        return new ProjX86(prj);
    }

    private Node sar( SarNode sar ) {
        if( sar.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti)
            return new SarIX86(sar, ti);
        return new SarX86(sar);
    }

    private Node shl( ShlNode shl ) {
        if( shl.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti )
            return new ShlIX86(shl, ti);
        return new ShlX86(shl);
    }

    private Node shr(ShrNode shr) {
        if( shr.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti )
            return new ShrIX86(shr, ti);
        return new ShrX86(shr);
    }

    private Node st( StoreNode st ) {
        // Look for "*ptr op= val"
        Node op = st.val();
        if( op instanceof AddNode ) {
            if( op.in(1) instanceof LoadNode ld &&
                ld.in(0)==st.in(0) &&
                ld.mem()==st.mem() &&
                ld.ptr()==st.ptr() &&
                ld.off()==st.off() ) {
                if( op instanceof AddNode ) {
                    return new MemAddX86(address(st),st.ptr(),idx,off,scale,imm(op.in(2)),val);
                }
                throw Utils.TODO();
            }
        }

        return new StoreX86(address(st),st.ptr(),idx,off,scale,imm(st.val()),val);
    }

    private Node sub( SubNode sub ) {
        return sub.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti
            ? new AddIX86(sub, TypeInteger.constant(-ti.value()))
            : new SubX86(sub);
    }

    private Node xor(XorNode xor) {
        if(xor.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti)
            return new XorIX86(xor, ti);
        return new XorX86(xor);
    }

    // Gather X86 addressing mode bits prior to constructing.  This is a
    // builder pattern, but saving the bits in a *local* *global* here to keep
    // mess contained.
    private static int off, scale, imm;
    private static Node idx, val;
    private <N extends MemOpNode> N address( N mop ) {
        off = scale = imm = 0;  // Reset
        idx = val = null;
        Node base = mop.ptr();
        // Skip/throw-away a ReadOnly, only used to typecheck
        if( base instanceof ReadOnlyNode read ) base = read.in(1);
        assert !(base instanceof AddNode) && base._type instanceof TypeMemPtr; // Base ptr always, not some derived
        if( mop.off() instanceof AddNode add && add.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti ) {
            off = (int)ti.value();
            assert off == ti.value(); // In 32-bit range
            idx = add.in(1);
            if( idx instanceof ShlNode shift && shift.in(2) instanceof ConstantNode shfcon &&
                shfcon._con instanceof TypeInteger tscale && 0 <= tscale.value() && tscale.value() <= 3 ) {
                idx = shift.in(1);
                scale = (int)tscale.value();
            }
        } else {
            if( mop.off() instanceof ConstantNode con && con._con instanceof TypeInteger ti ) {
                off = (int)ti.value();
                assert off == ti.value(); // In 32-bit range
            } else {
                idx = mop.off();
            }
        }
        return mop;
    }

    private int imm( Node xval ) {
        assert val==null && imm==0;
        if( xval instanceof ConstantNode con && con._con instanceof TypeInteger ti ) {
            val = null;
            imm = (int)ti.value();
            assert imm == ti.value(); // In 32-bit range
        } else {
            val = xval;
        }
        return imm;
    }

}
