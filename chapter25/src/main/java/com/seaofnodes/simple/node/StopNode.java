package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.util.Utils;

import java.util.BitSet;
import java.util.HashMap;

// A collection of function ReturnNodes for a compilation unit.
public class StopNode extends CFGNode {

    public StopNode() {
        super();
        _type = compute();
    }
    public StopNode(StopNode stop) { super(stop); }
    @Override public Tag serialTag() { return Tag.Stop; }
    public void packed(BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, HashMap<Integer,Integer> aliases) { baos.packed1(nIns()); }
    static Node make( BAOS bais ) {
        StopNode stop = new StopNode();
        stop.setDefX(bais.packed1()-1,null);
        return stop;
    }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        // For the sake of many old tests, and single value prints as "return val"
        ReturnNode ret1 = ret();
        if( ret1!=null ) return ret1._print0(sb,visited);
        sb.append("Stop[ ");
        for( Node n : _inputs )
            if( n instanceof ReturnNode ret ) {
                String name = ret.fun()._name;
                if( name== null || !name.startsWith("sys.") )
                    ret._print0(sb, visited).append(" ");
            }
        return sb.append("]");
    }

    @Override public boolean blockHead() { return true; }


    // If a single Return, return it.
    // Otherwise, null because ambiguous.
    public ReturnNode ret() {
        ReturnNode ret1 = null;
        for( Node n : _inputs ) {
            if( n instanceof ReturnNode ret && ret.fun().isPublic() ) {
                if( ret1 != null ) return null; // Ambiguous
                ret1 = ret;
            }
        }
        return ret1;
    }

    @Override
    public Type compute() {
        // During Parsing, new Stops and Returns are being added.  This Stop
        // cannot know if more are coming, so must assume the worst.
        if( CodeGen.CODE._phase ==null || CodeGen.CODE._phase == CodeGen.Phase.Parse )
            return TypeTuple.make(Type.CONTROL,TypeMem.BOT,Type.BOTTOM,TypeFunPtr.BOT);
        // Just meet-over-inputs
        Type tt = Type.TOP;
        for( Node def : _inputs ) {
            tt = tt.meet(def._type);
            // Returns for <clinit> are always alive because program semantics, so
            // force their FIDX to escape
            if( def instanceof ReturnNode ret && ret.fun().isClz() )
                tt = ((TypeTuple)tt).meetFrom(3,ret.fun().sig());
        }
        return tt;
    }

    @Override
    public Node idealize() {
        int len = nIns();
        for( int i=0; i<nIns(); i++ )
            if( in(i) instanceof ReturnNode ret && addDep(ret.fun()).isDead() )
                delDef(i--);
        if( len != nIns() ) return this;
        return null;
    }

    @Override public int idepth() {
        if( _idepth!=0 ) return _idepth;
        int d=0;
        for( Node ret : _inputs )
            // All public results are hooked on the Stop, including data
            // values, so must check CFG
            if( ret instanceof CFGNode cfg )
                d = Math.max(d,cfg.idepth()+1);
        return _idepth=d;
    }
}
