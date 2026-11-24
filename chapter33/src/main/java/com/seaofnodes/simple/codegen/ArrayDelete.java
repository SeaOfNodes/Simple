package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.SB;
import java.util.*;

/** Demand-driven ownership at a deletion checkpoint. Field queries follow
 * memory SSA. Call summaries supply may-stores, returned origins, and exclusive
 * outgoing fields at that call's memory output, never at all calls at once.
 * Runtime recycling remains separate from these proofs. */
public final class ArrayDelete {
    public record Consume(int arg, List<String> path) {
        public Consume { path=List.copyOf(path); }
        @Override public String toString() {
            SB sb=new SB().p("arg").p(arg);
            for( String field : path ) sb.p('.').p(field);
            return sb.toString();
        }
    }

    // An origin is a local Node, an incoming base.field, a callee allocation,
    // or a call's exclusive outgoing base.field. Interned inside this check.
    // Node equality/hashing must be identity: Node.hashCode participates in GVN.
    private record Ref(Node node, Ref base, String field, Allocation.Site site) {
        @Override public int hashCode() {
            return System.identityHashCode(node)+31*System.identityHashCode(base)+
                17*System.identityHashCode(site)+Objects.hashCode(field);
        }
        @Override public boolean equals(Object o) {
            return o instanceof Ref r && node==r.node && base==r.base && site==r.site && Objects.equals(field,r.field);
        }
        boolean fresh() { return node instanceof NewNode || node instanceof CallNode || node instanceof LoopNode && base!=null; }
    }
    private static final class Effects {
        final IdentityHashMap<Allocation.Site,HashSet<Ref>> values=new IdentityHashMap<>();
        final HashMap<Ref,HashMap<String,HashSet<Ref>>> stores=new HashMap<>();
        final HashSet<Ref> returns=new HashSet<>();
        boolean building=true;
    }

    public final CallNode _call;
    public String _reject;
    public Node _unique;
    public Consume _requirement;
    private Ref _owner;
    private String _field;
    private int _alias;
    private final Allocation _allocation;
    private final HashMap<Ref,Ref> _origins=new HashMap<>();
    private final IdentityHashMap<Node,HashSet<Ref>> _refs=new IdentityHashMap<>();
    private final IdentityHashMap<CallNode,Effects> _effects=new IdentityHashMap<>();
    private final IdentityHashMap<CallNode,HashSet<Ref>> _owned=new IdentityHashMap<>();
    private final HashMap<Ref,HashSet<Ref>> _loopEntries=new HashMap<>();
    private final IdentityHashMap<Node,Boolean> _borrows=new IdentityHashMap<>();
    private final BitSet _active=new BitSet(), _after=new BitSet();
    private final ArrayList<LoopNode> _backedges=new ArrayList<>();
    private HashSet<Ref> _old;
    private HashSet<Ref> _next;
    private String _arrayType;
    private boolean _deletable;

