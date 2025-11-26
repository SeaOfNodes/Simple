package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;

public class riscv extends Machine {

    public riscv(CodeGen code) {
        if( !"SystemV".equals(code._callingConv) )
            throw new IllegalArgumentException("Unknown calling convention "+code._callingConv);
    }

    @Override public String name() {return "riscv";}
    //@Override public int maxReg() { return MAX_REG; }

    // Using ABI names instead of register names
    public static int ZERO =  0,  RPC=  1,  RSP=  2,  S12=  3,  S13=  4,  T0 =  5,  T1 =  6,  T2 =  7;
    public static int S0   =  8,  S1 =  9,  A0 = 10,  A1 = 11,  A2 = 12,  A3 = 13,  A4 = 14,  A5 = 15;
    public static int A6   = 16,  A7 = 17,  S2 = 18,  S3 = 19,  S4 = 20,  S5 = 21,  S6 = 22,  S7 = 23;
    public static int S8   = 24,  S9 = 25,  S10= 26,  S11= 27,  T3 = 28,  T4 = 29,  T5 = 30,  T6 = 31;

    // FP registers
    public static int F0   = 32,  F1  = 33,  F2  = 34,  F3  = 35,  F4  = 36,  F5  = 37,  F6  = 38,  F7   = 39;
    public static int FS0  = 40,  FS1 = 41,  FA0 = 42,  FA1 = 43,  FA2 = 44,  FA3 = 45,  FA4 = 46,  FA5  = 47;
    public static int FA6  = 48,  FA7 = 49,  FS2 = 50,  FS3 = 51,  FS4 = 52,  FS5 = 53,  FS6 = 54,  FS7  = 55;
    public static int FS8  = 56,  FS9 = 57,  FS10 = 58, FS11 = 59, FT8 = 60,  FT9 = 61,  FT10 = 62, FT11 = 63;

    static final int MAX_REG = 64;

    public static final int F_OFFSET = 32;

    static final String[] REGS = new String[] {
        "zero","rpc"  , "rsp" , "s12" , "s13" , "t0"  , "t1"  , "t2"  ,
        "s0"  , "s1"  , "a0"  , "a1"  , "a2"  , "a3"  , "a4"  , "a5"  ,
        "a6"  , "a7"  , "s2"  , "s3"  , "s4"  , "s5"  , "s6"  , "s7"  ,
        "s8"  , "s9"  , "s10" , "s11" , "t3"  , "t4"  , "t5"  , "t6"  ,
        "f0"  , "f1"  , "f2"  , "f3"  , "f4"  , "f5"  , "f6"  , "f7"  ,
        "fs0" , "fs1" , "fa0" , "fa1" , "fa2" , "fa3" , "fa4" , "fa5" ,
        "fa6" , "fa7" , "fs2" , "fs3" , "fs4" , "fs5" , "fs6" , "fs7" ,
        "fs8" , "fs9" , "fs10", "fs11", "ft8" , "ft9" , "ft10", "ft11"
        // Register-based naming hella easier to debug than "official" register
        // names when single-stepping in debugger.
        //"zero", "r1"  , "rsp" , "r3" , "r4" , "r5" , "r6" , "r7" ,
        //"r8"  , "r9"  , "r10" , "r11", "r12", "r13", "r14", "r15",
        //"r16" , "r17" , "r18" , "r19", "r20", "r21", "r22", "r23",
        //"r24" , "r25" , "r26" , "r27", "r28", "r29", "r30", "r31",
        //"f0"  , "f1"  , "f2"  , "f3"  , "f4"  , "f5"  , "f6"  , "f7"  ,
        //"fs0" , "fs1" , "fa0" , "fa1" , "fa2" , "fa3" , "fa4" , "fa5" ,
        //"fa6" , "fa7" , "fs2" , "fs3" , "fs4" , "fs5" , "fs6" , "fs7" ,
        //"fs8" , "fs9" , "fs10", "fs11", "ft8" , "ft9" , "ft10", "ft11"
    };
    @Override public String[] regs() { return REGS; }

