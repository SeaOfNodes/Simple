package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.Machine;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;

import java.io.ByteArrayOutputStream;

public class arm extends Machine {

    // X86-64 V2.  Includes e.g. SSE4.2 and POPCNT.
    @Override public String name() { return "arm"; }

    // GPR(S)
    static final int X0  =  0,  X1  =  1,  X2  =  2,  X3  =  3,  X4  =  4,  X5  =  5,  X6  =  6,  X7  =  7;
    static final int X8  =  8,  X9  =  9,  X10 = 10,  X11 = 11,  X12 = 12,  X13 = 13,  X14 = 14,  X15 = 15;
    static final int X16 = 16,  X17 = 17,  X18 = 18,  X19 = 19,  X20 = 20,  X21 = 21,  X22 = 22,  X23 = 23;
    static final int X24 = 24,  X25 = 25,  X26 = 26,  X27 = 27,  X28 = 28,  X29 = 29,  X30 = 30,  RSP = 31;

    // Floating point registers
    static final int D0  = 32,  D1  = 33,  D2  = 34,  D3  = 35,  D4  = 36,  D5  = 37,  D6  = 38,  D7  = 39;
    static final int D8  = 40,  D9  = 41,  D10 = 42,  D11 = 43,  D12 = 44,  D13 = 45,  D14 = 46,  D15 = 47;
    static final int D16 = 48,  D17 = 49,  D18 = 50,  D19 = 51,  D20 = 52,  D21 = 53,  D22 = 54,  D23 = 55;
    static final int D24 = 56,  D25 = 57,  D26 = 58,  D27 = 59,  D28 = 60,  D29 = 61,  D30 = 62,  D31 = 63;

    static final int FLAGS = 60;

    static final String[] REGS = new String[] {
        "X0",  "X1",  "X2",  "X3",  "X4",  "X5",  "X6",  "X7",
        "X8",  "X9",  "X10", "X11", "X12", "X13", "X14", "X15",
        "X16", "X17", "X18", "X19", "X20", "X21", "X22", "X23",
        "X24", "X25", "X26", "X27", "X28", "X29", "RPC", "RSP",
        "D0",  "D1",  "D2",  "D3",  "D4",  "D5",  "D6",  "D7",
        "D8",  "D9",  "D10", "D11", "D12", "D13", "D14", "D15",
        "D16", "D17", "D18", "D19", "D20", "D21", "D22", "D23",
        "D24", "D25", "D26", "D27", "D28", "D29", "D30", "D31",
        "flags"
    };
    @Override public String reg( int reg ) {
        return reg < REGS.length ? REGS[reg] : "[rsp+"+(reg-REGS.length)*4+"]";
    }

    // from (x0-x30)
    // General purpose register mask: pointers and ints, not floats
    static final long RD_BITS = 0xFFFFFFFFL;
    static final RegMask RMASK = new RegMask(RD_BITS);
    static final long WR_BITS = 0x7FFFFFFFL; // All the GPRs, not RSP
    static final RegMask WMASK = new RegMask(WR_BITS);

    // Float mask from(d0–d31)
    static final long FP_BITS = 0xFFFFFFFFL<<D0;
    static final RegMask DMASK = new RegMask(FP_BITS);

    // Load/store mask; both GPR and FPR
    static final RegMask MEM_MASK = new RegMask(WR_BITS | FP_BITS);

    static final RegMask SPLIT_MASK = new RegMask(WR_BITS | FP_BITS, -1L - (1L<<FLAGS));

    static final RegMask FLAGS_MASK = new RegMask(FLAGS);
    //  x30 (LR): Procedure link register, used to return from subroutines.
    static final RegMask RPC_MASK = new RegMask(1L << X30);

