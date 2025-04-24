package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeMem;
import com.seaofnodes.simple.type.TypeTuple;
import java.util.BitSet;

public class CProjNode extends CFGNode {

    // Which slice of the incoming multipart value
    public int _idx;

    // Debugging label
    public String _label;

    public CProjNode(Node ctrl, int idx, String label) {
        super(ctrl);
        _idx = idx;
        _label = label;
    }
    public CProjNode(CProjNode c) { super(c); _idx = c._idx; _label = c._label; }

    @Override public String label() { return _label; }

    @Override public StringBuilder _print1(StringBuilder sb, BitSet visited) { return sb.append(_label); }

    @Override public boolean blockHead() { return true; }

    public CFGNode ctrl() { return cfg(0); }

    @Override
    public Type compute() {
        Type t = ctrl()._type;
        return t instanceof TypeTuple tt ? tt._types[_idx] : Type.BOTTOM;
    }

    @Override
    public Node idealize() {
        if( ctrl()._type instanceof TypeTuple tt ) {
            if( tt._types[_idx]==Type.XCONTROL )
                return Parser.XCTRL; // We are dead
            if( ctrl() instanceof IfNode && tt._types[1-_idx]==Type.XCONTROL ) // Only true for IfNodes
                return ctrl().in(0);               // We become our input control
        }

        // Flip a negating if-test, to remove the not
        if( ctrl() instanceof IfNode iff && addDep(iff.pred()) instanceof NotNode not )
            return new CProjNode(new IfNode(iff.ctrl(),not.in(1)).peephole(),1-_idx,_idx==0 ? "False" : "True");

        // Copy of some other input
        return ((MultiNode)ctrl()).pcopy(_idx);
    }

    // Only called during basic-block layout, inverts a T/F CProj
    public void invert() {
        _label = _idx == 0 ? "False" : "True";
        _idx = 1-_idx;
    }

    @Override
    public boolean eq( Node n ) { return _idx == ((CProjNode)n)._idx; }

    @Override
    int hash() { return _idx; }

}
