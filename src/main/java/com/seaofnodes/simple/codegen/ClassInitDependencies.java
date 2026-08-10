package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.*;
import java.util.*;

// Search for Class Initialization Cycles.

// Done after Opto, so dead code does not make a cycle.

// Build the inits-graph, a call-graph plus CLInits plus class parents.

// (1) Scan all functions f, building _edges[f][]: the set of <clinit> fcns
// from CompUnits from CLInitNodes, and the set of fcn call targets in f and
// the class parent.  These are all reported as fidxs (ints).
//
// (2) Walk the _edges graph using Tarjans SCC.  Reject cycles containing more
// than one <clinit>.
//
// (3) Report on <clinit> cycles, if any
//
public class ClassInitDependencies {
    private final CodeGen _code;
    // Function FIDX -> callees, touched initializers, and parent initializer.
    public final int[][] _edgess;
    private final int[] _pres;
    private final int[] _lows;
    private final int[] _stk ;
    private final int[] _spos; // Stack position; FIDX -> stack slot + 1, or 0 when absent.
    private int _sp;
    private int _pre;


    // Build once; tests can inspect the graph independently of cycle checking.
    public ClassInitDependencies( CodeGen code ) {
        _code = code;
        assert code._phase.ordinal() >= CodeGen.Phase.IterPostOpto.ordinal();
        _pres = new int[code._linker._len];
        _lows = new int[code._linker._len];
        _stk  = new int[code._linker._len];
        _spos = new int[code._linker._len];

        //
        BitSet visit = code.visit();                   // Visit set per-fcn
        Ary<CFGNode> work = new Ary<>(CFGNode.class);  // Worklist per-fcn
        IntHashMap iset = new IntHashMap(0);           // dedup hash set per-fcn
        _edgess = new int[code._linker._len][];

        // For all functions, build the _edges array
        for( FunNode fun : code._linker )
            if( fun!=null && !fun.isDead() )
                // Visit function and collect direct class touches and callees
                _edgess[fun.sig().fidx()] = walk(code,fun,work,iset,visit,null);

        // FIDX zero represents escapees - an unknown callee, including a
        // callback supplied by another compilation.  Its code can call any
        // exposed function.  Keep this edge in saved summaries so later
        // applications contribute their callbacks too.

        // Filter escapees by dead and not a class (class initializers are
        // reached through bodies, not directly callable function pointers).
        int[] escaped = ((TypeTuple)code._stop._type).mem()._escFs;
        AryInt callees = new AryInt();
        // An imported Stop can still carry FULL (only local bodies have edges)
        // so limit by existing FIDXs.
        int limit = code._linker._len;
        for( int fidx = XInt.next(escaped,0,limit); fidx >= 0; fidx = XInt.next(escaped,fidx,limit) ) {
            FunNode fun = code.link(fidx); // Discard dead bodies and external C functions.
            if( fun!=null && !fun.isClz() ) callees.push(fidx);
        }
        _edgess[0] = callees.isEmpty() ? null : callees.asAry();
        visit.clear();
    }

    // Return the outgoing init edge set for this function
    private static int[] walk( CodeGen code, FunNode fun, Ary<CFGNode> work, IntHashMap iset, BitSet visit,
                              Ary<Parser.Lexer> sites) {
        if( fun.summary()!=null ) return fun.summary()._classDependencyFIDXs;
        // Walk function body, looking for outgoing init edges
        assert iset .isEmpty(); // int hash set for de-dup
        assert visit.isEmpty(); // needs a visit set, because loops
        assert work .isEmpty(); // needs a worklist, because DAG, parallel explore paths

        // Children require their parents to init first;
        // add child->parent edge
        if( fun.isClz() && fun._compunit._par != null )
            iset.put(code.link(fun._compunit._par).sig().fidx(),1);

        work.push(fun);
        while( !work.isEmpty() ) {
            CFGNode cfg = work.pop();
            if( visit.get(cfg._nid) ) continue; // Been there, done that
            visit.set(cfg._nid);
            assert !cfg._type.isHigh();
            // Do not walk off end of function
            if( cfg instanceof ReturnNode ) continue;

            // CLInit adds a class init requirement to this function
            // (plus all parent classes)
            if( cfg instanceof CLInitNode init ) {
                FunNode clz = code.link(init.cu()); // <clinit> referred to
                iset.put(clz.sig().fidx(),1);       // Add to edge set
                if( sites!=null && sites.atX(clz.sig().fidx())==null ) sites.setX(clz.sig().fidx(),init._loc);
            }

            // Calls union all their fptr fidxs into the callee set
            if( cfg instanceof CallNode call && call.fptr()._type instanceof TypeFunPtr fp ) {
                if( XInt.isHigh(fp.fidxs()) ) {
                    iset.put(0,1);
                    if( sites!=null && sites.atX(0)==null ) sites.setX(0,call._loc);
                }
                // Union call targets into the callee set
                else for( int dstx = XInt.next(fp.fidxs(),0); dstx>=0; dstx=XInt.next(fp.fidxs(),dstx) ) {
                    iset.put(dstx,1);
                    if( sites!=null && sites.atX(dstx)==null ) sites.setX(dstx,call._loc);
                }
            }

            // Forward recursion to walk the whole function
            for( Node use : cfg._outputs ) {
                // Do not walk into another function
                if( !(use instanceof CFGNode cfg1) || use instanceof FunNode || use instanceof StopNode ) continue;
                // Expect control flow edge is sensible
                assert (use instanceof RegionNode r && r._inputs.find(cfg)>0) ||  use.in(0)==cfg;
                if( !visit.get(cfg1._nid) ) // Small speed opt
                    work.add(cfg1);
            }
        }

        visit.clear();
        int[] edges = iset.isEmpty() ? null : iset.keySet();
        iset.clear();
        return edges;
    }