    // Arguments masks
    static final RegMask X0_MASK = new RegMask(X0);
    static final RegMask X1_MASK = new RegMask(X1);
    static final RegMask X2_MASK = new RegMask(X2);
    static final RegMask X3_MASK = new RegMask(X3);
    static final RegMask X4_MASK = new RegMask(X4);
    static final RegMask X5_MASK = new RegMask(X5);
    static final RegMask X6_MASK = new RegMask(X6);
    static final RegMask X7_MASK = new RegMask(X7);

    // Arguments(float) masks
    static final RegMask D0_MASK = new RegMask(D0);
    static final RegMask D1_MASK = new RegMask(D1);
    static final RegMask D2_MASK = new RegMask(D2);
    static final RegMask D3_MASK = new RegMask(D3);
    static final RegMask D4_MASK = new RegMask(D4);
    static final RegMask D5_MASK = new RegMask(D5);
    static final RegMask D6_MASK = new RegMask(D6);
    static final RegMask D7_MASK = new RegMask(D7);

    // Calling convention; returns a machine-specific register
    // for incoming argument idx.
    // index 0 for control, 1 for memory, real args start at index 2
    static final RegMask[] CALLINMASK = new RegMask[] {
        X0_MASK,
        X1_MASK,
        X2_MASK,
        X3_MASK,
        X4_MASK,
        X5_MASK,
        X6_MASK,
        X7_MASK,
    };
    static final RegMask[] XMMS = new RegMask[] {
        D0_MASK,
        D1_MASK,
        D2_MASK,
        D3_MASK,
        D4_MASK,
        D5_MASK,
        D6_MASK,
        D7_MASK,
    };

    // ARM ENCODING
    public enum OPTION {UXTB,
        UXTH,
        UXTX,
        SXTB,
        SXTH,
        SXTW,
        SXTX,
    }

    static public String invert(String op) {
        return switch (op) {
            case "==" -> "!=";
            case "!=" -> "==";
            case ">" -> "<=";
            case "<" -> ">=";
            case ">=" -> "<";
            case "<=" -> ">";
            default -> throw new IllegalStateException("Unexpected value: " + op);
        };
    }
    public enum COND {
        EQ,
        NE,
        CS,
        CC,
        MI,
        PL,
        VS,
        VC,
        HI,
        LS,
        GE,
        LT,
        GT,
        LE,
        AL,
        NV
    }

    // extra 1 is enoded in opt
    // sf is encoded in opcode
    public static int inst(int opcode, int opt,  int rm ,OPTION option, int immm3, int rn, int rd) {
        return (opcode << 24) | (opt << 21) | (rm << 16) | (option.ordinal() << 13) | (immm3 << 10) | (rn << 5) << rd;
    }

    // sh is encoded in opcdoe
    public static int imm_inst(int opcode, int imm12, int rn, int rd) {
        return (opcode << 22) | (imm12 << 10) | (rn << 5) | rd;
    }

    // logical immediates
    // sf is encoded in opcode as well as "opc" and "N"
    public static int logic_imm(int opcode, int immr, int imms, int rn, int rd) {
        return (opcode << 22) | (immr << 16) | (imms << 10) | (rn << 5) | rd;
    }

    // for normal add, reg1, reg2 cases (reg-to-reg)
    // using shifted-reg form
    public static int r_reg(int opcode, int shift, int rm, int imm6, int rn, int rd) {
        return (opcode << 24) | (shift << 21) | (rm << 16) | (imm6 << 10) << (rn << 5) | rd;
    }

    public static int shift_reg(int opcode, int rm, int op2, int rn, int rd) {
        return (opcode << 21) | (rm << 16) | (op2 << 10) | (rn << 5) | rd;
    }

    //  MUL can be considered an alias for MADD with the third operand Ra being set to 0
    public static int madd(int opcode, int rm, int ra, int rn, int rd) {
        return (opcode << 21) | (rm << 16) | (ra << 10) | (rn << 5) | rd;
    }

    // encodes movk
    public static int mov(int opcode, int shift, int imm16, int rd) {
            return (opcode << 23) | (shift << 21) | (imm16 << 5) | rd;
    }

