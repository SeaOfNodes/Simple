package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.isa.Arm64;
import static com.seaofnodes.isa.Arm64.*;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;

public class arm extends Machine {
    public arm( CodeGen code ) {
        if( !"SystemV".equals(code._callingConv) )
            throw new IllegalArgumentException("Unknown calling convention "+code._callingConv);
    }

    // ARM64
    @Override public String name() { return "arm"; }

    // GPR(S)
    public static final int X0  =  0,  X1  =  1,  X2  =  2,  X3  =  3,  X4  =  4,  X5  =  5,  X6  =  6,  X7  =  7;
    public static final int X8  =  8,  X9  =  9,  X10 = 10,  X11 = 11,  X12 = 12,  X13 = 13,  X14 = 14,  X15 = 15;
    public static final int X16 = 16,  X17 = 17,  X18 = 18,  X19 = 19,  X20 = 20,  X21 = 21,  X22 = 22,  X23 = 23;
    public static final int X24 = 24,  X25 = 25,  X26 = 26,  X27 = 27,  X28 = 28,  X29 = 29,  X30 = 30,  RSP = 31;

    // Floating point registers
    public static final int D0  = 32,  D1  = 33,  D2  = 34,  D3  = 35,  D4  = 36,  D5  = 37,  D6  = 38,  D7  = 39;
    public static final int D8  = 40,  D9  = 41,  D10 = 42,  D11 = 43,  D12 = 44,  D13 = 45,  D14 = 46,  D15 = 47;
    public static final int D16 = 48,  D17 = 49,  D18 = 50,  D19 = 51,  D20 = 52,  D21 = 53,  D22 = 54,  D23 = 55;
    public static final int D24 = 56,  D25 = 57,  D26 = 58,  D27 = 59,  D28 = 60,  D29 = 61,  D30 = 62,  D31 = 63;

    static final int FLAGS = 64;
    static final int MAX_REG = 65;
    public static final int D_OFFSET = 32;

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
    @Override public String[] regs() { return REGS; }

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

    static final RegMask SPLIT_MASK = new RegMask(WR_BITS | FP_BITS, -2L/*skip flags*/);

    static final RegMask FLAGS_MASK = new RegMask(FLAGS);

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

    // True if signed 9-bit immediate
    private static boolean imm9(TypeInteger ti) {
        // 55 = 64-9
        return ti.isConstant() && ((ti.value()<<55)>>55) == ti.value();
    }
    // True if signed 12-bit immediate
    private static boolean imm12(TypeInteger ti) {
        // 52 = 64-12
        return ti.isConstant() && ((ti.value()<<52)>>52) == ti.value();
    }

    // Can we encode this in ARM's 12-bit LOGICAL immediate form?
    // Some combination of shifted bit-masks.
    private static int imm12Logical(TypeInteger ti) {
        return ti.isConstant() ? Arm64.logicalImmediate(ti.value()) : -1;
    }

    public static void imm_inst(Encoding enc, Node n,Node n2,  int opcode, int imm12) {
        short self = enc.reg(n);
        short reg1 = enc.reg(n2);

        // ADD/SUB encode an unsigned magnitude; switch operation for a negative addend.
        if( imm12<0 && (opcode==OPI_ADD || opcode==OPI_SUB) ) {
            opcode ^= OPI_ADD ^ OPI_SUB;
            imm12 = -imm12;
        }
        int body = Arm64.imm_inst(opcode, imm12&0xFFF, reg1, self);
        enc.add4(body);
    }

    public static void imm_inst_subs(Encoding enc, Node n,Node n2,  int opcode, int imm12) {
        short reg1 = enc.reg(n2);
        // 31 = 11111
        int body = Arm64.imm_inst(opcode, imm12&0xFFF, reg1, 31);
        enc.add4(body);
    }

    // for cases where rs1 and dst are the same, eg add x0, x0, 1
    public static void imm_inst(Encoding enc, int opcode, int imm12, int self) {
        int body = Arm64.imm_inst(opcode, imm12&0xFFF, self, self);
        enc.add4(body);
    }