    // General purpose register mask: pointers and ints, not floats
    static final long RD_BITS = 0b11111111111111111111111111111111L; // All the GPRs
    static final RegMask RMASK = new RegMask(RD_BITS);

    static final long WR_BITS = 0b11111111111111111111111111111010L; // All the GPRs, minus ZERO and RSP
    static final RegMask WMASK = new RegMask(WR_BITS);
    // Float mask from(f0-ft10).  TODO: ft10,ft11 needs a larger RegMask
    static final long FP_BITS = 0b11111111111111111111111111111111L<<F0;
    static final RegMask FMASK = new RegMask(FP_BITS);

    // Load/store mask; both GPR and FPR
    static final RegMask MEM_MASK = new RegMask(WR_BITS | FP_BITS);

    static final RegMask SPLIT_MASK = new RegMask( WR_BITS | FP_BITS, -1L );


    // Arguments masks
    public static RegMask A0_MASK = new RegMask(A0);
    public static RegMask A1_MASK = new RegMask(A1);
    public static RegMask A2_MASK = new RegMask(A2);
    public static RegMask A3_MASK = new RegMask(A3);
    public static RegMask A4_MASK = new RegMask(A4);
    public static RegMask A5_MASK = new RegMask(A5);
    public static RegMask A6_MASK = new RegMask(A6);
    public static RegMask A7_MASK = new RegMask(A7);

    public static final RegMask RPC_MASK = new RegMask(RPC);

    // Float arguments masks
    public static RegMask FA0_MASK = new RegMask(FA0);
    public static RegMask FA1_MASK = new RegMask(FA1);
    public static RegMask FA2_MASK = new RegMask(FA2);
    public static RegMask FA3_MASK = new RegMask(FA3);
    public static RegMask FA4_MASK = new RegMask(FA4);
    public static RegMask FA5_MASK = new RegMask(FA5);
    public static RegMask FA6_MASK = new RegMask(FA6);
    public static RegMask FA7_MASK = new RegMask(FA7);

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

    // major opcode: OP
    public static int OP_LOAD    = 0b00_000_11;
    public static int OP_LOADFP  = 0b00_001_11;
    public static int OP_CUSTOM0 = 0b00_010_11;
    public static int OP_IMM     = 0b00_100_11;
    public static int OP_AUIPC   = 0b00_101_11;

    public static int OP_STORE   = 0b01_000_11;
    public static int OP_STOREFP = 0b01_001_11;
    public static int OP_CUSTOM1 = 0b01_010_11;
    public static int OP         = 0b01_100_11;
    public static int OP_LUI     = 0b01_101_11;

    public static int OP_CUSTOM2 = 0b10_010_11;
    public static int OP_FP      = 0b10_100_11;

    public static int OP_BRANCH  = 0b11_000_11;
    public static int OP_JALR    = 0b11_001_11;
    public static int OP_RESERVED= 0b11_010_11;
    public static int OP_JAL     = 0b11_011_11;


    // Since riscv instructions are fixed we can just or them together
    public static int r_type(int opcode, int rd, int func3, int rs1, int rs2, int func7) {
        assert 0 <= rs1 && rs1 < 32;
        assert 0 <= rs2 && rs2 < 32;
        assert 0 <= rd &&  rd  < 32;
        return (func7 << 25) | (rs2 << 20) | (rs1 << 15) | (func3 << 12) | (rd << 7) | opcode;
    }
    public static void r_type(Encoding enc, Node n, int func3, int func7) {
        short dst  = enc.reg(n);
        short src1 = enc.reg(n.in(1));
        short src2 = enc.reg(n.in(2));
        int body = r_type(OP,dst,func3,src1,src2,func7);
        enc.add4(body);
    }

    public static void rf_type(Encoding enc, Node n, RM func3, int func7) {
        short dst  = (short)(enc.reg(n      )-F_OFFSET);
        short src1 = (short)(enc.reg(n.in(1))-F_OFFSET);
        short src2 = (short)(enc.reg(n.in(2))-F_OFFSET);
        int body = r_type(OP_FP,dst,func3.ordinal(),src1,src2,func7);
        enc.add4(body);
    }


