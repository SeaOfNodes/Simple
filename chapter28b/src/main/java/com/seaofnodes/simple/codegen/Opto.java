package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.IterPeeps.WorkList;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Ary;

import java.util.BitSet;

// Optimistic Algorithm
abstract public class Opto {

    // Optimistic analysis - extended SCCP

    // Start all Types at TOP, and propagate changes until stable.  Unlike a
    // normal Sparse Conditional Constant Propagation, this version is
    // interprocedural, computing effectively CFA0 in linear time using the
    // normal SCCP algorithm.

    // This algo also builds a Call Graph; as Calls become reachable (CONTROL
    // not TOP nor XCONTROL), they "link" against the reaching function
    // pointers - adding edges from the Call to the Fun, and from the Return to
    // the CallEnd.  This allows the normal algorithm to use the normal edge
    // propagation rules as function argument types stabilize.  In the end, we
    // get a fairly precise set of Call Graph edges between CallNodes and
    // FunNodes.
    public static void opto(CodeGen code) {
        // Reset Types before running the worklist algorithm
        Ary<Type> oldTypes = new Ary<>(Type.class);
        WorkList<Node> deps = new WorkList<>();
        WorkList<Node> changes = new WorkList<>();
        Ary<Node> modes = new Ary<>(Node.class);
        resetToTop(code,oldTypes,modes,deps);
        unlinkStart(code);

        // OPTIMISTIC INTERPROCEDURAL SCCP
        sccp(code, oldTypes, changes, deps);

        // Settle unresolved modes using SCCP's int/float evidence.  Some
        // computes wait at TOP until a mode is known; others already return
        // BOTTOM and stay there.  Resume SCCP only on the affected nodes;
        // types are not reset, and any further changes must still fall.
        resolveNumericModes(code,modes);

        if( !code._iter._work.isEmpty() )
            sccp(code, oldTypes, changes, deps);
        Node dep;
        while( (dep=deps.pop())!=null ) code.add(dep);

        // After SCCP add everything changed to the worklist and see if
        // peepholes can make more progress.
        moveChangesToWorklist(code, oldTypes, changes);
    }

    // Reset Types before running the worklist algorithm
    private static void resetToTop(CodeGen code, Ary<Type> oldTypes, Ary<Node> modes, WorkList<Node> deps ) {
        code._start.walk( x -> {
                // Opto ends Parm.inProgress(), enabling constant folding even
                // when a parameter's type was already constant before SCCP.
                if( x instanceof ParmNode && x._type.isHighOrConst() ) deps.push(x);
                // SCCP adds call edges and default arguments, not numeric
                // operations. Gather unresolved modes on this required walk.
                if( x instanceof ModeNode mode && mode.mode()==0 ) modes.push(x);
                oldTypes.setX(x._nid,x._type); // Compare the final SCCP type with Iter's type
                x._type = Type.TOP;            // Reset all Nodes to TOP
                code._iter.add(x); // Visit everybody at least once; most Nodes produce better than TOP
                return null;
            } );
    }


    // Only entry initializers start with unknown callers. Discover every
    // other function through calls or escaping values during SCCP.
    private static void unlinkStart(CodeGen code) {
        // CodeGen owns these until FinalizeIR; linkStart may need them again.
        for( CompUnit cu : code._compunits.values() ) {
            ProjNode mem = cu._start.proj(1);
            if( mem!=null ) code.keepUntilFinalize(mem);
        }

        // Iterate all functions.
        // TODO: use CodeGen _link list
        for( Node stop : code._stop._inputs ) {
            for( int i=0; i<stop.nIns(); i++ ) {
                ReturnNode ret = (ReturnNode)stop.in(i);
                FunNode fun = ret.fun();
                if( !fun.isRoot() &&
                    fun.in(1) instanceof StartNode ) {
                    fun.removeDeadPath(1);
                    // Unhook from Stop without treating the ReturnNode as
                    // DEAD.  It stays in limbo until Opto links when a caller
                    // is discovered (or not, and the function is really dead).
                    stop._inputs.del(i--);
                    ret.delUse(stop);
                }
                // Unlink the existing conservative call linkages.
                for( int j=1; j<fun.nIns(); j++ )
                    if( fun.in(j) instanceof CallNode call )
                        call.unlink(fun,j--);
            }
        }
    }

