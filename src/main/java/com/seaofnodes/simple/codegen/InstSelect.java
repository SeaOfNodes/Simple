package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import java.util.BitSet;
import java.util.IdentityHashMap;

// Replace ideal nodes with machine nodes while preserving CFG/loop metadata.
// Selection also owns target ABI masks and rebuilding the copied graph's uses.
class InstSelect {
    private final CodeGen _code;
    private InstSelect(CodeGen code) { _code=code; }

    static CodeGen select(CodeGen code, String cpu, String callingConv, String base) {
        // Ideal evaluation schedules the ideal graph without a CPU port.
        if( cpu==null ) return code;
        return code.phase(CodeGen.Phase.Select, code._phase==CodeGen.Phase.Unlink,
                          () -> new InstSelect(code).run(cpu,callingConv,base));
    }

    private void run(String cpu, String callingConv, String base) {
        _code._callingConv = callingConv;

        // Look for CPU in fixed named place:
        //   com.seaofnodes.simple.node.cpus."cpu"."cpu.class"
        String clzFile = base+"."+cpu+"."+cpu;
        try { _code._mach = Class.forName(clzFile).asSubclass(Machine.class).getDeclaredConstructor(CodeGen.class).newInstance(_code); }
        catch( Exception e ) { throw new RuntimeException(e); }

        _code._cCallingConv = _code._mach.cCallingConv(callingConv);

        // Build global copies of common register masks.
        long callerSave = _code._mach.callerSave();
        long cCallerSave= _code._mach.callerSave(_code._cCallingConv);
        long  neverSave = _code._mach. neverSave();
        int maxReg = Math.min(64,_code._mach.regs().length);
        assert maxReg>=64 || (-1L << maxReg & callerSave)==0; // No stack slots in callerSave
        assert maxReg>=64 || (-1L << maxReg & cCallerSave)==0; // No stack slots in cCallerSave
        _code._callerSave = new RegMask(callerSave);
        _code._cCallerSave= new RegMask(cCallerSave);

        // Build a Return RegMask array.  All returns have the following inputs:
        // 0 - ctrl
        // 1 - memory
        // 2 - varies with returning GPR,FPR
        // 3 - RPC
        // 4+- Caller save registers
        _code._retMasks = new RegMask[(maxReg-_code._callerSave.size()-Long.bitCount( neverSave ))+4];
        for( int reg=0, i=4; reg<maxReg; reg++ )
            if( !_code._callerSave.test(reg) && ((1L<<reg)&neverSave)==0 )
                _code._retMasks[i++] = new RegMask(reg);
        _code._rpcMask = new RegMask(_code._mach.rpc());
        _code._retMasks[3] = _code._rpcMask;

        // Convert to machine ops
        _code._uid = 1;               // All new machine nodes reset numbering
        var map = new IdentityHashMap<Node,Node>();
        _instSelect( _code._stop, map );
        _code._stop  = (StopNode)map.get(_code._stop );
        StartNode start = (StartNode)map.get(_code._start);
        _code._start = start==null ? new StartNode(_code._start) : start;
        _instOuts(_code._stop,_code.visit());
        _code._visit.clear();

        // Replace the CompUnit stop (and list of functions)
        // with hardware-specific ones
        for( CompUnit cu : _code._compunits.values() ) {
            cu._start= (StartCUNode)map.get(cu._start);
            cu._stop = (StopCUNode )map.get(cu._stop );
        }
    }

    private Node _instSelect( Node n, IdentityHashMap<Node,Node> map ) {
        if( n==null ) return null;
        Node x = map.get(n);
        if( x !=null ) return x; // Been there, done that

        // If n is a MachNode already, then its part of a multi-node expansion.
        // It does not need instruction selection (already selected!)
        // but it does need its inputs walked.
        if( n instanceof MachNode ) {
            for( int i=0; i < n.nIns(); i++ )
                n._inputs.set(i, _instSelect(n.in(i),map) );
            return n;
        }

        // Produce a machine node from n; map it to flag as done so stops cycles.
        if( n instanceof MemOpNode mem && mem.off() instanceof ExternOffsetNode off ) {
            // External fields replace base+offset with symbol+0. Keep the ideal
            // declaring type on the address for machine load/store width selection.
            TypeMemPtr ptr = (TypeMemPtr)mem.ptr()._type;
            n._inputs.set(2,new ExternNode(TypeMemPtr.make((byte)2,ptr._obj,true),off._extern));
            n._inputs.set(3,ConstantNode.raw(TypeInteger.ZERO));
        }
        map.put(n, x=_code._mach.instSelect(n) );
        // Carry loop-tree and pre-order info across the ideal->mach transition
        if( n instanceof CFGNode ncfg && x instanceof CFGNode xcfg ) {
            xcfg._ltree = ncfg._ltree;
            xcfg._pre = ncfg._pre;
        }
        // Walk machine op and replace inputs with mapped inputs
        for( int i=0; i < x.nIns(); i++ )
            x._inputs.set(i, _instSelect(x.in(i),map) );
        // Post selection action
        if( x instanceof MachNode mach ) {
            if( n instanceof ReturnNode ret )
                ((ReturnNode)mach)._fun = (FunNode)map.get(ret._fun);
            mach.postSelect(_code);  // Post selection action
        }

        return x;
    }

    private void _instOuts( Node n, BitSet visit ) {
        if( visit.get(n._nid) ) return;
        visit.set(n._nid);
        for( Node in : n._inputs )
            if( in!=null ) {
                in._outputs.push(n);
                // CallNode special: outputs are partially ordered; CallEnd in slot 0
                if( in instanceof CallNode call && n instanceof CallEndNode cend && call.out(0) != cend )
                    call._outputs.swap(0,call.nOuts()-1);
                _instOuts(in,visit);
            }
    }
}