    public static int u_type(int opcode, int rd, int imm20) {
        assert 0 <= rd && rd < 32;
        return (imm20 << 12) | (rd << 7) | opcode;
    }

    public static int j_type(int opcode, int rd, int delta) {
        assert -(1L<<20) <= delta && delta < (1L<<20);
        assert 0 <= rd && rd < 32;
        // Messy branch offset encoding
        // 31 30-21 20 19-12 11-7  6-0
        // 20 10- 1 11 19-12 rpc   JAL
        assert (delta&1)==0;    // Low bit is always zero, not encoded
        int imm10_01 = (delta>> 1) & 0x3FF;
        int imm11    = (delta>>11) &     1;
        int imm12_19 = (delta>>12) &  0xFF;
        int imm20    = (delta>>19) &     1;
        int bits = imm20<<19 | imm10_01 << 9 | imm11 << 8 | imm12_19;
        return bits << 12 | rd << 7 | opcode;
    }


    public static int i_type(int opcode, int rd, int func3, int rs1, int imm12) {
        assert 0 <= rd  &&  rd  < 32;
        assert 0 <= rs1 &&  rs1 < 32;
        assert opcode >= 0 && func3 >=0 && imm12 >= 0; // Zero-extend by caller
        return  (imm12 << 20) | (rs1 << 15) | (func3 << 12) | (rd << 7) | opcode;
    }


    // S-type instructions(store)
    public static int s_type(int opcode, int func3, int rs1, int rs2, int imm12) {
        assert 0 <= rs1 &&  rs1 < 32;
        assert 0 <= rs2 &&  rs2 < 32;
        assert 0 <= func3;

        assert imm12 >= 0;      // Masked to high zero bits by caller
        int imm_lo = imm12 & 0x1F;
        int imm_hi = imm12 >> 5;
        return (imm_hi << 25) | (rs2 << 20) | (rs1 << 15) | (func3 << 12) | (imm_lo << 7) | opcode;
    }

    // BRANCH
    public static int b_type(int opcode, int func3, short rs1, short rs2, int delta) {
        assert 0 <= rs1 && rs1 < 32;
        assert 0 <= rs2 && rs2 < 32;
        assert -4*1024 <= delta && delta < 4*1024;
        assert (delta&1)==0;    // Low bit is always zero, not encoded
        // Messy branch offset encoding
        // 31 30 29 28 27 26 25 24-20 19-15 14-12 11 10  9  8  7  6-0
        // 12 10  9  8  7  6  5 SRC2  SRC1  FUNC3  4  3  2  1 11  OP
        int imm4_1 = (delta>> 1) & 0xF;
        int imm10_5= (delta>> 5) &0x3F;
        int imm11  = (delta>>11) &   1;
        int imm12  = (delta>>12) &   1;
        int imm5 = imm4_1<<1 | imm11;
        int imm7 = imm12<<6 | imm10_5;
        return (imm7 << 25 ) | (rs2 << 20) | (rs1 << 15) | (func3 << 12) | (imm5 << 7) | opcode;
    }

    public enum RM {
        RNE,       // Round to Nearest, ties to Even
        RTZ,       // Round towards Zero
        RDN,       // Round Down
        RUP,       // Round Up
        DIRECT,    // Round to Nearest, ties to Max Magnitude
        RESERVED1, // Reserved for futue use
        RESERVED2, // Reserved for future use
        DYN,       // In instruction’s rm field, selects dynamic rounding mode; In Rounding Mode register, reserved
    }

    // Since opcode is the same just return back func3
    static public int jumpop(String op) {
        return switch(op) {
        case "=="  -> 0x0;
        case "!="  -> 0x1;
        case "<"   -> 0x4;
        case ">="  -> 0x5;
        case "u<"  -> 0x6;
        case "u<=" -> 0x7;
        default  ->  throw Utils.TODO();
        };
    }

