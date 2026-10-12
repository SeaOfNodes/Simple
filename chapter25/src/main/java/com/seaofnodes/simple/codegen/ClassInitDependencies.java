package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.*;
import java.util.*;

// Search for Class Initialization Cycles.

// Done after Opto, so dead code does not make a cycle.

// Build the inits-graph, a call-graph plus CLInits plus class parents.

// (1) Scan all functions f, building _edges[f][]: the set of <cinit> fcns from
// CompUnits from CLInitNodes, and the set of fcn call targets in f and the
// class parent.  These are all reported as fidxs (ints).
//
// (2) Walk the _edges graph using Tarjans SCC.  Stop at cycles containing a
// <clint> (and continue with cycles NOT containing a <clinit>, which is
// ordinary recursive functions).
//
// (3) Report on <clinit> cycles
//
//
public class ClassInitDependencies {
    private final CodeGen _code;
    // Function FIDX -> callees, touched initializers, and parent initializer.
    public final int[][] _edgess;
    private final int[] _pres;
    private final int[] _lows;
    private final int[] _stk ;
    private int _sp;


    // Build once; tests can inspect the graph independently of cycle checking.
    public ClassInitDependencies( CodeGen code ) {
        _code = code;
        assert code._phase.ordinal() >= CodeGen.Phase.Opto.ordinal();
        _pres = new int[code._linker._len];
        _lows = new int[code._linker._len];
        _stk  = new int[code._linker._len];

        //
        BitSet visit = code.visit();                   // Visit set per-fcn
        Ary<CFGNode> work = new Ary<>(CFGNode.class);  // Worklist per-fcn
        IntHashMap iset = new IntHashMap(0);           // dedup hash set per-fcn
        _edgess = new int[code._linker._len][];

        // For all functions, build the _edges array
        for( FunNode fun : code._linker )
            if( fun!=null && !fun.isDead() )
                // Visit function and collect direct class touches and callees
                _edgess[fun.sig().fidx()] = walk(code,fun,work,iset,visit);
        visit.clear();
    }

    // Return the outgoing init edge set for this function
    private static int[] walk( CodeGen code, FunNode fun, Ary<CFGNode> work, IntHashMap iset, BitSet visit) {
        // Walk function body, looking for outgoing init edges
        assert iset .isEmpty(); // int hash set for de-dup
        assert visit.isEmpty(); // needs a visit set, because loops
        assert work .isEmpty(); // needs a worklist, because DAG, parallel explore paths

        // Children classes always dependent on parents
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
            }

            // Calls union all their fptr fidxs into the callee set
            if( cfg instanceof CallNode call && call.fptr()._type instanceof TypeFunPtr fp ) {
                assert !XInt.isHigh(fp.fidxs()) && fp.nfcns()>0; // Call is alive with valid targets
                // Union call targets into the callee set
                for( int dstx = XInt.next(fp.fidxs(),0); dstx>=0; dstx=XInt.next(fp.fidxs(),dstx) )
                    iset.put(dstx,1);
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

    // Return an error string, or null for an acyclic class-init graph.
    public String check() {
        _pres[0] = 1;           // Preorders stored in pres[0] and start at 1
        BitSet visit = _code.visit();

        int idx = 0;
        for( int fidx = 1; fidx < _edgess.length; fidx++ ) {
            if( _pres[fidx] == 0 ) // Already visited
                idx = scc(fidx);
            if( idx != -1 ) break; // Found a clz cycle
        }
        visit.clear();

        // Error report for a clz heading a cycle
        if( idx != -1 ) {
            SB sb = new SB().p("Cyclic class initialization: ");
            String first = null;
            for( int i=idx; i<_sp; i++ ) {
                FunNode fun = _code.link(_stk[i]);
                if( fun.isClz() ) {
                    if( first==null ) first = fun._compunit._cname;
                    sb.p(fun._compunit._cname).p(" -> ");
                }
            }
            sb.p(first);
            visit.clear();
            return sb.toString();
        }

        return null;            // No errors
    }

    // Tarjan SCC
    int scc( int fidx ) {
        // Assign pre-order number
        _lows[fidx] = _pres[fidx] = _pres[0]++;
        _stk[_sp++] = fidx;
        // walk outbound edges
        if( _edgess[fidx]!=null ) {
            for( int edge : _edgess[fidx] ) {
                if( edge >= _pres.length ) continue; // Out of bounds happens when calling C fcns
                if( _pres[edge] == 0 ) { // Not visited
                    int idx = scc(edge);
                    if( idx != -1 ) return idx; // Found a cycle
                    _lows[fidx] = Math.min(_lows[fidx],_lows[edge]);
                } else if( _edgess[edge]!=null && onStk(edge) ) {
                    _lows[fidx] = Math.min(_lows[fidx],_pres[edge]);
                }
            }
        }
        // Found a cycle
        if( _lows[fidx] == _pres[fidx] ) {
            int idx = find(fidx);
            if( hasClzCycle(idx) )
                return idx;     // Found a class cycle
            // Pop stack, including fidx
            _sp = idx;
        }
        return -1;              // No cycle
    }

    private int find(int e) {
        for( int i=0; i<_sp; i++ )  if( _stk[i] == e ) return i;
        return -1;
    }
    private boolean onStk(int e) { return find(e) != -1; }

    private boolean hasClzCycle(int idx) {
        if( idx+1 == _sp ) return false; // Cycle of 1 does not count
        for( int i=idx; i<_sp; i++ )
            if( _code.link(_stk[i]).isClz() )
                return true;
        return false;
    }

}
