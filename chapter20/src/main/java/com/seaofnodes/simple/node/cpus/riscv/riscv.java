package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.codegen.Machine;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;

public class riscv extends Machine{
    @Override public String name() {return "riscv";}

    // Using ABI names instead of register names
    public static int             RA =  1,  SP =  2,  GP =  3,  TP =  4,  T0 =  5,  T1 =  6,  T2 =  7;
    public static int S0   =  8,  S1 =  9,  A0 = 10,  A1 = 11,  A2 = 12,  A3 = 13,  A4 = 14,  A5 = 15;
    public static int A6   = 16,  A7 = 17,  S2 = 18,  S3 = 19,  S4 = 20,  S5 = 21,  S6 = 22,  S7 = 23;
    public static int S8   = 24,  S9 = 25,  S10 = 26, S11 = 27, T3 = 28,  T4 = 29,  T5 = 30,  T6 = 31;

    // FP registers
    public static int F0   = 32,  F1  = 33,  F2  = 34,  F3  = 35,  F4  = 36,  F5  = 37,  F6  = 38,  F7   = 39;
    public static int FS0  = 40,  FS1 = 41,  FA0 = 42,  FA1 = 43,  FA2 = 44,  FA3 = 45,  FA4 = 46,  FA5  = 47;
    public static int FA6  = 48,  FA7 = 49,  FS2 = 50,  FS3 = 51,  FS4 = 52,  FS5 = 53,  FS6 = 54,  FS7  = 55;
    public static int FS8  = 56,  FS9 = 57,  FS10 = 58, FS11 = 59, FT8 = 60,  FT9 = 61,  FT10 = 62, FT11 = 63;

    public static int FLAGS = 0 ;

    // General purpose register mask: pointers and ints, not floats
    public static RegMask RMASK = new RegMask(0b11111111111111111111111111111110L);
    // Float mask from(ft0–ft11)
    public static RegMask FMASK = new RegMask(0b11111111111111111111111111111111L<<F0);

    // Load/store mask; both GPR and FPR
    public static RegMask MEM_MASK = new RegMask((1L<<64)-1);


    // Return single int/ptr register
    public static RegMask RET_MASK  = new RegMask(1L<< A0);
    public static RegMask RET_FMASK = new RegMask(1L<<FA0);

    // Arguments masks
    public static RegMask A0_MASK = new RegMask(1L<<A0);
    public static RegMask A1_MASK = new RegMask(1L<<A1);
    public static RegMask A2_MASK = new RegMask(1L<<A2);
    public static RegMask A3_MASK = new RegMask(1L<<A3);
    public static RegMask A4_MASK = new RegMask(1L<<A4);
    public static RegMask A5_MASK = new RegMask(1L<<A5);
    public static RegMask A6_MASK = new RegMask(1L<<A6);
    public static RegMask A7_MASK = new RegMask(1L<<A7);

    public static RegMask FLAGS_MASK = new RegMask(1L << FLAGS);

    // Float arguments masks
    public static RegMask FA0_MASK = new RegMask(1L<<FA0);
    public static RegMask FA1_MASK = new RegMask(1L<<FA1);
    public static RegMask FA2_MASK = new RegMask(1L<<FA2);
    public static RegMask FA3_MASK = new RegMask(1L<<FA3);
    public static RegMask FA4_MASK = new RegMask(1L<<FA4);
    public static RegMask FA5_MASK = new RegMask(1L<<FA5);
    public static RegMask FA6_MASK = new RegMask(1L<<FA6);
    public static RegMask FA7_MASK = new RegMask(1L<<FA7);

    // Int arguments calling conv
    static RegMask[] CALLINMASK_RISCV_INT = new RegMask[] {
        null,
        null,
        A0_MASK,
        A1_MASK,
        A2_MASK,
        A3_MASK,
        A4_MASK,
        A5_MASK,
        A6_MASK,
        A7_MASK
    };

