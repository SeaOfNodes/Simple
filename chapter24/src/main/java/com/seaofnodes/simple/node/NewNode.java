package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;
import java.util.BitSet;
import java.util.HashMap;

/**
 *  Allocation!  Allocate a chunk of memory, and pre-zero it.
 *  The inputs include control and size, and ALL aliases being set.
 *  The output is large tuple, one for every alias plus the created pointer.
 *  New is expected to be followed by projections for every alias.
 */
public class NewNode extends Node implements MultiNode {

    public final TypeMemPtr _ptr;

    public NewNode(TypeMemPtr ptr, Node... nodes) {
        super(nodes);
        assert !ptr.nullable();
        _ptr = ptr;
        if( nodes[0]==null ) return; // No asserts for deserializing, which is the only time no control at construction
        int len = ptr._obj._fields.length;
        // Control in slot 0
        assert nodes[0]._type==Type.CONTROL || nodes[0]._type == Type.XCONTROL;
        // Malloc-length in slot 1
        assert nodes[1]._type instanceof TypeInteger || nodes[1]._type==Type.NIL;
        for( int i=0; i<len; i++ )
          assert ptr._obj._fields[i]._one || nodes[2 + i]._type.isa( TypeMem.BOT );
    }

    public NewNode(NewNode nnn) { super(nnn); _ptr = nnn._ptr; }
    @Override public Tag serialTag() { return Tag.New; }
    @Override public void packed(BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, HashMap<Integer,Integer> aliases) {
        baos.packed1(nIns());          // Number of alias inputs
        baos.packed2(types.get(_ptr)); // NPE if fails lookup
    }
    static Node make( BAOS bais, Type[] types)  {
        Node[] ins = new Node[bais.packed1()];
        TypeMemPtr ptr = (TypeMemPtr)types[bais.packed2()];
        return new NewNode(ptr,ins);
    }

    public Node mem (int idx) { return in(idx+2); }

    @Override public String label() { return "new_"+glabel(); }
    @Override public String glabel() {
        return _ptr._obj.isAry() ? "ary_"+_ptr._obj._fields[1]._t.str() : _ptr._obj.str();
    }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        sb.append("new ");
        return sb.append(_ptr._obj.str());
    }

    int findAlias(int alias) {
        int idx = _ptr._obj.findAlias(alias);
        assert idx!= -1;        // Error, caller should be calling
        return idx+2;           // Skip ctrl, size
    }

    // 0 - ctrl
    // 1 - byte size
    // 2-len+2 - aliases, one per field
    // len+2 - 2*len+2 - initial values, one per field
    public Node size() { return in(1); }

    @Override
    public TypeTuple compute() {
        Field[] fs = _ptr._obj._fields;
        Type[] ts = new Type[fs.length+2];
        ts[0] = Type.CONTROL;
        ts[1] = _ptr;
        for( int i=0; i<fs.length; i++ ) {
            if( _ptr._obj._fields[i]._one ) {
                // Once-only fields use the declared type
                ts[i+2] = _ptr._obj._fields[i]._t;
            } else {
                // Others take from the inputs
                Type mt = in(i+2)._type;
                TypeMem mem = mt==Type.TOP ? TypeMem.TOP : (TypeMem)mt;
                Type tfld = mem._t.meet(mem._t.makeZero());
                Type tfld2 = tfld.join(fs[i]._t );
                ts[i+2] = TypeMem.make(fs[i]._alias,tfld2);
            }
        }
        return TypeTuple.make(ts);
    }

    @Override
    public Node idealize() {
        Node progress=null;
        // Skip memory casts
        for( int i=1; i<nIns(); i++ ) {
            if( in(i) instanceof CastNode cast )
                progress = setDef(i,cast.in(1));
        }

        // Skip MemMerge
        for( int i=2; i<nIns(); i++ ) {
            if( in( i ) instanceof MemMergeNode mem ) {
                int alias = _ptr._obj._fields[i-2]._alias;
                progress = setDef(i,mem.alias(alias));
            }
        }

        return progress == null ? null : this;
    }

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
    public RegMask regmap(int i) { return i==1 ? _arg3Mask : null; }
    public RegMask outregmap() { return null; }
    public RegMask outregmap(int idx) { return idx==1  ? _retMask : null; }
    public RegMask killmap() { return _kills; }
    public void asm(CodeGen code, SB sb) {
        sb.p("#calloc, ").p(code.reg(size()));
    }
}
