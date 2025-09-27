package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.Machine;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;

public class riscv extends Machine {
    @Override public String name() {return "riscv";}

    // Using ABI names instead of register names
    static final int             RPC=  1,  SP =  2,  GP =  3,  TP =  4,  T0 =  5,  T1 =  6,  T2 =  7;
    static final int S0   =  8,  S1 =  9,  A0 = 10,  A1 = 11,  A2 = 12,  A3 = 13,  A4 = 14,  A5 = 15;
    static final int A6   = 16,  A7 = 17,  S2 = 18,  S3 = 19,  S4 = 20,  S5 = 21,  S6 = 22,  S7 = 23;
    static final int S8   = 24,  S9 = 25,  S10 = 26, S11 = 27, T3 = 28,  T4 = 29,  T5 = 30,  T6 = 31;

    // FP registers
    static final int F0   = 32,  F1  = 33,  F2  = 34,  F3  = 35,  F4  = 36,  F5  = 37,  F6  = 38,  F7   = 39;
    static final int FS0  = 40,  FS1 = 41,  FA0 = 42,  FA1 = 43,  FA2 = 44,  FA3 = 45,  FA4 = 46,  FA5  = 47;
    static final int FA6  = 48,  FA7 = 49,  FS2 = 50,  FS3 = 51,  FS4 = 52,  FS5 = 53,  FS6 = 54,  FS7  = 55;
    static final int FS8  = 56,  FS9 = 57,  FS10 = 58, FS11 = 59, FT8 = 60,  FT9 = 61,  FT10 = 62, FT11 = 63;

    static final int MAX_REG = 61;

    static final String[] REGS = new String[] {
        "zero", "r1"  , "rsp" , "r3"  , "r4"  , "r5"  , "r6"  , "r7"  ,
        "r8"  , "r9"  , "r10" , "r11" , "r12" , "r13" , "r14" , "r15" ,
        "r16" , "r17" , "r18" , "r19" , "r20" , "r21" , "r22" , "r23" ,
        "r24" , "r25" , "r26" , "r27" , "r28" , "r29" , "r30" , "r31" ,
        "f0"  , "f1"  , "f2"  , "f3"  , "f4"  , "f5"  , "f6"  , "f7"  ,
        "fs0" , "fs1" , "fa0" , "fa1" , "fa2" , "fa3" , "fa4" , "fa5" ,
        "fa6" , "fa7" , "fs2" , "fs3" , "fs4" , "fs5" , "fs6" , "fs7" ,
        "fs8" , "fs9" , "fs10", "fs11", "ft8" , "ft9" , "ft10", "ft11"
    };
    @Override public String reg( int reg ) {
        return reg < REGS.length ? REGS[reg] : "[rsp+"+(reg-REGS.length)*4+"]";
    }


    // General purpose register mask: pointers and ints, not floats
    static final long RD_BITS = 0b11111111111111111111111111111111L; // All the GPRs
    static final RegMask RMASK = new RegMask(RD_BITS);

    static final long WR_BITS = 0b11111111111111111111111111111010L; // All the GPRs, minus ZERO and SP
    static final RegMask WMASK = new RegMask(WR_BITS);
    // Float mask from(f0-ft10).  TODO: ft10,ft11 needs a larger RegMask
    static final long FP_BITS = 0b11111111111111111111111111111111L<<F0;
    static final RegMask FMASK = new RegMask(FP_BITS);

    // Load/store mask; both GPR and FPR
    static final RegMask MEM_MASK = new RegMask(WR_BITS | FP_BITS);

    static final RegMask SPLIT_MASK = new RegMask( WR_BITS | FP_BITS, -1L );


    // Arguments masks
    static final RegMask A0_MASK = new RegMask(A0);
    static final RegMask A1_MASK = new RegMask(A1);
    static final RegMask A2_MASK = new RegMask(A2);
    static final RegMask A3_MASK = new RegMask(A3);
    static final RegMask A4_MASK = new RegMask(A4);
    static final RegMask A5_MASK = new RegMask(A5);
    static final RegMask A6_MASK = new RegMask(A6);
    static final RegMask A7_MASK = new RegMask(A7);

    static final RegMask RPC_MASK = new RegMask(RPC);

    // Float arguments masks
    static final RegMask FA0_MASK = new RegMask(FA0);
    static final RegMask FA1_MASK = new RegMask(FA1);
    static final RegMask FA2_MASK = new RegMask(FA2);
    static final RegMask FA3_MASK = new RegMask(FA3);
    static final RegMask FA4_MASK = new RegMask(FA4);
    static final RegMask FA5_MASK = new RegMask(FA5);
    static final RegMask FA6_MASK = new RegMask(FA6);
    static final RegMask FA7_MASK = new RegMask(FA7);

    // Int arguments calling conv
    static RegMask[] CALLINMASK = new RegMask[] {
        A0_MASK,
        A1_MASK,
        A2_MASK,
        A3_MASK,
        A4_MASK,
        A5_MASK,
        A6_MASK,
        A7_MASK
    };

