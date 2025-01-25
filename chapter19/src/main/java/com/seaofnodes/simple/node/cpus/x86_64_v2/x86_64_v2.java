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

    // General purpose register mask: pointers and ints, not floats
    public static RegMask RMASK = new RegMask(0b1111111111111111);

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
        case AddNode      add   -> add(add);
        case ConstantNode con   -> con(con);
        case FunNode      fun   -> new FunX86(fun);
        case ParmNode     parm  -> new ParmX86(parm);
        case ReturnNode   ret   -> new RetX86(ret,(FunX86)ret.rpc().in(0));
        case StartNode    start -> new StartNode(start);
        case StopNode     stop  -> new StopNode(stop);
        default -> throw Utils.TODO();
        };
    }


    private Node con( ConstantNode con ) {
        return switch( con._type ) {
        case TypeInteger ti  -> new IntX86(con);
        case TypeFloat   tf  -> throw Utils.TODO();
        case TypeMemPtr  tmp -> throw Utils.TODO();
        case TypeFunPtr  tmp -> throw Utils.TODO();
        case TypeNil     tn  -> throw Utils.TODO();
        // TOP, BOTTOM, XCtrl, Ctrl, etc.  Never any executable code.
        case Type t -> new ConstantNode(con._type);
        };
    }


    private Node add( AddNode add ) {
        if( add.in(2) instanceof ConstantNode con && con._con instanceof TypeInteger ti )
            return new AddIX86(add, ti);
        throw Utils.TODO();
    }
}
