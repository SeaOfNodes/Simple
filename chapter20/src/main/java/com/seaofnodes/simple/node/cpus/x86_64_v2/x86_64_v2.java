package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.Machine;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;

public class x86_64_v2 extends Machine {
    // X86-64 V2.  Includes e.g. SSE4.2 and POPCNT.
    @Override public String name() { return "x86_64_v2"; }

    static final int RAX =  0, RCX =  1, RDX =  2, RBX =  3, RSP =  4, RBP =  5, RSI =  6, RDI =  7;
    static final int R08 =  8, R09 =  9, R10 = 10, R11 = 11, R12 = 12, R13 = 13, R14 = 14, R15 = 15;

    static final int XMM0  = 16, XMM1  = 17, XMM2  = 18, XMM3  = 19, XMM4  = 20, XMM5  = 21, XMM6  = 22, XMM7  = 23;
    static final int XMM8  = 24, XMM9  = 25, XMM10 = 26, XMM11 = 27, XMM12 = 28, XMM13 = 29, XMM14 = 30, XMM15 = 31;
    static final int FLAGS = 32;
    static final int MAX_REG = 33;
    static final int RPC = 33;

    // General purpose register mask: pointers and ints, not floats
    static final long RD_BITS = 0b1111111111111111L; // All the GPRs
    static RegMask RMASK = new RegMask(RD_BITS);
    // No RSP in the *write* general set.
    static final long WR_BITS = 0b1111111111101111L; // All the GPRs minus RSP
    static RegMask WMASK = new RegMask(WR_BITS);
    // Xmm register mask
    static final long FP_BITS = 0b1111111111111111L << XMM0; // All the XMMs
    static final RegMask XMASK = new RegMask(FP_BITS);
    static final RegMask FLAGS_MASK = new RegMask(FLAGS);
    static final RegMask RPC_MASK = new RegMask(RPC);

    static final long SPILLS = -(1L << MAX_REG);
    static final RegMask SPLIT_MASK = new RegMask(WR_BITS | FP_BITS | (1L<<FLAGS) | SPILLS, -1L );

    // Load/store mask; both GPR and FPR
    static RegMask MEM_MASK = new RegMask(WR_BITS | FP_BITS);

    static final RegMask RAX_MASK = new RegMask(RAX);
    static final RegMask RCX_MASK = new RegMask(RCX);
    static final RegMask RDX_MASK = new RegMask(RDX);
    static final RegMask RDI_MASK = new RegMask(RDI);
    static final RegMask R08_MASK = new RegMask(R08);
    static final RegMask R09_MASK = new RegMask(R09);
    static final RegMask RSI_MASK = new RegMask(RSI);

    static final RegMask XMM0_MASK = new RegMask(XMM0);

    // Human-readable name for a register number, e.g. "RAX".
    // Hard crash for bad register number, fix yer bugs!
    static final String[] REGS = new String[] {
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
        RCX_MASK,
        RDX_MASK,
        R08_MASK,
        R09_MASK,
    };

    // SystemV(param passing)
    static RegMask[] CALLINMASK_SYSTEMV = new RegMask[] {
        RDI_MASK,
        RSI_MASK,
        RDX_MASK,
        RCX_MASK,
        R08_MASK,
        R09_MASK,
    };

    // Limit of float args passed in registers
    static RegMask[] XMMS4 = new RegMask[]{
        new RegMask(XMM0), new RegMask(XMM1), new RegMask(XMM2), new RegMask(XMM3),
    };

    // Map from function signature and argument index to register.
    // Used to set input registers to CallNodes, and ParmNode outputs.
    static RegMask callInMask( TypeFunPtr tfp, int idx ) {
        if( idx==0 ) return RPC_MASK;
        if( idx==1 ) return null;
        // Count floats in signature up to index
        int fcnt=0;
        for( int i=2; i<idx; i++ )
            if( tfp.arg(i-2) instanceof TypeFloat )
                fcnt++;
        // Floats up to XMMS in XMM registers
        if( tfp.arg(idx-2) instanceof TypeFloat ) {
            if( fcnt < XMMS4.length )
                return XMMS4[fcnt];
        } else {
            RegMask[] cargs = switch( CodeGen.CODE._callingConv ) {
            case "SystemV" -> CALLINMASK_SYSTEMV;
            case "Win64"   -> CALLINMASK_WIN64;
            default        -> throw new IllegalArgumentException("Unknown calling convention: "+CodeGen.CODE._callingConv);
            };
            if( idx-2-fcnt < cargs.length )
                return cargs[idx-2-fcnt];
        }
        throw Utils.TODO(); // Pass on stack slot
    }

