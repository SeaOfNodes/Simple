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
    public static int FLAGS = 16;

    // General purpose register mask: pointers and ints, not floats
    public static RegMask RMASK = new RegMask(0b1111111111111111);
    // No RSP in the *write* general set.
    public static RegMask WMASK = new RegMask(0b1111111111101111);

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
        case AddNode      add   -> add(add);
        case BoolNode     bool  -> cmp(bool);
        case CProjNode    c     -> new CProjNode(c);
        case ConstantNode con   -> con(con);
        case FunNode      fun   -> new FunX86(fun);
        case IfNode       iff   -> jmp(iff);
        case MemMergeNode mem   -> new MemMergeNode(mem);
        case NewNode      nnn   -> new NewX86(nnn);
        case ParmNode     parm  -> new ParmX86(parm);
        case PhiNode      phi   -> new PhiNode(phi);
        case ProjNode     prj   -> prj(prj);
        case ReturnNode   ret   -> new RetX86(ret,ret.fun());
        case ShlNode      shl   -> shl(shl);
        case SarNode      sar   -> sar(sar);
        case StartNode    start -> new StartNode(start);
        case StopNode     stop  -> new StopNode(stop);
        case SubNode      sub   -> sub(sub);

        case LoopNode     loop  -> new LoopNode(loop);
        case RegionNode   region-> new RegionNode(region);
        default -> throw Utils.TODO();
        };
    }


    private Node con( ConstantNode con ) {
        return switch( con._con ) {
        case TypeInteger ti  -> new IntX86(con);
        case TypeFloat   tf  -> throw Utils.TODO();
        case TypeMemPtr  tmp -> throw Utils.TODO();
        case TypeFunPtr  tmp -> throw Utils.TODO();
        case TypeNil     tn  -> throw Utils.TODO();
        // TOP, BOTTOM, XCtrl, Ctrl, etc.  Never any executable code.
        case Type t -> new ConstantNode(con);
        };
    }


    private Node sar( SarNode sar ) {
        if(sar.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti)
            return new SarX86(sar, ti);
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

    private Node shl( ShlNode shl ) {
        if( shl.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti ) {
            return new ShlIX86(shl, ti);
        }
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

    private Node jmp( IfNode iff ) {
        if( iff.in(1) instanceof BoolNode bool )
            return new JmpX86(iff,bool);
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
}
