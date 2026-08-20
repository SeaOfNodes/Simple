package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.BAOS;

import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;

/**
 *  CallEnd
 */
public class CallEndNode extends CFGNode implements MultiNode {

    // When set true, this Call/CallEnd/Fun/Return is being trivially inlined
    boolean _folding;
    // +2 must clone, so await all cleanup but OK
    // +1 trivial now,
    //  0: take a look,
    // -1: somethings wrong, but might improve
    // -2: never and stop asking,
    private byte _inline;
    public final TypeRPC _rpc;

    public CallEndNode(CallNode call, TypeRPC rpc) {
        super(new Node[]{call});
        _rpc = rpc;
    }
    public CallEndNode(CallEndNode cend) { super(cend); _rpc = cend._rpc; }
    public CallEndNode(TypeRPC rpc) {
        super(new Node[0]);
        _rpc = TypeRPC.constant(CodeGen.CODE._rpcs.next(rpc.rpc()));
    }
    private CallEndNode(int nIns, TypeRPC rpc) {
        super(new Node[nIns]);
        _rpc = rpc;
    }
    @Override public Tag serialTag() { return Tag.CallEnd; }
    public void packed( BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, IdentityHashMap<Node, Integer> anodes ) {
        baos.packed1(nIns());
        // Linked CallEnds depend on Return types which depend on CallEnds;
        // break the cycle
        baos.packed2(types.get(_type));
        baos.packed2(types.get(_rpc));
    }
    static Node make( BAOS bais, Type[] types )  {
        int nIns = bais.packed1();
        Type type = types[bais.packed2()];
        Node cend = new CallEndNode(nIns,(TypeRPC)types[bais.packed2()]);
        cend._type = type;
        return cend;
    }

    @Override public boolean blockHead() { return true; }

    public CallNode call() { return (CallNode)in(0); }
    boolean folding() { return _folding; }
    public byte inline() { return _inline; }

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
            return TypeTuple.STATE.dual();
        // Grab the TFP and use the functions declared return type.
        // If the call.fptr() is a FRef, return will be BOTTOM.
        Type ftype = addDep(call.fptr())._type;
        if( !(ftype instanceof TypeFunPtr tfp) )
            return ftype.isHigh() ? TypeTuple.STATE.dual() : TypeTuple.STATE;
        // Mid-fold, just take the one single callers' return type
        if( _folding ) {
            TypeTuple tt = (TypeTuple)in(1)._type;
            return tt.makeFrom(2,tt.ret().join(tfp._ret));
        }

        // Here, if I can figure out I've linked *all* callers, then I can meet
        // across the linked returns and join with the function return type.

        // External functions have an exact FIDX but deliberately no local
        // FunNode/Return edge.  They conservatively crush public memory and
        // produce their declared return type.  A missing non-external target
        // is still an unresolved/error call.
        int externs = 0;
        if( !XInt.isHigh(tfp.fidxs()) )
            for( int fidx = XInt.next(tfp.fidxs(),0); fidx >= 0; fidx = XInt.next(tfp.fidxs(),fidx) )
                if( CodeGen.CODE.externFunc(fidx)!=null )
                    externs++;
        boolean hasExtern = externs != 0;

        // If we have an error-call, e.g. wrong args, then we never link.
        // Pre-Opto this looks like a missing target fcn, and we assume it will
        // appear later - meanwhile, we use a conservative approx of memory
        // effects.
        if( (nIns()-1)+externs < tfp.nfcns() ) {
            // If during Opto, assume call won't be called and thus won't return anything.
            boolean opto = CodeGen.CODE._phase.ordinal() >= CodeGen.Phase.Opto.ordinal();
            Type tmem = opto ? TypeMem.TOP : TypeMem.BOT;
            Type ret  = opto ? Type.TOP    : tfp.ret();
            return TypeTuple.make(Type.CONTROL,tmem,ret);
        }

