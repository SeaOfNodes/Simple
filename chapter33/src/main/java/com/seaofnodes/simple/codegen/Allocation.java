package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.parser.Parser;
import java.util.*;

/** First allocation-lifetime experiment. Builds plans, not machine allocations.
 * One DFS of the call DAG: discover callees on entry, compose their symbolic
 * summaries on exit. No call-tree expansion, caller rewalk, or IR mutation.
 * Within a summary, reference edges propagate destination requirements with a
 * small worklist. A rejected summary is never a usable allocation plan.
 */
public final class Allocation {
    private final Summary[] _summaries;
    private final Summary _entrySummary;
    private final ArrayList<Summary> _postorder = new ArrayList<>();
    public final ArrayList<ArrayDelete> _deletes = new ArrayList<>();

    // Destination bits: result, static storage, Call/Parm input indices, then
    // borrowed field paths. A child's storage need not share its holder's owner.
    private static final int RESULT=0, STATIC=1;

    /** One abstract storage object. Imported allocations are distinct per call
     *  site; a parameter denotes borrowed storage, not an allocation to move. */
    public static final class Site {
        public final NewNode _allocation; // null for borrowed/static storage or unloaded allocations
        public final int _arg;            // >=2: Parm index, -1: static, 0: fresh/path
        public final String _name;
        public final long _bytes;         // snapshot before later IR rewrites
        public final boolean _fresh;      // also true for allocations in unloaded bodies
        private final boolean _buddy;
        public final BitSet _owners = new BitSet();
        public final HashMap<String,HashSet<Site>> _fields = new HashMap<>();
        public Summary _frame;           // activation creating this object
        public Summary _scope;           // scratch frame, null for an external destination
        private CFGNode _birth;          // allocation control, or importing call
        private final ArrayList<String> _destinations;
        private final HashMap<String,Site> _loads = new HashMap<>();
        Site _base;                      // borrowed field origin, e.g. arg3.c
        String _field;
        private int _depth;

        private Site(Summary sum, NewNode allocation, int arg, String name) {
            this(sum,allocation,arg,name,
                 allocation!=null && allocation.size()._type instanceof TypeInteger size && size.isConstant() ? size.value() : -1,
                 allocation!=null,allocation!=null && allocation._deletable);
        }
        private Site(Summary sum, NewNode allocation, int arg, String name, long bytes, boolean fresh, boolean buddy) {
            _allocation=allocation; _arg=arg; _name=name;
            _destinations=sum._destinations;
            _bytes=bytes; _fresh=fresh; _buddy=buddy;
            if( arg>=2 ) _owners.set(arg);
            if( arg==-1 ) _owners.set(STATIC);
        }

        public String destination() {
            if( buddy() ) return "buddy";
            if( _owners.isEmpty() ) return _scope==null || _scope._loop==null ? "frame" : _scope.name()+" iteration";
            SB sb = new SB();
            if( _owners.get(RESULT) ) sb.p("result & ");
            if( _owners.get(STATIC) ) sb.p("static & ");
            ArrayList<String> names = new ArrayList<>();
            for( int i=_owners.nextSetBit(2); i>=0; i=_owners.nextSetBit(i+1) )
                names.add(_destinations.get(i));
            Collections.sort(names); // Paths are discovered in arbitrary order.
            for( String name : names ) sb.p(name).p(" & ");
            return sb.unchar(3).toString();
        }

        public boolean buddy() { return _buddy; }
    }

    /** Symbolic summary, with separate destinations for separate arguments.
     *  Object fields are weak updates: all stored references remain possible.
     *  Borrowed field paths describe incoming contents separately from stores. */

    // An activation summary: a function or one loop iteration. Loops share their
    // enclosing function's SSA/Site vocabulary, so composition needs no cloning
    // or argument substitution. Calls substitute that vocabulary in compose().
    public static final class Summary {
        public final FunNode _fun;        // owning function, also for loop frames
        public final LoopNode _loop;      // null for a function activation
        public Summary _parent;
        public final ArrayList<Summary> _frames = new ArrayList<>();
        public final ArrayList<Site> _sites = new ArrayList<>();
        // Ports map formal/output identity to its value. Loop Phis are entry
        // and continuation ports; captured SSA values name themselves. Memory
        // ports are included; their retention effects are summarized below.
        public final IdentityHashMap<Node,Node> _entry = new IdentityHashMap<>();
        public final IdentityHashMap<Node,Node> _exit = new IdentityHashMap<>();
        public final IdentityHashMap<Node,Node> _next = new IdentityHashMap<>();
        public final HashSet<Site> _returns = new HashSet<>();
        public final HashSet<Site> _retained = new HashSet<>();
        public final HashSet<Site> _continues = new HashSet<>();
        public final HashSet<Site> _scratch = new HashSet<>();
        public final ArrayList<ArrayDelete.Consume> _consumes = new ArrayList<>();
        boolean _opaqueEffects; // raw/native effects absent from the field graph
        private final HashSet<Site> _escapes = new HashSet<>();
        public String _reject;
        private byte _state; // 0 pre/unseen, 1 mid/active DFS, 2 post/complete
        private byte _ownershipState;
        private boolean _ownershipCycle;
        // All allocation-interesting Nodes; Calls, Stores, News, Ptr ops
        private final ArrayList<Node> _nodes = new ArrayList<>();
        // Just the Calls
        private final ArrayList<CallNode> _calls = new ArrayList<>();
        // Pointer expression -> possible storage objects, in this function's vocabulary.
        private final IdentityHashMap<    Node,HashSet<Site>> _values;
        // Call -> returned storage after substituting actuals and closing effects.
        private final IdentityHashMap<CallNode,HashSet<Site>> _results;
        private final BitSet _resolved;
        private final BitSet _raw; // Scalar expressions feeding lexical lifetime endpoints
        private final IdentityHashMap<LoopNode,Summary> _loops = new IdentityHashMap<>();
        private final ArrayList<String> _destinations;
        private Site _static;
        private int _pathLimit; // own reads plus the path depths imported at calls
        private boolean _changed;

        private Summary(FunNode fun) {
            _fun=fun; _loop=null;
            _values=new IdentityHashMap<>();
            _results=new IdentityHashMap<>();
            _resolved=new BitSet();
            _raw=new BitSet();
            _destinations=new ArrayList<>(List.of("result","static"));
            _static=new Site(this,null,-1,"static");
        }
        private Summary(Summary fun, LoopNode loop) {
            _fun=fun._fun; _loop=loop;
            _values=fun._values; _results=fun._results; _resolved=fun._resolved;
            _raw=fun._raw;
            _destinations=fun._destinations; _static=fun._static;
        }
        public String name() { return _loop!=null ? "loop#"+_loop._nid : _fun==null ? "saved contract" : _fun.label(); }
        // Hit the end of summary findings with no errors.
        public boolean supported() { return _state==2 && _reject==null; }
        // Throw or return valid summary
        public Summary requireSupported() {
            if( !supported() ) throw new IllegalStateException("Allocation "+name()+": "+_reject);
            return this;
        }
        // Collect first rejection
        private void reject(String why) { if( _reject==null ) _reject=why; }
    }

