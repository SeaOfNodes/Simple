package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Ary;
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

        // The whole-world assumption: we use it if compiling `main`, and not otherwise.
        // The assumption is no unknown callers into functions:
        // - during or past an Opto phase (but not before, since parse through
        //   pessimistic must assume somebody calls an unknown function which
        //   calls everybody).
        // - Have a `main` to start tracing the Call Graph from
        // - - True for most test cases
        // - - False for lacking a main; we assume the linker will call all public functions

        // True if we can make the "whole world" assumption, which means we are
        // compiling the program entry: `main`.
        boolean wholeWorld = code.link(code._main) != null;


        // If wholeWorld, remove the Start inputs to functions - they are not
        // called unless they get linked (hence go dead and can be removed).
        // If not wholeWorld, keep only public methods (and not the std lib).
        unlinkStart(wholeWorld,code);


        // OPTIMISTIC INTERPROCEDURAL SCCO
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

        // Throw away the Call Graph

        // The remaining passes assume all calls are unlinked; i.e. we are
        // throwing away the Call Graph here.  Functions only reachable from
        // internal calls need to be re-hooked to stop/start less they go dead.
        removeCallGraph(code);

        // To help with testing, sort StopNode Returns by NID
        Arrays.sort(code._stop._inputs._es,0,code._stop.nIns(),(x,y) -> x._nid - y._nid );
    }

    // Reset Types before running the worklist algorithm
    private static void resetToTop(CodeGen code, Ary<Type> oldTypes ) {
        code._start.walk( x -> {
                oldTypes.setX(x._nid,x._type); // Record original types for asserts
                x._type = Type.TOP;            // Reset all Nodes to TOP
                code._iter.add(x); // Visit everybody at least once; most Nodes produce better than TOP
                // Unlink all existing (conservative) linkages.
                if( x instanceof CallNode call )
                    call.unlink_all();
                return null;
            } );
    }


    // If wholeWorld, remove the Start inputs to functions - they are not
    // called unless they get linked (hence go dead and can be removed).  If
    // not wholeWorld, keep only public methods (and not the std lib).
    private static void unlinkStart(boolean wholeWorld, CodeGen code) {
        for( FunNode fun : code._linker ) {
            if( fun != null && !fun.isDead() && !funIsPublic(code,wholeWorld,fun) ) {
                assert fun.in(1)==code._start; // Start always in slot 1
                fun.removeDeadPath(1);
                // By same logic, remove stop->return linkage, without
                // remove the (now dead) ReturnNode; it awaits in limbo
                // for the opto phase to link to some valid call
                code._stop._inputs.del(code._stop._inputs.find(fun.ret()));
                fun.ret().delUse(code._stop);
            }
        }
    }

    // Function is public (callable from Start directly).
    // - Always true for main
    // - Never true for stdlib, or anonymous functions
    // - Named functions use `!wholeWorld`
    private static boolean funIsPublic( CodeGen code, boolean wholeWorld, FunNode fun ) {
        // Always true for main
        if( fun.sig().fidx() == code._main.fidx() ) return true;
        // Never true for stdlib or anonymous functions
        if( fun._name == null || fun._name.startsWith("sys.") )
            return false;
        // Named functions (and not stdlib) are callable if not whole-world.
        return !wholeWorld;
    }

    // As part of building a CallGraph, when opto finds a function ptr flowing
    // into a Call, link the Call and Fun.
    private static void linkCG(CodeGen code, TypeFunPtr tfp, CallNode call) {
        if( tfp.nargs() != call.nargs() ) return; // Error calls hit this
        for( long fidxs=tfp.fidxs(); fidxs!=0; fidxs=TypeFunPtr.nextFIDX(fidxs) ) {
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

            // If a TFP adds a new function input to a call, link to the new Fun
            if( nval instanceof TypeFunPtr tfp ) {
                for( Node use : n._outputs )
                    if( !use._type.isHigh() && use instanceof CallNode call && call.fptr() == n )
                        linkCG(code,tfp,call);
            }

            // Link a Call which becomes alive
            if( n instanceof CallNode call && oval.isHigh() && !nval.isHigh() && call.fptr()._type instanceof TypeFunPtr )
                linkCG(code,call.tfp(),call);

            // Since n._type changed, visit all output neighbors
            code._iter.addAll(n._outputs);
            n.moveDepsToWorklist(code._iter);
            // Quadratic (expensive) small-step assert
            assert check(code);
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
                TypeMemPtr tmp = (TypeMemPtr) Parser.TYPES.get(off._name);
                off.subsume( off.asOffset(tmp._obj) );
                i--;
            }
        }
    }

    // The remaining passes assume all calls are unlinked; i.e. we are throwing
    // away the Call Graph here.  Functions only reachable from internal calls
    // need to be re-hooked to stop/start less they go dead.
    private static void removeCallGraph(CodeGen code) {
        code._start.walk( x -> {
                // Unlink all existing (conservative) linkages.
                if( x instanceof CallNode call ) {
                    for( Node y : call._outputs ) {
                        // Keep function alive and hooked to Start so it gets
                        // codegen'd.  Function is callable by this call via
                        // TFP, although perhaps not by any other means.
                        if( y instanceof FunNode fun && !(fun.in(1) instanceof StartNode) ) {
                            ParmNode rpc = fun.rpc();
                            if( rpc==null ) // Ensure valid RPC
                                rpc = makeRPC(fun);
                            // Insert a hook to Start and Stop
                            fun.insertDef(1,code._start);
                            for( Node use : fun._outputs )
                                if( use instanceof ParmNode parm )
                                    parm.insertDef(1,ConstantNode.make(parm._type).peephole());
                            code._stop.addDef(fun.ret());
                        }
                    }
                    call.unlink_all();
                }
                return null;
            } );

    }

    // Make a valid Parm RPC.
    // First make sure a valid RPC; single-call into multi-fun will have each
    // function having a single call site, so the RPC becomes a constant and
    // folds - but since multiple targets, the Call never inlines.  Recreate a
    // valid RPC so codegen (and Eval2) understands the calling convention.
    private static ParmNode makeRPC( FunNode fun ) {
        ParmNode rpc = new ParmNode("$rpc",0,fun.ret().rpc()._type,fun);
        for( int i=1; i<fun.nIns(); i++ ) {
            CallEndNode cend = ((CallNode)fun.in(i)).cend();
            rpc.addDef(ConstantNode.make(cend._rpc).peephole());
        }
        fun.ret().setDef(3,rpc.init());
        return rpc;
    }


}