    // As part of building a CallGraph, when opto finds a function ptr flowing
    // into a Call, link the Call and Fun.
    private static void linkCG(CodeGen code, TypeFunPtr tfp, CallNode call) {
        if( tfp.nargs() != call.nargs() ) return; // Error calls hit this
        int[] fidxs = tfp.fidxs();
        // An unresolved forward call can still carry the infinite, inferred
        // function set into Opto.  There is no finite call graph to link yet;
        // type checking will report the unresolved reference afterwards.  This
        // can yet be valid if the call is eventually dead.
        //   `if( false ) frefCall(args);`
        if( XInt.isHigh(fidxs) ) return;
        for( int fidx = XInt.next(fidxs,0); fidx >=0; fidx = XInt.next(fidxs,fidx) ) {
            // unlinkStart deliberately leaves uncalled functions in a
            // temporarily dead limbo.  A newly discovered call revives them;
            // do not use the cleanup lookup which removes dead linker entries.
            FunNode fun = code._link(fidx);
            // null here means an external function; i.e. this Call
            // calls to an outside library and all its arguments escape.
            if( fun != null && !call.linked(fun) ) {
                call.link( fun );
                code._iter.add(fun);
                code._iter.addAll(fun._outputs);
            }
        }
    }

    // Start added some FIDXs, the same functions now can be reached from start
    // and called by anybody.  Add the CG edges.
    private static void linkStart( CodeGen code, TypeTuple tt  ) {
        TypeMem tmem = (TypeMem)tt._types[1];
        int[] fidxs = tmem._escFs;
        if( XInt.isHigh(fidxs) ) {
            // A scalar BOTTOM can flow into memory while compiling an error
            // program.  It exposes all public functions, not private fields.
            for( FunNode fun : code._linker )
                if( fun != null && code.publicFIDX(fun.sig().fidx()) )
                    linkStart(code,fun);
            return;
        }
        for( int fidx = XInt.next(fidxs,0); fidx >=0; fidx = XInt.next(fidxs,fidx) ) {
            FunNode fun = code._linker.atX(fidx);
            if( fun==null )  assert code._externFunc.containsKey(fidx);
            else             linkStart(code,fun);
        }
    }

    // Function is now reachable by unknown callers through Start.  The
    // default inputs must therefore use conservative declared types.
    static void linkStart( CodeGen code, FunNode fun ) {
        assert !fun.isDead();
        StartCUNode start = fun._compunit._start;
        if( !fun.unknownCallers() ) {
            assert !start.isDead();
            code.keepInCompUnit(fun);
            // Function is reachable by any *remote* caller who gets the pointer!
            fun.insertDef(1,start);
            code._iter.add(fun);
            for( Node p : fun._outputs )
                if( p instanceof ParmNode parm ) {
                    Node defalt = parm._idx==1 ? start.proj(1) : ConstantNode.seed(parm._declaredType).peephole();
                    parm.insertDef(1,defalt);
                    code.add(parm);
                }
        }
    }


    // The core SCCP algorithm
    private static void sccp( CodeGen code, Ary<Type> oldTypes, WorkList<Node> changes, WorkList<Node> deps ) {
        // Iter workList is empty at this point
        // OPTIMISTIC PASS
        Node n;
        int count = 0;          // Debug counter
        while( (n = code._iter._work.pop()) != null ) {
            if( n.isDead() ) continue;
            count++;
            Type oval = n._type, nval = n.compute();
            if( n instanceof CLInitNode init ) init.link();
            // Remember possible peephole work without walking the graph again.
            // SCCP can still fall back to the old type, so filter at the end.
            if( nval!=oldTypes.atX(n._nid) || n.isUnlocked() ) changes.push(n);
            if( oval == nval ) continue;
            assert oval.isa(nval) : "Non-monotonic SCCP: "+n.getClass().getSimpleName()+"#"+n._nid+" "+n+" old="+oval+" new="+nval;    // Types start high and always fall
            Type pesiVal = oldTypes.atX(n._nid);
            assert pesiVal==null || nval.isa(pesiVal) : "SCCP below pessimistic type: "+n.getClass().getSimpleName()+"#"+n._nid+" "+n+" pessimistic="+pesiVal+" new="+nval; // Never fall worse than the pessimistic pass
            n._type = nval;

            // Now we have a series of stanzas where we lazily create the Call
            // Graph - adding graph edges between every call site and called
            // function, including external (unknown) callers.  These edges are
            // not in from the start because they are *too many*; we would get
            // O(n^2) edges.  So all along we have been treating them as
            // "virtual" CFG edges - a FunNode taking a Start input is treated
            // as if every call on the planet might call it.  Now we make these
            // CG edges *concrete*, adding them back as we discover that a call
            // calls a particular function.

            // If a TFP adds a new function input to a call, link to the new
            // Fun.  This adds new edges to the graph allowing argument values
            // to flow from calls into functions.  The added edges are
            // effectively a refined Call Graph.
            if( nval instanceof TypeFunPtr tfp ) {
                for( Node use : n._outputs )
                    if( !use._type.isHigh() && use instanceof CallNode call && call.fptr() == n )
                        linkCG(code,tfp,call);
            }

            // Link a Call which becomes alive.  Like above, adds new Call
            // Graph edges to the graph.
            if( n instanceof CallNode call && nval == Type.CONTROL && call.fptr()._type instanceof TypeFunPtr )
                linkCG(code,call.tfp(),call);

            // If an otherwise-dead function pointer escapes, any future linked
            // caller might find and call it.  Force the function to be alive
            // and called by Start.
            if( n instanceof StartNode )
                linkStart(code,(TypeTuple)n._type);

            // Since n._type changed, visit all output neighbors
            code._iter.addAll(n._outputs);
            // SCCP consumes these without running peepholes. Save them for
            // the following Iter pass before moving clears the dependency list.
            for( int i=0; i<n.nDeps(); i++ ) deps.push(n.dep(i));
            n.moveDepsToWorklist(code._iter);
            // Quadratic (expensive) small-step assert.
            assert !CodeGen.expensiveAssert(count) || worklistCheck(code);
        }
    }