    ArrayDelete(Allocation allocation, List<Node> nodes, CallNode call, int arg) {
        this(allocation,nodes,call,new Consume(arg,List.of()),false);
    }
    ArrayDelete(Allocation allocation, List<Node> nodes, CallNode call, Consume consume) {
        this(allocation,nodes,call,consume,true);
    }
    private ArrayDelete(Allocation allocation, List<Node> nodes, CallNode call, Consume consume, boolean atCaller) {
        _allocation=allocation; _call=call;
        if( consume.arg()<2 || consume.arg()>=call.nIns()-1 ) throw new IllegalArgumentException("Not a call argument");
        Node value=call.arg(consume.arg());
        Type type=value._type;
        HashSet<Ref> roots=refs(value);
        for( String name : consume.path() ) {
            if( roots.size()!=1 || !(type instanceof TypeMemPtr ptr) ) {
                reject("ambiguous consuming owner for "+consume); return;
            }
            Field field=ptr._obj.field(name);
            if( field==null ) { reject("missing consuming field ."+name); return; }
            _owner=roots.iterator().next(); _field=name; _alias=field._alias;
            roots=contents(call.in(1),_owner,name,_alias);
            // This checkpoint's next ownership version belongs to the next
            // iteration; the entry and other preceding versions are checked here.
            roots.remove(ref(call,_owner,name,null));
            type=field._t;
        }
        if( !(type instanceof TypeMemPtr ptr) || !ptr._obj.isAry() || ptr.nullable() ) {
            reject("requires a non-null array"); return;
        }
        _arrayType=ptr._obj._name;
        _deletable=ptr._del;
        if( _reject!=null ) return;
        if( roots.isEmpty() ) { reject("array has no known origin"); return; }
        _old=new HashSet<>(roots);
        ArrayDeque<Ref> entries=new ArrayDeque<>(roots);
        while( !entries.isEmpty() ) {
            HashSet<Ref> entry=_loopEntries.get(entries.pop());
            if( entry!=null ) for( Ref r : entry ) if( _old.add(r) ) entries.push(r);
        }
        for( Ref root : _old ) if( !root.fresh() ) {
            Consume incoming=entryPath(root);
            if( incoming==null ) { reject("cannot express incoming ownership as an argument field path"); return; }
            if( _requirement!=null && !_requirement.equals(incoming) ) {
                reject("array has multiple incoming ownership paths"); return;
            }
            _requirement=incoming; _unique=value;
        }
        after(call);
        // Merges reached after this checkpoint exclude paths that bypassed it.
        _refs.clear(); _effects.clear(); _owned.clear();

        // A unique-entry requirement excludes aliases in the other arguments.
        // Callees may rely on this; check the actuals at each consuming caller.
        if( atCaller ) for( int i=2; i<call.nIns()-1; i++ )
            if( i!=consume.arg() && pointer(call.arg(i)) && contains(call.arg(i)) )
                reject("argument #"+(i-2)+" aliases the array at deletion");

        // Check explicit stores and substituted callee stores by the same rule:
        // any alias must be killed at the checkpoint's incoming memory version.
        for( Ref root : roots ) if( root.node==null && root.base!=null ) slot(root.base,root.field);
        for( Node n : nodes ) {
            if( _reject!=null ) return;
            if( n instanceof StoreNode st && pointer(st.val()) && contains(st.val()) ) {
                if( later(st) ) reject("store #"+st._nid+" uses the array after deletion");
                else for( Ref base : refs(st.ptr()) ) slot(base,st._name);
            }
            if( n instanceof CallNode other && other!=call && !borrow(other) && !unborn(other) ) {
                Effects effect=effects(other);
                if( effect==null ) { reject("call #"+other._nid+" ("+target(other)+") has unknown reference effects before deletion"); continue; }
                for( var base : effect.stores.entrySet() ) for( var field : base.getValue().entrySet() )
                    if( contains(field.getValue()) ) {
                        if( later(other) ) reject("call #"+other._nid+" stores the array after deletion");
                        else slot(base.getKey(),field.getKey());
                    }
            }
            if( n instanceof CLInitNode init && !_allocation.arrayBorrow(init) && !later(init) && !unborn(init) )
                reject("class initialization #"+init._nid+" has unknown reference effects before deletion");
        }
        for( Node n : nodes ) {
            if( _reject!=null ) return;
            if( pointer(n) && contains(n) ) for( Node use : n._outputs ) {
                if( _reject!=null ) return;
                use(n,use);
            }
        }
        if( _reject==null && !_backedges.isEmpty() ) {
            if( atCaller && !consume.path().isEmpty() && !_allocation.arrayOwned(call,consume) )
                reject("callee does not preserve unique field ownership across deletion backedge");
            else backedges(nodes);
        }
    }

