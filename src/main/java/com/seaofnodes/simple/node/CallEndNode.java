package com.seaofnodes.simple.node;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.type.*;
import java.util.BitSet;

/**
 *  CallEnd
 */
public class CallEndNode extends CFGNode implements MultiNode {

    // When set true, this Call/CallEnd/Fun/Return is being trivially inlined
    private boolean _folding;
    public final TypeRPC _rpc;

    public CallEndNode(CallNode call) { super(new Node[]{call}); _rpc = TypeRPC.constant(_nid); }
    public CallEndNode(CallEndNode cend) { super(cend); _rpc = cend._rpc; }

    @Override public String label() { return "CallEnd"; }
    @Override public boolean blockHead() { return true; }

    public CallNode call() { return (CallNode)in(0); }

    @Override public CFGNode idom(Node dep) {
        // Folding the idom is the one inlining Return
        return _folding ? cfg(1) : super.idom(dep);
    }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        sb.append("cend( ");
        sb.append( in(0) instanceof CallNode ? "Call, " : "----, ");
        for( int i=1; i<nIns()-1; i++ )
            in(i)._print0(sb,visited).append(",");
        sb.setLength(sb.length()-1);
        return sb.append(")");
    }

    @Override
    public Type compute() {
        if( !(in(0) instanceof CallNode call) )
            return TypeTuple.RET.dual();
        Type ret = Type.BOTTOM;
        if( addDep(call.fptr())._type instanceof TypeFunPtr tfp ) {
            ret = tfp.ret();
            // Here, if I can figure out I've found *all* callers, then I can meet
            // across the linked returns and join with the function return type.
            if( tfp.isConstant() && nIns()>1 ) {
                assert nIns()==2;     // Linked exactly once for a constant
                ret = ((TypeTuple)in(1)._type).ret(); // Return type
            }
        }
        return TypeTuple.make(call._type,TypeMem.BOT,ret);
    }

    @Override
    public Node idealize() {

        // Trivial inlining: call site calls a single function; single function
        // is only called by this call site.
        if( !_folding && nIns()==2 && in(0) instanceof CallNode call ) {
            Node fptr = call.fptr();
            if( fptr.nOuts() == 1 && // Only user is this call
                fptr instanceof ConstantNode && // We have an immediate call
                // Function is being called, and its not-null
                fptr._type instanceof TypeFunPtr tfp && tfp.notNull() &&
                // Arguments are correct
                call.err()==null ) {
                ReturnNode ret = (ReturnNode)in(1);
                FunNode fun = ret.fun();
                // Expecting Start, and the Call
                if( fun.nIns()==3 ) {
                    assert fun.in(1) instanceof StartNode && fun.in(2)==call;
                    // Disallow self-recursive inlining (loop unrolling by another name)
                    CFGNode idom = call;
                    while( !(idom instanceof FunNode) && idom!=null )
                        idom = idom.idom();
                    // Inline?
                    if( idom != fun && idom != null ) {
                        // Trivial inline: rewrite
                        _folding = true;
                        // Rewrite Fun so the normal RegionNode ideal collapses
                        fun._folding = true;
                        fun.setDef(1,Parser.XCTRL); // No default/unknown StartNode caller
                        fun.setDef(2,call.ctrl());  // Bypass the Call;
                        fun.ret().setDef(3,null);   // Return is folding also
                        CodeGen.CODE.addAll(fun._outputs);
                        // Repeat defs 1 layer down, for users of Parm (Phis)
                        for( Node parm : fun._outputs )
                            if( parm instanceof ParmNode )
                                CodeGen.CODE.addAll(parm._outputs);

                        // Inlining immediately blows all cache idepth fields past the inline point.
                        // Bump the global version number invalidating them en-masse.
                        CodeGen.CODE.invalidateIDepthCaches();
                        return this;
                    }
                } else {
                    addDep(fun);
                }
            } else { // Function ptr has multiple users (so maybe multiple call sites)
                addDep(fptr);
            }
        }

        return null;
    }

    @Override public Node pcopy(int idx) {
        return _folding ? in(1).in(idx) : null;
    }
}