    public static void imm_inst_n(Encoding enc, Node n, Node n2, int opcode, int imm13) {
        short self = enc.reg(n);
        short reg1 = enc.reg(n2);
        int body = Arm64.imm_inst_n(opcode, imm13, reg1, self);
        enc.add4(body);
    }

    // for cases where rs1 and dst are the same, eg add x0, x0, 1
    public static int imm_inst_l(Encoding enc, Node n, int opcode, int imm12) {
        short self = enc.reg(n);
        short reg1 = enc.reg(n.in(1));
        int body = Arm64.imm_inst(opcode, imm12&0xFFF, reg1, self);
        return body;
    }

    // for normal add, reg1, reg2 cases (reg-to-reg)
    // using shifted-reg form

    public static void r_reg(Encoding enc, Node n, int opcode) {
        short self = enc.reg(n);
        short reg1 = enc.reg(n.in(1));
        short reg2 = enc.reg(n.in(2));
        int body = Arm64.r_reg(opcode, 0, reg2, 0,  reg1, self >= 32 ? reg1: self);
        enc.add4(body);
    }

    public static void r_reg_subs(Encoding enc, Node n, int opcode) {
        short reg1 = enc.reg(n.in(1));
        short reg2 = enc.reg(n.in(2));
        // 31 = 11111
        int body = Arm64.r_reg(opcode, 0, reg2, 0,  reg1, 31);
        enc.add4(body);
    }

    public static void shift_reg(Encoding enc, Node n, int op2) {
        short self = enc.reg(n);
        short reg1 = enc.reg(n.in(1));
        short reg2 = enc.reg(n.in(2));
        int body = Arm64.shift_reg(0b10011010110, reg2, op2, reg1, self);
        enc.add4(body);
    }

    //  MUL can be considered an alias for MADD with the third operand Ra being set to 0

    public static void madd(Encoding enc, Node n, int opcode, int ra) {
        short self = enc.reg(n);
        short reg1 = enc.reg(n.in(1));
        short reg2 = enc.reg(n.in(2));
        int body = Arm64.madd(opcode, reg2, ra,  reg1, self);
        enc.add4(body);
    }

    public static void f_scalar(Encoding enc, Node n, int op ) {
        short self = (short)(enc.reg(n)      -D_OFFSET);
        short reg1 = (short)(enc.reg(n.in(1))-D_OFFSET);
        short reg2 = (short)(enc.reg(n.in(2))-D_OFFSET);
        int body = Arm64.f_scalar(0b00011110, 1, reg2, op,  reg1, self);
        enc.add4(body);
    }

    public static void f_cmp(Encoding enc, Node n) {
        short reg1 = (short)(enc.reg(n.in(1))-D_OFFSET);
        short reg2 = (short)(enc.reg(n.in(2))-D_OFFSET);
        int body = Arm64.f_cmp(0b00011110, 3, reg1,  reg2);
        enc.add4(body);
    }

