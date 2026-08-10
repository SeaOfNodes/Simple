package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.BAOS;

import java.util.HashMap;
import java.util.IdentityHashMap;

/**
 *  CallEnd
 */
public class CallEndNode extends CFGNode implements MultiNode {

    // When set true, this Call/CallEnd/Fun/Return is being trivially inlined
    boolean _folding;
    // +2: must clone, so await all cleanup but OK
    // +1: trivial now, so can inline without cloning and without waiting for cleanup
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
        baos.packed1(Serialize.nIns(this,anodes));
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
    public boolean folding() { return _folding; }

    @Override public CFGNode idom(Node dep) {
        // Folding the idom is the one inlining Return
        return _folding ? cfg(1) : super.idom(dep);
    }

    @Override
    protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        p.p("cend( ");
        p.p( in(0) instanceof CallNode ? "Call, " : "----, ");
        for( int i=1; i<nIns()-1; i++ )
            p.n(in(i)).p(",");
        return p.unchar(',').unchar(' ').close();
    }

    @Override
    public Type compute() {
        // Something is dead or busted, early exit with high/dead result
        if( !(in(0) instanceof CallNode call) || call._type != Type.CONTROL )
            return TypeTuple.STATE.dual();
        // The function pointer carries a result type even without a body.
        Type ftype = addDep(call.fptr())._type;
        // Something is dead or busted, early exit with high/dead result
        if( !(ftype instanceof TypeFunPtr tfp) )
            return ftype.isHigh() ? TypeTuple.STATE.dual() : TypeTuple.STATE;
        // Mid-fold, just take the inlined callee's return.
        if( _folding ) return in(1)._type;

        // Unknown targets can change any memory and return the declared type.
        if( XInt.isHigh(tfp.fidxs()) )
            return TypeTuple.make(Type.CONTROL,TypeMem.BOT,tfp.ret());

        // Check for making a "C" call
        int externs = 0;
        for( int fidx = XInt.next(tfp.fidxs(),0); fidx >= 0; fidx = XInt.next(tfp.fidxs(),fidx) )
            if( CodeGen.CODE.externFunc(fidx)!=null ) externs++;
        if( externs!=0 ) {
            // C calls have no Return edges. Simple calls have an extra self
            // argument, so the two cannot be targets of the same valid call.
            assert externs==tfp.nfcns();
            return TypeTuple.make(Type.CONTROL,call.exposed(this),tfp.ret());
        }

        // An unlinked target may have effects, so early conservative return.
        // This also covers invalid calls (e.g. wrong arity), which never link.
        // SCCP links valid finite targets as soon as they become known, before
        // computing their CallEnd, so this check does not trigger during SCCP.
        if( nIns()-1 < tfp.nfcns() )
            return TypeTuple.make(Type.CONTROL,TypeMem.BOT,tfp.ret());

        // Start at a high tuple (matching Returns' tuple)
        TypeTuple state = TypeTuple.STATE.dual();
        // Meet over all Returns
        for( int i=1; i<nIns(); i++ )
            state = (TypeTuple)state.meet(in(i)._type);
        // Keep a live continuation even before any callee exit is reachable.
        // Otherwise a transient no-return result can tear down the caller
        // and lose class-init dependencies before SCCP finds those exits.
        if( state.ctl()==Type.XCONTROL )
            return TypeTuple.make(Type.CONTROL,TypeMem.TOP,Type.TOP);
        // Final JOIN over the signature guarentee and meet-over-returns
        return state.makeFrom(2,state.ret().join(tfp.ret()));
    }

    @Override
    public Node idealize() {
        return null;
    }

    public byte maybeInline() {
        byte inline = inlineCandidate(false);
        if( CodeGen.CODE._midAssert )
            return inline;
        _inline = inline;
        if( _inline > 0 )
            doInline();

        return _inline;
    }

    // This CallEnd might be an inline candidate, but the actual code is in
    // another CompUnit and needs loading to decide. Called at an empty worklist.
    public boolean loadForInline() {
        CodeGen code = CodeGen.CODE;
        if( code._phase!=CodeGen.Phase.IterPostOpto || nIns()!=2 ||
            !(in(1) instanceof ReturnNode ret) || ret.fun().summary()==null ) return false;
        // Fetch bodies only for calls in functions compiled from source here.
        // Calls inside imported functions become eligible when inlined into one.
        if( !(in(0) instanceof CallNode call) || !call.fun().isFromSource() ||
            inlineCandidate(true)<=0 ) return false;
        code.loadForInline(ret.fun()._compunit);
        return true;
    }

    // Check if candidate right now, sets no flags. Loading uses the same
    // policy, but can only estimate the size of a summarized body.
    private byte inlineCandidate(boolean loading) {
        if( _inline < -2 ) return -2; // Never
        if( _folding ) return -2;     // No, and will collapse anyways
        if( !(in(0) instanceof CallNode call) ) return -2; // malformed because mid-death

        if( nIns()!=2 ) return -1;    // Not exactly 1 link, but can change

        // Have exactly 1 linked function
        ReturnNode ret = (ReturnNode)in(1);
        FunNode fun = ret.fun();

        // Preserve initializer boundaries until post-Opto cycle validation.
        if( fun.isClz() ) return -2;

        // Heuristic forced inlining turned off via name
        if( fun._name != null &&
            fun._name.endsWith("_noInline") )
            return -2;          // Never, stop asking

        // Disallow self-recursive inlining (loop unrolling by another name).
        for( int i=1; i < fun.nIns(); i++ )
            if( fun.cfg(i).fun()==fun ) // Check for linked call input inside "fun"
                { addDep(fun); return -1; } // No, but can lose the recursive edge

        // Things that might improve, but not now a candidate
        Node fptr = call.fptr();
        // Constant Function is being called, and its not-null
        if( !(fptr._type instanceof TypeFunPtr tfp && tfp.notNull() && tfp.isConstant()) )
            { addDep(fptr); return -1; }

        // Arguments are correct
        if( call.nargs()!=tfp.nargs() || !goodArgs(call,tfp) )
            { addDep(fptr); return -1; }

        // Encouraged inlining because small size and constructor.
        int maxSize = fun.inlineLimit();
        // A large parse can leave only a tiny initializer.  Recognize that
        // and await cleanup to shrink the body.
        if( fun._approxUIDs >= maxSize ) {
            if( fun.summary()!=null || !smallBody(fun) ) return -1;
        } else if( fun.summary()==null && fun.body().cardinality() >= maxSize ) // Expensive size check
            return -1;          // Try later (if cleanup makes the real body small enough)

        if( fun.summary()!=null ) return loading ? (byte)1 : (byte)-1;

        // Can be cloned but not trivial
        if( fun.nIns() > 2 ) { addDep(fun); return 2; }

        // A trivial candidate right now
        return 1;
    }

    // Bounded, conservative recognition of straight-line initializer boilerplate.
    // Walk uses from the entry and stop at Return, never following callers or
    // constant inputs.  This also avoids scanning sparse MemMerge input arrays.
    // Watch only this small prefix, including an unsupported node that may fold.
    private static final Node[] TINYBODY = new Node[15]; // Tiny worklist
    private boolean smallBody(FunNode fun) {
        ReturnNode ret = fun.ret();
        for( Node in : ret._inputs )
            if( in!=null ) addDep(in);
        if( ret.ctrl()!=fun ) return false;
        TINYBODY[0] = fun;
        int len=1;
        for( int i=0; i<len; i++ ) {
            Node n = TINYBODY[i];
            addDep(n);
            if( n==ret ) continue;
            if( n instanceof ParmNode parm && parm.region()!=fun ) return false;
            if( n!=fun && !(n instanceof ParmNode) &&
                !(n instanceof StoreNode) && !(n instanceof MemMergeNode) )
                return false;                             // Something unexpected
            if( n.nOuts()>TINYBODY.length ) return false; // Too big
            for( Node use : n._outputs ) {
                if( use==null ) continue;
                int j=0;
                while( j<len && TINYBODY[j]!=use ) j++;
                if( j<len ) continue; // Already in TINYBODY
                if( len==TINYBODY.length ) { addDep(use); return false; } // Too big
                TINYBODY[len++] = use;  // Add to worklist
            }
        }
        return true;
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
            if( !arg._type.isa(formal) || !arg._type.accessISA(formal) )
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


    @Override public Node pcopy(int idx, Node dep) {
        return _folding ? in(1).in(idx) : null;
    }

    @Override public Node copy() {
        return _folding ? super.copy() : new CallEndNode(_rpc);
    }

}