    public static int ret(int opcode) {
        return (opcode << 10);
    }

    // FMOV (scalar, immediate)
    public static int f_mov(int opcode, int ftype,  int imm8, int rd) {
        return (opcode << 24) | (ftype << 22) |(imm8 << 13) | (128 << 5) | rd;
    }

    public static int f_scalar(int opcode, int ftype, int rm, int op, int rn, int rd) {
        return (opcode << 24) | (ftype << 22) | (1 << 21) | (rm << 16) | (op << 10) | (rn << 5) | rd;
    }

    public static int load_adr(int opcode, int offset, int base, int rt) {
        return (opcode << 21) | (offset << 12) | (3 << 10) | (base << 5) | rt;
    }

    public static int indr_adr(int opcode, int rm, int option, int s, int rn, int rt) {
        return (opcode << 21) | (rm << 16) | (option << 13) | (s << 12) | (2 << 10) | (rn << 5) | rt;
    }

    // encoding for vcvt, size is encoded in operand
    // <Qd>, <Qm>
    // F32.S32
    //encoded as op = 0b00, size = 0b10.
    // VCVT<c>.<Td>.<Tm> <Dd>, <Dm>
    // opcode is broken down into 4 pieces
    public static int f_convert(int opcode_1, int opcode_2, int opcode_3, int opcode_4,  int vd, int vm)  {
        return (opcode_1 << 28) | (opcode_2 << 24) | (opcode_3 << 20) | (opcode_4 << 16) |
                (vd << 12) | (0x01100010 << 4) | vm;
    }
    public static int float_cast(int opcode, int ftype, int rn, int rd) {
        return (opcode << 24) | (ftype << 22) | (2176 << 10) | (rn << 5) | rd;
    }

    // ftype = 3
    public static int f_cmp(int opcode, int ftype, int rm, int rn) {
        return (opcode  << 24) | (ftype << 21) | (rm << 16) | (8 << 10) | (rn << 5) | 8;
    }

    // Todo: maybe missing zero here after label << 5
    public static int b_cond(int opcode, int label, COND cond) {
        return (opcode << 24) | (label << 5) | cond.ordinal();
    }

    public static int cond_set(int opcode, int rm, COND cond, int rn, int rd) {
        return (opcode << 21) | (rm << 16) | (cond.ordinal() << 12) | (rn << 5) | rd;
    }

    public static int b(int opcode, int imm26) {
        return (opcode << 26) | imm26;
    }

    public static int load_str_imm(int opcode, int imm9, int rn, int rt) {
        return (opcode << 21) | (imm9 << 12) |(1 << 10) |(rn << 5) | rt;
    }

    public static void push_4_bytes(int value, ByteArrayOutputStream bytes) {
        bytes.write(value);
        bytes.write(value >> 8);
        bytes.write(value >> 16);
        bytes.write(value >> 24);
    }

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

    static final long CALLEE_SAVE =
        1L<<X19 |
        1L<<X20 | 1L<<X21 | 1L<<X22 | 1L<<X23 |
        1L<<X24 | 1L<<X25 | 1L<<X26 | 1L<<X27 |
        1L<<X28 |
        1L<<D9  | 1L<<D10 | 1L<<D11 |
        1L<<D12 | 1L<<D13 | 1L<<D14 | 1L<<D15;

    static final RegMask CALLER_SAVE_MASK;
    static {
        long caller = ~CALLEE_SAVE;
        caller &= ~(1L<<RSP);
        CALLER_SAVE_MASK = new RegMask(caller,1L<<FLAGS);
    }
    static RegMask armCallerSave() { return CALLER_SAVE_MASK; }
    @Override public RegMask callerSave() { return armCallerSave(); }

    static final RegMask CALLEE_SAVE_MASK = new RegMask(CALLEE_SAVE);
    static RegMask armCalleeSave() { return CALLEE_SAVE_MASK; }
    @Override public RegMask calleeSave() { return armCalleeSave(); }


