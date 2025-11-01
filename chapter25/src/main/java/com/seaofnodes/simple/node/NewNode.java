package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.util.SB;
import java.util.BitSet;
import java.util.HashMap;
import static com.seaofnodes.simple.util.Utils.TODO;

/**
 * Allocation!  Allocate a chunk of memory, no init.
 * Takes in ctrl and size, and TypeStruct.
 * Produces a ptr and private memory.
*/
public class NewNode extends Node implements MultiNode {
    public final TypeStruct _ts;
    public NewNode( TypeStruct ts, Node ctrl, Node size ) { super(ctrl,size); _ts = ts; }

    public Node ctrl() { return in(0); }
    public Node size() { return in(1); }

    // --- Serialization
    public NewNode(NewNode nnn) { super(nnn); _ts = nnn._ts; }
    @Override public Tag serialTag() { return Tag.New; }
    @Override public void packed(BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, HashMap<Integer,Integer> aliases) {
        throw TODO();
    }
    static Node make( BAOS bais, Type[] types)  {
        //Node[] ins = new Node[bais.packed1()];
        //TypeMemPtr ptr = (TypeMemPtr)types[bais.packed2()];
        //return new NewNode(1.0f,ptr,ins);
        throw TODO();
    }

    @Override public String glabel() {
        return _ts.isAry() ? "ary_"+_ts._fields[1]._t.str() : _ts.str();
    }
    @Override public String label() { return "new_"+glabel(); }

    @Override public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        sb.append("new ");
        return sb.append(_ts.str());
    }

    @Override public TypeTuple compute() {
        return TypeTuple.make(TypeMemPtr.make(_ts), TypeMem.make(1,_ts));
    }

    @Override public Node idealize() {
        return null;
    }

    @Override public boolean eq(Node n) { return _ts == ((NewNode)n)._ts; }

    @Override int hash() { return _ts.hashCode(); }

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
    public RegMask regmap(int i) { return i==1 ? _arg3Mask : null; }
    public RegMask outregmap() { return null; }
    public RegMask outregmap(int idx) { return idx==1  ? _retMask : null; }
    public RegMask killmap() { return _kills; }
    public void asm(CodeGen code, SB sb) {
        sb.p("#malloc, ").p(code.reg(size()));
    }

}