    public boolean proven() { return _reject==null && _unique==null; }
    private void reject(String why) { if( _reject==null ) _reject=why; }
    private Ref ref(Node n, Ref base, String field, Allocation.Site site) {
        Ref r=new Ref(n,base,field,site);
        Ref old=_origins.putIfAbsent(r,r);
        return old==null ? r : old;
    }
    private Ref ref(Node n) { return ref(n,null,null,null); }
    private static HashSet<Ref> set(Ref r) { HashSet<Ref> s=new HashSet<>(); s.add(r); return s; }
    private static boolean pointer(Node n) { return n!=null && (n._type instanceof TypeMemPtr || n._type instanceof TypePtr); }
    private Type type(Ref r) {
        if( r.base!=null && type(r.base) instanceof TypeMemPtr p ) {
            Field field=p._obj.field(r.field);
            if( field!=null ) return field._t;
        }
        Type t=r.site!=null && r.site._allocation!=null ? r.site._allocation._type : r.node==null ? TypePtr.PTR : r.node._type;
        return t instanceof TypeTuple tt && (r.node instanceof NewNode || r.site!=null && r.site._allocation!=null) ? tt.at(0) : t;
    }
    private int alias(Ref base, String name) {
        if( type(base) instanceof TypeMemPtr p ) {
            Field field=p._obj.field(name);
            if( field!=null ) return field._alias;
        }
        return 1; // Unknown base: query the whole-memory slice conservatively.
    }
    private static Consume entryPath(Ref r) {
        if( r.node instanceof ParmNode parm ) return new Consume(parm._idx,List.of());
        if( r.node!=null || r.base==null ) return null;
        Consume parent=entryPath(r.base);
        if( parent==null ) return null;
        ArrayList<String> path=new ArrayList<>(parent.path()); path.add(r.field);
        return new Consume(parent.arg(),path);
    }
    private boolean contains(Node n) {
        if( n._type instanceof TypeMemPtr p && p._obj!=TypeStruct.BOT && !p._obj._name.equals(_arrayType) ) return false;
        return contains(refs(n));
    }
    private boolean contains(HashSet<Ref> refs) {
        for( Ref old : _old ) if( mayAlias(refs,old) ) return true;
        return false;
    }
    private boolean updated(HashSet<Ref> refs) {
        if( _next!=null ) for( Ref r : _next ) if( mayAlias(refs,r) ) return true;
        return false;
    }
    private boolean mayAlias(HashSet<Ref> refs, Ref base) {
        for( Ref r : refs ) {
            if( r==base ) return true;
            if( r.fresh() || base.fresh() ) continue;
            // Literal/static arrays cannot inhabit a deletable array slot.
            if( _deletable && r.node instanceof ConstantNode && type(r) instanceof TypeMemPtr p &&
                p._obj.isAry() && !p._del ) continue;
            // A separate array parameter is disjoint under the propagated
            // unique-entry requirement, checked against actuals at callers.
            // Do not infer that different owner paths are disjoint: two
            // wrapper parameters might denote the same wrapper.
            if( _requirement!=null && _old!=null && _old.contains(base) &&
                entryPath(base)!=null && r.node instanceof ParmNode ) continue;
            if( type(r) instanceof TypeMemPtr x && type(base) instanceof TypeMemPtr y &&
                x._obj!=TypeStruct.BOT && y._obj!=TypeStruct.BOT && !x._obj._name.equals(y._obj._name) ) continue;
            return true;
        }
        return false;
    }
    private boolean unborn(CFGNode effect) {
        for( Ref root : _old ) {
            CFGNode birth=root.node instanceof NewNode alloc ? (CFGNode)alloc.ctrl() :
                root.node instanceof CallNode call ? call : null;
            if( birth==null || effect==birth || !effect.dominates(birth,null) ) return false;
        }
        return true;
    }
    private HashSet<Ref> refs(Node n) {
        HashSet<Ref> known=_refs.get(n);
        if( known!=null ) return known;
        HashSet<Ref> result=new HashSet<>();
        if( _reject!=null ) return result;
        if( _active.get(n._nid) ) { reject("cyclic reference at #"+n._nid); return result; }
        _active.set(n._nid);
        if( n._type==Type.NIL || n._type.isHigh() ) {
        } else if( n instanceof NewNode || n instanceof ParmNode || n instanceof ConstantNode ) result.add(ref(n));
        else if( n instanceof ProjNode proj && proj._idx==0 && proj.in(0) instanceof NewNode ) result.add(ref(proj.in(0)));
        else if( n instanceof GuardNode || n instanceof ReadOnlyNode || n instanceof ConstructNode || n instanceof InitViewNode )
            result.addAll(refs(n.in(1)));
        else if( n instanceof ArrayInitNode ) result.addAll(refs(n.in(2)));
        else if( n instanceof PhiNode phi ) {
            for( int i=1; i<phi.nIns(); i++ ) if( liveInput(phi,i) ) result.addAll(refs(phi.in(i)));
        } else if( n instanceof LoadNode load ) {
            HashSet<Ref> bases=refs(load.ptr());
            if( bases.size()!=1 ) reject("ambiguous owner of field ."+load._name+" at #"+load._nid);
            else result.addAll(contents(load.mem(),bases.iterator().next(),load._name,load._alias));
        } else if( n instanceof ProjNode proj && proj._idx==2 && proj.in(0) instanceof CallEndNode end ) {
            // Forwarded arguments need no field substitution, including calls
            // which also update memory. Their stores are checked separately.
            ArrayList<Node> args=_allocation.arrayResult(end.call());
            if( args!=null ) for( Node arg : args ) result.addAll(refs(arg));
            else {
                Effects effect=effects(end.call());
                if( effect==null ) reject("untracked returned reference at call #"+end.call()._nid);
                else result.addAll(effect.returns);
            }
        } else reject("untracked reference #"+n._nid+" ("+n.label()+")");
        _active.clear(n._nid); _refs.put(n,result);
        return result;
    }