    // Return single int/ptr register.  Used by CallEnd output and Return input.
    static final RegMask[] RET_MASKS;
    static {
        int nSaves = CALLEE_SAVE_MASK.size();
        RET_MASKS = new RegMask[4 + nSaves];
        RET_MASKS[0] = null;
        RET_MASKS[1] = null;     // Memory
        RET_MASKS[2] = null;     // Varies, either XMM0 or RAX
        RET_MASKS[3] = RMASK;    // Expected R30 but could be any register
        short reg = CALLEE_SAVE_MASK.firstReg();
        for( int i=0; i<nSaves; i++ ) {
            RET_MASKS[i+4] = new RegMask(reg);
            reg = CALLEE_SAVE_MASK.nextReg(reg);
        }
    }
    static RegMask retMask( TypeFunPtr tfp, int i ) {
        return i==2
            ? (tfp.ret() instanceof TypeFloat ? D0_MASK : X0_MASK)
            : RET_MASKS[i];
    }

    // Create a split op; any register to any register, including stack slots
    @Override public SplitNode split(String kind, byte round, LRG lrg) {  return new SplitARM(kind,round);  }

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
            case CallNode     call  -> call(call);
            case CastNode     cast  -> new CastNode(cast);
            case CallEndNode  cend  -> new CallEndARM(cend);
            case CProjNode    c     -> new CProjNode(c);
            case ConstantNode con   -> con(con);
            case DivFNode     divf  -> new DivFARM(divf);
            case DivNode      div   -> new DivARM(div);
            case FunNode      fun   -> new FunARM(fun);
            case IfNode       iff   -> jmp(iff);
            case LoadNode     ld    -> ld(ld);
            case MemMergeNode mem   -> new MemMergeNode(mem);
            case MulFNode     mulf  -> new MulFARM(mulf);
            case MulNode      mul   -> mul(mul);
            case NewNode      nnn   -> new NewARM(nnn);
            case NotNode      not   -> new NotARM(not);
            case OrNode       or    ->  or(or);
            case ParmNode     parm  -> new ParmARM(parm);
            case PhiNode      phi   -> new PhiNode(phi);
            case ProjNode     prj   -> prj(prj);
            case ReadOnlyNode read  -> new ReadOnlyNode(read);
            case ReturnNode   ret   -> new RetARM(ret,ret.fun());
            case SarNode      sar   -> sar(sar);
            case ShlNode      shl   -> shl(shl);
            case ShrNode      shr   -> shr(shr);
            case StartNode    start -> new StartNode(start);
            case StopNode     stop  -> new StopNode(stop);
            case StoreNode    st    -> st(st);
            case SubFNode     subf  -> new SubFARM(subf);
            case SubNode      sub   -> sub(sub);
            case ToFloatNode  tfn   -> i2f8(tfn);
            case XorNode      xor   -> xor(xor);

