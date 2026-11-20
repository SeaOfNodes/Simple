package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.SB;
import java.util.*;

/** First allocation-lifetime experiment. Builds plans, not machine allocations.
 * One DFS of the call DAG: discover callees on entry, compose their symbolic
 * summaries on exit. No call-tree expansion, caller rewalk, or IR mutation.
 * Within a summary, reference edges propagate destination requirements with a
 * small worklist. A rejected summary is never a usable allocation plan.
 */
public final class Allocation {
    private final Summary[] _summaries;
    private final ArrayList<Summary> _postorder = new ArrayList<>();

    // Destination bits: result, static storage, then Call/Parm input indices.
    private static final int RESULT=0, STATIC=1;

    /** One abstract storage object. Imported allocations are distinct per call
     *  site; a parameter denotes borrowed storage, not an allocation to move. */
    public static final class Site {
        public final NewNode _allocation; // null for a formal or static storage
        public final int _arg;            // >=2: Parm index, -1: static, 0: fresh
        public final String _name;
        public final long _bytes;         // snapshot before later IR rewrites
        public final BitSet _owners = new BitSet();
        public final HashMap<String,HashSet<Site>> _fields = new HashMap<>();

        private Site(NewNode allocation, int arg, String name) {
            _allocation=allocation; _arg=arg; _name=name;
            _bytes=allocation!=null && allocation.size()._type instanceof TypeInteger size && size.isConstant()
                ? size.value() : -1;
            if( arg>=2 ) _owners.set(arg);
            if( arg==-1 ) _owners.set(STATIC);
        }

        public String destination() {
            if( _owners.isEmpty() ) return "frame";
            SB sb = new SB();
            for( int i=_owners.nextSetBit(0); i>=0; i=_owners.nextSetBit(i+1) )
                sb.p(i==RESULT ? "result" : i==STATIC ? "static" : "arg"+i).p(" & ");
            return sb.unchar(3).toString();
        }
    }

    /** Symbolic summary, with separate destinations for separate arguments.
     *  Object fields are weak updates: all stored references remain possible.
     *  The first cut rejects pointer loads rather than guessing their origin. */

    // Per-function summary; allocation sites; failure reasons
    public static final class Summary {
        public final FunNode _fun;
        public final ArrayList<Site> _sites = new ArrayList<>();
        public final HashSet<Site> _returns = new HashSet<>();
        public String _reject;
        private byte _state; // 0 pre/unseen, 1 mid/active DFS, 2 post/complete
        // All allocation-interesting Nodes; Calls, Stores, News, Ptr ops
        private final ArrayList<Node> _nodes = new ArrayList<>();
        // Just the Calls
        private final ArrayList<CallNode> _calls = new ArrayList<>();
        // Pointer expression -> possible storage objects, in this function's vocabulary.
        private final IdentityHashMap<    Node,HashSet<Site>> _values  = new IdentityHashMap<>();
        // Call -> returned storage after substituting actuals and importing effects once.
        private final IdentityHashMap<CallNode,HashSet<Site>> _results = new IdentityHashMap<>();
        private final BitSet _resolving = new BitSet();
        private final Site _static = new Site(null,-1,"static");

        private Summary(FunNode fun) { _fun=fun; }
        // Hit the end of summary findings with no errors.
        public boolean supported() { return _state==2 && _reject==null; }
        // Throw or return valid summary
        public Summary requireSupported() {
            if( !supported() ) throw new IllegalStateException("Allocation "+_fun.label()+": "+_reject);
            return this;
        }
        // Collect first rejection
        private void reject(String why) { if( _reject==null ) _reject=why; }
    }

    public Allocation(CodeGen code) {
        _summaries = new Summary[code._linker._len];
        for( FunNode fun : code._linker )
            if( fun!=null && !fun.isDead() && !fun._type.isHigh() )
                _summaries[fun.sig().fidx()] = new Summary(fun);
        for( Summary sum : _summaries )
            if( sum!=null )
                visit(sum);
    }

    public Summary summary(String name) {
        for( Summary sum : _summaries )
            if( sum!=null && name.equals(sum._fun._name) ) return sum;
        throw new IllegalArgumentException("No allocation summary for "+name);
    }

    public void requireSupported() {
        for( Summary sum : _postorder )
            sum.requireSupported();
    }