    static RegMask[] XMMS = new RegMask[] {
            FA0_MASK,
            FA1_MASK,
            FA2_MASK,
            FA3_MASK,
            FA4_MASK,
            FA5_MASK,
            FA6_MASK,
            FA7_MASK
    };

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
            if( fcnt < XMMS.length )
                return XMMS[fcnt];
        } else {
            RegMask[] cargs = CALLINMASK;
            if( idx-2-fcnt < cargs.length )
                return cargs[idx-2-fcnt];
        }
        throw Utils.TODO(); // Pass on stack slot
    }

    // callee saved(riscv)
    static final long CALLEE_SAVE =
        (1L<< S0) | (1L<< S1) | (1L<< S2 ) | (1L<< S3 ) |
        (1L<< S4) | (1L<< S5) | (1L<< S6 ) | (1L<< S7 ) |
        (1L<< S8) | (1L<< S9) | (1L<< S10) | (1L<< S11) |
        (1L<<FS0) | (1L<<FS1) | (1L<<FS2 ) | (1L<<FS3 ) |
        (1L<<FS4) | (1L<<FS5) | (1L<<FS6 ) | (1L<<FS7 ) |
        (1L<<FS8) | (1L<<FS9) | (1L<<FS10) | (1L<<FS11);

    static final RegMask CALLER_SAVE_MASK;
    static {
        long caller = ~CALLEE_SAVE;
        caller &= ~(1L<<SP);
        CALLER_SAVE_MASK = new RegMask(caller,0);
    }
    static RegMask riscCallerSave() { return CALLER_SAVE_MASK; }
    @Override public RegMask callerSave() { return riscCallerSave(); }

    static final RegMask CALLEE_SAVE_MASK = new RegMask(CALLEE_SAVE);
    static RegMask riscCalleeSave() { return CALLEE_SAVE_MASK; }
    @Override public RegMask calleeSave() { return riscCalleeSave(); }

    // Return single int/ptr register.  Used by CallEnd output and Return input.
    static final RegMask[] RET_MASKS;
    static {
        int nSaves = CALLEE_SAVE_MASK.size();
        RET_MASKS = new RegMask[4 + nSaves];
        RET_MASKS[0] = null;
        RET_MASKS[1] = null;     // Memory
        RET_MASKS[2] = null;     // Varies, either XMM0 or RAX
        RET_MASKS[3] = RMASK;    // Expected R1 but could be any register really
        short reg = CALLEE_SAVE_MASK.firstReg();
        for( int i=0; i<nSaves; i++ ) {
            RET_MASKS[i+4] = new RegMask(reg);
            reg = CALLEE_SAVE_MASK.nextReg(reg);
        }
    }
    static RegMask retMask( TypeFunPtr tfp, int i ) {
        return i==2
            ? (tfp.ret() instanceof TypeFloat ? FA0_MASK : A0_MASK)
            : RET_MASKS[i];
    }


    // Return a MachNode unconditional branch
    @Override public CFGNode jump() {
        throw Utils.TODO();
    }

    // Create a split op; any register to any register, including stack slots
    @Override  public SplitNode split(String kind, byte round, LRG lrg) { return new SplitRISC(kind,round);  }

    // Break an infinite loop
    @Override public IfNode never( CFGNode ctrl ) {
        throw Utils.TODO();
    }

    // True if signed 12-bit immediate
    private static boolean imm12(TypeInteger ti) {
        // 64-12==52
        return ti.isConstant() && ((ti.value()<<52)>>52) == ti.value();
    }


    @Override public Node instSelect( Node n ) {
        return switch (n) {
        case AddFNode addf -> addf(addf);
        case AddNode add -> add(add);
        case AndNode and -> and(and);
        case BoolNode bool -> cmp(bool);
        case CallNode call -> call(call);
        case CastNode cast  -> new CastRISC(cast);
        case CallEndNode cend -> new CallEndRISC(cend);
        case CProjNode c -> new CProjNode(c);
        case ConstantNode con -> con(con);
        case DivFNode divf -> new DivFRISC(divf);
        case DivNode div -> new DivRISC(div);
        case FunNode fun -> new FunRISC(fun);
        case IfNode iff -> jmp(iff);
        case LoadNode ld -> ld(ld);
        case MemMergeNode mem -> new MemMergeNode(mem);
        case MulFNode mulf -> new MulFRISC(mulf);
        case MulNode mul -> new MulRISC(mul);
        case NewNode nnn -> new NewRISC(nnn);
        case OrNode or -> or(or);
        case ParmNode parm -> new ParmRISC(parm);
        case PhiNode phi -> new PhiNode(phi);
        case ProjNode prj -> prj(prj);
        case ReadOnlyNode read -> new ReadOnlyNode(read);
        case ReturnNode ret -> new RetRISC(ret, ret.fun());
        case SarNode sar -> sra(sar);
        case ShlNode shl -> sll(shl);
        case ShrNode shr -> srl(shr);
        case StartNode start -> new StartNode(start);
        case StopNode stop -> new StopNode(stop);
        case StoreNode st -> st(st);
        case SubFNode subf -> new SubFRISC(subf);
        case SubNode sub -> sub(sub);
        case ToFloatNode tfn -> i2f8(tfn);
        case XorNode xor -> xor(xor);

        case LoopNode loop -> new LoopNode(loop);
        case RegionNode region -> new RegionNode(region);
        default -> throw Utils.TODO();
        };
    }

    private Node addf(AddFNode addf) {
        return new AddFRISC(addf);
    }

    private Node add(AddNode add) {
        if( add.in(2) instanceof ConstantNode off && off._con instanceof TypeInteger ti && imm12(ti) )
            return new AddIRISC(add, (int)ti.value());
        return new AddRISC(add);
    }

    private Node and(AndNode and) {
        if( and.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti && imm12(ti) )
            return new AndIRISC(and, (int)ti.value());
        return new AndRISC(and);
    }

    private Node call(CallNode call) {
        if( call.fptr() instanceof ConstantNode con && con._con instanceof TypeFunPtr tfp )
            return new CallRISC(call, tfp);
        return new CallRRISC(call);
    }

    private Node cmp(BoolNode bool) {
        if( bool.in(2) instanceof ConstantNode off && off._con instanceof TypeInteger toff )
            return new SetIRISC(bool, (int)toff.value());
        // Float variant
        if( bool.isFloat() )
            return new SetFRISC(bool);
        return new SetRISC(bool);
    }

    private Node con( ConstantNode con ) {
        if( !con._con.isConstant() ) return new ConstantNode( con ); // Default unknown caller inputs
        return switch( con._con ) {
        case TypeInteger ti  -> new IntRISC(con);
        case TypeFloat   tf  -> new FltRISC(con);
        case TypeFunPtr  tfp -> new TFPRISC(con);
        case TypeMemPtr  tmp -> throw Utils.TODO();
        case TypeNil     tn  -> throw Utils.TODO();
        // TOP, BOTTOM, XCtrl, Ctrl, etc.  Never any executable code.
        case Type t -> t==Type.NIL ? new IntRISC(con) : new ConstantNode(con);
        };
    }

    private Node jmp( IfNode iff ) {
        if( iff.in(1) instanceof BoolNode bool && !bool.isFloat() )
            return new BranchRISC(iff, bool.op(), bool.in(1), bool.in(2));
        // Vs zero
        return new BranchRISC(iff, "==", iff.in(1), null);
    }

    private Node or(OrNode or) {
        if( or.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti && imm12(ti) )
            return new OrIRISC(or, (int)ti.value());
        return new OrRISC(or);
    }

    private Node xor(XorNode xor) {
        if( xor.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti && imm12(ti) )
            return new XorIRISC(xor, (int)ti.value());
        return new XorRISC(xor);
    }

    private Node sra(SarNode sar) {
        if( sar.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti && imm12(ti) )
            return new SraIRISC(sar, (int)ti.value());
        return new SraRISC(sar);
    }

    private Node srl(ShrNode shr) {
        if( shr.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti && imm12(ti) )
            return new SrlIRISC(shr, (int)ti.value());
        return new SrlRISC(shr);
    }

    private Node sll(ShlNode sll) {
        if( sll.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti && imm12(ti) )
            return new SllIRISC(sll, (int)ti.value());
        return new SllRISC(sll);
    }

    private Node sub(SubNode sub) {
        return sub.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti && imm12(ti)
            ? new AddIRISC(sub, (int)(-ti.value()))
            : new SubRISC(sub);
    }

    private Node i2f8(ToFloatNode tfn) {
        assert tfn.in(1)._type instanceof TypeInteger ti;
        return new I2F8RISC(tfn);
    }

    private Node prj(ProjNode prj) {
        return new ProjRISC(prj);
    }

    private Node ld(LoadNode ld) {
        return new LoadRISC(address(ld),ld.ptr(),idx,off);
    }

    private Node st(StoreNode st) {
        int imm=0;
        Node xval = st.val();
        if( xval instanceof ConstantNode con && con._con instanceof TypeInteger ti && imm12(ti) ) {
            xval = null;
            imm = (int)ti.value();
            assert imm == ti.value(); // In 32-bit range
        }
        return new StoreRISC(address(st),st.ptr(),idx,off,imm,xval);
    }

    // Gather addressing mode bits prior to constructing.  This is a builder
    // pattern, but saving the bits in a *local* *global* here to keep mess
    // contained.
    private static int off;
    private static Node idx;
    private <N extends MemOpNode> N address( N mop ) {
        off = 0;  // Reset
        idx = null;
        Node base = mop.ptr();
        // Skip/throw-away a ReadOnly, only used to typecheck
        if( base instanceof ReadOnlyNode read ) base = read.in(1);
        assert !(base instanceof AddNode) && base._type instanceof TypeMemPtr; // Base ptr always, not some derived
        if( mop.off() instanceof ConstantNode con && con._con instanceof TypeInteger ti && imm12(ti) ) {
            off = (int)ti.value();
        } else {
            idx = mop.off();
        }
        return mop;
    }

}
