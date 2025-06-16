package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.util.BAOS;
import java.util.HashMap;
import java.util.BitSet;

public class ParmNode extends PhiNode {

    // Argument indices are mapped one-to-one on CallNode inputs
    public final int _idx;             // Argument index

    public ParmNode(String label, int idx, Type declaredType, Node... inputs) {
        super(label,declaredType,inputs);
        _idx = idx;
    }
    public ParmNode(ParmNode parm) { super(parm, parm._label, parm._minType ); _idx = parm._idx; }
    @Override public Tag serialTag() { return Tag.Parm; }
    @Override public void packed(BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, HashMap<Integer,Integer> aliases) {
        baos.packed1(nIns());
        baos.packed2(_label==null ? 0 : strs.get(_label));
        baos.packed2(types.get(_minType)); // NPE if fails lookup
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

    public FunNode fun() { return (FunNode)in(0); }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        if( "main".equals(fun()._name) && _label.equals("arg") )
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

    @Override public boolean eq( Node n ) {
        return ((ParmNode)n)._idx==_idx && super.eq(n);
    }
}
