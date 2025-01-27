package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.Machine;
import com.seaofnodes.simple.RegMask;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;

import java.rmi.server.UID;

public class x86_64_v2 extends Machine {
    // X86-64 V2.  Includes e.g. SSE4.2 and POPCNT.
    @Override public String name() { return "x86_64_v2"; }

    public static int RAX =  0, RCX =  1, RDX =  2, RBX =  3, RSP =  4, RBP =  5, RSI =  6, RDI =  7;
    public static int R08 =  8, R09 =  9, R10 = 10, R11 = 11, R12 = 12, R13 = 13, R14 = 14, R15 = 15;
    public static int FLAGS = 16;

    public static int XMM0 = 0, XMM1 = 1, XMM2 = 2, XMM3 = 3, XMM4 = 4, XMM5 = 5, XMM6 = 6, XMM7 = 7;
    public static int XMM8 = 8, XMM9 = 9, XMM10 = 10, XMM11 = 11, XMM12 = 12, XMM13 = 13, XMM14 = 14;
    public static int XMM15 = 15;

    // General purpose register mask: pointers and ints, not floats
    public static RegMask RMASK = new RegMask(0b1111111111111111);
    // No RSP in the *write* general set.
    public static RegMask WMASK = new RegMask(0b1111111111101111);
    // Xmm register mask
    public static RegMask XMASK = new RegMask(0b111111111110111);

    public static RegMask FLAGS_MASK = new RegMask(1L<<FLAGS);

    // Return single int/ptr register
    public static RegMask RET_MASK = new RegMask(RAX);

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
        case DivFNode     divf  -> divf(divf);
        case DivNode      div   -> div(div);
        case FunNode      fun   -> new FunX86(fun);
        case IfNode       iff   -> jmp(iff);
        case MulFNode     mulf  -> mulf(mulf);
        case MulNode      mul   -> mul(mul);
        case OrNode       or   ->  or(or);
        case ParmNode     parm  -> new ParmX86(parm);
        case PhiNode      phi   -> new PhiNode(phi);
        case MulNode      mul   -> mul(mul);
        case DivNode      div   -> div(div);
        case ReturnNode   ret   -> new RetX86(ret,(FunX86)ret.rpc().in(0));
        case SarNode      sar   -> sar(sar);
        case ShlNode      shl   -> shl(shl);
        case ShrNode      shr   -> shr(shr);
        case StartNode    start -> new StartNode(start);
        case StopNode     stop  -> new StopNode(stop);
        case SubFNode     subf  -> subf(subf);
        case SubNode      sub   -> sub(sub);
        case ToFloatNode  tfn   -> fild(tfn);
        case XorNode      xor   -> xor(xor);
        case RegionNode   region-> new RegionNode(region);
        default -> throw Utils.TODO();
        };
    }


    private Node con( ConstantNode con ) {
        return switch( con._type ) {
        case TypeInteger ti  -> new IntX86(con);
        case TypeFloat   tf  -> new FltIX86(con);
        case TypeMemPtr  tmp -> throw Utils.TODO();
        case TypeFunPtr  tmp -> throw Utils.TODO();
        case TypeNil     tn  -> throw Utils.TODO();
        // TOP, BOTTOM, XCtrl, Ctrl, etc.  Never any executable code.
        case Type t -> new ConstantNode(con._type);
        };
    }

    private Node or(OrNode or) {
        if(or.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti)
            return new OrIX86(or, ti);
        throw Utils.TODO();
    }

    private Node fild(ToFloatNode tfn) {
        if(tfn.in(1)._type instanceof TypeInteger ti) {
            return new FildIX86(tfn, ti);
        }
        throw Utils.TODO();
    }
    private Node xor(XorNode xor) {
        if(xor.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti)
            return new XorIX86(xor, ti);
        throw Utils.TODO();
    }

    private Node and(AndNode and) {
        if(and.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti)
            return new AndIX86(and, ti);
        throw Utils.TODO();
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

    private Node mul(MulNode mul) {
        if(mul.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti)
            return new MulX86(mul, ti);
        throw Utils.TODO();
    }

    private Node div(DivNode div) {
        if(div.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti)
            return new DivX86(div, ti);

        throw Utils.TODO();
    }
    private Node shr(ShrNode shr) {
        if( shr.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti )
            return new ShrIX86(shr, ti);

        throw Utils.TODO();
    }

    private Node addf(AddFNode addf) {
        throw Utils.TODO();
    }

    private Node mulf(MulFNode mulf) {
        throw Utils.TODO();
    }

    private Node divf(DivFNode divf) {
        throw Utils.TODO();
    }

    private Node subf(SubFNode subf) {
        throw Utils.TODO();
    }

    private Node add( AddNode add ) {
        return add.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti
            ? new AddIX86(add, ti)
            : new  AddX86(add);
    }

    private Node sub( SubNode sub ) {
        if( sub.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti )
            return new AddIX86(sub, TypeInteger.constant(-ti.value()));
        throw Utils.TODO();
    }


    // Because X86 flags, a normal ideal Bool is 2 X86 ops: a "cmp" and at "setz".
    // Ideal If reading from a setz will skip it and use the "cmp" instead.
    private Node cmp( BoolNode bool ) {
        if( bool.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti )
            return new SetX86(new CmpIX86(bool, ti),bool.op());
        throw Utils.TODO();
    }

    private Node jmp( IfNode iff ) {
        if( iff.in(1) instanceof SetX86 set && set.in(1) instanceof CmpIX86 cmp )
            return new JmpX86(iff,cmp,set._bop);
        throw Utils.TODO();
    }
}
