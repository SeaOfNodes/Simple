package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.type.Type;
import java.util.BitSet;

public class StopNode extends CFGNode {

    public final String _src;

    public StopNode(String src) {
        super();
        _src = src;
        _type = compute();
    }

    @Override
    public String label() {
        return "Stop";
    }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        // For the sake of many old tests, and single value prints as "return val"
        if( ret()!=null ) return ret()._print0(sb,visited);
        sb.append("Stop[ ");
        for( Node ret : _inputs )
            ret._print0(sb, visited).append(" ");
        return sb.append("]");
    }

    @Override public boolean blockHead() { return true; }


    // If a single Return, return it.
    // Otherwise, null because ambiguous.
    public ReturnNode ret() {
        return nIns()==1 && in(0) instanceof ReturnNode ret ? ret : null;
    }

    @Override
    public Type compute() {
        return Type.BOTTOM;
    }

    @Override
    public Node idealize() {
        int len = nIns();
        for( int i=0; i<nIns(); i++ )
            if( ((ReturnNode)in(i)).fun()==null )
                delDef(i--);
        if( len != nIns() ) return this;
        return null;
    }

    @Override public int idepth() {
        if( _idepth!=0 ) return _idepth;
        int d=0;
        for( Node ret : _inputs )
            d = Math.max(d,((ReturnNode)ret).idepth()+1);
        return _idepth=d;
    }

    // Add a new function exit point.  Validates Parsers function borders, then
    // hands off to the guaranteed return.
    public void addReturn(Node ctrl, Node mem, Node rez, FunNode fun) {
        ReturnNode ret = (ReturnNode)_inputs.last();
        assert ret.fun()==fun && ret.inProgress();
        ret.addReturn(ctrl,mem,rez);
    }
}
