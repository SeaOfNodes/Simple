package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.*;

// Detach analysis call edges after building the loop tree. Every live body
// belongs to this invocation; preserve it for scheduling and emission.
abstract class FinishCalls {


    static void run(CodeGen code) {

        // The remaining passes assume all calls are unlinked; i.e. we are throwing
        // away the Call Graph here.  Functions only reachable from internal calls
        // need to be re-hooked to stop/start less they go dead.
        for( FunNode fun : code._linker ) {
            // Already linked to start, not going dead
            if( fun==null || fun.isDead() )
                continue;

            // Insert the post-Opto code-generation hook.  Unlike an Opto-time
            // Start input, this no longer carries unknown-caller semantics.
            ReturnNode ret = fun.ret();
            StartNode start = fun._compunit._start;
            assert start!=null;
            if( fun.nIns() <= 1 || fun.in(1) != start ) {
                fun.insertDef(1,start);
                for( Node use : fun._outputs )
                    if( use instanceof ParmNode parm )
                        parm.insertDef(1,ConstantNode.raw(parm._type));
                if( fun._compunit._stop._inputs.find(ret) == -1 )
                    fun._compunit._stop.addDef(ret);
            }
            // Unlink from Call
            for( int i=2; i<fun.nIns(); i++ )
                ((CallNode)fun.in(i)).unlink(fun,i--);
            if( fun.rpc()==null ) { // Ensure valid RPC
                // First make sure a valid RPC; single-call into multi-fun will have each
                // function having a single call site, so the RPC becomes a constant and
                // folds - but since multiple targets, the Call never inlines.  Recreate a
                // valid RPC so codegen (and Eval2) understands the calling convention.
                ParmNode rpc = new ParmNode( "$rpc", 0, ret.rpc()._type, fun );
                rpc.addDef(ret.rpc());
                ret.setDef( 3, rpc.init() );
            }
        }

        code._stop.walk(n -> {
            if( n instanceof CallEndNode cend )
                for( int i=1; i<cend.nIns(); i++ )
                    if( cend.in(i) instanceof ReturnNode )
                        cend.delDef(i--);
            if( n instanceof CallNode call )
                call.unlink_all();
            return null;
        });
    }
}
