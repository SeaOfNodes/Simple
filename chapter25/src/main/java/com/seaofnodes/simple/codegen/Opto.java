package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.simple.util.Utils;

import java.util.Arrays;

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

        // Pessimistic peephole optimization on a worklist - this completes
        // whatever peepholes can run after parsing.
        code._iter.iterate(code);

        // Reset Types before running the worklist algorithm
        Ary<Type> oldTypes = new Ary<>(Type.class); // Used for asserts only
        resetToTop(code,oldTypes);
        unlinkStart(code);

        // OPTIMISTIC INTERPROCEDURAL SCCP
        sccp(code, oldTypes);

        // After SCCP add everything changed to the worklist and see if
        // peepholes can make more progress.
        moveChangesToWorklist(code, oldTypes);

        // Progress with improved types.  E.g. since we have a full Call Graph
        // here, we might do better inlining.
        code._iter.iterate(code);

        // TODO:
        // loop unroll, peel, RCE, etc

        // Freeze field sizes; do struct layouts; convert field offsets into
        // constants.
        fieldSizes(code);
        // Propagate field size constants
        code._iter.iterate(code);

        // To help with testing, sort StopNode Returns by NID
        for( Node stop : code._stop._inputs )
            Arrays.sort(stop._inputs._es,0,stop.nIns(),
                        (x,y) -> ((((ReturnNode)x).fun().isClz() ? -1 : x._nid) -
                                  (((ReturnNode)y).fun().isClz() ? -1 : y._nid) ));
    }

    // Reset Types before running the worklist algorithm
    private static void resetToTop(CodeGen code, Ary<Type> oldTypes ) {
        code._start.walk( x -> {
                oldTypes.setX(x._nid,x._type); // Record original types for asserts
                x._type = Type.TOP;            // Reset all Nodes to TOP
                code._iter.add(x); // Visit everybody at least once; most Nodes produce better than TOP
                return null;
            } );
    }


    // If wholeWorld, remove the Start inputs to functions - they are not
    // called unless they get linked (hence go dead and can be removed).  If
    // not wholeWorld, keep only public methods (and not the std lib).
    private static void unlinkStart(CodeGen code) {
        // Iterate the Stop-of-Stops
        for( Node stop : code._stop._inputs ) {
            for( int i=0; i<stop.nIns(); i++ ) {
                ReturnNode ret = (ReturnNode)stop.in(i);
                FunNode fun = ret.fun();
                // Non-public functions unhook completely from Start.
                // They only can be reached if directly called.
                if( !fun.isPublic() ) {
                    assert fun.in(1)==code._start; // Start always in slot 1
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
        for( long fidxs = tfp.fidxs(); fidxs != 0; fidxs = TypeFunPtr.nextFIDX(fidxs) ) {
            int fidx = Long.numberOfTrailingZeros(fidxs);
            FunNode fun = code.link(fidx);
            // null here means an external function; i.e. this Call
            // calls to an outside library and all its arguments escape.
            if( fun != null && !call.linked(fun) ) {
                call.link( fun );
                code._iter.add(fun);
                code._iter.addAll(fun._outputs);
            }
        }
    }

    // The core SCCP algorithm
    private static void sccp( CodeGen code, Ary<Type> oldTypes ) {
        // Iter workList is empty at this point
        // OPTIMISTIC PASS
        Node n;
        int count = 0;          // Debug counter
        while( (n = code._iter._work.pop()) != null ) {
            if( n.isDead() ) continue;
            count++;
            Type oval = n._type, nval = n.compute();
            if( oval == nval ) continue;
            assert oval.isa(nval);    // Types start high and always fall
            Type pesiVal = oldTypes.at(n._nid);
            assert nval.isa(pesiVal); // Never fall worse than the pessimistic pass
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
            if( n instanceof CallNode call && oval.isHigh() && !nval.isHigh() && call.fptr()._type instanceof TypeFunPtr )
                linkCG(code,call.tfp(),call);

            // If a otherwise-dead function pointer escapes, any future linked
            // caller might find and call it.  Force the function to be alive
            // and called by Start.
            if( n instanceof ReturnNode ret ) {
                // TODO: Check for function ptrs escaping through memory
                if( ret._type instanceof TypeTuple tt && tt._types[2] instanceof TypeFunPtr tfp ) {
                    // If an anonymous function has its address taken, it needs to
                    // be available in some compilation unit with a name that
                    // *other* CUs can link against.
                    for( long fidxs=tfp.fidxs(); fidxs!=0; fidxs=TypeFunPtr.nextFIDX(fidxs) ) {
                        int fidx = Long.numberOfTrailingZeros(fidxs);
                        FunNode fun = code._linker.at(fidx);
                        if( !fun.isDead() && (fun.nIns() < 2 || fun.in(1) != code._start ) ) {
                            // Function is added back to its original CompUnit
                            fun._compunit._stop.addDef(fun.ret());
                            // Function is reachable by any *remote* caller who gets the pointer!
                            fun.insertDef(1,code._start);
                            code._iter.add(fun);
                            for( Node p : fun._outputs )
                                if( p instanceof ParmNode parm )
                                    parm.insertDef(1,code.con(parm._con));
                        }
                    }
                }
            }

            // Since n._type changed, visit all output neighbors
            code._iter.addAll(n._outputs);
            n.moveDepsToWorklist(code._iter);
            // Quadratic (expensive) small-step assert
            //assert check(code);
        }
    }

    // Quadratic (expensive) small-step assert
    private static boolean check(CodeGen code) {
        code._start.walk( x -> {
                Type xval = x.compute();
                assert xval == x._type || (x._type.isa( xval ) && code._iter._work.on( x ));
                return null;
            });
        return true;
    }

    // After SCCP add everything changed to the worklist and see if peepholes
    // can make more progress.
    private static void moveChangesToWorklist(CodeGen code, Ary<Type> oldTypes) {
        code._start.walk( x -> {
                assert x.compute() == x._type;      // Hit the fixed point
                assert x._nid >= oldTypes._len || x._type.isa(oldTypes.at(x._nid)); // Hit at least the bottom-up type
                code._iter.add(x);
                return null;
            });
    }

    // Freeze field sizes; do struct layouts; convert field offsets into
    // constants.
    private static void fieldSizes( CodeGen code ) {
        for( int i=0; i<code._start.nOuts(); i++ ) {
            Node use = code._start.out(i);
            if( use instanceof ConFldOffNode off ) {
                Node con = off.asOffset();
                if( con != null ) { // Can be null for trying to reference missing field
                    off.subsume( con );
                    i--;
                }
            }
        }
    }
}
