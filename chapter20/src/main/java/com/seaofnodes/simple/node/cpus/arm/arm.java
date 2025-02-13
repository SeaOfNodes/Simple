package com.seaofnodes.simple.node.cpus.arm;



import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.Machine;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.cpus.riscv.*;
import com.seaofnodes.simple.node.cpus.x86_64_v2.*;
import com.seaofnodes.simple.type.*;

public class arm extends Machine{

    // X86-64 V2.  Includes e.g. SSE4.2 and POPCNT.
    @Override public String name() { return "arm"; }

    // GPR(S)
    public static int X0  =  0,  X1  =  1,  X2  =  2,  X3  =  3,  X4  =  4,  X5  =  5,  X6  =  6,  X7  =  7;
    public static int X8  =  8,  X9  =  9,  X10 = 10,  X11 = 11,  X12 = 12,  X13 = 13,  X14 = 14,  X15 = 15;
    public static int X16 = 16,  X17 = 17,  X18 = 18,  X19 = 19,  X20 = 20,  X21 = 21,  X22 = 22,  X23 = 23;
    public static int X24 = 24,  X25 = 25,  X26 = 26,  X27 = 27,  X28 = 28,  X29 = 29,  X30 = 30;

    // Floating point registers

    public static int D0  =  31,  D1  =  32,  D2  =  33,  D3  =  34,  D4  =  35,  D5  =  36,  D6  =  37,  D7  =  38;
    public static int D8  =  39,  D9  =  40,  D10 = 41,  D11 = 42,  D12 = 43,  D13 = 44,  D14 = 45,  D15 = 46;
    public static int D16 = 47,  D17 = 48,  D18 = 49,  D19 = 50,  D20 = 51,  D21 = 52,  D22 = 53,  D23 = 54;
    public static int D24 = 55,  D25 = 56,  D26 = 57,  D27 = 58,  D28 = 59,  D29 = 60;

    // from (x0-x30)
    // General purpose register mask: pointers and ints, not floats
    public static RegMask RMASK = new RegMask(0b11111111111111111111111111111110L);

    // Float mask from(d0–d30)
    public static RegMask DMASK = new RegMask(0b11111111111111111111111111111111L<<D0);

    // Arguments masks
    public static RegMask X0_MASK = new RegMask(1L<<X0);
    public static RegMask X1_MASK = new RegMask(1L<<X1);
    public static RegMask X2_MASK = new RegMask(1L<<X2);
    public static RegMask X3_MASK = new RegMask(1L<<X3);
    public static RegMask X4_MASK = new RegMask(1L<<X4);
    public static RegMask X5_MASK = new RegMask(1L<<X5);
    public static RegMask X6_MASK = new RegMask(1L<<X6);
    public static RegMask X7_MASK = new RegMask(1L<<X7);


    // Arguments(float) masks
    public static RegMask D0_MASK = new RegMask(1L<<D0);
    public static RegMask D1_MASK = new RegMask(1L<<D1);
    public static RegMask D2_MASK = new RegMask(1L<<D2);
    public static RegMask D3_MASK = new RegMask(1L<<D3);
    public static RegMask D4_MASK = new RegMask(1L<<D4);
    public static RegMask D5_MASK = new RegMask(1L<<D5);
    public static RegMask D6_MASK = new RegMask(1L<<D6);
    public static RegMask D7_MASK = new RegMask(1L<<D7);


    public static final String[] REGS = new String[] {
            "X0",  "X1",  "X2",  "X3",  "X4",  "X5",  "X6",  "X7",
            "X8",  "X9",  "X10", "X11", "X12", "X13", "X14", "X15",
            "X16", "X17", "X18", "X19", "X20", "X21", "X22", "X23",
            "X24", "X25", "X26", "X27", "X28", "X29", "X30",
            "D0",  "D1",  "D2",  "D3",  "D4",  "D5",  "D6",  "D7",
            "D8",  "D9",  "D10", "D11", "D12", "D13", "D14", "D15",
            "D16", "D17", "D18", "D19", "D20", "D21", "D22", "D23",
            "D24", "D25", "D26", "D27", "D28", "D29", "D30"
    };

    @Override public String reg( int reg ) { return REGS[reg]; }

    // Calling convention; returns a machine-specific register
    // for incoming argument idx.
    // index 0 for control, 1 for memory, real args start at index 2
    static RegMask[] CALLINMASK = new RegMask[] {
            X1_MASK,
            X2_MASK,
            X3_MASK,
            X4_MASK,
            X5_MASK,
            X6_MASK,
            X7_MASK
    };

    static int[] CALLINARG = new int[] {
            0,
            0,
            X1,
            X2,
            X3,
            X4,
            X5,
            X6,
            X7
    };

    static int callInArg(TypeFunPtr tfp, int idx){
        return CALLINARG[idx];
    }

    static RegMask callInMask(int idx) {
        return CALLINMASK[idx];
    }

    // Create a split op; any register to any register, including stack slots
    @Override public Node split() {  return new SplitARM();  }

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
            case CallEndNode  cend  -> new CallEndNode((CallNode)cend.in(0));
            case CallNode     call  -> call(call);
            case CProjNode    c     -> new CProjNode(c);
            case ConstantNode con   -> con(con);
            case DivFNode     divf  -> new DivFARM(divf);
            case DivNode      div   -> new DivARM(div);
            case FunNode      fun   -> new FunX86(fun);
            case IfNode       iff   -> jmp(iff);
            case LoadNode     ld    -> ld(ld);
            case MemMergeNode mem   -> new MemMergeNode(mem);
            case MulFNode     mulf  -> new MulFARM(mulf);
            case MulNode      mul   -> new MulFARM(mul);
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
            case SubFNode     subf  -> new SubFARM(subf);
            case SubNode      sub   -> sub(sub);
            case ToFloatNode  tfn   -> i2f8(tfn);
            case XorNode      xor   -> xor(xor);

            case LoopNode     loop  -> new LoopNode(loop);
            case RegionNode   region-> new RegionNode(region);
            default -> throw Utils.TODO();
        };
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
        if( and.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti )
            return new AndIARM(and, ti);
        return new AndARM(and);
    }

    private Node or(OrNode or) {
        if(or.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti)
            return new OrIARM(or, ti);

        return new OrARM(or);
    }

    private Node xor(XorNode xor) {
        if(xor.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti)
            return new XorIARM(xor, ti);
        return new XorARM(xor);
    }

    private Node con(ConstantNode con) {
        if( !con._con.isConstant() ) return new ConstantNode( con ); // Default unknown caller inputs
        return switch( con._con ) {
            case TypeInteger ti  -> new IntARM(con);
            case TypeFloat   tf  -> new IntARM(con);
            case TypeFunPtr  tfp -> new TFPARM(con);
            case TypeMemPtr  tmp -> new ConstantNode(con);
            case TypeNil     tn  -> throw Utils.TODO();
            // TOP, BOTTOM, XCtrl, Ctrl, etc.  Never any executable code.
            case Type t -> new ConstantNode(con);
        };
    }

    private Node sar(SarNode sar){
        if( sar.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti)
            return new AsrIARM(sar, ti);
        return new AsrARM(sar);
    }

    private Node shl(ShlNode shl) {
        if( shl.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti)
            return new LslIARM(shl, ti);
        return new LslARM(shl);

    }

    private Node shr(ShrNode shr) {
        if( shr.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti)
            return new LsrIARM(shr, ti);
        return new LsrARM(shr);
    }
}