    // Unique entry -> unique exit is a strong ownership transfer, even when
    // the implementation takes its no-growth path. May-stores remain weak.
    private HashSet<Ref> owned(CallNode call) {
        HashSet<Ref> slots=_owned.get(call);
        if( slots!=null ) return slots;
        slots=new HashSet<>(); _owned.put(call,slots);
        Allocation.Summary sum=_allocation.target(call);
        if( sum!=null ) for( Consume consume : sum._consumes )
            if( _allocation.arrayOwned(call,consume) ) {
                HashSet<Ref> bases=refs(call.arg(consume.arg()));
                for( int i=0; i<consume.path().size()-1; i++ ) {
                    String field=consume.path().get(i);
                    HashSet<Ref> next=new HashSet<>();
                    for( Ref base : bases ) next.addAll(contents(call.in(1),base,field,alias(base,field)));
                    bases=next;
                }
                if( bases.size()==1 ) slots.add(ref(null,bases.iterator().next(),consume.path().getLast(),null));
            }
        return slots;
    }

    private HashSet<Ref> substitute(CallNode call, Allocation.Site site, Effects effect) {
        HashSet<Ref> known=effect.values.get(site);
        if( known!=null ) return known;
        HashSet<Ref> values=new HashSet<>(); effect.values.put(site,values);
        if( site._arg>=2 ) values.addAll(refs(call.arg(site._arg)));
        else if( site._arg==-1 ) values.add(ref(CodeGen.CODE._start));
        else if( site._fresh ) values.add(ref(call,null,null,site));
        else if( site._base!=null )
            for( Ref base : substitute(call,site._base,effect) )
                values.addAll(contents(call.in(1),base,site._field,alias(base,site._field)));
        else reject("unmapped call storage at #"+call._nid);
        return values;
    }

    private Effects effects(CallNode call) {
        Effects known=_effects.get(call);
        if( known!=null ) {
            if( known.building ) { reject("cyclic call-field effects at #"+call._nid); return null; }
            return known;
        }
        Allocation.Summary sum=_allocation.target(call);
        if( sum==null || !sum.supported() || sum._opaqueEffects || sum._fun._extern ) return null;
        Effects effect=new Effects(); _effects.put(call,effect);
        HashSet<Ref> owned=owned(call);
        for( Allocation.Site site : sum._sites ) if( !site._fields.isEmpty() )
            for( Ref base : substitute(call,site,effect) ) for( var field : site._fields.entrySet() ) {
                HashSet<Ref> values=effect.stores.computeIfAbsent(base,k -> new HashMap<>())
                    .computeIfAbsent(field.getKey(),k -> new HashSet<>());
                if( owned.contains(ref(null,base,field.getKey(),null)) ) values.add(ref(call,base,field.getKey(),null));
                else for( Allocation.Site value : field.getValue() ) values.addAll(substitute(call,value,effect));
            }
        for( Allocation.Site site : sum._returns ) effect.returns.addAll(substitute(call,site,effect));
        effect.building=false;
        return effect;
    }