        // A linked function for every concrete function
        TypeTuple state = hasExtern
            ? TypeTuple.make(Type.CONTROL,TypeMem.BOT,tfp.ret())
            : TypeTuple.STATE.dual();
        for( int i=1; i<nIns(); i++ )
            state = (TypeTuple)state.meet(in(i)._type);
        // A reachable call whose presently linked exits are all unreachable
        // keeps a conservative continuation.  In particular, do not let a
        // transient no-return result tear down the caller during SCCP.
        if( state.ctl()==Type.XCONTROL &&
            CodeGen.CODE._phase.ordinal() >= CodeGen.Phase.Opto.ordinal() )
            return TypeTuple.make(Type.CONTROL,TypeMem.TOP,Type.TOP);
        // At least as good as the TFP
        return state.makeFrom(2,state.ret().join(tfp.ret()));
    }

    @Override
    public Node idealize() {

        _inline = inlineCandidate();
        if( _inline == 1 ) {
            ReturnNode ret = (ReturnNode)in(1);
            FunNode fun = ret.fun();
            doTrivialInlining(fun);
            return this;
        }
        // Delay for cleanup, before cloning
        if( _inline >= 2 )
            CodeGen.CODE._iter.deferInline(CodeGen.CODE,this);

        return null;
    }

    // Check if candidate right now, sets no flags
    private byte inlineCandidate() {
        if( _inline < -2 ) return -2; // Never
        if( _folding ) return -2;     // No, and will collapse anyways
        if( !(in(0) instanceof CallNode call) ) return -2; // malformed because mid-death

        if( nIns()!=2 ) return -1;    // Not exactly 1 link, but can change

        // Have exactly 1 linked function
        ReturnNode ret = (ReturnNode)in(1);
        FunNode fun = ret.fun();

        // Heuristic forced inlining off via name
        if( fun._name != null &&
            fun._name.endsWith("_noInline") )
            return -2;          // Never, stop asking

        // Disallow self-recursive inlining (loop unrolling by another name).
        for( int i=1; i < fun.nIns(); i++ )
            if( fun.cfg(i).fun()==fun ) // Check for linked call input inside "fun"
                { addDep(fun); return -1; } // No, but can lose the recursive edge

        // Things that might improve, so not now a candidate
        Node fptr = call.fptr();
        // Constant Function is being called, and its not-null
        if( !(fptr._type instanceof TypeFunPtr tfp && tfp.notNull() && tfp.isConstant()) )
            { addDep(fptr); return -1; }

        // Arguments are correct
        if( call.nargs()!=tfp.nargs() || !goodArgs(call,tfp) )
            { addDep(fptr); return -1; }

        // Encouraged inlining because small size and constructor.
        int maxSize = fun._name!=null && fun.isInit() && !fun.isClz() ? 200 : 100;
        if( fun._approxUIDs >= maxSize )
            return -2;          // Try later (if shrinking helps)
        if( fun.body().cardinality() >= maxSize )
            return -1;          // Try later (if cleanup makes the real body small enough)

        // Can be cloned but not trivial
        if( fun.nIns() > 2 ) { addDep(fun); return 2; }

        // A candidate right now
        return 1;
    }

    public void doInline() {
        assert _inline > 0;    // Only called when possible to inline

        ReturnNode ret = (ReturnNode)in(1);
        FunNode fun = ret.fun();
        CallNode call = call();
        assert fun.sig().isa(call.fptr()._type);

        // Clone first
        if( _inline==2 ) {
            assert !CodeGen.CODE._midAssert; // Triggered inlining
            // Remove the existing function linkage
            call.unlink_all();
            // Clone the function body
            FunNode fun2 = fun.copyBody();
            // Call uses the unique new function
            Node fptr2 = new FunPtrNode(fun2.sig(),CodeGen.CODE._start,fun2.ret()).peephole();
            call.setDef(call.nIns()-1,fptr2);
            // Link to the new function
            call.link(fun2);
            fun = fun2;
        }

        // Trivial inlining: call site calls a single function; single function
        // is only called by this call site.
        doTrivialInlining(fun);
    }

    boolean goodArgs( CallNode call, TypeFunPtr tfp ) {
        for( int i=0; i<tfp.nargs(); i++ ) {
            Node arg = call.arg(i+2);
            // The graph's function-pointer type can still contain open forward
            // references here.  Compare against its recursively upgraded form,
            // so deep mutability requirements participate before inlining.
            Type formal = tfp.arg(i).upgradeType(Parser.TYPES);
            if( !arg._type.isa(formal) )
                { addDep(arg); return  false; }
        }
        return true;
    }


    // Do trivial inlining.  Inlining does not need to clone code, merely
    // triggers folding the Call/Fun and Return/CallEnd away.
    private boolean doTrivialInlining( FunNode fun ) {
        assert !CodeGen.CODE._midAssert; // Triggered inlining
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
        // More peeps
        CodeGen.CODE.add(fun);
        CodeGen.CODE.add(this);
        CodeGen.CODE.addAll(_outputs);
        CodeGen.CODE.addAll(_inputs);
        return true;            // Always return true
    }


    @Override public Node pcopy(int idx) {
        return _folding ? in(1).in(idx) : null;
    }

    @Override public Node copy() {
        return _folding ? super.copy() : new CallEndNode(_rpc);
    }

}
