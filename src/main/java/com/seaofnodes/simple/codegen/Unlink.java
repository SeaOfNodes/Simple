package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.util.Ary;

// Discard call-graph edges after LoopTree. Machine selection keeps only this
// object's bodies; ideal evaluation keeps imports so their calls can execute.
abstract class Unlink {
    // Disconnect compiler-only interprocedural edges before scheduling. Keep
    // signatures in the linker; runtime calls resolve FIDXs, not graph edges.
    static Ary<FunNode> forEval(CodeGen code, StartNode start, StopNode stop) {
        Ary<Node> nodes = new Ary<>(Node.class);
        start.walk(n -> { nodes.add(n); return null; });
        Ary<FunNode> funs = new Ary<>(FunNode.class);
        for( Node n : nodes )
            if( n instanceof FunNode fun ) {
                fun.ret().keep();
                if( fun.summary()==null ) funs.add(fun);
            }
        for( Node n : nodes ) {
            if( n instanceof CallNode call ) call.unlink_all();
            if( n instanceof CLInitNode init ) init.unlink();
            if( n instanceof FunPtrNode ptr && ptr.ret()!=null ) ptr.setDef(1,null);
        }
        for( FunNode fun : funs ) {
            // Keep bodies rooted for scheduling after their callers detach.
            if( fun.nIns()==1 ) {
                fun.addDef(fun._compunit._start);
                for( Node use : fun._outputs )
                    if( use instanceof ParmNode parm ) {
                        ConstantNode con = ConstantNode.raw(parm._type);
                        con.setDef(0,start);
                        parm.addDef(con);
                    }
            }
            restoreRPC(fun);
        }
        code.releaseUnlinkedNodes();
        for( Node n : nodes )
            if( n instanceof StartCUNode unitStart ) detachEmptyStart(unitStart);
        // A summary is a dispatch target, not a schedulable implementation.
        // Retain its own wrapper graph without including it in this schedule.
        for( int i = 0; i<stop.nIns(); i++ ) {
            StopCUNode unit = (StopCUNode)stop.in(i);
            boolean summary = false;
            for( Node r : unit._inputs )
                summary |= ((ReturnNode)r).fun().summary()!=null;
            if( summary || unit.nIns()==0 ) {
                unit.start().setDef(0,null);
                stop.delDef(i--);
            }
        }
        for( Node n : nodes )
            if( n instanceof FunNode fun ) fun.ret().unkeep();
        return funs;
    }

    private static void unlinkImports(CodeGen code) {
        // Imported function constants keep their symbolic identity, not an
        // embedded copy of the other object's Return and body.
        for( Node n : code._start._outputs.asAry() )
            if( n instanceof FunPtrNode ptr && ptr.ret()!=null && !ptr.fun().isFromSource() ) {
                ptr.liftType(ptr._type);
                ptr.setDef(1,null);
            }
        for( FunNode fun : code._linker ) {
            if( fun==null || fun.isDead() || fun.isFromSource() )
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
            if( removeImports && !fun.isFromSource() ) {
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
            restoreRPC(fun);
        }
        code.releaseUnlinkedNodes();
        for( CompUnit cu : code._compunits.values() )
            if( cu._start!=null && !cu._start.isDead() ) detachEmptyStart(cu._start);

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

    private static void detachEmptyStart(StartCUNode start) {
        // An unused unit can lose its outer Stop edge during optimization.
        // Detach its empty wrapper from the scheduling root as well.
        if( start.in(1) instanceof StopCUNode stop && stop.nIns()==0 ) start.setDef(0,null);
    }

    private static void restoreRPC(FunNode fun) {
        if( fun.rpc()!=null ) return;
        // A single-call target can have a folded RPC even when a multi-target
        // call cannot inline it. Restore the parameter for codegen and for
        // Eval2's continuation, now that calls no longer supply graph edges.
        ReturnNode ret = fun.ret();
        ParmNode rpc = new ParmNode("$rpc",0,ret.rpc()._type,fun);
        rpc.addDef(ret.rpc());
        ret.setDef(3,rpc.init());
    }
}
