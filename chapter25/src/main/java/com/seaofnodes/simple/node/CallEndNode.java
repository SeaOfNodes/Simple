package com.seaofnodes.simple.node;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.util.Utils;
import java.util.BitSet;
import java.util.HashMap;

/**
 *  CallEnd
 */
public class CallEndNode extends CFGNode implements MultiNode {

    // When set true, this Call/CallEnd/Fun/Return is being trivially inlined
    private boolean _folding;
    public final TypeRPC _rpc;

    public CallEndNode(CallNode call) { super(new Node[]{call}); _rpc = TypeRPC.constant(_nid); }
    public CallEndNode(CallEndNode cend) { super(cend); _rpc = cend._rpc; }
    @Override public Tag serialTag() { return Tag.CallEnd; }
    public void packed( BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, HashMap<Integer,Integer> aliases) {
        baos.packed1(nIns());
        // Linked CallEnds depend on Return types which depend on CallEnds;
        // break the cycle
        baos.packed2(types.get(_type));
    }
    static Node make( BAOS bais, Type[] types )  {
        Node cend = new CallEndNode((CallNode)null);
        cend.setDefX(bais.packed1()-1,null);
        cend._type = types[bais.packed2()];
        return cend;
    }

    @Override public boolean blockHead() { return true; }

    public CallNode call() { return (CallNode)in(0); }
    boolean folding() { return _folding; }

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
        if( !(in(0) instanceof CallNode call) || call._type != Type.CONTROL )
            return TypeTuple.RET.dual();
        // Mid-fold, just take the one single callers' return type
        if( _folding ) return in(1)._type;
        // Grab the TFP and use the functions declared return type.
        // If the call.fptr() is a FRef, return will be BOTTOM.
        Type ftype = addDep(call.fptr())._type;
        Type ret = ftype.isHigh() ? Type.TOP : Type.BOTTOM;
        if( ftype instanceof TypeFunPtr tfp ) {
            ret = tfp.ret();
            // Here, if I can figure out I've found *all* callers, then I can meet
            // across the linked returns and join with the function return type.
            if( 1+tfp.nfcns() == nIns() ) { // A linked function for every concrete function
                ret = Type.TOP;
                for( int i=1; i<nIns(); i++ ) {
                    Type tret = in(i)._type instanceof TypeTuple rtt ? rtt.ret() : in(i)._type;
                    ret = ret.meet(tret);
                }
                ret = ret.join(tfp.ret());
            }
        }
        return TypeTuple.make(call._type,TypeMem.BOT,ret);
    }

    @Override
    public Node idealize() {

        // Worklist-based inlining.  Cannot inline if folding, or calling
        // multiple targets or no CallNode (malformed because dying).
        if( !_folding && nIns()==2 && in(0) instanceof CallNode call ) {
            Node fptr = call.fptr();
            if( fptr.isConst() && // We have an immediate call
                // Function is being called, and its not-null
                fptr._type instanceof TypeFunPtr tfp && tfp.notNull() &&
                // Arguments are correct
                call.err()==null ) {
                ReturnNode ret = (ReturnNode)in(1);
                FunNode fun = ret.fun();
                int isTrivial = trivialInlining( fptr, fun );

                // Encouraged inlining because small size and constructor.
                int maxSize = fun._name!=null && fun.isInit() && !fun.isClz() ? 200 : 100;
                if( isTrivial==1 && fun._approxUIDs < maxSize ) {
                    assert fun.sig().isa(tfp);
                    assert !CodeGen.CODE._midAssert; // Triggered inlining
                    CodeGen.CODE.add(fun);
                    // Remove the existing function linkage
                    call.unlink_all();
                    // Clone the function body
                    FunNode fun2 = fun.copyBody();
                    // Call uses the unique new function
                    FunPtrNode fptr2 = new FunPtrNode(fun2.ret(),fun2.sig().fidx()).init();
                    call.setDef(call.nIns()-1,fptr2);
                    // Link to the new function
                    call.link(fun2);
                    assert trivialInlining( fptr2, fun2 )==0;
                    fun = fun2;
                    isTrivial = 0;
                }

                // Trivial inlining: call site calls a single function; single function
                // is only called by this call site.
                if( isTrivial==0 )
                    return doTrivialInlining(fun);

            } else { // Function not (yet) a constant function pointer
                addDep(fptr);
            }
        }

        return null;
    }

    // Check for trivial inlining: call only calls fun; fun only called by
    // call.  Returns 0 for trivial, +1 for not-trivial because fptr/fun, and
    // -1 for not-trivial because idoms.
    public int trivialInlining( Node fptr, FunNode fun ) {
        // Heuristic forced inlining off via name
        if( fun._name != null &&
            fun._name.endsWith("_noInline") )
            return -1;

        if( !fun.sig().isa(fptr._type) )
            return -1; // Stall until these align

        // Disallow self-recursive inlining (loop unrolling by another name).
        for( int i=1; i < fun.nIns(); i++ )
            if( fun.cfg(i).fun()==fun ) // Check for linked call input inside "fun"
                { addDep(fun); return -1; }

        CFGNode idom = call(), prior = this;
        while( !(idom instanceof FunNode fun3) ) {
            if( idom==null ) {
                addDep(prior);
                if( prior instanceof RegionNode && !(prior instanceof StartNode) )
                    for( int i=1; i<prior.nIns(); i++ )
                        addDep(prior.in(i));
                return -1;
            }
            prior = idom;
            idom = idom.idom();
        }


        // If the *inlined* function is mid-collapse, also do not inline (yet)
        idom = fun.ret();
        while( idom != fun ) {
            if( idom==null ) return -1; // Forced off, half-folded call
            if( idom instanceof FunNode fun2 )
                { addDep(fun2); return -1; }
            if( idom instanceof CallEndNode cend && cend._folding )
                { addDep(cend); return -1; }
            idom = idom.idom();
        }

        // Expecting just the Call
        if( fptr.nOuts() > 1 ) {
            addDep(fptr);
            for( Node out : fptr._outputs )
                if( out instanceof ScopeNode )
                    return -1;  // Wait for these to disappear on their own
            return 1;
        }
        // Only fun user is this call
        if( fun.nIns() > 2 ) { addDep(fun); return 1; }
        // Trivial inlining: call site calls a single function; single function
        // is only called by this call site.
        assert fun.in(1)==call();

        return 0;
    }


    // Do trivial inlining.  Inlining does not need to clone code, merely
    // triggers folding the Call/Fun and Return/CallEnd away.
    private Node doTrivialInlining( FunNode fun ) {
        // Add the size heuristic to grow caller, preventing self-recursive
        // functions from endlessly inlining.
        fun()._approxUIDs += fun._approxUIDs;
        // Trivial inline: rewrite
        _folding = true;
        // Rewrite Fun so the normal RegionNode ideal collapses
        fun._folding = true;
        fun.setDef(1,call().ctrl());// Bypass the Call;
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


    @Override public Node pcopy(int idx) {
        return _folding ? in(1).in(idx) : null;
    }
}
