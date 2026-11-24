package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import java.util.*;

/** Effects introduced by a function, independent of its incoming memory.
 * Scan each body once, then union effects along call edges to a fixed point.
 * This includes recursion and paths which never reach Return. */
public final class FunctionEffects {
    public int[] _writes;
    public boolean _referenceWrites;
    public TypeMem _escapes;
    private final ArrayList<FunNode> _callees = new ArrayList<>();

    public FunctionEffects(int[] writes, boolean referenceWrites, TypeMem escapes) {
        _writes=writes; _referenceWrites=referenceWrites; _escapes=escapes;
    }
    private FunctionEffects() { this(XInt.EMPTY,false,TypeMem.START); }

    public static IdentityHashMap<FunNode,FunctionEffects> analyze(CodeGen code) {
        IdentityHashMap<FunNode,FunctionEffects> effects=new IdentityHashMap<>();
        for( FunNode fun : code._linker )
            if( fun!=null && !fun.isDead() && !fun._type.isHigh() ) {
                FunctionEffects effect=fun.summary()==null ? new FunctionEffects() : fun.summary()._effects;
                effects.put(fun,effect);
                if( fun.summary()==null ) effect.collect(fun);
            }
        // Sets only grow. Recursive components need no special fallback and
        // cannot accidentally prove themselves effect-free before convergence.
        boolean changed;
        do {
            changed=false;
            for( FunctionEffects effect : effects.values() )
                for( FunNode callee : effect._callees ) {
                    FunctionEffects child=effects.get(callee);
                    changed |= effect.merge(child);
                }
        } while( changed );
        return effects;
    }

    private boolean merge(FunctionEffects other) {
        int[] writes=other==null ? XInt.FULL : XInt.meet(_writes,other._writes);
        boolean refs=other==null || _referenceWrites || other._referenceWrites;
        TypeMem escapes=other==null ? TypeMem.BOT : _escapes.escapes(other._escapes);
        boolean changed=writes!=_writes || refs!=_referenceWrites || escapes!=_escapes;
        _writes=writes; _referenceWrites=refs; _escapes=escapes;
        return changed;
    }

    private void collect(FunNode fun) {
        BitSet seen=new BitSet();
        ArrayDeque<Node> work=new ArrayDeque<>();
        work.push(fun); work.push(fun.ret());
        while( !work.isEmpty() ) {
            Node n=work.pop();
            if( seen.get(n._nid) ) continue;
            seen.set(n._nid);
            if( n instanceof CFGNode cfg && !(cfg instanceof ReturnNode) &&
                (cfg==fun || !cfg.isFunLeaf()) )
                for( Node use : cfg._outputs )
                    if( use instanceof CFGNode next && !cfg.skip(next) && !next._type.isHigh() ) work.push(next);
            if( n.isFunLeaf() ) continue;
            // Replacing the effect table can enable an alias-preservation peep
            // even though no graph type changed. Wake its consumers explicitly.
            if( n instanceof LoadNode load && "$init".equals(load._name) ) CodeGen.CODE.add(load);
            // Raw addresses can hide a reference in an otherwise scalar store.
            // Until raw effects have their own contract, they block ownership proofs.
            if( n instanceof PtrToIntNode ) _referenceWrites=true;
            if( n instanceof StoreNode st ) {
                int alias=st._alias;
                if( alias==1 && st.ptr()._type instanceof TypeMemPtr ptr ) {
                    Field field=ptr._obj.field(st._name);
                    if( field!=null ) alias=field._alias;
                }
                _writes=alias==1 ? XInt.FULL : XInt.make(_writes,alias);
                if( reference(st.val()._type) ) {
                    _referenceWrites=true;
                    expose(st.val());
                }
            }
            if( n instanceof EscapeNode esc && !esc._borrow ) {
                _writes=XInt.make(_writes,esc.fld()._alias);
                _referenceWrites |= reference(esc.fld()._t);
            }
            if( n instanceof CLInitNode init && !init.bypass() ) {
                FunNode target=CodeGen.CODE.link(init.cu());
                if( target==null ) merge(null);
                else _callees.add(target);
            }
            if( n instanceof CallNode call ) call(call);
            if( n instanceof CallEndNode end ) { work.push(end.call()); continue; }
            // Memory walks find even unpinned Stores. Stop at incoming Parms,
            // constants, and interprocedural return edges, not their cached types.
            for( Node in : n._inputs ) if( in!=null ) work.push(in);
        }
    }

    private static boolean reference(Type t) {
        return !(t instanceof TypeInteger || t instanceof TypeFloat) && !t.isHigh() && t!=Type.NIL;
    }

    private void expose(Node value) {
        // Incoming references are supplied at each imported call, not frozen
        // into a library-wide set of possible callbacks. All reference arguments
        // still conservatively escape when applying the summary.
        if( value instanceof ParmNode parm && !(parm._idx==2 && parm.fun().isClz()) ) return;
        _escapes=_escapes.escapesFrom(value._type);
    }

    private void call(CallNode call) {
        if( Intrinsic.pure(call) ) return;
        // The terminating native has no returning memory effects.
        boolean exits=call.cend()._type instanceof TypeTuple tt && tt.mem()==TypeMem.TOP;
        TypeFunPtr fp=call.fptr()._type instanceof TypeFunPtr f ? f : null;
        boolean unknown=fp==null || XInt.isHigh(fp.fidxs());
        boolean external=unknown;
        if( !unknown )
            for( int idx=XInt.next(fp.fidxs(),0); idx>=0; idx=XInt.next(fp.fidxs(),idx) ) {
                FunNode target=CodeGen.CODE.link(idx);
                if( target==null ) external=true;
                else _callees.add(target);
            }
        if( external && !exits ) { _writes=XInt.FULL; _referenceWrites=true; }
        // Simple callees can retain parameters too. Using their actuals keeps
        // this conservative without importing the caller's entire memory state.
        int first=fp!=null && fp.nargs()>0 && fp.arg(0)==TypePtr.PTR ? 3 : 2;
        for( int i=first; i<call.nIns()-1; i++ ) expose(call.in(i));
    }
}