    static int[] CALLINARG_RISCV_INT = new int[] {
            0, // Control, no register
            0, // Memory, no register
            A0,
            A1,
            A2,
            A3,
            A4,
            A5,
            A6,
            A7
    };

    static int callInArgInt(int idx) {
        return CALLINARG_RISCV_INT[idx];
    }

    static RegMask callInMaskInt(int idx) {
        return CALLINMASK_RISCV_INT[idx];
    }

    // Float arguments
    static RegMask[] CALLINMASK_RISCV_FLOAT = new RegMask[] {
            null,
            null,
            FA0_MASK,
            FA1_MASK,
            FA2_MASK,
            FA3_MASK,
            FA4_MASK,
            FA5_MASK,
            FA6_MASK,
            FA7_MASK
    };


    static int[] CALLINARG_RISCV_FLOAT = new int[] {
            0, // Control, no register
            0, // Memory, no register
            FA0,
            FA1,
            FA2,
            FA3,
            FA4,
            FA5,
            FA6,
            FA7
    };


    static int callInArgFloat(int idx) {
        return CALLINARG_RISCV_FLOAT[idx];
    }

    static RegMask callInMaskFloat(int idx) {
        return CALLINMASK_RISCV_FLOAT[idx];
    }
    // caller saved(riscv)
    //public static final long RISCV_CALLER_SAVED= TBD
    // callee saved(riscv)
    public static final long RISCV_CALLEE_SAVED =
            (1L << FS0) | (1L << FS1) | (1L << FS2) | (1L << FS3) | (1L << FS4)
            | (1L << FS5) | (1L << FS6) | (1L << FS7) | (1L << FS8) | (1L << FS9) | (1L << FS10);


    // Calling conv metadata
    public int GPR_COUNT_CONV_RISCV = 7;  // A0, A1, A2, A3, A4, A5, A6, A7
    public int FLOAT_COUNT_CONV_RISCV = 7; // FA0, FA1, FA2, FA3, FA4, FA5, FA6, FA7

    public static final String[] REGS = new String[] {
            "flags","ra"  , "sp"  , "gp"  , "tp"  , "t0"  , "t1"  , "t2"  ,
            "s0"  , "s1"  , "a0"  , "a1"  , "a2"  , "a3"  , "a4"  , "a5"  ,
            "a6"  , "a7"  , "s2"  , "s3"  , "s4"  , "s5"  , "s6"  , "s7"  ,
            "s8"  , "s9"  , "s10" , "s11" , "t3"  , "t4"  , "t5"  , "t6"  ,
            "f0"  , "f1"  , "f2"  , "f3"  , "f4"  , "f5"  , "f6"  , "f7"  ,
            "fs0" , "fs1" , "fa0" , "fa1" , "fa2" , "fa3" , "fa4" , "fa5" ,
            "fa6" , "fa7" , "fs2" , "fs3" , "fs4" , "fs5" , "fs6" , "fs7" ,
            "fs8" , "fs9" , "fs10", "fs11", "ft8" , "ft9" , "ft10", "ft11",
    };

    // General purpose register mask:
    @Override public String reg( int reg ) { return REGS[reg]; }
    // Return a MachNode unconditional branch
    @Override public CFGNode jump() {
        throw Utils.TODO();
    }

    // Create a split op; any register to any register, including stack slots
    @Override  public Node split() { return new SplitRISC();  }

    // Break an infinite loop
    @Override public IfNode never( CFGNode ctrl ) {
        throw Utils.TODO();
    }

