package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.Serialize;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeMem;
import com.seaofnodes.simple.type.TypeTuple;
import com.seaofnodes.simple.util.BAOS;
import java.util.BitSet;
import java.util.HashMap;

public class ProjNode extends Node implements Proj {

    // Which slice of the incoming multipart value
    public final int _idx;

    // Debugging label
    public final String _label;

    public ProjNode(Node ctrl, int idx, String label) {
        super(new Node[]{ctrl});
        _idx = idx;
        _label = label;
    }
    public ProjNode(ProjNode p) { super(p); _idx = p._idx; _label = p._label; }
    @Override public Tag serialTag() { return Tag.Proj; }
    @Override public void packed(BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, HashMap<Integer,Integer> aliases) {
        baos.packed1(_idx);
        baos.packed2(_label==null ? 0 : strs.get(_label));
    }
    static Node make( BAOS bais, String[] strs )  {
        return new ProjNode(null, bais.packed1(), strs[bais.packed2()] );
    }

    @Override public String label() { return _label; }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        if( _label != null )  return sb.append(_label);
        if( in(0) instanceof CallEndNode cend && cend.call()!=null )
            return cend.call()._print0(sb,visited);
        return sb.append("LONELY PROJ");
    }

    @Override public CFGNode cfg0() {
        return in(0) instanceof CFGNode cfg ? cfg : in(0).cfg0();
    }

    @Override public boolean isMem() { return _type instanceof TypeMem; }
    @Override public boolean isPinned() { return true; }

    @Override
    public Type compute() {
        Type t = in(0)._type;
        return t instanceof TypeTuple tt ? tt._types[_idx] : Type.BOTTOM;
    }

    @Override public Node idealize() { return ((MultiNode)in(0)).pcopy(_idx); }

    @Override
    public boolean eq( Node n ) { return _idx == ((ProjNode)n)._idx; }

    @Override
    int hash() { return _idx; }

    @Override public int idx() { return _idx; }

    @Override public void gather( HashMap<String,Integer> strs ) {
        Serialize.gather(strs,_label);
    }
}