    private HashSet<Ref> contents(Node mem, Ref base, String field, int alias) {
        return contents(mem,base,field,alias,new BitSet());
    }
    private HashSet<Ref> contents(Node mem, Ref base, String field, int alias, BitSet seen) {
        HashSet<Ref> result=new HashSet<>();
        if( _reject!=null || mem._type.isHigh() || seen.get(mem._nid) ) return result;
        // A field query is reachability through memory definitions, with strong
        // stores cutting edges. A loop revisit adds no new reachable definition.
        seen.set(mem._nid);
        if( mem instanceof MemMergeNode merge ) {
            if( alias==1 ) {
                for( Node slice : merge._inputs ) if( slice!=null ) result.addAll(contents(slice,base,field,alias,seen));
            } else {
                Node slice=merge.explicitAlias(alias);
                result.addAll(contents(slice==null ? merge.in(1) : slice,base,field,alias,seen));
            }
        } else if( mem instanceof StoreNode st ) {
            HashSet<Ref> bases=refs(st.ptr());
            boolean same=(st._alias==alias || st._alias==1 || alias==1) && st._name.equals(field);
            boolean exact=same && bases.size()==1 && bases.contains(base);
            if( same && mayAlias(bases,base) ) {
                if( "[]".equals(field) ) reject("array-element ownership needs an index proof");
                else result.addAll(refs(st.val()));
            }
            if( !exact ) result.addAll(contents(st.mem(),base,field,alias,seen));
        } else if( mem instanceof EscapeNode esc ) {
            HashSet<Ref> bases=refs(esc.self());
            boolean same=esc.fld()._alias==alias || alias==1;
            if( same && mayAlias(bases,base) ) result.addAll(contents(esc.priv(),base,field,alias,seen));
            if( !same || bases.size()!=1 || !bases.contains(base) ) result.addAll(contents(esc.pub(),base,field,alias,seen));
        } else if( mem instanceof PhiNode phi && !(mem instanceof ParmNode) ) {
            if( phi.region() instanceof LoopNode loop && inside(_call,loop) && base==_owner &&
                field.equals(_field) && alias==_alias && type(ref(null,base,field,null)) instanceof TypeMemPtr p && p._obj.isAry() ) {
                // A loop header is an ownership boundary, just like a call.
                // Keep its entry version distinct from calls on the backedge.
                Ref entry=ref(loop,base,field,null);
                if( !_loopEntries.containsKey(entry) ) {
                    _loopEntries.put(entry,new HashSet<>());
                    _loopEntries.get(entry).addAll(contents(phi.in(1),base,field,alias,seen));
                }
                result.add(entry);
            } else for( int i=1; i<phi.nIns(); i++ ) if( liveInput(phi,i) ) result.addAll(contents(phi.in(i),base,field,alias,seen));
        } else if( mem instanceof ProjNode proj && proj.in(0) instanceof CallEndNode end ) {
            CallNode call=end.call();
            if( call==_call && base==_owner && field.equals(_field) && alias==_alias ||
                owned(call).contains(ref(null,base,field,null)) ) result.add(ref(call,base,field,null));
            else if( call==_call || Intrinsic.arrayDelete(call) || borrow(call) )
                result.addAll(contents(call.in(1),base,field,alias,seen));
            else {
                Effects effect=effects(call);
                if( effect==null ) reject("unknown reference effects at call #"+call._nid+" ("+target(call)+")");
                else {
                    for( var store : effect.stores.entrySet() ) if( mayAlias(set(store.getKey()),base) ) {
                        Set<Ref> values=store.getValue().get(field);
                        if( values!=null ) result.addAll(values);
                    }
                    result.addAll(contents(call.in(1),base,field,alias,seen));
                }
            }
        } else if( mem instanceof ProjNode proj && proj.in(0) instanceof NewNode ) {
        } else if( mem instanceof ProjNode proj && proj.in(0) instanceof CLInitNode init && _allocation.arrayBorrow(init) ) {
            result.addAll(contents(init.mem(),base,field,alias,seen));
        } else if( mem instanceof ParmNode || mem instanceof ConstantNode || mem instanceof ProjNode proj && proj.in(0) instanceof StartNode ) {
            if( !base.fresh() ) result.add(ref(null,base,field,null));
        } else reject("unknown memory #"+mem._nid+" ("+mem.getClass().getSimpleName()+") for ."+field);
        return result;
    }