    @Override public Node instSelect( Node n ) {
        return switch (n) {
        case AddFNode addf -> addf(addf);
        case AddNode add -> add(add);
        case AndNode and -> and(and);
        case BoolNode bool -> cmp(bool);
        case CallEndNode cend -> new CallEndNode((CallNode) cend.in(0));
        case CallNode call -> call(call);
        case CProjNode c -> new CProjNode(c);
        case ConstantNode con -> con(con);
        case DivFNode divf -> new DivFRISC(divf);
        case DivNode div -> new DivRISC(div);
        case FunNode fun -> new FunRISC(fun);
        case IfNode iff -> jmp(iff);
        case LoadNode ld -> ld(ld);
        case MemMergeNode mem -> new MemMergeNode(mem);
        case MulFNode mulf -> new MulFRISC(mulf);
        case MulNode mul -> mul(mul);
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
        Node rhs = add.in(2);
        if( rhs instanceof ConstantNode off && off._con instanceof TypeInteger toff ) {
            return new AddIRISC(add, toff);
        }
        return new AddRISC(add);
    }

    private Node and(AndNode and) {
        if( and.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti )
            return new AndIRISC(and, ti);
        return new AndRISC(and);
    }

    private Node call(CallNode call) {
        if( call.fptr() instanceof ConstantNode con && con._con instanceof TypeFunPtr tfp )
            return new CallRISC(call, tfp);
        return new CallRRISC(call);
    }

    private Node cmp(BoolNode bool) {
        Node cmp = _cmp(bool);
        return new SetRISC(cmp, bool.op());
    }

    private Node _cmp(BoolNode bool) {
        // Float variant
        if( bool instanceof BoolNode.EQF ||
            bool instanceof BoolNode.LTF ||
            bool instanceof BoolNode.LEF )
            return new CmpFRISC(bool);

        Node rhs = bool.in(2);
        return bool.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti
                ? new CmpIRISC(bool, ti)
                : new CmpRISC(bool);
    }

    private Node con( ConstantNode con ) {
        if( !con._con.isConstant() ) return new ConstantNode( con ); // Default unknown caller inputs
        return switch( con._con ) {
        case TypeInteger ti  -> new IntRISC(con);
        case TypeFloat   tf  -> new IntRISC(con);
        case TypeFunPtr  tfp -> new TFPRISC(con);
        case TypeMemPtr  tmp -> new ConstantNode(con);
        case TypeNil     tn  -> throw Utils.TODO();
        // TOP, BOTTOM, XCtrl, Ctrl, etc.  Never any executable code.
        case Type t -> new ConstantNode(con);
        };
    }

    private Node jmp( IfNode iff ) {
        // If/Bool combos will match to a Cmp/Set which sets flags.
        // Most general arith ops will also set flags, which the Jmp needs directly.
        // Loads do not set the flags, and will need an explicit TEST
        if( !(iff.in(1) instanceof BoolNode) )
            iff.setDef(1,new BoolNode.EQ(iff.in(1),new ConstantNode(TypeInteger.ZERO)));
        return new CBranchRISC(iff, ((BoolNode)iff.in(1)).op());
    }

    private Node or(OrNode or) {
        if(or.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti)
            return new OrIRISC(or, ti);

        return new OrRISC(or);
    }

    private Node xor(XorNode xor) {
        if(xor.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti)
            return new XorIRISC(xor, ti);
        return new XorRISC(xor);
    }

    private Node sra(SarNode sar) {
        if( sar.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti)
            return new SraIRISC(sar, ti);
        return new SraRISC(sar);
    }

    private Node srl(ShrNode shr) {
        if( shr.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti)
            return new SrlIRISC(shr, ti);
        return new SrlRISC(shr);
    }

    private Node sll(ShlNode sll) {
        if( sll.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti)
            return new SllIRISC(sll, ti);
        return new SllRISC(sll);
    }

    private Node sub(SubNode sub) {
        return sub.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti
                ? new AddIRISC(sub, TypeInteger.constant(-ti.value()))
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
        return new LoadRISC(ld);
    }

    private Node st(StoreNode st) {
        return new StoreRISC(st);
    }

    private Node mul(MulNode mul) {
        return mul.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti
                ? new MulIRISC(mul, ti)
                : new MulRISC(mul);
    }
}