    // Quadratic (expensive) fixed-point assert
    public static boolean fixedPointCheck(CodeGen code) {
        return fixedPointCheck(code,code._stop);
    }
    public static boolean fixedPointCheck(CodeGen code, Node root) {
        root.walk( x -> {
                Type xval = x.compute();
                assert xval == x._type;
                return null;
            });
        return true;
    }

    // Quadratic (expensive) small-step assert for SCCP, where changed nodes
    // may already be on the worklist.
    private static boolean worklistCheck(CodeGen code) {
        code._midAssert = true;
        Node root = code._start;
        root.walk( x -> {
                Type xval = x.compute();
                assert xval == x._type || (x._type.isa( xval ) && code._iter._work.on( x ))
                    : "Non-monotonic or missed SCCP work: "+x.getClass().getSimpleName()+"#"+x._nid+" "+x+" old="+x._type+" new="+xval;
            return null;
            });
        code._midAssert = false;
        return true;
    }

    // Types can stay unchanged while SCCP edits edges or numeric modes. Such
    // edits unlock the node from GVN. Revisit both kinds of change and their
    // neighbors; distant dependencies consumed by SCCP were saved separately.
    private static void moveChangesToWorklist(CodeGen code, Ary<Type> oldTypes, WorkList<Node> changes) {
        Node n;
        while( (n=changes.pop())!=null ) {
            if( n.isDead() ) continue;
            assert n.compute() == n._type; // Hit the fixed point.
            Type old = oldTypes.atX(n._nid);
            assert old==null || n._type.isa(old);
            if( n._type!=old || n.isUnlocked() ) {
                code.addAll(n);
                code.addAll(n._outputs);
                n.moveDepsToWorklist();
            }
        }
    }

    // Resolve modes left undecided by SCCP, following recursive inputs for
    // evidence. Float wins because integer operands can be converted to float.
    private static void resolveNumericModes(CodeGen code, Ary<Node> modes) {
        final BitSet visited = new BitSet();
        for( Node n : modes ) {
            if( !n.isDead() && n instanceof ModeNode modeNode && modeNode.mode()==0 ) {
                visited.clear();
                visited.set(n._nid); // Inspect inputs, not Bool's integer result
                int mode = 0;
                for( int i=1; i<n.nIns(); i++ )
                    mode |= numericEvidence(n.in(i),visited);
                // A closed recursive numeric component can have no remaining
                // concrete evidence after SCCP, e.g. dead all-integer loops.
                // With no float evidence, settle to integer.
                modeNode.setMode((byte)((mode&2)==2 ? 2 : 1));
                code.add(n);
                code.addAll(n.outs());
            }
        }
    }

    // Gather numeric evidence through Phi and interprocedural return cycles.
    // The visited set makes self- and mutually-recursive calls finite.
    private static int numericEvidence(Node n, BitSet visited) {
        if( n == null ) return 0;
        if( visited.get(n._nid) ) return 0;
        visited.set(n._nid);

        Type t = n._type;
        if( t instanceof TypeFloat   ) return 2;
        if( t instanceof TypeInteger ) return 1;
        if( t!=Type.BOTTOM && !(t instanceof TypeTuple) )
            return 0; // Something unrelated, like a ptr

        int evidence = 0;
        assert !(n instanceof ConvertNode); // No test case
        switch( n ) {
        case ModeNode modeNode when modeNode.mode() != 0 -> { return modeNode.mode(); }
        case ModeNode modeNode -> {}
        case PhiNode phi -> {}
        case ReturnNode ret -> {}
        case ProjNode proj when proj.in(0) instanceof CallEndNode cend -> {
            for( int i=1; i<cend.nIns(); i++ )
                evidence |= numericEvidence(cend.in(i),visited);
        }
        default -> { return 0; }
        }
        for( int i=1; i<n.nIns(); i++ )
            evidence |= numericEvidence(n.in(i),visited);

        return evidence;
    }
}