            case LoopNode     loop  -> new LoopNode(loop);
            case RegionNode   region-> new RegionNode(region);
            default -> throw Utils.TODO();
        };
    }

    private Node mul(MulNode mul) {
        return new MulARM(mul);
    }

    private Node cmp(BoolNode bool){
        Node cmp = _cmp(bool);
        return new SetARM(cmp, bool.op());
    }

    private Node i2f8(ToFloatNode tfn) {
        assert tfn.in(1)._type instanceof TypeInteger ti;
        return new I2F8ARM(tfn);
    }

    private Node ld(LoadNode ld) {
        return new LoadARM(address(ld), ld.ptr(), idx, off);
    }

    private Node jmp(IfNode iff) {
        // If/Bool combos will match to a Cmp/Set which sets flags.
        // Most general arith ops will also set flags, which the Jmp needs directly.
        // Loads do not set the flags, and will need an explicit TEST
        if( !(iff.in(1) instanceof BoolNode) )
            iff.setDef(1,new BoolNode.EQ(iff.in(1),new ConstantNode(TypeInteger.ZERO)));
        return new BranchARM(iff, invert(((BoolNode)iff.in(1)).op()));
    }

    private Node _cmp(BoolNode bool) {
        if( bool instanceof BoolNode.EQF ||
                bool instanceof BoolNode.LTF ||
                bool instanceof BoolNode.LEF )
            return new CmpFARM(bool);


        return bool.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti
                ? new CmpIARM(bool, ti)
                : new CmpARM(bool);
    }


    private Node addf(AddFNode addf) {
        return new AddFARM(addf);
    }

    private Node add(AddNode add) {
        Node rhs = add.in(2);
        if( rhs instanceof ConstantNode off && off._con instanceof TypeInteger toff ) {
            return new AddIARM(add, toff);
        }
        return new AddARM(add);
    }

    private Node sub(SubNode sub) {
        return sub.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti
                ? new AddIARM(sub, TypeInteger.constant(-ti.value()))
                : new SubARM(sub);
    }

    private Node and(AndNode and) {
//        if( and.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti )
//            return new AndIARM(and, ti);
        return new AndARM(and);
    }

    private Node or(OrNode or) {
//        if(or.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti)
//            return new OrIARM(or, ti);

        return new OrARM(or);
    }

    private Node xor(XorNode xor) {
//        if(xor.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti)
//            return new XorIARM(xor, ti);
        return new XorARM(xor);
    }

    private Node con(ConstantNode con) {
        if( !con._con.isConstant() ) return new ConstantNode( con ); // Default unknown caller inputs
        return switch( con._con ) {
            case TypeInteger ti  -> new IntARM(con);
            case TypeFloat   tf  -> new FloatARM(con);
            case TypeFunPtr  tfp -> new TFPARM(con);
            case TypeMemPtr  tmp -> new ConstantNode(con);
            case TypeNil     tn  -> throw Utils.TODO();
            // TOP, BOTTOM, XCtrl, Ctrl, etc.  Never any executable code.
            case Type t -> new ConstantNode(con);
        };
    }

    private Node sar(SarNode sar){
//        if( sar.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti)
//            return new AsrIARM(sar, ti);
        return new AsrARM(sar);
    }

    private Node shl(ShlNode shl) {
//        if( shl.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti)
//            return new LslIARM(shl, ti);
        return new LslARM(shl);

    }

    private Node shr(ShrNode shr) {
//        if( shr.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti)
//            return new LsrIARM(shr, ti);
        return new LsrARM(shr);
    }

    private Node call(CallNode call){
        if(call.fptr() instanceof ConstantNode con && con._con instanceof TypeFunPtr tfp)
            return new CallARM(call, tfp);
        return new CallRRARM(call);
    }

    private Node prj(ProjNode prj) {
        return new ProjARM(prj);
    }


    private static int off;
    private static Node idx;
    private Node st(StoreNode st) {
        Node xval = st.val();
        if( xval instanceof ConstantNode con && con._con == TypeInteger.ZERO )
            xval = null;
        return new StoreARM(address(st),st.ptr(),idx,off,xval);
    }

    // Gather addressing mode bits prior to constructing.  This is a builder
    // pattern, but saving the bits in a *local* *global* here to keep mess
    // contained.
    private <N extends MemOpNode> N address( N mop ) {
        off = 0;  // Reset
        idx = null;
        Node base = mop.ptr();
        // Skip/throw-away a ReadOnly, only used to typecheck
        if( base instanceof ReadOnlyNode read ) base = read.in(1);
        assert !(base instanceof AddNode) && base._type instanceof TypeMemPtr; // Base ptr always, not some derived
        if( mop.off() instanceof ConstantNode con && con._con instanceof TypeInteger ti ) {
            off = (int)ti.value();
            assert off == ti.value(); // In 32-bit range
        } else {
            idx = mop.off();
        }
        return mop;
    }


}
