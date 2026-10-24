package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.*;

// Discard call-graph edges after LoopTree. Machine selection keeps only this
// object's bodies; ideal evaluation keeps imports so their calls can execute.
abstract class Unlink {
    private static void unlinkImports(CodeGen code) {
        // Imported function constants keep their symbolic identity, not an
        // embedded copy of the other object's Return and body.
        for( Node n : code._start._outputs.asAry() )
            if( n instanceof FunPtrNode ptr && ptr.ret()!=null && !code.owns(ptr.fun()) ) {
                ptr.liftType(ptr._type);
                ptr.setDef(1,null);
            }
        for( FunNode fun : code._linker ) {
            if( fun==null || fun.isDead() || code.owns(fun) )
                continue;
            for( int i=1; i<fun.nIns(); i++ )
                if( fun.in(i) instanceof CallNode call )
                    call.unlink(fun,i--);
        }
        for( int i=0; i<code._stop.nIns(); i++ ) {
            StopCUNode stop=(StopCUNode)code._stop.in(i);
            CompUnit cu=code._compunits.get(stop.start()._fname);
            if( cu._src==null ) code._stop.delDef(i--);
        }
        // The whole-object wrappers now summarize only the emitted units.
        code._stop ._type=code._stop .compute();
        code._start._type=code._start.compute();
    }

    static void run(CodeGen code, boolean removeImports) {
        if( removeImports ) unlinkImports(code);

        // The remaining passes assume all calls are unlinked; i.e. we are throwing
        // away the Call Graph here.  Functions only reachable from internal calls
        // need to be re-hooked to stop/start less they go dead.
        for( FunNode fun : code._linker ) {
            // Already linked to start, not going dead
            if( fun==null || fun.isDead() )
                continue;

            // Imported functions are not emitted by this object. Ideal
            // evaluation retains them so their calls can actually execute.
            if( removeImports && !code.owns(fun) ) {
                while( fun.nIns() > 1 ) {
                    if( fun.in(1) instanceof CallNode call )
                        call.unlink(fun,1);
                    else
                        fun.removeDeadPath(1);
                }
                StartCUNode start = fun._compunit._start;
                StopCUNode  stop  = fun._compunit._stop;
                // Unhook the Stop->Return edge also
                ReturnNode ret = fun.ret();
                int idx = stop._inputs.find(ret);
                if( idx != -1 ) {
                    stop._inputs.del(idx);
                    ret.delUse(stop);
                }
                //
                if( stop.nIns()==0 ) {
                    idx = code._stop._inputs.find(stop);
                    if( idx != -1 )
                        code._stop.delDef(idx);
                    if( start.nIns() > 0 && start.in(0) != null )
                        start.setDef(0,null);
                    if( start.nIns() > 1 && start.in(1) != null )
                        start.setDef(1,null);
                }
                continue;
            }

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