    // caller saved(systemv)
    static final long SYSTEM5_CALLER_SAVE =
        (1L<< RAX) | (1L<< RCX) | (1L<< RDX) |
        (1L<< RDI) | (1L<< RSI) |
        (1L<< R08) | (1L<< R09) | (1L<< R10) | (1L<< R11) |
        (1L<<FLAGS)|           // Flags are killed
        // All FP regs are killed
        FP_BITS;
    static final RegMask SYSTEM5_CALLER_SAVE_MASK = new RegMask(SYSTEM5_CALLER_SAVE);

    // caller saved(win64)
    static final long WIN64_CALLER_SAVE =
        (1L<< RAX) | (1L<< RCX) | (1L<< RDX) |
        (1L<< R08) | (1L<< R09) | (1L<< R10) | (1L<< R11) |
        (1L<<FLAGS)|           // Flags are killed
        // Only XMM0-XMM5 are killed; XMM6-XMM15 are preserved
        (1L<<XMM0) | (1L<<XMM1) | (1L<<XMM2) | (1L<<XMM3) |
        (1L<<XMM4) | (1L<<XMM5);
    static final RegMask WIN64_CALLER_SAVE_MASK = new RegMask(WIN64_CALLER_SAVE);

    static RegMask x86CallerSave() {
        return switch( CodeGen.CODE._callingConv ) {
        case "SystemV" -> SYSTEM5_CALLER_SAVE_MASK;
        case "Win64"   ->   WIN64_CALLER_SAVE_MASK;
        default        -> throw new IllegalArgumentException("Unknown calling convention: "+CodeGen.CODE._callingConv);
        };
    }
    @Override public RegMask callerSave() { return x86CallerSave(); }

    static final RegMask SYSTEM5_CALLEE_SAVE_MASK;
    static final RegMask   WIN64_CALLEE_SAVE_MASK;
    static {
        long callee = ~SYSTEM5_CALLER_SAVE;
        // Remove the spills
        callee &= (1L<<MAX_REG)-1;
        callee &= ~(1L<<FLAGS);
        callee &= ~(1L<<RSP);
        SYSTEM5_CALLEE_SAVE_MASK = new RegMask(callee);

        callee = ~WIN64_CALLER_SAVE;
        // Remove the spills
        callee &= (1L<<MAX_REG)-1;
        callee &= ~(1L<<FLAGS);
        callee &= ~(1L<<RSP);
        WIN64_CALLEE_SAVE_MASK = new RegMask(callee);
    }
    static RegMask x86CalleeSave() {
        return switch( CodeGen.CODE._callingConv ) {
        case "SystemV" -> SYSTEM5_CALLEE_SAVE_MASK;
        case "Win64"   ->   WIN64_CALLEE_SAVE_MASK;
        default        -> throw new IllegalArgumentException("Unknown calling convention: "+CodeGen.CODE._callingConv);
        };
    }
    @Override public RegMask calleeSave() { return x86CalleeSave(); }


    static final RegMask[] WIN64_RET_MASKS, SYS5_RET_MASKS;
    static {
        WIN64_RET_MASKS = makeRetMasks(  WIN64_CALLEE_SAVE_MASK);
         SYS5_RET_MASKS = makeRetMasks(SYSTEM5_CALLEE_SAVE_MASK);
    }
    private static RegMask[] makeRetMasks(RegMask mask) {
        int nSaves = mask.size();
        RegMask[] masks = new RegMask[4 + nSaves];
        masks[0] = null;
        masks[1] = null;     // Memory
        masks[2] = null;     // Varies, either XMM0 or RAX
        masks[3] = RPC_MASK;
        short reg = mask.firstReg();
        for( int i=0; i<nSaves; i++ ) {
            masks[i+4] = new RegMask(reg);
            reg = mask.nextReg(reg);
        }
        return masks;
    }

    // Return single int/ptr register.  Used by CallEnd output and Return input.
    static RegMask retMask( TypeFunPtr tfp, int i ) {
        if( i==2 )
            return tfp.ret() instanceof TypeFloat ? XMM0_MASK : RAX_MASK;
        RegMask[] masks = switch( CodeGen.CODE._callingConv ) {
        case "SystemV" ->  SYS5_RET_MASKS;
        case "Win64"   -> WIN64_RET_MASKS;
        default        -> throw new IllegalArgumentException("Unknown calling convention: "+CodeGen.CODE._callingConv);
        };
        return masks[i];
    }

    // Create a split op; any register to any register, including stack slots
    @Override public SplitNode split(String kind, byte round, LRG lrg) {  return new SplitX86(kind,round);  }

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
        int scale = 1;
        if( base instanceof ShlNode && !(idx instanceof ShlNode) ) throw Utils.TODO(); // Bug in canonicalization, should on RHS
        if( idx instanceof ShlNode shift && shift.in(2) instanceof ConstantNode shfcon &&
            shfcon._con instanceof TypeInteger tscale && 0 <= tscale.value() && tscale.value() <= 3 ) {
            idx = shift.in(1);
            scale = 1 << ((int)tscale.value());
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
        if( bool.isFloat() )
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
        throw Utils.TODO();
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
