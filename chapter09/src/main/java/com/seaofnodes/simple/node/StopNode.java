package com.seaofnodes.simple.node;

import com.seaofnodes.simple.IterOptim2;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.IterPeeps;

import java.util.BitSet;

public class StopNode extends Node {

    public final String _src;
    
    public StopNode(String src) {
        super();
        _src = src;
    }

    @Override
    public String label() {
        return "Stop";
    }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        if( ret()!=null ) return ret()._print0(sb, visited);
        sb.append("Stop[ ");
        for( Node ret : _inputs )
            ret._print0(sb, visited).append(" ");
        return sb.append("]");
    }

    @Override public boolean isCFG() { return true; }

    // If a single Return, return it.
    // Otherwise, null because ambiguous.
    public ReturnNode ret() {
        return nIns()==1 ? (ReturnNode)in(0) : null;
    }
    
    @Override
    public Type compute() {
        return Type.BOTTOM;
    }

    @Override
    public Node idealize() {
        int len = nIns();
        for( int i=0; i<nIns(); i++ )
            if( in(i)._type==Type.XCONTROL )
                delDef(i--);
        if( len != nIns() ) return this;
        return null;
    }

    public Node addReturn(Node node) {
        return addDef(node);
    }

    public StopNode iterate(            ) {
        if (Node._disableDeps) return new IterOptim2().iterate(this, false);
        else                   return IterPeeps.iterate(this,false);
    }
    public StopNode iterate(boolean show) {
        if (Node._disableDeps) return new IterOptim2().iterate(this, show);
        else                   return IterPeeps.iterate(this,show);
    }
}