    private void visit(Summary sum) {
        if( sum._state==2 ) return; // Post-order, done
        // Mid-visit, so found a Call Graph cycle, i.e. recursion
        if( sum._state==1 ) {
            // Recursive SCC/mega-call work goes here; do not expand a call tree.
            sum.reject("recursive call component");
            return;
        }
        // Was pre-order, now mid-order
        sum._state=1;
        if( sum._fun._extern || sum._fun.summary()!=null ) {
            sum.reject("opaque body: allocation contract unavailable");
        } else {
            // Collection interesting function body and call sites
            collect(sum);
            for( CallNode call : sum._calls ) {
                Summary callee = target(call);
                if( callee==null ) sum.reject("opaque or indirect call #"+call._nid);
                else {
                    // Recursive walk Call Graph DAG
                    visit(callee);
                    // If some callee is rejected, so is this caller
                    if( callee._reject!=null )
                        sum.reject("call #"+call._nid+" -> "+callee._fun.label()+": "+callee._reject);
                }
            }
            if( sum._reject==null )
                build(sum);
        }
        // Postder
        sum._state=2;
        _postorder.add(sum);
    }

    private Summary target(CallNode call) {
        if( !(call.fptr()._type instanceof TypeFunPtr fp) || !fp.isConstant() ) return null;
        int idx=fp.fidx();
        return idx>=0 && idx<_summaries.length ? _summaries[idx] : null;
    }

    // Follow control, memory, and references, not arithmetic/offset expressions.
    // Find allocations at control anchors too: their only remaining use might
    // be a scalar load or a raw address buried under arithmetic.  Don't walk into
    // other functions, so no following linked Calls or Parms.
    private void collect(Summary sum) {
        BitSet seen = new BitSet();
        ArrayDeque<Node> work = new ArrayDeque<>();
        // Start from the return
        work.push(sum._fun.ret());
        // Repeat until entire function is walked
        while( !work.isEmpty() ) {
            Node n = work.pop();
            if( seen.get(n._nid) ) continue; // Been there, done that
            seen.set(n._nid);
            // Add to function body
            sum._nodes.add(n);
            if( n instanceof CFGNode )
                for( Node use : n._outputs )
                    if( use instanceof NewNode alloc && alloc.ctrl()==n ||
                        use instanceof ParmNode parm && parm.fun()==sum._fun )
                        work.push(use);
            // Reference uses stay within this function; Parm call links and
            // constant/global anchors are boundaries.  In particular, find raw
            // conversions without following their scalar arithmetic users.
            if( !n.isFunLeaf() || n instanceof ParmNode )
                for( Node use : n._outputs )
                    if( use instanceof PtrToIntNode || pointer(use) && !use.isFunLeaf() )
                        work.push(use);
            if( n.isFunLeaf() ) continue;
            if( n instanceof CallNode call ) sum._calls.add(call);
            // Iteration/winner destinations go here.  Until then reject loops,
            // even when a flow-insensitive union would appear to fit a frame.
            if( n instanceof LoopNode ) sum.reject("loop destinations not implemented");
            if( n instanceof PtrToIntNode ) sum.reject("raw address escapes lifetime tracking #"+n._nid);

            // CLInit accesses static state, not caller parameters.  Its body is
            // analyzed separately; stores publishing local objects to static
            // state already give those objects the static destination. Do not
            // mistake an own-class initialization guard for recursive calling.
            if( n instanceof CallEndNode cend ) { work.push(cend.call()); continue; }

            // Walk selected edges.  We can skip pure-data ops, but need to
            // walk both control and memory at least.
            for( Node in : n._inputs )
                if( in!=null && (in instanceof CFGNode || in.isMem() ||
                    in._type instanceof TypeMem || pointer(in) || in instanceof NewNode) )
                    work.push(in);
        }
    }

