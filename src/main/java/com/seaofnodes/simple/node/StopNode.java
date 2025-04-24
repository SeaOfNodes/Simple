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
    public StopNode(StopNode stop) { super(stop);  _src = stop==null ? null : stop._src; }

    @Override
    public String label() {
        return "Stop";
    }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        // For the sake of many old tests, and single value prints as "return val"
        if( ret()!=null ) return ret()._print0(sb,visited);
        sb.append("Stop[ ");
        for( Node ret : _inputs )
            if( ret!=null )
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
            if( ((ReturnNode)in(i)).fun().isDead() )
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
}