    private boolean liveInput(PhiNode phi, int i) {
        Node ctrl=phi.region().in(i);
        return !ctrl._type.isHigh() && (!_after.get(phi.region()._nid) || _after.get(ctrl._nid));
    }
    private void slot(Ref base, String field) {
        if( base==_owner && field.equals(_field) ) return;
        if( contains(contents(_call.in(1),base,field,alias(base,field))) )
            reject("field ."+field+" still holds the array at deletion");
    }
    private boolean borrow(CallNode call) {
        Boolean known=_borrows.get(call);
        if( known!=null ) return known;
        boolean result=Intrinsic.arrayDelete(call) || _allocation.arrayBorrow(call);
        _borrows.put(call,result); return result;
    }
    private static String target(CallNode call) {
        return call.fptr()._type instanceof TypeFunPtr fp && fp.isConstant() ? CodeGen.CODE.funcName(fp.fidx()) : "indirect";
    }
    private void use(Node value, Node use) {
        if( use==null ) { reject("unclassified kept reference #"+value._nid); return; }
        if( use==_call ) return;
        if( use instanceof PtrToIntNode ) { reject("raw-address borrow of the array at #"+use._nid); return; }
        if( use instanceof CallNode call ) {
            if( later(call) ) reject("call #"+call._nid+" uses the array after deletion");
            else if( !borrow(call) && effects(call)==null ) reject("call #"+call._nid+" has no borrowing-only contract");
            return;
        }
        if( use instanceof StoreNode store && store.val()==value ) return;
        if( use instanceof EscapeNode || use instanceof NewNode || use instanceof ArrayInitNode ) return;
        if( pointer(use) && (use instanceof PhiNode || use instanceof GuardNode || use instanceof ReadOnlyNode ||
                            use instanceof ConstructNode || use instanceof InitViewNode) ) return;
        if( use.isFunLeaf() && !(use instanceof ReturnNode) ) return;
        if( later(use) ) reject("use #"+use._nid+" ("+use.label()+") survives deletion");
    }

    private void after(CallNode call) {
        if( call.cend()==null ) { reject("checkpoint has no continuation"); return; }
        ArrayDeque<CFGNode> work=new ArrayDeque<>();
        work.push(call.cend());
        while( !work.isEmpty() ) {
            CFGNode n=work.pop();
            if( _after.get(n._nid) || n._type.isHigh() ) continue;
            _after.set(n._nid);
            if( n instanceof ReturnNode ) continue;
            for( Node use : n._outputs )
                if( use instanceof CFGNode next && !n.skip(next) ) {
                    if( next instanceof LoopNode loop && n==loop.back() && inside(call,loop) ) {
                        if( !_backedges.contains(loop) ) _backedges.add(loop);
                    } else work.push(next);
                }
        }
        assert !_after.get(call._nid);
    }

    private static boolean inside(CFGNode ctrl, LoopNode loop) {
        for( CFGNode.LoopTree tree=ctrl._ltree; tree!=null; tree=tree._par )
            if( tree._head==loop ) return true;
        return false;
    }