    // Post-order work during the Call Graph visit
    private void build(Summary sum) {
        for( Node n : sum._nodes ) {
            // Gather allocation sites
            if( n instanceof NewNode alloc ) {
                if( !(alloc.size()._type instanceof TypeInteger size) || !size.isConstant() )
                    sum.reject("variable-size allocation #"+alloc._nid);
                Site site = new Site(alloc,0,"new#"+alloc._nid+":"+alloc._ts._name);
                if( TypeStruct.startsClzPrefix(alloc._ts._name) ) site._owners.set(STATIC);
                sum._sites.add(site);
                sum._values.put(alloc,set(site));
            }
            if( n instanceof ParmNode parm && pointer(parm) ) {
                if( parm.fun()!=sum._fun ) sum.reject("foreign parameter #"+parm._nid);
                Site site = new Site(null,parm._idx,"arg"+parm._idx+":"+parm.label());
                sum._sites.add(site);
                sum._values.put(parm,set(site));
            }
        }
        for( CallNode call : sum._calls ) compose(sum,call);
        for( Node n : sum._nodes ) {
            if( n instanceof StoreNode store && pointer(store.val()) ) {
                Set<Site> dst = refs(sum,store.ptr()), src=refs(sum,store.val());
                edge(dst, store._name, src);
            }
        }
        sum._returns.addAll(refs(sum,sum._fun.ret().expr()));
        for( Site site : sum._returns ) site._owners.set(RESULT);
        if( !sum._static._fields.isEmpty() ) sum._sites.add(sum._static);

        // Local closure only: an object retained in a destination retains its
        // referenced objects there too.  Cyclic object graphs need no DAG rewalk.
        ArrayDeque<Site> work=new ArrayDeque<>(sum._sites);
        while( !work.isEmpty() ) {
            Site src = work.pop();
            for( Set<Site> values : src._fields.values() )
                for( Site dst : values ) {
                    int before = dst._owners.cardinality();
                    dst._owners.or(src._owners); // Union owners set
                    if( dst._owners.cardinality() != before ) work.push(dst);
                }
        }
        // Bin packing goes here: fixed destinations become caller landing zones.
        // Free-list work goes here: only after checking remaining usable aliases.
        // Parallel growing tips go here: one owner may need several storage areas.
    }

    // n has pointer type
    private static boolean pointer(Node n) {
        return n!=null && (n._type instanceof TypeMemPtr || n._type instanceof TypePtr);
    }

    // One element Set of Site
    private static HashSet<Site> set(Site site) {
        HashSet<Site> set = new HashSet<>(); set.add(site); return set;
    }

    private static void edge(Set<Site> dst, String field, Set<Site> src) {
        if( src.isEmpty() ) return;
        for( Site site : dst )
            site._fields.computeIfAbsent(field,k -> new HashSet<>()).addAll(src);
    }


    // Which storage objects can this pointer expression denote?  This resolves
    // pointer VALUE flow, not the fields reachable through the pointer.  A Phi
    // unions possible targets; casts/views preserve targets; a call result uses
    // compose's caller-side targets.  New/Parm targets were seeded by build.
    // Example: refs(cond ? a : b) = {site(a), site(b)}.
    // build() uses these sets to add Store edges, then propagates owner requirements
    // through that separate object graph.  Object cycles are handled there;
    // _resolving rejects cyclic SSA values (loop Phi support is future work).
    private HashSet<Site> refs(Summary sum, Node n) {
        if( !pointer(n) ) return new HashSet<>();
        HashSet<Site> old = sum._values.get(n);
        if( old!=null ) return old;
        HashSet<Site> values = new HashSet<>();
        if( sum._resolving.get(n._nid) ) {
            sum.reject("cyclic reference value #"+n._nid);
            return values;
        }
        sum._resolving.set(n._nid);
        if( n instanceof ConstantNode ) {
            // Zero/null carries no storage obligation.  Other constant objects
            // have static backing; references loaded from them are not guessed.
            if( n._type!=Type.NIL && n._type!=TypeInteger.ZERO ) values.add(sum._static);
        } else if( n instanceof ProjNode proj && proj.in(0) instanceof NewNode alloc && proj._idx==0 ) {
            Set<Site> sites = sum._values.get(alloc);
            if( sites==null ) sum.reject("foreign allocation #"+alloc._nid);
            else values.addAll(sites);
        } else if( n instanceof ProjNode proj && proj.in(0) instanceof CallEndNode end && proj._idx==2 ) {
            values.addAll(compose(sum,end.call()));
        } else if( n instanceof PhiNode phi && !(phi instanceof ParmNode) ) {
            for( int i=1; i<n.nIns(); i++ )
                if( !phi.region().in(i)._type.isHigh() ) values.addAll(refs(sum,n.in(i)));
        } else if( n instanceof ReadOnlyNode || n instanceof GuardNode ||
                   n instanceof ConstructNode || n instanceof InitViewNode || n instanceof ConvertNode ) {
            values.addAll(refs(sum,n.in(1)));
        } else if( n instanceof ArrayInitNode ) {
            values.addAll(refs(sum,n.in(2)));
        } else {
            // Field-path/array-element origins go here. A parameter's child may
            // have a different owner; never silently substitute the base owner.
            sum.reject("reference origin "+n.getClass().getSimpleName()+" #"+n._nid);
        }
        sum._resolving.clear(n._nid);
        sum._values.put(n,values);
        return values;
    }