    // Since opcode is the same just return back func3
    static public int fsetop(String op) {
        return switch(op) {
        case "<"  -> 1;
        case "<=" -> 0;
        case "==" -> 2;
        default   -> throw Utils.TODO();
        };
    }

    @Override public RegMask callArgMask( TypeFunPtr tfp, int idx, int maxArgSlot ) { return callInMask(tfp,idx,maxArgSlot); }
    static RegMask callInMask( TypeFunPtr tfp, int idx, int maxArgSlot ) {
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
        // Pass on stack slot(8 and higher)
        if( maxArgSlot>0 ) throw Utils.TODO();
        return new RegMask(MAX_REG + 1 + (idx - 2));
    }

    @Override public short maxArgSlot( TypeFunPtr tfp ) {
        int icnt=0, fcnt=0;     // Count of ints, floats
        for( int i=0; i<tfp.nargs(); i++ ) {
            if( tfp.arg(i) instanceof TypeFloat ) fcnt++;
            else icnt++;
        }
        int nstk = Math.max(icnt-8,0)+Math.max(fcnt-8,0);
        return (short)nstk;
    }

    static final long CALLER_SAVE =
        (1L<<RPC) |
        (1L<<A0 ) | (1L<<A1 ) | (1L<<A2 ) | (1L<<A3 ) | (1L<<A4 ) | (1L<<A5 ) | (1L<<A6 ) | (1L<<A7 ) |
        (1L<<T0 ) | (1L<<T1 ) | (1L<<T2 ) | (1L<<T3 ) | (1L<<T4 ) | (1L<<T5 ) | (1L<<T6 ) |
        (1L<<FA0) | (1L<<FA1) | (1L<<FA2) | (1L<<FA3) | (1L<<FA4) | (1L<<FA5) | (1L<<FA6) | (1L<<FA7) |
        (1L<<F0 ) | (1L<<F1 ) | (1L<<F2 ) | (1L<<F3 ) | (1L<<F4 ) | (1L<<F5 ) | (1L<<F6 ) | (1L<<F7 ) |
        (1L<<FT8) | (1L<<FT9) | (1L<<FT10)| (1L<<FT11);

    @Override public long callerSave() { return CALLER_SAVE; }
    @Override public long  neverSave() { return (1L<<RSP) | (1L<<ZERO); }
    @Override public RegMask retMask( TypeFunPtr tfp ) {
        return tfp.ret() instanceof TypeFloat ? FA0_MASK : A0_MASK;
    }
    @Override public int rpc() { return RPC; }


    // Return a MachNode unconditional branch
    @Override public CFGNode jump() { return new UJmpRISC(); }

    // Create a split op; any register to any register, including stack slots
    @Override  public SplitNode split(String kind, byte round, LRG lrg) { return new SplitRISC(kind,round);  }

    // True if signed 12-bit immediate
    public static boolean imm12(TypeInteger ti) {
        // 52 = 64-12
        return ti.isConstant() && ((ti.value()<<52)>>52) == ti.value();
    }
    // True if HIGH 20-bit signed immediate, with all zeros low.
    public static boolean imm20Exact(TypeInteger ti) {
        // shift left 32 to clear out the upper 32 bits.
        // shift right SIGNED to sign-extend upper 32 bits; then shift 12 more to clear out lower 12 bits.
        // shift left 12 to re-center the bits.
        return ti.isConstant() && (((ti.value()<<32)>>>44)<<12) == ti.value();
    }

