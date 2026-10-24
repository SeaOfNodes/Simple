package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.*;
import java.util.BitSet;
import java.util.IdentityHashMap;

// Replace ideal nodes with machine nodes, then rebuild the copied graph's uses.
class InstSelect {
    private final CodeGen _code;
    InstSelect(CodeGen code) { _code=code; }

    @SuppressWarnings("unchecked")
    void run(String cpu, String callingConv, String base) {
        _code._callingConv = callingConv;

        // Look for CPU in fixed named place:
        //   com.seaofnodes.simple.node.cpus."cpu"."cpu.class"
        String clzFile = base+"."+cpu+"."+cpu;
        try { _code._mach = ((Class<Machine>) Class.forName( clzFile )).getDeclaredConstructor(new Class[]{CodeGen.class}).newInstance(_code); }
        catch( Exception e ) { throw new RuntimeException(e); }

        // Build global copies of common register masks.
        long callerSave = _code._mach.callerSave();
        long  neverSave = _code._mach. neverSave();
        int maxReg = Math.min(64,_code._mach.regs().length);
        assert maxReg>=64 || (-1L << maxReg & callerSave)==0; // No stack slots in callerSave
        _code._callerSave = new RegMask(callerSave);

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
        _code._stop  = ( StopNode)map.get(_code._stop );
        StartNode start = (StartNode)map.get(_code._start);
        _code._start = start==null ? new StartNode(_code._start) : start;
        _instOuts(_code._stop,_code.visit());
        _code._visit.clear();
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
        map.put(n, x=select(n) );
        // Stop retains the callable body. Its lifetime edge is not a machine operand.
        if( n instanceof FunPtrNode ) x._inputs.pop();
        // Walk machine op and replace inputs with mapped inputs
        for( int i=0; i < x.nIns(); i++ )
            x._inputs.set(i, _instSelect(x.in(i),map) );
        if( x instanceof MachNode mach )
            mach.postSelect(_code);  // Post selection action

        return x;
    }

    private void _instOuts( Node n, BitSet visit ) {
        if( visit.get(n._nid) ) return;
        visit.set(n._nid);
        for( Node in : n._inputs )
            if( in!=null ) {
                in._outputs.push(n);
                _instOuts(in,visit);
            }
    }


    private Node select(Node n) {
        if( n instanceof GuardNode guard ) return new GuardMach(guard);
        if( n instanceof PtrToIntNode ptr ) return new PtrToIntMach(ptr);
        Node op = n;
        if( n instanceof ModeNode mode && mode.mode()==2 ) {
            // Adapt to the existing CPU selectors. As with machine expansion,
            // copy inputs without adding uses to the old graph; _instOuts
            // rebuilds reverse edges after selection has finished.
            op = switch(n) {
            case AddNode add -> new AddFNode(add);
            case SubNode sub -> new SubFNode(sub);
            case MulNode mul -> new MulFNode(mul);
            case DivNode div -> new DivFNode(div);
            case MinusNode neg -> new MinusFNode(neg);
            default -> n;
            };
            op._type = n._type;
        }
        return _code._mach.instSelect(op);
    }
}