    public Allocation(CodeGen code) {
        _summaries = new Summary[code._linker._len];
        Summary entry=null;
        for( FunNode fun : code._linker )
            if( fun!=null && !fun.isDead() && !fun._type.isHigh() ) {
                Summary sum=new Summary(fun);
                _summaries[fun.sig().fidx()] = sum;
                if( code.entryClinitName().equals(fun._name) ) entry=sum;
            }
        _entrySummary=entry;
        for( Summary sum : _summaries )
            if( sum!=null )
                visit(sum);
        // Callees precede callers. Discharged requirements disappear; incoming
        // ones become the caller's own contract. This is separate from placement.
        for( Summary sum : _postorder ) ownership(sum);
    }

    private void ownership(Summary sum) {
        if( sum._ownershipState==2 ) return;
        if( sum._ownershipState==1 ) { sum._ownershipCycle=true; return; }
        sum._ownershipState=1;
        for( CallNode call : sum._calls ) {
            if( call.fptr()._type instanceof TypeFunPtr fp && !XInt.isHigh(fp.fidxs()) )
                for( int fidx=XInt.next(fp.fidxs(),0); fidx>=0; fidx=XInt.next(fp.fidxs(),fidx) ) {
                    Summary callee=fidx<_summaries.length ? _summaries[fidx] : null;
                    if( callee!=null ) { ownership(callee); sum._ownershipCycle |= callee._ownershipCycle; }
                }
        }
        for( CallNode call : sum._calls ) {
            if( Intrinsic.arrayDelete(call) && Intrinsic.arrayDeleteType(call.tfp()) && call.nargs()==1 )
                consume(sum,new ArrayDelete(this,sum._nodes,call,2));
            else if( call.fptr()._type instanceof TypeFunPtr fp && !XInt.isHigh(fp.fidxs()) )
                for( int fidx=XInt.next(fp.fidxs(),0); fidx>=0; fidx=XInt.next(fp.fidxs(),fidx) ) {
                    Summary callee=fidx<_summaries.length ? _summaries[fidx] : null;
                    if( callee!=null )
                        for( ArrayDelete.Consume requirement : new ArrayList<>(callee._consumes) )
                            consume(sum,new ArrayDelete(this,sum._nodes,call,requirement));
                }
        }
        sum._ownershipState=2;
    }

    private void consume(Summary sum, ArrayDelete check) {
        // Recursive SCC heads need the same entry/outgoing ownership boundary
        // as loop heads. Their allocation graphs are not built yet; do not
        // assume a consuming requirement also proves a unique outgoing field.
        if( sum._ownershipCycle ) check._reject="recursive consuming contracts are not supported";
        _deletes.add(check);
        if( check._reject==null && check._requirement!=null && !sum._consumes.contains(check._requirement) )
            sum._consumes.add(check._requirement);
    }

    void checkOwnership() {
        // A first-class callback type does not yet carry consuming paths.
        // Do not let it erase a requirement established on a concrete function.
        for( Summary sum : _postorder ) for( CallNode call : sum._calls ) {
            TypeFunPtr target=call.fptr()._type instanceof TypeFunPtr fp ? fp : null;
            int first=target!=null && target.nargs()>0 && target.arg(0)==TypePtr.PTR ? 3 : 2;
            for( int i=first; i<call.nIns()-1; i++ ) {
                Type arg=call.arg(i)._type;
                int[] callbacks=arg instanceof TypeFunPtr fp ? fp.fidxs() :
                    nativeCall(call) && arg instanceof TypeMemPtr ? TypeMem.START.escapesFrom(arg)._escFs : XInt.EMPTY;
                if( callbacks!=XInt.EMPTY ) for( Summary callee : _summaries )
                    if( callee!=null && !callee._consumes.isEmpty() && XInt.bit(callbacks,callee._fun.sig().fidx()) )
                        throw Parser.error("Consuming callback requires an explicit ownership contract",call._loc);
                if( (target==null || XInt.isHigh(target.fidxs())) && deletableStorage(arg,new HashSet<>()) )
                    throw Parser.error("Unknown call cannot verify ownership of deletable storage",call._loc);
            }
        }
    }

    private static boolean deletableStorage(Type t, HashSet<Type> seen) {
        if( !seen.add(t) || !(t instanceof TypeMemPtr ptr) ) return false;
        if( ptr._del ) return true;
        if( ptr._obj._fields!=null )
            for( Field field : ptr._obj._fields ) if( deletableStorage(field._t,seen) ) return true;
        return false;
    }

    public Summary summary(String name) {
        for( Summary sum : _summaries )
            if( sum!=null && name.equals(sum._fun._name) ) return sum;
        throw new IllegalArgumentException("No allocation summary for "+name);
    }

    Summary summary(FunNode fun) { return _summaries[fun.sig().fidx()]; }

    // Boundary contracts contain symbolic storage, never graph node IDs or
    // implementation frames. Site zero is static storage. Local scratch is
    // omitted; only external destinations and their reference graph survive.
    static void write(BAOS out, Summary sum) {
        assert sum._state==2;
        out.packed4(sum._consumes.size());
        for( ArrayDelete.Consume consume : sum._consumes ) {
            out.packed4(consume.arg()); out.packed4(consume.path().size());
            for( String field : consume.path() ) out.packedS(field);
        }
        out.packed1(sum._opaqueEffects ? 1 : 0);
        out.packedS(sum._reject==null ? "" : sum._reject);
        if( !sum.supported() ) return;
        out.packed4(sum._destinations.size());
        for( String name : sum._destinations ) out.packedS(name);
        ArrayList<Site> sites=new ArrayList<>();
        HashMap<Site,Integer> ids=new HashMap<>();
        ids.put(sum._static,0);
        sites.add(sum._static);
        for( Site site : sum._sites )
            if( site!=sum._static && !site._owners.isEmpty() ) {
                ids.put(site,sites.size()); sites.add(site);
            }
        // Include field/path dependencies even when they have no destination
        // of their own. Assign IDs before writing so cycles need no recursion.
        for( int i=0; i<sites.size(); i++ ) {
            Site site=sites.get(i);
            if( site._base!=null && !ids.containsKey(site._base) ) {
                ids.put(site._base,sites.size()); sites.add(site._base);
            }
            for( HashSet<Site> values : site._fields.values() )
                for( Site child : values ) if( !ids.containsKey(child) ) {
                    ids.put(child,sites.size()); sites.add(child);
                }
        }
        out.packed4(sites.size());
        for( Site site : sites ) {
            out.packed4(site._arg); out.packedS(site._name);
            out.packed8(site._bytes);
            out.packed1((site._fresh ? 1 : 0) | (site.buddy() ? 2 : 0));
            out.packed4(site._owners.cardinality());
            for( int bit=site._owners.nextSetBit(0); bit>=0; bit=site._owners.nextSetBit(bit+1) ) out.packed4(bit);
        }
        for( Site site : sites ) {
            out.packed4(site._base==null ? -1 : ids.get(site._base));
            if( site._base!=null ) out.packedS(site._field);
            out.packed4(site._depth);
            out.packed4(site._fields.size());
            for( var field : site._fields.entrySet() ) {
                out.packedS(field.getKey()); out.packed4(field.getValue().size());
                for( Site child : field.getValue() ) out.packed4(ids.get(child));
            }
        }
        out.packed4(sum._returns.size());
        for( Site site : sum._returns ) out.packed4(ids.get(site));
    }