    @Override public Node instSelect( Node n ) {
        return switch (n) {
        case AddFNode    addf -> addf(addf);
        case AddNode      add -> add(add);
        case AndNode      and -> and(and);
        case BoolNode    bool -> cmp(bool);
        case CallNode    call -> call(call);
        case CastNode   cast  -> new CastRISC(cast);
        case CallEndNode cend -> new CallEndRISC(cend);
        case CProjNode      c -> new CProjNode(c);
        case ConstantNode con -> con(con);
        case DivFNode    divf -> new DivFRISC(divf);
        case DivNode      div -> new DivRISC(div);
        case FunNode      fun -> new FunRISC(fun);
        case IfNode       iff -> jmp(iff);
        case LoadNode      ld -> ld(ld);
        case MemMergeNode mem -> new MemMergeNode(mem);
        case MinusNode    neg -> new NegRISC(neg);
        case MulFNode    mulf -> new MulFRISC(mulf);
        case MulNode      mul -> new MulRISC(mul);
        case NewNode      nnn -> nnn(nnn);
        case NotNode      not -> new NotRISC(not);
        case OrNode        or -> or(or);
        case ParmNode    parm -> new ParmRISC(parm);
        case PhiNode      phi -> new PhiNode(phi);
        case ProjNode     prj -> prj(prj);
        case ReadOnlyNode read-> new ReadOnlyNode(read);
        case ReturnNode   ret -> new RetRISC(ret, ret.fun());
        case SarNode      sar -> sra(sar);
        case ShlNode      shl -> sll(shl);
        case ShrNode      shr -> srl(shr);
        case StartNode  start -> new StartNode(start);
        case StopNode    stop -> new StopNode(stop);
        case StoreNode     st -> st(st);
        case SubFNode    subf -> new SubFRISC(subf);
        case SubNode      sub -> sub(sub);
        case ToFloatNode  tfn -> i2f8(tfn);
        case XorNode      xor -> xor(xor);

        case LoopNode loop -> new LoopNode(loop);
        case RegionNode region -> new RegionNode(region);
        default -> throw Utils.TODO();
        };
    }

    private Node addf(AddFNode addf) {
        return new AddFRISC(addf);
    }

    private Node add(AddNode add) {
        if( add.in(2) instanceof ConstantNode off2 && off2._con instanceof TypeInteger ti && imm12(ti) )
            return new AddIRISC(add, (int)ti.value(),true);
        return new AddRISC(add);
    }

    private Node and(AndNode and) {
        if( and.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti ) {
            if( imm12(ti) )
                return new AndIRISC(and, (int)ti.value());
            // Could be any size low bit mask
            if( ti.value() == 0xFFFFFFFFL )
                return new SrlIRISC(new SllIRISC(and,32),32,false);
        }
        return new AndRISC(and);
    }

    private Node call(CallNode call) {
        return call.fptr() instanceof ConstantNode con && con._con instanceof TypeFunPtr tfp
            ? new CallRISC(call, tfp)
            : new CallRRISC(call);
    }
    private Node nnn(NewNode nnn) {
        // TODO: pass in the TFP for alloc
        return new NewRISC(nnn);
    }

    private Node cmp(BoolNode bool) {
        // Float variant directly implemented in hardware
        if( bool.isFloat() )
            return new SetFRISC(bool);

        // Only < and <u are implemented in hardware.
        // x <  y - as-is
        // x <= y - flip and negate; !(y < x); `slt tmp=y,x; xori dst=tmp,#1`
        // x == y - sub and vs0 == `sub tmp=x-y; sltu dst=tmp,#1`

        // x >  y - swap; y < x
        // x >= y - swap and negate; !(x < y); `slt tmp=y,x;` then NOT.
        // x != y - sub and vs0 == `sub tmp=x-y; sltu dst=tmp,#1` then NOT.

        // The ">", ">=" and "!=" in Simple include a NotNode, which can be
        // implemented with a XOR.  If one of the above is followed by a NOT
        // we can remove the double XOR in the encodings.

        return switch( bool.op() ) {
        case "<" -> bool.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti && imm12(ti)
            ? new SetIRISC(bool, (int)ti.value(),false)
            : new  SetRISC(bool, false);
        // x <= y - flip and negate; !(y < x); `slt tmp=y,x; xori dst=tmp,#1`
        case "<=" -> new XorIRISC(new SetRISC(bool.swap12(), false),1);
        // x == y - sub and vs0 == `sub tmp=x-y; sltu dst=tmp,#1`
        case "==" -> new SetIRISC(new SubRISC(bool),1,true);
        default -> throw Utils.TODO();
        };
    }

