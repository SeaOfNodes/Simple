package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeMemPtr;
import java.util.BitSet;

public class ParmNode extends PhiNode {

    // Argument indices are mapped one-to-one on CallNode inputs
    public final int _idx;             // Argument index

    public ParmNode(String label, int idx, Type declaredType, Node... inputs) {
        super(label,declaredType,inputs);
        _idx = idx;
    }
    public ParmNode(ParmNode parm) { super(parm, parm._label, parm._minType ); _idx = parm._idx; }

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

    @Override
    public Node idealize() {
        if( !(region() instanceof FunNode) )
            return in(1);       // Input has collapse to e.g. starting control.
        // If function is folding, do all possible peeps
        if( fun()._folding ) return super.idealize();

        // Can upgrade minType even while in-progress
        if( _minType instanceof TypeMemPtr tmp && _minType.isFRef() ) {
            TypeMemPtr tmp2 = (TypeMemPtr) Parser.TYPES.get(tmp._obj._name);
            if( tmp2!=null && tmp2 != _minType ) {
                _minType = tmp2;
                return this;
            }
        }
        // Skip most phi optimizations on parms
        return null;
    }

    @Override public boolean eq( Node n ) {
        return ((ParmNode)n)._idx==_idx && super.eq(n);
    }
}