    // Apply an already-built callee summary to this particular call.  Translate
    // callee Sites into caller Sites: formal -> refs(actual), static -> static,
    // escaping allocation -> fresh Site tagged with this call.  This copies
    // analysis objects, not runtime objects or IR.  Private callee scratch stays
    // in the callee.  A second call gets distinct Sites for its fresh allocations.
    // Then translate field edges and returned targets through the same map.
    // For helper(h) { h.c = new Thing; }, calling helper(a) adds a.c -> call/new.
    // The map values are SETS because an actual may select several objects.
    // Callee result/argument owner bits are relative to its signature; do not
    // copy them into the caller.  build's closure derives the caller's owners
    // from the translated edges and uses of the result.  Only static is absolute.
    private HashSet<Site> compose(Summary sum, CallNode call) {
        HashSet<Site> result=sum._results.get(call);
        if( result!=null ) return result;
        result=new HashSet<>();
        sum._results.put(call,result);
        Summary callee=target(call);
        if( callee==null || !callee.supported() ) {
            sum.reject("unavailable allocation summary at call #"+call._nid);
            return result;
        }
        IdentityHashMap<Site,HashSet<Site>> map=new IdentityHashMap<>();
        map.put(callee._static,set(sum._static));
        // Establish all substitutions first, so forward/cyclic field edges can
        // be copied without depending on Site visitation order.
        for( Site site : callee._sites ) {
            if( site._arg>=2 ) {
                if( site._arg>=call.nIns()-1 ) {
                    sum.reject("missing argument at call #"+call._nid);
                    map.put(site,new HashSet<>());
                } else map.put(site,refs(sum,call.arg(site._arg)));
            } else if( site._allocation!=null && !site._owners.isEmpty() ) {
                Site copy=new Site(site._allocation,0,"call#"+call._nid+"/"+site._name);
                // Return/argument owners are substituted by edges in this caller.
                if( site._owners.get(STATIC) ) copy._owners.set(STATIC);
                sum._sites.add(copy);
                map.put(site,set(copy));
            }
        }
        // Retention effects exist even when the function returns a scalar.
        for( Site site : callee._sites ) {
            Set<Site> dst=map.get(site);
            if( dst==null ) continue;
            for( var field : site._fields.entrySet() ) for( Site child : field.getValue() ) {
                Set<Site> src=map.get(child);
                if( src==null ) sum.reject("unmapped retained storage at call #"+call._nid);
                else edge(dst,field.getKey(),src);
            }
        }
        for( Site site : callee._returns ) {
            Set<Site> returned=map.get(site);
            if( returned==null ) sum.reject("unmapped returned storage at call #"+call._nid);
            else result.addAll(returned);
        }
        return result;
    }

    @Override public String toString() {
        StringBuilder out = new StringBuilder("Allocation destinations (analysis only; no stack lowering)\n");
        for( Summary sum : _postorder ) {
            out.append("\n+-- ").append(sum._fun.label()).append("\n");
            ArrayList<CallNode> calls=new ArrayList<>(sum._calls);
            calls.sort(Comparator.comparingInt(n -> n._nid));
            for( CallNode call : calls ) {
                Summary callee=target(call);
                out.append("|   call#").append(call._nid).append(" -> ")
                    .append(callee==null ? "?" : callee._fun.label()).append("\n");
            }
            if( !sum.supported() ) {
                out.append("`-- REJECT: ").append(sum._reject).append("\n");
                continue; // Do not display a partial plan as a safe placement.
            }
            ArrayList<Site> sites=new ArrayList<>(sum._sites);
            sites.sort(Comparator.comparing(s -> s._name));
            for( Site site : sites ) {
                out.append("|   [").append(site.destination()).append("] ").append(site._name);
                if( site._allocation!=null ) out.append(" (").append(site._bytes).append(" bytes)");
                out.append("\n");
                // Stable debugging order belongs here, not in the analysis maps.
                ArrayList<String> edges=new ArrayList<>();
                for( var field : site._fields.entrySet() ) for( Site child : field.getValue() )
                    edges.add("|      ."+field.getKey()+" --> "+child._name+"\n");
                Collections.sort(edges);
                for( String edge : edges ) out.append(edge);
            }
            out.append("`-- return");
            ArrayList<String> returns=new ArrayList<>();
            for( Site site : sum._returns ) returns.add(site._name);
            Collections.sort(returns);
            for( String name : returns ) out.append(" -> ").append(name);
            out.append("\n");
        }
        return out.toString();
    }
}
