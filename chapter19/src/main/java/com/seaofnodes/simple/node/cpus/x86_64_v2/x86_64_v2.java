package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.Machine;
import com.seaofnodes.simple.RegMask;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;

public class x86_64_v2 extends Machine {
    // X86-64 V2.  Includes e.g. SSE4.2 and POPCNT.
    @Override public String name() { return "x86_64_v2"; }

    public static int RAX =  0, RCX =  1, RDX =  2, RBX =  3, RSP =  4, RBP =  5, RSI =  6, RDI =  7;
    public static int R08 =  8, R09 =  9, R10 = 10, R11 = 11, R12 = 12, R13 = 13, R14 = 14, R15 = 15;
    public static int FLAGS = 32;

    public static int XMM0  = 16, XMM1  = 17, XMM2  = 18, XMM3  = 19, XMM4  = 20, XMM5  = 21, XMM6  = 22, XMM7  = 23;
    public static int XMM8  = 24, XMM9  = 25, XMM10 = 26, XMM11 = 27, XMM12 = 28, XMM13 = 29, XMM14 = 30, XMM15 = 31;

    // General purpose register mask: pointers and ints, not floats
    public static RegMask RMASK = new RegMask(0b1111111111111111);
    // No RSP in the *write* general set.
    public static RegMask WMASK = new RegMask(0b1111111111101111);
    // Xmm register mask
    public static RegMask XMASK = new RegMask(0b111111111110111);

    public static RegMask FLAGS_MASK = new RegMask(1L<<FLAGS);

    // Return single int/ptr register
    public static RegMask RET_MASK = new RegMask(RAX);

    public static RegMask RDI_MASK = new RegMask(1L<<RDI);



    // Human-readable name for a register number, e.g. "RAX"
    @Override public String reg( int reg ) {
        throw Utils.TODO();
    }
    // Calling convention; returns a machine-specific register
    // for incoming argument idx.
    // index 0 for control, 1 for memory, real args start at index 2
    @Override public int callInArg( int idx ) {
        return switch(idx) {
        case 0 -> 0;            // Control: no register
        case 1 -> 1;            // Memory : no register
        case 2 -> RDI;          // Arg#2 in simple, arg#0 or #1 in other ABIs
        default -> throw Utils.TODO();
        };
    }
    public static RegMask[] CALLINMASK = new RegMask[] {
        null,
        null,
        RDI_MASK,
    };
    @Override public RegMask callInMask( int idx ) { return CALLINMASK[idx]; }

    // Create a split op; any register to any register, including stack slots
    @Override public Node split() {
        throw Utils.TODO();
    }

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
        case CProjNode    c     -> new CProjNode(c);
        case ConstantNode con   -> con(con);
        case DivFNode     divf  -> new DivFX86(divf);
        case DivNode      div   -> new DivX86(div);
        case FunNode      fun   -> new FunX86(fun);
        case IfNode       iff   -> jmp(iff);
        case MemMergeNode mem   -> new MemMergeNode(mem);
        case MulFNode     mulf  -> mulf(mulf);
        case MulNode      mul   -> mul(mul);
        case NewNode      nnn   -> new NewX86(nnn);
        case OrNode       or   ->  or(or);
        case ParmNode     parm  -> new ParmX86(parm);
        case PhiNode      phi   -> new PhiNode(phi);
        case ProjNode     prj   -> prj(prj);
        case ReturnNode   ret   -> new RetX86(ret,ret.fun());
        case SarNode      sar   -> sar(sar);
        case ShlNode      shl   -> shl(shl);
        case ShrNode      shr   -> shr(shr);
        case StartNode    start -> new StartNode(start);
        case StopNode     stop  -> new StopNode(stop);
        case SubFNode     subf  -> subf(subf);
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
        if( !(rhs instanceof ConstantNode off && off._con instanceof TypeInteger toff) )
            return _lea(add,lhs,rhs,0);

        // ((base + (idx << scale)) + off)
        if( lhs instanceof AddNode ladd )
            return _lea(add,ladd.in(1),ladd.in(2),toff.value());

        // lhs + rhs1
        return new AddIX86(add, toff);
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


    private Node addf(AddFNode addf) {
        return new AddFX86(addf);
    }

    private Node and(AndNode and) {
        if(and.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti)
            return new AndIX86(and, ti);
        throw Utils.TODO();
    }

    // Because X86 flags, a normal ideal Bool is 2 X86 ops: a "cmp" and at "setz".
    // Ideal If reading from a setz will skip it and use the "cmp" instead.
    private Node cmp( BoolNode bool ) {
        MachConcreteNode cmp = bool.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti
            ? new CmpIX86(bool, ti)
            : new  CmpX86(bool);
        return new SetX86(cmp,bool.op());
    }

    private Node con( ConstantNode con ) {
        return switch( con._con ) {
        case TypeInteger ti  -> new IntX86(con);
        case TypeFloat   tf  -> new FltX86(con);
        case TypeMemPtr  tmp -> throw Utils.TODO();
        case TypeFunPtr  tmp -> throw Utils.TODO();
        case TypeNil     tn  -> throw Utils.TODO();
        // TOP, BOTTOM, XCtrl, Ctrl, etc.  Never any executable code.
        case Type t -> new ConstantNode(con);
        };
    }

    private Node i2f8(ToFloatNode tfn) {
        if( tfn.in(1)._type instanceof TypeInteger ti)
            return new I2f8X86(tfn, ti);
        throw Utils.TODO();
    }

    private Node jmp( IfNode iff ) {
        return new JmpX86(iff, iff.in(1) instanceof BoolNode bool ? bool.op() : "==");
    }

    private Node mul(MulNode mul) {
        return mul.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti
            ? new MulIX86(mul, ti)
            : new MulX86(mul);
    }

    private Node mulf(MulFNode mulf) {
        return new MulFX86(mulf);
    }

    private Node or(OrNode or) {
        if(or.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti)
            return new OrIX86(or, ti);
        throw Utils.TODO();
    }

    private Node prj( ProjNode prj ) {
        if( prj.in(0) instanceof NewNode nnn && prj._type instanceof TypeMemPtr ) {
            for( int i=0; i< nnn._len; i++ ) {
                Type init = nnn.init(i)._type;
                if( init != init.makeZero() )
                    throw Utils.TODO(); // Insert a Store
            }
            // Delete all initial values, already folded into stores
            for( int i=0; i< nnn._len; i++ )
                nnn._inputs.pop();
        }
        return new ProjX86(prj);
    }

    private Node sar( SarNode sar ) {
        if(sar.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti)
            return new SarX86(sar, ti);
        throw Utils.TODO();
    }

    private Node shl( ShlNode shl ) {
        if( shl.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti )
            return new ShlIX86(shl, ti);

        throw Utils.TODO();
    }

    private Node shr(ShrNode shr) {
        if( shr.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti )
            return new ShrIX86(shr, ti);

        throw Utils.TODO();
    }

    private Node subf(SubFNode subf) {
        return new SubFX86(subf);
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

}
