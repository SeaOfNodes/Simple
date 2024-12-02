package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.IterPeeps;
import com.seaofnodes.simple.GlobalCodeMotion;
import com.seaofnodes.simple.GraphVisualizer;

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
        if( ret()!=null ) return ret()._print0(sb, visited);
        sb.append("Stop[ ");
        for( Node ret : _inputs )
            ret._print0(sb, visited).append(" ");
        return sb.append("]");
    }

    @Override public boolean blockHead() { return true; }

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

    @Override public int idepth() {
        if( _idepth!=0 ) return _idepth;
        int d=0;
        for( Node n : _inputs )
            if( n!=null )
                d = Math.max(d,((CFGNode)n).idepth()+1);
        return _idepth=d;
    }
    @Override public CFGNode idom(Node dep) { return null; }

    public Node addReturn(Node node) {
        return addDef(node);
    }

    public StopNode iterate(            ) { return IterPeeps.iterate(this).typeCheck().GCM(false); }
    public StopNode iterate(boolean show) { return IterPeeps.iterate(this).typeCheck().GCM(show ); }
    StopNode typeCheck() {
        String err = walk( Node::err );
        if( err != null ) throw new RuntimeException(err);
        return this;
    }
    StopNode GCM(boolean show) {
        Parser.START.buildLoopTree(this);
        if( show )
            System.out.println(new GraphVisualizer().generateDotOutput(this,null,null));
        GlobalCodeMotion.buildCFG(this);
        return this;
    }
}
