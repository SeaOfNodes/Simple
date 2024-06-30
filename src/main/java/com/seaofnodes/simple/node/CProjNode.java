package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeMem;
import com.seaofnodes.simple.type.TypeTuple;
import java.util.BitSet;

public class CProjNode extends CFGNode {

    // Which slice of the incoming multipart value
    public final int _idx;

    // Debugging label
    public final String _label;

    public CProjNode(Node ctrl, int idx, String label) {
        super(ctrl);
        _idx = idx;
        _label = label;
    }

    @Override public String label() { return _label; }

    @Override StringBuilder _print1(StringBuilder sb, BitSet visited) { return sb.append(_label); }

    @Override public boolean isMultiTail() { return in(0).isMultiHead(); }
    @Override public boolean blockHead() { return true; }

    public CFGNode ctrl() { return cfg(0); }

    @Override
    public Type compute() {
        Type t = ctrl()._type;
        return t instanceof TypeTuple tt ? tt._types[_idx] : Type.BOTTOM;
    }

    @Override
    public Node idealize() {
        if ( ctrl() instanceof IfNode iff ) {
            if ( iff._type instanceof TypeTuple tt && tt._types[1 - _idx] == Type.XCONTROL )
                return iff.in(0); // We become our input control

            // Flip a negating if-test, to remove the not
            if( iff.pred().addDep(this) instanceof NotNode not )
                return new CProjNode(new IfNode(iff.ctrl(),not.in(1)).peephole(),1-_idx,_idx==0 ? "False" : "True");
        }
        return null;
    }

    @Override
    public Node getBlockStart() {
        return ctrl() instanceof IfNode ? this : ctrl().getBlockStart();
    }
    @Override
    boolean eq( Node n ) { return _idx == ((CProjNode)n)._idx; }

    @Override
    int hash() { return _idx; }

}
