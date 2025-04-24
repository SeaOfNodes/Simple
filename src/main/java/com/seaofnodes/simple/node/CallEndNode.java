package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.type.*;

/**
 *  CallEnd
 */
public class CallEndNode extends CFGNode implements MultiNode {

    // When set true, this Call/CallEnd/Fun/Return is being trivially inlined
    private boolean _folding;
    public boolean folding() { return _folding; }
    public final TypeRPC _rpc;

    public CallEndNode(CallNode call) { super(new Node[]{call}); _rpc = TypeRPC.constant(_nid); }
    public CallEndNode(CallEndNode cend) { super(cend); _rpc = cend._rpc; }

    @Override public String label() { return "CallEnd"; }
    @Override public boolean blockHead() { return true; }

    public CallNode call() { return (CallNode)in(0); }

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
        if( !(in(0) instanceof CallNode call) )
            return TypeTuple.RET.dual();
        Type ret = Type.BOTTOM;
        TypeMem mem = TypeMem.BOT;
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
                    while( !(idom instanceof FunNode) )
                        idom = idom.idom();
                    // Inline?
                    if( idom != fun ) {
                        // Trivial inline: rewrite
                        _folding = true;
                        // Rewrite Fun so the normal RegionNode ideal collapses
                        fun._folding = true;
                        fun.setDef(1,Parser.XCTRL); // No default/unknown StartNode caller
                        fun.setDef(2,call.ctrl());  // Bypass the Call;
                        fun.ret().setDef(3,null);   // Return is folding also
                        CodeGen.CODE.addAll(fun._outputs);
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

    // Clone only tiny, straight-line boilerplate, after ordinary peeps settle.
    // Larger bodies still use the existing single-caller, non-cloning inline.
    public boolean inlineSmall() {
        if( isDead() || _folding || nIns()!=2 || !(in(0) instanceof CallNode call) ) return false;
        Node fptr = call.fptr();
        if( !(fptr instanceof ConstantNode) ||
            !(fptr._type instanceof TypeFunPtr tfp && tfp.notNull() && tfp.isConstant()) )
            { addDep(fptr); return false; }
        if( call.err()!=null ) return false;
        ReturnNode ret = (ReturnNode)in(1);
        FunNode fun = ret.fun();
        if( fun._folding ) return false;
        int len = smallBody(fun);
        if( len==0 ) return false;
        // All callers consume the three normal return projections.
        for( Node use : _outputs )
            if( !((use instanceof ProjNode p && p._idx>=0 && p._idx<3) ||
                  (use instanceof CProjNode cp && cp._idx==0)) ) return false;

        // Substitute control and arguments directly; no new function identity,
        // parameter Phis, or return address is needed for this straight line.
        Node[] copy = new Node[len];
        for( int i=0; i<len; i++ ) {
            Node n = TINYBODY[i];
            copy[i] = n==fun ? call.ctrl() :
                n instanceof ParmNode parm ? (parm._idx==0 ? null : call.arg(parm._idx)) :
                n==ret ? null : n.copyEmpty();
        }
        for( int i=0; i<len; i++ ) {
            Node n = TINYBODY[i];
            if( n instanceof StoreNode || n instanceof MemMergeNode ) {
                for( Node in : n._inputs ) copy[i].addDef(mapped(in,copy));
                CodeGen.CODE.add(copy[i]);
            }
        }
        Node[] result = new Node[3];
        for( int i=0; i<3; i++ ) result[i] = mapped(ret.in(i),copy).keep();
        keep();
        call.unlink_all();
        CodeGen.CODE.add(fun);
        CodeGen.CODE.addAll(fun._outputs);
        // subsume kills each projection; retain CallEnd until all are replaced.
        while( nOuts()>1 ) {
            Node use = out(0)==null ? out(1) : out(0);
            int idx = use instanceof ProjNode p ? p._idx : ((CProjNode)use)._idx;
            CodeGen.CODE.addAll(use._outputs);
            use.subsume(result[idx]);
        }
        unkill();
        for( Node n : result ) n.unkill();
        CodeGen.CODE.invalidateIDepthCaches();
        return true;
    }

    private static Node mapped(Node n, Node[] copy) {
        for( int i=0; i<copy.length; i++ )
            if( TINYBODY[i]==n ) return copy[i];
        return n;               // Constants outside the body are shared.
    }

    // Bounded recognition of initializer boilerplate. Walk uses from the entry
    // and stop at Return, never following callers or constant inputs. This also
    // avoids scanning sparse MemMerge input arrays while a large body shrinks.
    // Watch this small prefix, including an unsupported node that may fold.
    private static final Node[] TINYBODY = new Node[15]; // Tiny worklist
    private int smallBody(FunNode fun) {
        ReturnNode ret = fun.ret();
        for( Node in : ret._inputs )
            if( in!=null ) addDep(in);
        if( ret.ctrl()!=fun ) return 0;
        TINYBODY[0] = fun;
        int len=1;
        for( int i=0; i<len; i++ ) {
            Node n = TINYBODY[i];
            addDep(n);
            if( n==ret ) continue;
            if( n instanceof ParmNode parm && parm.region()!=fun ) return 0;
            if( n!=fun && !(n instanceof ParmNode) &&
                !(n instanceof StoreNode) && !(n instanceof MemMergeNode) )
                return 0;
            if( n.nOuts()>TINYBODY.length ) return 0;
            for( Node use : n._outputs ) {
                if( use==null ) continue;
                int j=0;
                while( j<len && TINYBODY[j]!=use ) j++;
                if( j<len ) continue;
                if( len==TINYBODY.length ) { addDep(use); return 0; }
                TINYBODY[len++] = use;
            }
        }
        return len;
    }

    @Override public Node pcopy(int idx) {
        return _folding ? in(1).in(idx) : null;
    }

    // ------------
    // MachNode specifics, shared across all CPUs
    public int _xslot;
    private RegMask _retMask;
    private RegMask _kills;
    public void cacheRegs(CodeGen code) {
        // Return mask depends on TFP (either GPR or FPR)
        _retMask = code._mach.retMask(call().tfp());
        // Kill mask is all caller-saves, and any mirror stack slots for args
        // in registers.
        RegMaskRW kills = code._callerSave.copy();
        // Start of stack slots
        int maxReg = code._mach.regs().length;
        // Incoming function arg slots, all low numbered in the RA
        int fslot = fun()._maxArgSlot;
        // Killed slots for this calls outgoing args
        int xslot = code._mach.maxArgSlot(call().tfp());
        _xslot = (maxReg+fslot)+xslot;
        for( int i=0; i<xslot; i++ )
            kills.set((maxReg+fslot)+i);
        _kills = kills;
    }
    public String op() { return "cend"; }
    public RegMask regmap(int i) { return null; }
    public RegMask outregmap() { return null; }
    public RegMask outregmap(int idx) { return idx==2  ? _retMask : null; }
    public RegMask killmap() { return _kills; }
    public void encoding( Encoding enc ) { }
    public void asm(CodeGen code, SB sb) {  }
}
