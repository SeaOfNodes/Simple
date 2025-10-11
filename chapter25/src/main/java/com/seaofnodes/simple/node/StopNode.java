package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.util.Utils;
import java.util.BitSet;
import java.util.HashMap;

public class StopNode extends CFGNode {

    public final String _src;

    public StopNode(String src) {
        super();
        _src = src;
        _type = compute();
    }
    public StopNode(StopNode stop) { super(stop);  _src = stop==null ? null : stop._src; }
    @Override public Tag serialTag() { return Tag.Stop; }
    public void packed(BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, HashMap<Integer,Integer> aliases) { baos.packed1(nIns()); }
    static Node make( BAOS bais ) {
        StopNode stop = new StopNode((String)null);
        stop.setDefX(bais.packed1()-1,null);
        return stop;
    }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        // For the sake of many old tests, and single value prints as "return val"
        ReturnNode ret1 = ret(CodeGen.CODE);
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
    public ReturnNode ret(CodeGen code) {
        Node ret1 = this;
        for( Node ret : _inputs ) {
            FunNode fun = ((ReturnNode)ret).fun();
            if( fun.isPublic(code) )
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