    static Summary read(BAOS in) {
        Summary sum=new Summary((FunNode)null);
        sum._state=2;
        int consumes=in.packed4();
        for( int i=0; i<consumes; i++ ) {
            int arg=in.packed4(), count=in.packed4();
            ArrayList<String> path=new ArrayList<>();
            for( int j=0; j<count; j++ ) path.add(in.packedS());
            sum._consumes.add(new ArrayDelete.Consume(arg,path));
        }
        sum._opaqueEffects=in.packed1()!=0;
        String reject=in.packedS();
        if( !reject.isEmpty() ) { sum.reject(reject); return sum; }
        sum._destinations.clear();
        int names=in.packed4();
        for( int i=0; i<names; i++ ) sum._destinations.add(in.packedS());
        int count=in.packed4();
        for( int i=0; i<count; i++ ) {
            int arg=in.packed4(); String name=in.packedS();
            long bytes=in.packed8(); int flags=in.packed1();
            Site site=new Site(sum,null,arg,name,bytes,(flags&1)!=0,(flags&2)!=0);
            site._owners.clear();
            int owners=in.packed4();
            for( int j=0; j<owners; j++ ) site._owners.set(in.packed4());
            sum._sites.add(site);
        }
        sum._static=sum._sites.get(0);
        for( Site site : sum._sites ) {
            int base=in.packed4();
            if( base>=0 ) {
                site._base=sum._sites.get(base); site._field=in.packedS();
                site._base._loads.put(site._field,site);
            }
            site._depth=in.packed4();
            int fields=in.packed4();
            for( int j=0; j<fields; j++ ) {
                String field=in.packedS(); int size=in.packed4();
                HashSet<Site> values=new HashSet<>();
                for( int k=0; k<size; k++ ) values.add(sum._sites.get(in.packed4()));
                site._fields.put(field,values);
            }
        }
        int returns=in.packed4();
        for( int i=0; i<returns; i++ ) sum._returns.add(sum._sites.get(in.packed4()));
        // Retention is represented by stores through incoming/static sites.
        for( Site site : sum._sites ) if( !site._fresh )
            for( HashSet<Site> children : site._fields.values() ) sum._retained.addAll(children);
        return sum;
    }

    public void requireSupported() {
        for( Summary sum : _postorder )
            sum.requireSupported();
    }

    /** Analysis-only deletion checkpoint. No storage is freed. A proof about
     *  incoming storage also reports the unique-entry contract still required. */
    public ArrayDelete checkArrayDelete(CallNode call, int arg) {
        for( Summary sum : _postorder )
            if( sum._calls.contains(call) ) return new ArrayDelete(this,sum._nodes,call,arg);
        throw new IllegalArgumentException("Deletion checkpoint is not in an analyzed body");
    }

    // A deliberately narrow helper contract: pointer fields are not changed,
    // and the result is scalar or forwards known arguments. Scalar element
    // copies, including loops, qualify. Scalar results need only effects;
    // reference results also require an allocation contract for their origins.
    boolean arrayBorrow(CallNode call) {
        // A nonreturning call contributes no retained references to its caller.
        if( call.cend()._type instanceof TypeTuple tt && tt.mem()==TypeMem.TOP ) return true;
        if( nativeCall(call) ) return false; // No pointer-memory effect contract yet.
        Summary sum=target(call);
        if( sum==null || sum._fun._extern ) return false;
        Type ret=sum._fun.ret().expr()._type;
        if( !(ret instanceof TypeInteger || ret instanceof TypeFloat) && arrayResult(call)==null ) return false;
        return noReferenceEffects(sum._fun);
    }

    // Direct argument forwarding is enough for copy/identity helpers. Field
    // paths and fresh return storage are serialized too, but their deletion
    // proofs need richer memory-origin queries before allowing these borrows.
    ArrayList<Node> arrayResult(CallNode call) {
        Summary sum=target(call);
        if( sum==null || !sum.supported() ) return null;
        ArrayList<Node> args=new ArrayList<>();
        for( Site site : sum._returns ) {
            if( site._arg<2 || site._arg>=call.nIns()-1 ) return null;
            args.add(call.arg(site._arg));
        }
        return args;
    }

    // Repeated consumption also needs an exclusive outgoing slot. Consuming
    // the entry value alone does not promise that: a helper could publish its
    // replacement into a second field before returning. The existing boundary
    // graph (including imported graphs) suffices for this narrow proof.
    boolean arrayOwned(CallNode call, ArrayDelete.Consume consume) {
        return arrayOwned(target(call),consume);
    }

    private static boolean arrayOwned(Summary sum, ArrayDelete.Consume consume) {
        if( sum==null || !sum.supported() || sum._opaqueEffects || consume.path().isEmpty() ) return false;
        Site owner=null;
        for( Site site : sum._sites ) if( site._arg==consume.arg() ) owner=site;
        if( owner==null ) return false;
        for( int i=0; i<consume.path().size()-1; i++ ) {
            String field=consume.path().get(i);
            Set<Site> stored=owner._fields.get(field);
            Site incoming=owner._loads.get(field);
            if( stored!=null && !stored.isEmpty() ) {
                if( stored.size()!=1 || incoming!=null && !stored.contains(incoming) ) return false;
                owner=stored.iterator().next();
            } else owner=incoming;
            if( owner==null ) return false;
        }
        String field=consume.path().getLast();
        HashSet<Site> values=new HashSet<>();
        Set<Site> stored=owner._fields.get(field);
        if( stored!=null ) values.addAll(stored);
        Site incoming=owner._loads.get(field);
        if( incoming!=null ) values.add(incoming);
        for( Site value : values ) {
            if( value!=incoming && !value._fresh || sum._returns.contains(value) ) return false;
            for( Site site : sum._sites ) for( var edge : site._fields.entrySet() )
                if( edge.getValue().contains(value) && (site!=owner || !edge.getKey().equals(field)) ) return false;
        }
        return true;
    }

