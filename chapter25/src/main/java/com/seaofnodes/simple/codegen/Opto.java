package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.simple.util.Utils;

import java.util.Arrays;
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
        Ary<Type> oldTypes = new Ary<>(Type.class); // Used for asserts only
        resetToTop(code,oldTypes);
        unlinkStart(code);

        // OPTIMISTIC INTERPROCEDURAL SCCP
        sccp(code, oldTypes);

        // After SCCP add everything changed to the worklist and see if
        // peepholes can make more progress.
        moveChangesToWorklist(code, oldTypes);

        // SCCP can leave a recursive numeric component at BOTTOM with no
        // arithmetic mode selected.  Resolve the whole component from all
        // available int/float evidence before IterPeeps consumes the types.
        resolveNumericModes(code);

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


        // Retain surviving functions in their CompUnits for later code
        // emission.  Start inputs are semantic: they mean unknown callers.
        for( FunNode fun : code._linker )
            if( fun != null && !fun.isDead() && !fun._type.isHigh() )
                keepInCompUnit(code,fun);

        for( CompUnit cu : code._compunits.values() ) {
            ProjNode mem = cu._start.proj(1);
            if( mem != null ) mem.unkeep();
        }

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
        // Keep the Start & Start.ProjMem alive even as we remove all uses
        for( CompUnit cu : code._compunits.values() ) {
            ProjNode mem = cu._start.proj(1);
            if( mem!=null ) mem.keep();
        }

        // Iterate the Stop-of-Stops
        for( Node stop : code._stop._inputs ) {
            for( int i=0; i<stop.nIns(); i++ ) {
                ReturnNode ret = (ReturnNode)stop.in(i);
                FunNode fun = ret.fun();
                // Start is a semantic unknown-caller edge.  Public functions
                // keep it; closed-world functions must be reached by a
                // concrete Call edge discovered during SCCP.
                if( !(code.owns(fun) && fun.isClz()) &&
                    !code.publicFIDX(fun.sig().fidx()) &&
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
        // type checking will report the unresolved reference afterwards.
        if( XInt.isHigh(fidxs) ) return;
        for( int fidx = XInt.next(fidxs,0); fidx >=0; fidx = XInt.next(fidxs,fidx) ) {
            // unlinkStart deliberately leaves uncalled functions in a
            // temporarily dead limbo.  A newly discovered call revives them;
            // do not use the cleanup lookup which removes dead linker entries.
            FunNode fun = code.lookupFun(fidx);
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
    private static void linkStart( CodeGen code, FunNode fun ) {
        assert !fun.isDead();
        StartCUNode start = fun._compunit._start;
        if( fun.nIns() < 2 || fun.in(1) != start ) {
            assert !start.isDead();
            keepInCompUnit(code,fun);
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

    // Keep a surviving function rooted in its compilation unit without
    // claiming that it has unknown callers.
    private static void keepInCompUnit(CodeGen code, FunNode fun) {
        StopCUNode stop = fun._compunit._stop;
        if( stop._inputs.find(fun.ret()) == -1 )
            code.add(stop).addDef(fun.ret());
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
            if( n instanceof CallNode call && oval.isHigh() && !nval.isHigh() && call.fptr()._type instanceof TypeFunPtr )
                linkCG(code,call.tfp(),call);

            // If an otherwise-dead function pointer escapes, any future linked
            // caller might find and call it.  Force the function to be alive
            // and called by Start.
            if( n instanceof StartNode && n._type instanceof TypeTuple tt )
                linkStart(code,tt);


            // Since n._type changed, visit all output neighbors
            code._iter.addAll(n._outputs);
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

    // After SCCP add everything changed to the worklist and see if peepholes
    // can make more progress.
    private static void moveChangesToWorklist(CodeGen code, Ary<Type> oldTypes) {
        code._start.walk( x -> {
                assert x.compute() == x._type;      // Hit the fixed point
                assert x._nid >= oldTypes._len || x._type.isa(oldTypes.at(x._nid)); // Hit at least the bottom-up type
                code.add(x);
                return null;
            });
    }

    // Resolve arithmetic modes which are ambiguous only because they belong to
    // a recursive component.  Float wins because integer operands can be
    // converted to float.
    private static void resolveNumericModes(CodeGen code) {
        final BitSet visited = new BitSet();
        code._stop.walk(n -> {
            if( n instanceof ModeNode modeNode && modeNode.mode()==0 ) {
                visited.clear();
                visited.set(n._nid); // Inspect inputs, not Bool's integer result
                int mode = 0;
                for( int i=1; i<n.nIns(); i++ )
                    mode |= numericEvidence(n.in(i),visited);
                if( mode != 0 )  {
                    // If both evidence, FP wins
                    modeNode.setMode((byte)((mode&2)==2 ? 2 : 1)).init();
                    code.add(n);
                }
            }
            return null;
        });
    }

    // Gather numeric evidence through Phi and interprocedural return cycles.
    // The visited set makes self- and mutually-recursive calls finite.
    private static int numericEvidence(Node n, BitSet visited) {
        if( n==null || visited.get(n._nid) ) return 0;
        visited.set(n._nid);

        Type t = n._type;
        if( t instanceof TypeFloat   ) return 2;
        if( t instanceof TypeInteger ) return 1;
        if( t!=Type.BOTTOM && !(t instanceof TypeTuple) )
            return 0; // Something unrelated, like a ptr

        int evidence = 0;
        switch( n ) {
        case ModeNode modeNode -> evidence = modeNode.mode();
        case PhiNode phi -> {}
        case ReturnNode ret -> {}
        case ProjNode proj when proj._idx==2 && proj.in(0) instanceof CallEndNode cend -> {
            for( int i=1; i<cend.nIns(); i++ )
                evidence |= numericEvidence(cend.in(i),visited);
        }
        case ConvertNode convert -> {
            Type dst = convert.dst();
            evidence = dst instanceof TypeFloat   ? 2 :
                       dst instanceof TypeInteger ? 1 : 0;
        }
        default -> { return 0; }
        }
        for( int i=1; i<n.nIns(); i++ )
            evidence |= numericEvidence(n.in(i),visited);

        return evidence;
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
