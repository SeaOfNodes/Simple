package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.type.*;

/** Allocate a zeroed object. Inputs {ctrl, $mem, size};
 *  results {ptr, $mem}.
 *  The memory input and result cover only the aliases in the allocated struct.
 */
public class NewNode extends Node implements MultiNode {

    public final TypeMemPtr _ptr;

    public NewNode(TypeMemPtr ptr, Node... nodes) {
        super(nodes);
        assert !ptr.nullable();
        _ptr = ptr;
        assert nodes.length==3;
        assert nodes[0]._type==Type.CONTROL || nodes[0]._type==Type.XCONTROL;
        assert nodes[1]._type instanceof TypeMem;
        assert nodes[2]._type instanceof TypeInteger || nodes[2]._type==Type.NIL;
    }

    public Node mem() { return in(1); }
    public Node size() { return in(2); }

    public Field field(int alias) {
        for( Field f : _ptr._obj._fields )
            if( f._alias==alias ) return f;
        return null;
    }

    public NewNode(NewNode nnn) { super(nnn); _ptr = nnn._ptr; }


    @Override public String label() { return "new_"+(_ptr._obj.isAry() ? "ary_"+_ptr._obj._fields[1]._type.str() : _ptr._obj.str()); }
    @Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        return p.p("new ").p(_ptr._obj.str());
    }


    @Override
    public TypeTuple compute() {
        return TypeTuple.make(_ptr,TypeMem.BOT);
    }

    @Override
    public Node idealize() { return null; }

    @Override
    public boolean eq(Node n) { return this == n; }

    @Override
    int hash() { return _ptr.hashCode(); }

    // ------------
    // MachNode specifics, shared across all CPUs
    public int _arg2Reg, _xslot;
    private RegMask _arg3Mask;
    private RegMask _retMask;
    private RegMask _kills;
    public void cacheRegs(CodeGen code) {
        _arg2Reg  = code._mach.callArgMask(TypeFunPtr.CALLOC,2,0).firstReg();
        _arg3Mask = code._mach.callArgMask(TypeFunPtr.CALLOC,3,0);
        // Return mask depends on TFP (either GPR or FPR)
        _retMask = code._mach.retMask(TypeFunPtr.CALLOC);
        // Kill mask is all caller-saves, and any mirror stack slots for args
        // in registers.
        RegMaskRW kills = code._callerSave.copy();
        // Start of stack slots
        int maxReg = code._mach.regs().length;
        // Incoming function arg slots, all low numbered in the RA
        int fslot = cfg0().fun()._maxArgSlot;
        // Killed slots for this calls outgoing args
        int xslot = code._mach.maxArgSlot(TypeFunPtr.CALLOC);
        _xslot = (maxReg+fslot)+xslot;
        for( int i=0; i<xslot; i++ )
            kills.set((maxReg+fslot)+i);
        _kills = kills;
    }
    public String op() { return "alloc"; }
    public RegMask regmap(int i) { return i==2 ? _arg3Mask : null; }
    public RegMask outregmap() { return null; }
    public RegMask outregmap(int idx) { return idx==0  ? _retMask : null; }
    public RegMask killmap() { return _kills; }
    public void asm(CodeGen code, SB sb) {
        sb.p("#calloc, ").p(code.reg(size()));
    }
}
