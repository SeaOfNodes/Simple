package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.util.Utils;
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
        ReturnNode ret1 = ret();
        if( ret1!=null ) return ret1._print0(sb,visited);
        sb.append("Stop[ ");
        for( Node ret : _inputs )
            if( ret!=null ) {
                String name = ((ReturnNode)ret).fun()._name;
                if( name== null || !name.startsWith("sys.") )
                    ret._print0(sb, visited).append(" ");
            }
        return sb.append("]");
    }

    @Override public boolean blockHead() { return true; }


    // If a single Return, return it.
    // Otherwise, null because ambiguous.
    public ReturnNode ret() {
        Node ret1 = this;
        for( Node ret : _inputs ) {
            String name = ((ReturnNode)ret).fun()._name;
            if( name==null || !name.startsWith("sys.") )
                ret1 = ret1==this ? ((ReturnNode)ret) : null;
        }
        return ret1==this ? null : (ReturnNode)ret1;
    }

    @Override
    public Type compute() {
        return Type.BOTTOM;
    }

    @Override
    public Node idealize() {
        int len = nIns();
        for( int i=0; i<nIns(); i++ )
            if( addDep(((ReturnNode)in(i)).fun()).isDead() )
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