    // Return an error string, or null if no cycle contains multiple clinits.
    public String check() {
        Arrays.fill(_pres,0);
        // A prior check may have stopped with a cycle still on the stack.
        while( _sp>0 ) _spos[_stk[--_sp]] = 0;
        _pre = 1;

        int idx = -1;
        for( int fidx = 0; fidx < _edgess.length; fidx++ )
            if( _pres[fidx] == 0 &&       // Already visited
                (idx = scc(fidx)) != -1 ) // And found a cycle
                return report(idx);       // Report cycle
        return null;
    }

    // Every internal edge of an SCC belongs to a cycle. Print all of them:
    // stack order is not a path, and enumerating all cycle paths can explode.
    // This bookkeeping and formatting are only needed on an error.
    private String report(int idx) {
        BitSet members = new BitSet();
        for( int i=idx; i<_sp; i++ ) members.set(_stk[i]);
        SB sb = new SB().p("Cyclic class initialization:");
        // Recover one surviving source site per edge only after finding an
        // error. A representative touch is enough; runtime first-touch order
        // need not be statically known. Lexer snapshots retain the source CU
        // even when the node has been inlined into a different function.
        Ary<CFGNode> work = new Ary<>(CFGNode.class);
        IntHashMap iset = new IntHashMap(0);
        BitSet visit = new BitSet();
        Ary<Parser.Lexer> sites = new Ary<>(Parser.Lexer.class);
        for( int i=idx; i<_sp; i++ ) {
            int src = _stk[i];
            FunNode fun = _code.link(src);
            Arrays.fill(sites._es,null);
            sites.clear();
            if( fun!=null ) walk(_code,fun,work,iset,visit,sites);
            if( _edgess[src]!=null )
                for( int dst : _edgess[src] )
                    if( members.get(dst) ) {
                        sb.p("\n  ").p(name(src)).p(" -> ").p(name(dst));
                        Parser.Lexer loc = sites.atX(dst);
                        if( loc!=null && loc._cu!=null )
                            sb.p('\n').p(SourceError.format(loc._cu,loc.pos(),"cyclic class initialization"));
                        else if( fun!=null && fun.isClz() && dst!=0 && fun._compunit._par==_code.link(dst)._compunit )
                            sb.p("\n    parent initialization requirement");
                    }
        }
        return sb.toString();
    }

    private String name(int fidx) {
        if( fidx==0 ) return "unknown call";
        FunNode fun = _code.link(fidx);
        String owner=fun._compunit._cname;
        if( fun.isClz() ) return owner;
        String label=fun.label();
        return (label.startsWith(owner+".") ? label : owner+"."+label)+" [fidx "+fidx+"]";
    }

    // Tarjan SCC
    int scc( int fidx ) {
        // Assign pre-order number
        _lows[fidx] = _pres[fidx] = _pre++;
        _spos[fidx] = _sp+1;
        _stk[_sp++] = fidx;
        // walk outbound edges
        if( _edgess[fidx]!=null ) {
            for( int edge : _edgess[fidx] ) {
                if( edge >= _pres.length ) continue; // Out of bounds happens when calling C fcns
                if( _pres[edge] == 0 ) { // Not visited
                    int idx = scc(edge);
                    if( idx != -1 ) return idx; // Found a cycle
                    _lows[fidx] = Math.min(_lows[fidx],_lows[edge]);
                } else if( _edgess[edge]!=null && _spos[edge]!=0 ) {
                    _lows[fidx] = Math.min(_lows[fidx],_pres[edge]);
                }
            }
        }
        // Found a cycle
        if( _lows[fidx] == _pres[fidx] ) {
            int idx = _spos[fidx]-1;
            if( hasClzCycle(idx) )
                return idx;     // Found a class cycle
            // Pop stack, including fidx, should be a really short loop
            while( _sp>idx ) _spos[_stk[--_sp]] = 0;
        }
        return -1;              // No cycle
    }

    private boolean hasClzCycle(int idx) {
        int clinits = 0;
        for( int i=idx; i<_sp; i++ ) {
            FunNode fun = _code.link(_stk[i]);
            if( fun!=null && fun.isClz() && ++clinits > 1 )
                return true;
        }
        return false;
    }

}
