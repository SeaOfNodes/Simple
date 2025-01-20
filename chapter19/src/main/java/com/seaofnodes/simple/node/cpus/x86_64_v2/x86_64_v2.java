package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.Machine;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;

public class x86_64_v2 extends Machine {
    // X86-64 V2.  Includes e.g. SSE4.2 and POPCNT.
    @Override public String name() { return "x86_64_v2"; }

    // Human-readable name for a register number, e.g. "RAX" or "R0"
    @Override public String reg( int reg ) {
        throw Utils.TODO();
    }
    // Calling convention; returns a machine-specific register
    // for incoming argument idx.
    // index 0 for control, 1 for memory, real args start at index 2
    @Override public int callInArg( int idx ) {
        throw Utils.TODO();
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
}