    @Override public RegMask callArgMask(TypeFunPtr tfp, int idx, int maxArgSlot ) { return callInMask(tfp,idx,maxArgSlot); }
    static RegMask callInMask(TypeFunPtr tfp, int idx, int maxArgSlot ) {
        if( idx==0 ) return CodeGen.CODE._rpcMask;
        if( idx==1 ) return null;
        // Count floats in signature up to index
        int fcnt=0;
        for( int i=2; i<idx; i++ )
            if( tfp.arg(i-2) instanceof TypeFloat)
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

    // Return the max stack slot used by this signature, or 0
    @Override public short maxArgSlot( TypeFunPtr tfp ) {
        int icnt=0, fcnt=0;     // Count of ints, floats
        for( int i=0; i<tfp.nargs(); i++ ) {
            if( tfp.arg(i) instanceof TypeFloat ) fcnt++;
            else icnt++;
        }
        int nstk = Math.max(icnt-8,0)+Math.max(fcnt-8,0);
        return (short)nstk;
    }

    private static final long CALLEE_SAVE =
        1L<<X19 |
        1L<<X20 | 1L<<X21 | 1L<<X22 | 1L<<X23 |
        1L<<X24 | 1L<<X25 | 1L<<X26 | 1L<<X27 |
        1L<<X28 |
        1L<<D9  | 1L<<D10 | 1L<<D11 |
        1L<<D12 | 1L<<D13 | 1L<<D14 | 1L<<D15;
    static final long CALLER_SAVE = ~CALLEE_SAVE & ~(1L<<RSP);
    @Override public long callerSave() { return CALLER_SAVE; }
    @Override public long neverSave() { return 1L<<RSP; }
    @Override public RegMask retMask( TypeFunPtr tfp ) {
        return tfp.ret() instanceof TypeFloat ? D0_MASK : X0_MASK;
    }
    @Override public int rpc() { return X30; }

    // Create a split op; any register to any register, including stack slots
    @Override public SplitNode split(String kind, byte round, LRG lrg) {  return new SplitARM(kind,round);  }

    // Return a MachNode unconditional branch
    @Override public CFGNode jump() {
        return new UJmpARM();
    }

    // Instruction selection
    @Override public Node instSelect(Node n ) {
        return switch( n ) {
        case AddFNode addf  -> new AddFARM(addf);
        case AddNode add    -> add(add);
        case AndNode and    -> and(and);
        case BoolNode bool  -> cmp(bool);
        case CallNode call  -> call(call);
        case CastNode cast  -> new CastARM(cast);
        case CallEndNode cend -> new CallEndARM(cend);
        case CProjNode c    -> new CProjNode(c);
        case ConstantNode con -> con(con);
        case DivFNode divf  -> new DivFARM(divf);
        case DivNode div    -> new DivARM(div);
        case FunNode fun    -> new FunARM(fun);
        case IfNode iff     -> jmp(iff);
        case LoadNode ld    -> ld(ld);
        case MemMergeNode mem -> new MemMergeNode(mem);
        case MinusNode neg  -> new NegARM(neg);
        case MulFNode mulf  -> new MulFARM(mulf);
        case MulNode mul    -> new MulARM(mul);
        case NewNode nnn    -> new NewARM(nnn);
        case NotNode not    -> new NotARM(not);
        case OrNode or      -> or(or);
        case ParmNode parm  -> new ParmARM(parm);
        case BulkMemPhiNode phi -> new BulkMemPhiNode(phi);
        case MemPhiNode phi -> new MemPhiNode(phi);
        case PhiNode phi    -> new PhiNode(phi);
        case ProjNode prj   -> new ProjARM(prj);
        case ReadOnlyNode read  -> new ReadOnlyNode(read);
        case ReturnNode ret -> new RetARM(ret,ret.fun());
        case SarNode sar    -> asr(sar);
        case ShlNode shl    -> lsl(shl);
        case ShrNode shr    -> lsr(shr);
        case StartNode start -> new StartNode(start);
        case StopNode stop  -> new StopNode(stop);
        case StoreNode st   -> st(st);
        case SubFNode subf  -> new SubFARM(subf);
        case SubNode sub    -> sub(sub);
        case ToFloatNode tfn-> new I2F8ARM(tfn);
        case ToIntegerNode cvt -> new F8ToIARM(cvt);
        case XorNode xor    -> xor(xor);

        case LoopNode  loop  -> new LoopNode(loop);
        case RegionNode region-> new RegionNode(region);
        default -> throw Utils.TODO();
        };
    }

    private Node cmp(BoolNode bool){
        Node cmp = _cmp(bool);
        return new SetARM(cmp, IfNode.negate(bool.op()));
    }
    private Node _cmp(BoolNode bool) {
        if( bool.isFloat() )
            return new CmpFARM(bool);
        return bool.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti
                ? new CmpIARM(bool, (int)ti.value())
                : new CmpARM(bool);
    }

    private Node ld(LoadNode ld) {
        return new LoadARM(address(ld), ld.ptr(), idx, off);
    }

    private Node jmp(IfNode iff) {
        // If/Bool combos will match to a Cmp/Set which sets flags.
        // Most general arith ops will also set flags, which the Jmp needs directly.
        // Loads do not set the flags, and will need an explicit TEST
        String op = "!=";
        if( iff.in(1) instanceof BoolNode bool ) op = bool.op();
        else if( iff.in(1)==null ) op = "=="; // Never-node cutout
        else iff.setDef(1, new BoolNode.NE(iff.in(1), new ConstantNode(TypeInteger.ZERO)));
        return new BranchARM(iff, op);
    }

    private Node add(AddNode add) {
        return add.in(2) instanceof ConstantNode off && off._con instanceof TypeInteger ti && imm12(ti)
                ? new AddIARM(add, (int)ti.value())
                : new AddARM(add);
    }

    private Node sub(SubNode sub) {
        return sub.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti && imm12(ti)
                ? new SubIARM(sub, (int)(ti.value()))
                : new SubARM(sub);
    }

    private Node con( ConstantNode con ) {
        if( !con._con.isConstant() ) return new ConstantNode( con ); // Default unknown caller inputs
        return switch( con._con ) {
            case TypeInteger ti  -> new IntARM(con);
            case TypeFloat   tf  -> new FloatARM(con);
            case TypeFunPtr  tfp -> new TFPARM(con);
            case TypeMemPtr tmp -> new ConstantNode(con);
            case TypeNil tn  -> throw Utils.TODO();
            // TOP, BOTTOM, XCtrl, Ctrl, etc.  Never any executable code.
            case Type t -> t==Type.NIL ? new IntARM(con) : new ConstantNode(con);
        };
    }

    private Node call(CallNode call){
        return call.fptr() instanceof ConstantNode con && con._con instanceof TypeFunPtr tfp
                ? new CallARM(call, tfp)
                : new CallRRARM(call);
    }

    private Node or(OrNode or) {
        int imm12;
        return or.in(2) instanceof ConstantNode off && off._con instanceof TypeInteger ti && (imm12 = imm12Logical(ti)) != -1
                ? new OrIARM(or, imm12)
                : new OrARM(or);
    }

    private Node xor(XorNode xor) {
        int imm12;
        return xor.in(2) instanceof ConstantNode off && off._con instanceof TypeInteger ti && (imm12 = imm12Logical(ti)) != -1
                ? new XorIARM(xor, imm12)
                : new XorARM(xor);
    }

    private Node and(AndNode and) {
        int imm12;
        return and.in(2) instanceof ConstantNode off && off._con instanceof TypeInteger ti && (imm12 = imm12Logical(ti)) != -1
                ? new AndIARM(and, imm12)
                : new AndARM(and);
    }

    private Node asr(SarNode asr) {
        return asr.in(2) instanceof ConstantNode off && off._con instanceof TypeInteger ti && ti.value() >= 0 && ti.value() < 63
                ? new AsrIARM(asr, (int)ti.value())
                : new AsrARM(asr);
    }

    private Node lsl(ShlNode lsl) {
        return lsl.in(2)  instanceof ConstantNode off && off._con instanceof TypeInteger ti && ti.value() >= 0 && ti.value() < 63
                ? new LslIARM(lsl, (int)ti.value())
                : new LslARM(lsl);
    }

    private Node lsr(ShrNode lsr) {
        return lsr.in(2)  instanceof ConstantNode off && off._con instanceof TypeInteger ti && ti.value() >= 0 && ti.value() < 63
                ? new LsrIARM(lsr, (int)ti.value())
                : new LsrARM(lsr);
    }

    private static int off;
    private static Node idx;
    private Node st(StoreNode st) {
        return new StoreARM(address(st),st.ptr(),idx,off,st.val());
    }

    // Gather addressing mode bits prior to constructing.  This is a builder
    // pattern, but saving the bits in a *local* *global* here to keep mess
    // contained.
    private <N extends MemOpNode> N address(N mop ) {
        off = 0;  // Reset
        idx = null;
        Node base = mop.ptr();
        // Skip/throw-away a ReadOnly, only used to typecheck
        if( base instanceof ReadOnlyNode read ) base = read.in(1);
        assert !(base instanceof AddNode) && base._type instanceof TypeMemPtr; // Base ptr always, not some derived
        if( mop.off() instanceof ConstantNode con && con._con instanceof TypeInteger ti && imm9(ti) ) {
            off = (int)ti.value();
            assert off == ti.value(); // In 32-bit range
        } else {
            idx = mop.off();
        }
        return mop;
    }

}
