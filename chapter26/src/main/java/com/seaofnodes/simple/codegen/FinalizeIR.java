package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import java.util.Arrays;

// Settle layouts and detach the analyzed call graph before the backend.
abstract class FinalizeIR {
    static void run(CodeGen code) {
        fieldSizes(code);
        code._iter.iterate(code);
        removeCallGraph(code);
        Arrays.sort(code._stop._inputs._es,0,code._stop.nIns(),(x,y) -> x._nid - y._nid);
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