    // The backedge is a call to the next iteration. The wrapper passes its
    // current field, not a saved copy of its old field. Check explicit Phi
    // arguments and invariant references used again, as well as stored copies
    // of the replacement. This last check is deliberately conservative: a
    // later iteration might kill a stored alias before growing, but we do not
    // yet infer that ordering as part of an iteration's entry contract.
    private void backedges(List<Node> nodes) {
        IdentityHashMap<Node,CFGNode> controls=new IdentityHashMap<>();
        BitSet active=new BitSet();
        for( LoopNode loop : _backedges ) {
            _next=new HashSet<>();
            if( _owner!=null ) for( Node use : loop._outputs )
                if( use instanceof PhiNode phi && phi.region()==loop && phi._type instanceof TypeMem mem &&
                    (mem._alias==_alias || mem._alias==1) )
                    _next.addAll(contents(phi.in(2),_owner,_field,_alias));
            // Include eventual exits as well as the next body. An invariant
            // saved pointer returned after the loop also crosses this boundary.
            BitSet next=new BitSet();
            ArrayDeque<CFGNode> work=new ArrayDeque<>();
            work.push(loop);
            while( !work.isEmpty() ) {
                CFGNode ctrl=work.pop();
                if( next.get(ctrl._nid) || ctrl._type.isHigh() ) continue;
                next.set(ctrl._nid);
                if( ctrl instanceof ReturnNode ) continue;
                for( Node use : ctrl._outputs )
                    if( use instanceof CFGNode cfg && !ctrl.skip(cfg) ) work.push(cfg);
            }
            for( Node n : nodes ) {
                if( _reject!=null ) return;
                // A helper after grow can retain the replacement through the
                // wrapper. That effect precedes the NEXT grow, even though it
                // does not precede this iteration's checkpoint.
                if( n instanceof CallNode call && call!=_call && later(call) && inside(call,loop) && !borrow(call) ) {
                    Effects effect=effects(call);
                    if( effect==null ) reject("call #"+call._nid+" has unknown reference effects across deletion backedge");
                    else for( var base : effect.stores.entrySet() ) for( var field : base.getValue().entrySet() )
                        if( updated(field.getValue()) && !(base.getKey()==_owner && field.getKey().equals(_field)) )
                            reject("replacement array alias stored across deletion backedge at call #"+call._nid);
                }
                if( n instanceof PhiNode phi && phi.region()==loop && pointer(phi) ) {
                    HashSet<Ref> carried=refs(phi.in(2));
                    if( contains(phi.in(2)) || updated(carried) )
                        reject("array reference crosses deletion backedge at Phi #"+phi._nid);
                }
                if( n instanceof StoreNode st && pointer(st.val()) && updated(refs(st.val())) ) {
                    CFGNode ctrl=Allocation.control(st,controls,active);
                    if( ctrl!=null && inside(ctrl,loop) &&
                        !(refs(st.ptr()).equals(set(_owner)) && st._name.equals(_field)) )
                        reject("replacement array alias stored across deletion backedge at #"+st._nid);
                }
                if( !pointer(n) || !contains(n) ) continue;
                CFGNode birth=Allocation.control(n,controls,active);
                if( birth!=null && next.get(birth._nid) ) continue;
                for( Node use : n._outputs ) {
                    if( use==null || use.isFunLeaf() ) continue;
                    CFGNode ctrl=Allocation.control(use,controls,active);
                    if( ctrl!=null && next.get(ctrl._nid) )
                        reject("array reference reused across deletion backedge at #"+use._nid);
                }
            }
        }
        _next=null;
    }

    // Follow data/memory definitions to their controls. A floating load on
    // pre-delete memory may run before delete; a pointer returned afterwards
    // is itself a post-delete use. No scheduling or graph mutation is needed.
    private boolean later(Node node) {
        BitSet seen=new BitSet();
        ArrayDeque<Node> work=new ArrayDeque<>();
        work.push(node);
        while( !work.isEmpty() ) {
            Node n=work.pop();
            if( seen.get(n._nid) ) continue;
            seen.set(n._nid);
            if( n instanceof CFGNode ) {
                if( _after.get(n._nid) ) return true;
                continue;
            }
            if( n instanceof PhiNode phi ) { work.push(phi.region()); continue; }
            if( n.isFunLeaf() ) continue;
            for( Node in : n._inputs ) if( in!=null ) work.push(in);
        }
        return false;
    }

    @Override public String toString() {
        SB sb=new SB().p("delete at call #").p(_call._nid).p(": ");
        if( _reject!=null ) return sb.p("REJECT: ").p(_reject).toString();
        if( _unique!=null ) sb.p("requires unique entry ").p(_requirement.toString()).p("; ");
        return sb.p("no surviving array access").toString();
    }
}