    boolean arrayBorrow(CLInitNode init) {
        return init.bypass() || noReferenceEffects(CodeGen.CODE.link(init.cu()));
    }

    private static boolean noReferenceEffects(FunNode fun) {
        FunctionEffects effects=CodeGen.CODE._effects.get(fun);
        return effects!=null && !effects._referenceWrites;
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
        if( sum._fun.summary()!=null && sum._fun.summary()._allocation!=null ) {
            Summary contract=sum._fun.summary()._allocation;
            sum._reject=contract._reject;
            sum._destinations.clear(); sum._destinations.addAll(contract._destinations);
            sum._static=contract._static;
            sum._sites.addAll(contract._sites);
            sum._returns.addAll(contract._returns);
            sum._retained.addAll(contract._retained);
            sum._consumes.addAll(contract._consumes);
            sum._opaqueEffects=contract._opaqueEffects;
        } else if( sum._fun._extern || sum._fun.summary()!=null ) {
            sum.reject("opaque body: allocation contract unavailable");
        } else {
            // Collection interesting function body and call sites
            collect(sum);
            for( Node n : sum._nodes )
                if( n instanceof PtrToIntNode || n instanceof CLInitNode init && !arrayBorrow(init) ) sum._opaqueEffects=true;
            for( CallNode call : sum._calls ) {
                if( nativeCall(call) ) {
                    if( !Intrinsic.pure(call) && !Intrinsic.arrayDelete(call) &&
                        !(call.cend()._type instanceof TypeTuple tt && tt.mem()==TypeMem.TOP) ) sum._opaqueEffects=true;
                    // Native code borrows its arguments for this call only.
                    // Returned storage still needs an explicit origin contract.
                    Type ret=call.tfp().ret();
                    if( !(ret instanceof TypeInteger || ret instanceof TypeFloat) )
                        sum.reject("native result storage has no allocation contract at call #"+call._nid);
                    continue;
                }
                Summary callee = target(call);
                if( callee==null ) sum.reject("opaque or indirect call #"+call._nid);
                else {
                    // Recursive walk Call Graph DAG
                    visit(callee);
                    sum._opaqueEffects |= callee._opaqueEffects;
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

    Summary target(CallNode call) {
        if( !(call.fptr()._type instanceof TypeFunPtr fp) || !fp.isConstant() ) return null;
        int idx=fp.fidx();
        return idx>=0 && idx<_summaries.length ? _summaries[idx] : null;
    }

    // Unlike external(), this excludes unknown targets and mixed Simple/native
    // target sets. Every possible target must obey the native borrowing rule.
    private static boolean nativeCall(CallNode call) {
        if( !(call.fptr()._type instanceof TypeFunPtr fp) || XInt.isHigh(fp.fidxs()) || fp.nfcns()==0 ) return false;
        for( int idx=XInt.next(fp.fidxs(),-1); idx>=0; idx=XInt.next(fp.fidxs(),idx) )
            if( CodeGen.CODE.externFunc(idx)==null ) return false;
        return true;
    }

    // Follow control, memory, and references, not arithmetic/offset expressions.
    // Find allocations at control anchors too: their only remaining use might
    // be a scalar load or a raw address buried under arithmetic.  Don't walk into
    // other functions, so no following linked Calls or Parms.
    private void collect(Summary sum) {
        BitSet seen = new BitSet();
        ArrayDeque<Node> work = new ArrayDeque<>();
        // Start at both ends: zero-exit loops cannot be reached from Return.
        work.push(sum._fun.ret());
        work.push(sum._fun);
        // Repeat until entire function is walked
        while( !work.isEmpty() ) {
            Node n = work.pop();
            if( seen.get(n._nid) ) continue; // Been there, done that
            seen.set(n._nid);
            // Add to function body
            sum._nodes.add(n);
            if( n instanceof CFGNode cfg && !(cfg instanceof ReturnNode) &&
                (cfg==sum._fun || !cfg.isFunLeaf()) )
                for( Node use : cfg._outputs )
                    if( use instanceof CFGNode next && !cfg.skip(next) ) work.push(next);
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
            if( n instanceof LoopNode loop ) sum._loops.put(loop,new Summary(sum,loop));

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
        keepOrigins(sum,seen);
    }

    // Follow the scope endpoint backwards, not all uses of an integer address.
    // Memory round trips and wrapper calls need no provenance: the original
    // conversion is kept here until its own lexical scope ends.
    private static void keepOrigins(Summary sum, BitSet seen) {
        ArrayDeque<Node> work=new ArrayDeque<>();
        for( Node n : sum._nodes ) if( n instanceof KeepAliveNode ) work.push(n.in(1));
        while( !work.isEmpty() ) {
            Node n=work.pop();
            if( pointer(n) || n instanceof ConstantNode || n instanceof CFGNode ||
                n instanceof LoadNode || n.isFunLeaf() ||
                n instanceof ProjNode || sum._raw.get(n._nid) ) continue;
            sum._raw.set(n._nid);
            if( !seen.get(n._nid) ) { seen.set(n._nid); sum._nodes.add(n); }
            for( Node in : n._inputs ) if( in!=null ) work.push(in);
        }
    }
    // Post-order work during the Call Graph visit
    private void build(Summary sum) {
        loopFrames(sum);
        for( Node n : sum._nodes ) {
            // Gather allocation sites
            if( n instanceof NewNode alloc ) {
                if( !alloc._deletable && (!(alloc.size()._type instanceof TypeInteger size) || !size.isConstant()) )
                    sum.reject("variable-size allocation #"+alloc._nid);
                Site site = new Site(sum,alloc,0,"new#"+alloc._nid+":"+alloc._ts._name);
                site._birth=(CFGNode)alloc.ctrl();
                if( TypeStruct.startsClzPrefix(alloc._ts._name) ) site._owners.set(STATIC);
                sum._sites.add(site);
                sum._values.put(alloc,set(site));
            }
            if( n instanceof ParmNode parm && pointer(parm) ) {
                if( parm.fun()!=sum._fun ) sum.reject("foreign parameter #"+parm._nid);
                while( sum._destinations.size()<=parm._idx )
                    sum._destinations.add("arg"+sum._destinations.size());
                Site site = new Site(sum,null,parm._idx,"arg"+parm._idx+":"+parm.label());
                sum._sites.add(site);
                sum._values.put(parm,set(site));
            }
            if( n instanceof LoadNode && pointer(n) ) sum._pathLimit++;
        }
        // Import fresh storage once per call, even though its reference edges
        // may need several local rounds. No repeated walk of a callee's IR.
        IdentityHashMap<CallNode,IdentityHashMap<Site,HashSet<Site>>> imports=new IdentityHashMap<>();
        for( CallNode call : sum._calls ) {
            if( nativeCall(call) ) continue;
            Summary callee=target(call);
            IdentityHashMap<Site,HashSet<Site>> map=new IdentityHashMap<>();
            imports.put(call,map);
            sum._results.put(call,new HashSet<>());
            map.put(callee._static,set(sum._static));
            int depth=0;
            for( Site site : callee._sites ) {
                depth=Math.max(depth,site._depth);
                if( site==callee._static ) continue;
                HashSet<Site> values=new HashSet<>();
                if( site._fresh && !site._owners.isEmpty() ) {
                    Site copy=new Site(sum,site._allocation,0,"call#"+call._nid+"/"+site._name,site._bytes,true,site.buddy());
                    copy._birth=call;
                    if( site._owners.get(STATIC) ) copy._owners.set(STATIC);
                    sum._sites.add(copy);
                    values.add(copy);
                }
                map.put(site,values);
            }
            sum._pathLimit+=depth;
        }

        // Loads, stores, and call effects depend on each other. Solve their weak
        // unions before assigning owners: caching an early empty load would lose
        // a later-discovered store. Targets, fields, and call results grow
        // monotonically, including cyclic loop Phis. Each expression is visited
        // once per round; a cycle uses the targets discovered so far.
        do {
            sum._changed=false;
            sum._resolved.clear();
            for( CallNode call : sum._calls ) if( !nativeCall(call) ) compose(sum,call,imports.get(call));
            for( Node n : sum._nodes ) {
                if( pointer(n) || sum._raw.get(n._nid) ) refs(sum,n);
                if( n instanceof StoreNode store && pointer(store.val()) )
                    if( edge(refs(sum,store.ptr()),store._name,refs(sum,store.val())) ) sum._changed=true;
            }
            if( pointer(sum._fun.ret().expr()) && sum._returns.addAll(refs(sum,sum._fun.ret().expr())) ) sum._changed=true;
        } while( sum._changed && sum._reject==null );
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
        if( sum._reject==null ) {
            frameBoundaries(sum);
            summarizeFrame(sum,sum,imports);
            if( sum._reject==null ) placeFrames(sum);
        }
        // Bin packing goes here: fixed destinations become caller landing zones.
        // Free-list work goes here: only after checking remaining usable aliases.
        // Parallel growing tips go here: one owner may need several storage areas.
    }

    // n has pointer type
    private static boolean pointer(Node n) {
        return n!=null && (n._type instanceof TypeMemPtr || n._type instanceof TypePtr);
    }

    // LoopTree already establishes reducible nesting and each CFG's tightest
    // containing loop. Allocation only supplies the corresponding frame summaries.
    private static void loopFrames(Summary sum) {
        for( Summary frame : sum._loops.values() ) {
            CFGNode.LoopTree tree=frame._loop._ltree;
            assert tree!=null && tree._head==frame._loop && tree._par!=null;
            frame._parent=tree._par._head instanceof StartNode ? sum : sum._loops.get(tree._par._head);
            assert frame._parent!=null : "Missing enclosing loop summary";
            frame._parent._frames.add(frame);
        }
    }

    // Everything reachable through stored references. Used both for backedge
    // safety and for retaining a winner's entire object graph on loop exit.
    private static HashSet<Site> reachable(HashSet<Site> roots) {
        HashSet<Site> seen=new HashSet<>(roots);
        ArrayDeque<Site> work=new ArrayDeque<>(roots);
        while( !work.isEmpty() )
            for( HashSet<Site> children : work.pop()._fields.values() )
                for( Site child : children )
                    if( seen.add(child) ) work.push(child);
        return seen;
    }

    // A store to storage not born in this iteration can carry its value into
    // the next iteration. Stores within an iteration's private graph are fine;
    // a surviving root will bring that entire graph into reachable() instead.
    private static void retained(HashSet<Site> dst, HashSet<Site> src,
                                 HashSet<Site> born, HashSet<Site> carried) {
        for( Site base : dst )
            if( !born.contains(base) ) { carried.addAll(src); return; }
    }

    // Memory reaching the backedge exposes stores that a pointer-Phi check alone
    // misses, e.g. an array allocated before the loop collecting each candidate.
    // Stop at this header: entry memory and exit-only stores do not execute on
    // the repeated path. Weak updates deliberately do not kill earlier stores.
    private static ArrayList<Node> backMemory(LoopNode loop) {
        ArrayList<Node> nodes=new ArrayList<>();
        BitSet seen=new BitSet();
        ArrayDeque<Node> work=new ArrayDeque<>();
        for( Node use : loop._outputs )
            if( use instanceof PhiNode phi && phi.region()==loop && phi._type instanceof TypeMem )
                work.push(phi.in(2));
        while( !work.isEmpty() ) {
            Node n=work.pop();
            if( seen.get(n._nid) ) continue;
            seen.set(n._nid);
            if( n instanceof PhiNode phi && phi.region()==loop ) continue;
            if( n instanceof ParmNode || n.isFunLeaf() ) continue;
            nodes.add(n);
            if( n instanceof ProjNode proj ) work.push(proj.in(0));
            else if( n instanceof CallEndNode end ) work.push(end.call());
            else for( Node in : n._inputs )
                if( in!=null && in._type instanceof TypeMem ) work.push(in);
        }
        return nodes;
    }

    private static Summary enclosing(Summary sum, CFGNode ctrl) {
        if( ctrl==null || ctrl._type.isHigh() ) return sum;
        assert ctrl._ltree!=null : "Missing LoopTree for CFG #"+ctrl._nid;
        if( ctrl._ltree._head instanceof StartNode ) return sum;
        Summary frame=sum._loops.get(ctrl._ltree._head);
        assert frame!=null : "Missing loop summary for CFG #"+ctrl._nid;
        return frame;
    }

    // Earliest control for a boundary value/use, without scheduling or changing
    // the IR. New's pointer AND private-memory projections inherit its control.
    // Floating scalar calculations can finish before returning from a frame;
    // only their scalar result then crosses the boundary.
    static CFGNode control(Node n, IdentityHashMap<Node,CFGNode> cache, BitSet active) {
        if( n instanceof CFGNode cfg ) return cfg;
        if( n instanceof PhiNode phi ) return phi.region();
        if( n.isFunLeaf() ) return null;
        if( cache.containsKey(n) ) return cache.get(n);
        if( active.get(n._nid) ) return CFGNode.safeCFG(n);
        active.set(n._nid);
        CFGNode ctrl=null;
        for( Node in : n._inputs )
            if( in!=null ) ctrl=CFGNode.deepest(ctrl,control(in,cache,active));
        active.clear(n._nid);
        cache.put(n,ctrl);
        return ctrl;
    }

    // Export a value from its producing activation to the common ancestor,
    // then capture it down to the consuming activation. These explicit ports,
    // not a later placement scan of uses, define what must survive cleanup.
    private static void crossing(Node value, Summary from, Summary to) {
        Summary common=from;
        while( true ) {
            Summary up=to;
            while( up!=null && up!=common ) up=up._parent;
            if( up!=null ) break;
            common=common._parent;
        }
        for( Summary frame=from; frame!=common; frame=frame._parent ) frame._exit.put(value,value);
        for( Summary frame=to; frame!=common; frame=frame._parent ) frame._entry.put(value,value);
    }

    // Build the function/iteration interfaces in the existing SSA vocabulary.
    // A loop's backedge is a return into its next activation, with Phi inputs
    // supplying the arguments. Breaks/fallout export through the normal interface.
    private void frameBoundaries(Summary sum) {
        IdentityHashMap<Node,CFGNode> controls=new IdentityHashMap<>();
        BitSet active=new BitSet();
        for( Site site : sum._sites ) if( site._birth!=null ) {
            site._frame=enclosing(sum,site._birth);
            for( Summary frame=site._frame; frame!=sum; frame=frame._parent ) frame._sites.add(site);
        }
        for( Summary frame : sum._loops.values() )
            for( Node use : frame._loop._outputs )
                if( use instanceof PhiNode phi && phi.region()==frame._loop ) {
                    frame._entry.put(phi,phi.in(1));
                    frame._next.put(phi,phi.in(2));
                }
        sum._exit.put(sum._fun.ret(),sum._fun.ret().expr());
        sum._exit.put(sum._fun.ret().mem(),sum._fun.ret().mem());
        for( Node n : sum._nodes ) if( n instanceof ParmNode ) sum._entry.put(n,n);
        if( sum._loops.isEmpty() ) return;
        for( Node n : sum._nodes ) {
            boundaryInputs(sum,n,controls,active);
            // collect() skips scalar arithmetic. Merge Phis still define exit
            // ports, including scalar results loaded from reclaimed scratch.
            if( n instanceof CFGNode )
                for( Node use : n._outputs )
                    if( use instanceof PhiNode phi && phi.region()==n ) boundaryInputs(sum,phi,controls,active);
        }
        // Scalar users were deliberately omitted by collect(). Their pointer
        // operands still need boundary ports (e.g. a load after an outside call).
        for( var value : sum._values.entrySet() ) {
            Node n=value.getKey();
            if( !pointer(n) || n instanceof ConstantNode ) continue;
            Summary producer=enclosing(sum,control(n,controls,active));
            for( Node use : n._outputs ) {
                if( use.isFunLeaf() || use instanceof PhiNode phi && phi.region() instanceof LoopNode ) continue;
                crossing(n,producer,enclosing(sum,control(use,controls,active)));
            }
        }
    }

    private static void boundaryInputs(Summary sum, Node n, IdentityHashMap<Node,CFGNode> controls, BitSet active) {
        if( n.isFunLeaf() || n instanceof PhiNode phi && phi.region() instanceof LoopNode ) return;
        Summary consumer=enclosing(sum,control(n,controls,active));
        for( Node in : n._inputs ) {
            if( in==null || in instanceof ConstantNode || in instanceof CFGNode ) continue;
            // Ordinary integer users do not extend the borrowing scope. Only
            // the endpoint and its expression carry raw-address obligations.
            if( sum._raw.get(in._nid) &&
                !(n instanceof KeepAliveNode) && !sum._raw.get(n._nid) ) continue;
            crossing(in,enclosing(sum,control(in,controls,active)),consumer);
        }
    }

    // The same summary closure for function returns and loop exits. Effects on
    // incoming storage escape the frame even without a returned pointer. Loops
    // additionally describe the continuation's values and memory effects.
    private void summarizeFrame(Summary sum, Summary frame,
                                IdentityHashMap<CallNode,IdentityHashMap<Site,HashSet<Site>>> imports) {
        frame._state=1;
        for( Summary child : frame._frames ) {
            summarizeFrame(sum,child,imports);
            if( child._reject!=null ) frame.reject(child._reject);
        }
        HashSet<Site> born=new HashSet<>();
        for( Site site : frame._sites ) if( site._fresh ) born.add(site);
        for( Node value : frame._exit.values() )
            if( frame._loop!=null || pointer(value) ) frame._returns.addAll(refs(sum,value));
        for( Site site : sum._sites )
            if( !born.contains(site) )
                for( HashSet<Site> children : site._fields.values() ) frame._retained.addAll(children);
        frame._escapes.addAll(frame._returns);
        frame._escapes.addAll(frame._retained);
        // Static allocations retain their absolute lifetime across substitution.
        for( Site site : born ) if( site._owners.get(STATIC) ) frame._escapes.add(site);
        frame._escapes.addAll(reachable(frame._escapes));
        if( frame._loop!=null ) {
            for( var port : frame._next.entrySet() )
                if( pointer(port.getKey()) || sum._raw.get(port.getKey()._nid) )
                    frame._continues.addAll(refs(sum,port.getValue()));
            for( Node n : backMemory(frame._loop) ) {
                if( n instanceof StoreNode store && pointer(store.val()) )
                    retained(refs(sum,store.ptr()),refs(sum,store.val()),born,frame._continues);
                if( n instanceof CallNode call && !nativeCall(call) ) {
                    var map=imports.get(call);
                    for( Site site : target(call)._sites )
                        for( HashSet<Site> children : site._fields.values() )
                            for( Site child : children ) retained(map.get(site),map.get(child),born,frame._continues);
                }
            }
            frame._continues.addAll(reachable(frame._continues));
            for( Site site : frame._continues )
                if( born.contains(site) && !site.buddy() ) {
                    frame.reject(frame.name()+" carries "+site._name+" across its backedge; collection/overlap destinations not implemented");
                    break;
                }
        }
        frame._state=2;
    }

    // Function and iteration cleanup use the same rule. Promote escaped storage
    // to the enclosing frame until its interface no longer exports that storage.
    // A function's external results already have symbolic owner requirements.
    // No RSP adjustment or actual reclamation is emitted by this planning pass.
    private static void placeFrames(Summary sum) {
        for( Site site : sum._sites ) if( site._frame!=null ) {
            // Buddy blocks are independent allocations, released by .del().
            // Keep tracking their references, but never put them in stack cleanup.
            if( site.buddy() ) { site._scope=site._frame; continue; }
            Summary scope=site._frame;
            while( scope!=null && scope._escapes.contains(site) ) scope=scope._parent;
            site._scope=scope;
            if( scope!=null ) scope._scratch.add(site);
        }
    }

    // One element Set of Site
    private static HashSet<Site> set(Site site) {
        HashSet<Site> set = new HashSet<>(); set.add(site); return set;
    }

    private static boolean edge(HashSet<Site> dst, String field, HashSet<Site> src) {
        if( src.isEmpty() ) return false;
        boolean changed=false;
        for( Site site : dst )
            changed |= site._fields.computeIfAbsent(field,k -> new HashSet<>()).addAll(src);
        return changed;
    }

    // Select a field's possible values, not its containing object's storage.
    // Fresh objects have only the stores we see. Borrowed objects also have an
    // incoming value, named by a path. That path is a separate borrowed Site,
    // substituted against the actual object's fields at each call.
    private HashSet<Site> field(Summary sum, HashSet<Site> bases, String name) {
        HashSet<Site> values=new HashSet<>();
        for( Site base : bases ) {
            if( !base._fresh ) {
                Site child=base._loads.get(name);
                if( child==null ) {
                    // Weak updates can invent an unbounded path even in straight
                    // line code: p.next=p.next.next. Stop at the body's finite
                    // read count rather than silently truncate possible origins.
                    // Memory-ordered field updates go here.
                    if( base._depth>=sum._pathLimit ) {
                        sum.reject("cyclic field-path expansion at "+base._name+"."+name);
                        continue;
                    }
                    String path=(base._arg>=2 ? "arg"+base._arg : base._name)+"."+name;
                    child=new Site(sum,null,0,path);
                    child._base=base; child._field=name; child._depth=base._depth+1;
                    if( base==sum._static || base._owners.get(STATIC) ) child._owners.set(STATIC);
                    else {
                        child._owners.set(sum._destinations.size());
                        sum._destinations.add(path);
                    }
                    base._loads.put(name,child);
                    sum._sites.add(child);
                    sum._changed=true;
                }
                values.add(child);
            }
            Set<Site> stored=base._fields.get(name);
            if( stored!=null ) values.addAll(stored);
        }
        return values;
    }

    // Which storage objects can this pointer expression denote?  This resolves
    // pointer VALUE flow, not the fields reachable through the pointer.  A Phi
    // unions possible targets; casts/views preserve targets; a call result uses
    // compose's caller-side targets; a load selects one field's targets.
    // New/Parm targets were seeded by build.
    // Example: refs(cond ? a : b) = {site(a), site(b)}.
    // build() uses these sets to add Store edges, then propagates owner requirements
    // through that separate object graph. Cyclic SSA values use the prior known
    // targets; the next local round propagates any newly discovered ones.
    private HashSet<Site> refs(Summary sum, Node n) {
        if( n==null || !(pointer(n) || sum._raw.get(n._nid)) ) return new HashSet<>();
        HashSet<Site> old = sum._values.computeIfAbsent(n,k -> new HashSet<>());
        if( n instanceof ParmNode || sum._resolved.get(n._nid) ) return old;
        sum._resolved.set(n._nid);
        HashSet<Site> values = new HashSet<>();
        if( n instanceof ConstantNode ) {
            // Zero/null carries no storage obligation.  Other constant objects
            // have static backing; field() handles their incoming contents.
            if( n._type!=Type.NIL && n._type!=TypeInteger.ZERO ) values.add(sum._static);
        } else if( n instanceof ProjNode proj && proj.in(0) instanceof NewNode alloc && proj._idx==0 ) {
            Set<Site> sites = sum._values.get(alloc);
            if( sites==null ) sum.reject("foreign allocation #"+alloc._nid);
            else values.addAll(sites);
        } else if( n instanceof ProjNode proj && proj.in(0) instanceof CallEndNode end && proj._idx==2 ) {
            Set<Site> result=sum._results.get(end.call());
            if( result==null ) sum.reject("foreign call #"+end.call()._nid);
            else values.addAll(result);
        } else if( n instanceof LoadNode load ) {
            values.addAll(field(sum,refs(sum,load.ptr()),load._name));
        } else if( n instanceof PhiNode phi && !(phi instanceof ParmNode) ) {
            for( int i=1; i<n.nIns(); i++ )
                if( !phi.region().in(i)._type.isHigh() ) values.addAll(refs(sum,n.in(i)));
        } else if( n instanceof ReadOnlyNode || n instanceof GuardNode ||
                   n instanceof ConstructNode || n instanceof InitViewNode || n instanceof ConvertNode ) {
            values.addAll(refs(sum,n.in(1)));
        } else if( n instanceof ArrayInitNode ) {
            values.addAll(refs(sum,n.in(2)));
        } else if( sum._raw.get(n._nid) ) {
            for( Node in : n._inputs ) values.addAll(refs(sum,in));
        } else {
            sum.reject("reference origin "+n.getClass().getSimpleName()+" #"+n._nid);
        }
        if( old.addAll(values) ) sum._changed=true;
        return old;
    }

    // Apply an already-built callee summary to this particular call.  Translate
    // callee Sites into caller Sites: formal -> refs(actual), static -> static,
    // borrowed field -> actual field targets. Fresh Sites were imported once by
    // build, tagged with this call. Private callee scratch stays in the callee.
    // A second call gets distinct Sites for its fresh allocations.
    // Then translate field edges and returned targets through the same map.
    // For helper(h) { h.c = new Thing; }, calling helper(a) adds a.c -> call/new.
    // The map values are SETS because an actual may select several objects.
    // Callee result/argument owner bits are relative to its signature; do not
    // copy them into the caller.  build's closure derives the caller's owners
    // from the translated edges and uses of the result.  Only static is absolute.
    private void compose(Summary sum, CallNode call, IdentityHashMap<Site,HashSet<Site>> map) {
        Summary callee=target(call);
        // Grow substitutions before transferring edges. A later local round
        // resolves forward references and aliasing actuals. For example, after
        // f(a,b) { a.c=new Thing; return b.c; }, f(h,h) returns that new Thing too.
        for( Site site : callee._sites ) {
            if( site._arg>=2 ) {
                if( site._arg>=call.nIns()-1 )
                    sum.reject("missing argument at call #"+call._nid);
                else if( map.get(site).addAll(refs(sum,call.arg(site._arg))) ) sum._changed=true;
            } else if( site._base!=null )
                if( map.get(site).addAll(field(sum,map.get(site._base),site._field)) ) sum._changed=true;
        }
        // Retention effects exist even when the function returns a scalar.
        for( Site site : callee._sites ) {
            HashSet<Site> dst=map.get(site);
            if( dst.isEmpty() ) continue;
            for( var field : site._fields.entrySet() ) for( Site child : field.getValue() ) {
                HashSet<Site> src=map.get(child);
                if( src==null ) sum.reject("unmapped retained storage at call #"+call._nid);
                else sum._changed |= edge(dst,field.getKey(),src);
            }
        }
        for( Site site : callee._returns ) {
            Set<Site> returned=map.get(site);
            if( returned==null ) sum.reject("unmapped returned storage at call #"+call._nid);
            else sum._changed |= sum._results.get(call).addAll(returned);
        }
    }

    // Printing has its own rooted traversal: reversing the analysis's entire
    // forest would put disconnected bodies (e.g. inlined constructors) first.
    private void printPostorder(Summary sum, BitSet seen, ArrayList<Summary> post) {
        if( sum==null || seen.get(sum._fun._nid) ) return;
        seen.set(sum._fun._nid);
        ArrayList<CallNode> calls=new ArrayList<>(sum._calls);
        calls.sort(Comparator.comparingInt(n -> n._nid));
        for( CallNode call : calls ) printPostorder(target(call),seen,post);
        post.add(sum);
    }

    @Override public String toString() {
        StringBuilder out = new StringBuilder("Allocation destinations (analysis only; no stack lowering)\n");
        BitSet seen=new BitSet();
        ArrayList<Summary> order=new ArrayList<>();
        printPostorder(_entrySummary,seen,order);
        Collections.reverse(order);
        int reachable=order.size();
        ArrayList<Summary> other=new ArrayList<>();
        for( Summary sum : _summaries ) printPostorder(sum,seen,other);
        Collections.reverse(other);
        order.addAll(other);
        for( int i=0; i<order.size(); i++ ) {
            if( i==reachable && _entrySummary!=null )
                out.append("\nOther retained functions (not reachable by direct calls from entry):\n");
            Summary sum=order.get(i);
            out.append("\n+-- ").append(sum._fun.label()).append("\n");
            for( ArrayDelete.Consume consume : sum._consumes ) {
                out.append("|   may consume entry(").append(consume).append("); requires unique access");
                if( arrayOwned(sum,consume) ) out.append("; returns unique ").append(consume);
                out.append('\n');
            }
            ArrayList<CallNode> calls=new ArrayList<>(sum._calls);
            calls.sort(Comparator.comparingInt(n -> n._nid));
            for( CallNode call : calls ) {
                Summary callee=target(call);
                out.append("|   call#").append(call._nid).append(" -> ")
                    .append(callee==null ? nativeCall(call) ? "native (borrow; no retain)" : "?" : callee._fun.label()).append("\n");
            }
            if( !sum.supported() ) {
                out.append("`-- REJECT: ").append(sum._reject).append("\n");
                continue; // Do not display a partial plan as a safe placement.
            }
            printFrames(out,sum,"|   ");
            printStorage(out,sum,"|   ");
            printSites(out,"|   cleanup on return -> ",sum._scratch);
            printSites(out,"`-- return -> ",sum._returns," | ");
        }
        return out.toString();
    }

    // Show storage in its owning frame, which may enclose its allocation site.
    // Borrowed/external destinations remain at function level.
    private static void printStorage(StringBuilder out, Summary frame, String indent) {
        ArrayList<Site> sites=new ArrayList<>();
        for( Site site : frame._sites )
            if( site._scope==frame || (frame._loop==null && site._scope==null) ) sites.add(site);
        sites.sort(Comparator.comparing(s -> s._name));
        for( Site site : sites ) {
            out.append(indent).append('[').append(site.destination()).append("] ").append(site._name);
            if( site._fresh ) {
                out.append(" (");
                if( site._bytes<0 ) out.append("dynamic size");
                else out.append(site._bytes).append(" bytes");
                out.append(')');
            }
            if( site._frame!=null && site._frame._loop!=null && site._scope!=site._frame )
                out.append("; ").append(site._frame.name()).append(" winner");
            out.append('\n');
            ArrayList<String> edges=new ArrayList<>();
            for( var field : site._fields.entrySet() ) for( Site child : field.getValue() )
                edges.add(indent+"   ."+field.getKey()+" --> "+child._name+"\n");
            Collections.sort(edges);
            for( String edge : edges ) out.append(edge);
        }
    }

    private static void printSites(StringBuilder out, String label, HashSet<Site> sites) {
        printSites(out,label,sites,", ");
    }
    private static void printSites(StringBuilder out, String label, HashSet<Site> sites, String separator) {
        out.append(label).append('{');
        ArrayList<String> names=new ArrayList<>();
        for( Site site : sites ) names.add(site._name);
        Collections.sort(names);
        for( int i=0; i<names.size(); i++ ) {
            if( i>0 ) out.append(separator);
            out.append(names.get(i));
        }
        out.append("}\n");
    }

    private static void printPorts(StringBuilder out, String label, IdentityHashMap<Node,Node> ports) {
        out.append(label).append('{');
        ArrayList<Node> keys=new ArrayList<>(ports.keySet());
        keys.sort(Comparator.comparingInt(n -> n._nid));
        for( int i=0; i<keys.size(); i++ ) {
            Node port=keys.get(i), value=ports.get(port);
            if( i>0 ) out.append(", ");
            out.append(port.label()).append('#').append(port._nid);
            if( port!=value ) out.append(" <- ").append(value.label()).append('#').append(value._nid);
        }
        out.append("}\n");
    }

    private static void printFrames(StringBuilder out, Summary parent, String indent) {
        ArrayList<Summary> frames=new ArrayList<>(parent._frames);
        frames.sort(Comparator.comparingInt(s -> s._loop._nid));
        for( Summary frame : frames ) {
            out.append(indent).append("+-- ").append(frame.name()).append(" frame; parent ").append(parent.name()).append('\n');
            printPorts(out,indent+"|   entry -> ",frame._entry);
            printPorts(out,indent+"|   continue values -> ",frame._next);
            printSites(out,indent+"|   continue refs -> ",frame._continues);
            printPorts(out,indent+"|   exit values -> ",frame._exit);
            printSites(out,indent+"|   return refs -> ",frame._returns);
            printSites(out,indent+"|   retain -> ",frame._retained);
            printStorage(out,frame,indent+"|   ");
            printFrames(out,frame,indent+"|   ");
            printSites(out,indent+"`-- cleanup on continue and return -> ",frame._scratch);
        }
    }
}
