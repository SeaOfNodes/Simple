package com.seaofnodes.simple.node;

import com.seaofnodes.simple.IterPeeps;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.type.*;
import java.util.BitSet;

/**
 *  CallEnd
 */
public class CallEndNode extends CFGNode implements MultiNode {

    // When set true, this Call/CallEnd/Fun/Return is being trivially inlined
    private boolean _folding;

    public CallEndNode(CallNode call) { super(call); }

    @Override
    public String label() { return "CallEnd"; }
    @Override public boolean isMultiHead() { return true; }
    @Override public boolean blockHead() { return true; }

    CallNode call() { return (CallNode)in(0); }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        sb.append("cend( ");
        if( call()==null ) sb.append("---, ");
        else sb.append("Call, ");
        for( int i=1; i<nIns()-1; i++ )
            in(i)._print0(sb,visited).append(",");
        sb.setLength(sb.length()-1);
        return sb.append(")");
    }

    @Override
    public Type compute() {
        Type ret = call().fptr()._type instanceof TypeFunPtr tfp ? tfp.ret() : Type.BOTTOM;
        return TypeTuple.make(call()._type,TypeMem.BOT,ret);
    }

    @Override
    public Node idealize() {

        // Trivial inlining: call site calls a single function; single function
        // is only called by this call site.
        if( !_folding && nIns()==2 ) {
            CallNode call = call();
            Node fptr = call.fptr();
            if( fptr.nOuts() == 1 ) { // Only user is this call
                ReturnNode ret = (ReturnNode)in(1);
                FunNode fun = ret.fun();
                assert fun.nIns()==3; // Expecting Start, and the Call
                assert fun.in(1) instanceof StartNode && fun.in(2)==call;

                // Trivial inline: rewrite
                _folding = true;
                // Rewrite Fun so the normal RegionNode ideal collapses
                fun._folding = true;
                fun.setDef(1,Parser.XCTRL); // No default/unknown StartNode caller
                fun.setDef(2,call.ctrl());  // Bypass the Call;
                fun.ret().setDef(3,null);   // Return is folding also
                IterPeeps.addAll(fun._outputs);
                return this;
            } else { // Function ptr has multiple users (so maybe multiple call sites)
                fptr.addDep(this);
            }
        }

        return null;
    }

    @Override public Node pcopy(int idx) {
        return _folding ? in(1).in(idx) : null;
    }
}
