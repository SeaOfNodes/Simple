package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.BAOS;

import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;

public class ParmNode extends PhiNode {

    // Argument indices are mapped one-to-one on CallNode inputs
    public final int _idx;             // Argument index
    public Type _declaredType;

    public ParmNode(String label, int idx, Type declaredType, Node... inputs) {
        super(label, inputs);
        _idx = idx;
        _declaredType = declaredType;
        _type = declaredType;
    }
    public ParmNode(ParmNode parm) { super(parm, parm._label ); _idx = parm._idx; _declaredType = parm._declaredType; }
    @Override public Tag serialTag() { return Tag.Parm; }
    @Override public void packed( BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, IdentityHashMap<Node, Integer> anodes ) {
        baos.packed1(nIns());   // Number of linked calls
        baos.packed2(_label==null ? 0 : strs.get(_label));
        baos.packed2(types.get(_declaredType)); // NPE if fails lookup
        baos.packed1(_idx);
    }
    static Node make( BAOS bais, String[] strs, Type[] types)  {
        Node[] ins = new Node[bais.packed1()];
        String label =   strs[bais.packed2()];
        Type minType =  types[bais.packed2()];
        return new ParmNode(label, bais.packed1(), minType, ins);
    }

    @Override public String label() { return MemOpNode.mlabel(_label); }

    @Override public String glabel() { return _label; }

    @Override boolean _upgradeType( HashMap<String,Type> TYPES) {
        Type t = _declaredType.upgradeType(TYPES);
        if( t == _declaredType ) return false;
        unlock();
        _declaredType = t;
        return true;
    }

    public FunNode fun() { return (FunNode)in(0); }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        if( fun()._name!=null && fun()._name.endsWith("<clinit>") && _label.equals("arg") )
            return sb.append("arg");
        sb.append("Parm_").append(_label).append("(");
        for( Node in : _inputs ) {
            if (in == null) sb.append("____");
            else in._print0(sb, visited);
            sb.append(",");
        }
        sb.setLength(sb.length()-1);
        sb.append(")");
        return sb;
    }


    // Always in-progress until we run out of unknown callers
    @Override public boolean inProgress() { return in(0) instanceof FunNode fun && fun.inProgress(); }

    @Override
    public Type compute() {
        if( !(region() instanceof RegionNode r) )
            return region()._type==Type.XCONTROL || region()._type==Type.TOP ? (_type instanceof TypeMem ? TypeMem.TOP : Type.TOP) : _type;
        // During parsing Phis have to be computed type pessimistically.
        if( r.inProgress() || in(nIns()-1)==null )
            return _declaredType;
        // Set type to local top of the starting type
        Type t = Type.TOP;
        for( int i = 1; i < nIns(); i++ ) {
            // If the region's control input is live, add this as a dependency
            // to the control because we can be peeped should it become dead.
            Type ctrl = addDep(r.in(i))._type;
            if( ctrl != Type.XCONTROL && ctrl != Type.TOP ) {
                if( in(i)._type==Type.BOTTOM )
                    return Type.BOTTOM;
                t = t.meet(in(i)._type);
            }
        }
        return t.join(_declaredType);
    }

    @Override
    public Node idealize() {
        if( !(region() instanceof FunNode) )
            // A dead function can lose every caller before the Parm itself is
            // removed; there is no replacement input in that transient form.
            return nIns()==1 ? null : in(1); // Input has collapse to e.g. starting control.
        // If function is folding, do all possible peeps
        if( fun()._folding ) return super.idealize();

        // Skip most phi optimizations on parms
        return null;
    }

    @Override public boolean eq( Node n ) {
        return ((ParmNode)n)._idx==_idx && super.eq(n);
    }
}
