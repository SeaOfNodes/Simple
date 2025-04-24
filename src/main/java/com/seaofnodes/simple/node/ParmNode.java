package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.type.Type;
import java.util.BitSet;

public class ParmNode extends PhiNode {

    // Argument indices are mapped one-to-one on CallNode inputs
    public final int _idx;             // Argument index

    public ParmNode(String label, int idx, Type declaredType, Node... inputs) {
        super(label,declaredType,inputs);
        _idx = idx;
    }
    public ParmNode(ParmNode parm) { super(parm, parm._label, parm._declaredType); _idx = parm._idx; }

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


    @Override
    public Node idealize() {
        if( inProgress() ) return null;
        return super.idealize();
    }

    // Always in-progress until we run out of unknown callers
    @Override public boolean inProgress() { return fun().inProgress(); }
}