    private Node con( ConstantNode con ) {
        if( !con._con.isConstant() ) return new ConstantNode( con ); // Default unknown caller inputs
        return switch( con._con ) {
        case TypeInteger ti -> {
            if( imm12(ti) ) yield new IntRISC(con);
            long x = ti.value();
            if( imm20Exact(ti) ) yield new LUI((int)x);
            if( (x<<32)>>32 == x ) { // Signed lower 32-bit immediate
                // Here, the low 12 bits get sign-extended, which means if
                // bit11 is set, the value is negative and lowers the LUI
                // value.  Add a bit 12 to compensate
                if( ((x>>11)&1)==1 ) x += 0x1000;
                yield new AddIRISC(new LUI((int)(x & ~0xFFF)), (int)(x & 0xFFF),false);
            }
            // Need more complex sequence for larger constants... or a load
            // from a constant pool, which does not need an extra register
            throw Utils.TODO();
        }
        // Load from constant pool
        case TypeFloat   tf  -> new FltRISC(con);
        case TypeFunPtr  tfp -> new TFPRISC(con);
        case TypeMemPtr  tmp -> throw Utils.TODO();
        case TypeNil     tn  -> throw Utils.TODO();
        // TOP, BOTTOM, XCtrl, Ctrl, etc.  Never any executable code.
        case Type t -> t==Type.NIL ? new IntRISC(con) : new ConstantNode(con);
        };
    }

    private Node jmp( IfNode iff ) {
        if( iff.in(1) instanceof BoolNode bool && !bool.isFloat() ) {
            // if less than or equal switch inputs
            String bop = bool.op();
            if( bop.equals("<=") || bop.equals(">") )
                return new BranchRISC(iff, IfNode.swap(bop), bool.in(2), bool.in(1));
            return new BranchRISC(iff, bop, bool.in(1), bool.in(2));
        }
        // Vs zero
        return new BranchRISC(iff, iff.in(1)==null ? "==" : "!=", iff.in(1), null);
    }

    private Node or(OrNode or) {
        if( or.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti && imm12(ti))
            return new OrIRISC(or, (int)ti.value());
        return new OrRISC(or);
    }

    private Node xor(XorNode xor) {
        if( xor.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti && imm12(ti))
            return new XorIRISC(xor, (int)ti.value());
        return new XorRISC(xor);
    }

    private Node sra(SarNode sar) {
        if( sar.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti && imm12(ti))
            return new SraIRISC(sar, (int)ti.value());
        return new SraRISC(sar);
    }

    private Node srl(ShrNode shr) {
        if( shr.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti && imm12(ti))
            return new SrlIRISC(shr, (int)ti.value(),true);
        return new SrlRISC(shr);
    }

    private Node sll(ShlNode sll) {
        if( sll.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti && imm12(ti))
            return new SllIRISC(sll, (int)ti.value());
        return new SllRISC(sll);
    }

    private Node sub(SubNode sub) {
        return sub.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti && imm12(ti)
            ? new AddIRISC(sub, (int)(-ti.value()),true)
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
        return new LoadRISC(ld,address(ld),off);
    }

    private Node st(StoreNode st) {
        Node xval = st.val() instanceof ConstantNode con && con._con == TypeInteger.ZERO ? null : st.val();
        return new StoreRISC(st,address(st), off, xval);
    }

    // Gather addressing mode bits prior to constructing.  This is a builder
    // pattern, but saving the bits in a *local* *global* here to keep mess
    // contained.
    private static int off;
    private Node address( MemOpNode mop ) {
        off = 0;  // Reset
        Node base = mop.ptr();
        // Skip/throw-away a ReadOnly, only used to typecheck
        if( base instanceof ReadOnlyNode read ) base = read.in(1);
        assert !(base instanceof AddNode) && base._type instanceof TypeMemPtr; // Base ptr always, not some derived
        if( mop.off() instanceof ConstantNode con && con._con instanceof TypeInteger ti && imm12(ti) ) {
            off = (int)ti.value();
        } else {
            base = new AddRISC(base,mop.off());
            base._type = mop.ptr()._type;
        }
        return base;
    }

}
