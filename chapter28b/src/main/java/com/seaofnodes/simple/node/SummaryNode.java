package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.codegen.*;
import java.util.HashMap;

// Analysis of an imported function whose implementation has not been loaded.
// This is never an inlineable or executable body.
public class SummaryNode extends CFGNode implements MultiNode {

    public TypeTuple _state;
    // Callees and touched class initializers, by FIDX, for class-init cycle checks.
    public final int[] _classDependencyFIDXs;

    public SummaryNode(TypeTuple state, int[] classDependencyFIDXs, Node... inputs) {
        super(inputs);
        _state = state;
        _classDependencyFIDXs = classDependencyFIDXs;
    }

    @Override public String label() { return "Summary"; }
    @Override public boolean blockHead() { return true; }
    @Override public Type compute() {
        if( in(0)._type.isHigh() ) return TypeTuple.STATE.dual();
        // A library summary does not forget escapes already present in the caller.
        TypeMem mem = in(1)._type instanceof TypeMem m ? m : in(1)._type.isHigh() ? TypeMem.TOP : TypeMem.BOT;
        mem = (TypeMem)mem.meet(_state.mem());
        for( int i = 2; i<nIns(); i++ ) mem=mem.escapes(in(i)._type);
        return TypeTuple.make(_state.ctl(),mem,_state.ret());
    }
    @Override public Node idealize() { return null; }
    public TypeMem exposed(Node dep) {
        dep.addDepForwards(this);
        if( dep.addDepForwards(in(0))._type.isHigh() ) return TypeMem.START;
        TypeMem mem = (TypeMem)_state.mem();
        for( int i = 2; i<nIns(); i++ ) mem=mem.escapes(dep.addDepForwards(in(i))._type);
        return TypeMem.make(1,Type.BOTTOM,false,false,false,mem._escFs,mem._escAs);
    }
    @Override boolean _upgradeType(HashMap<String,Type> types) {
        TypeTuple state = (TypeTuple)_state.upgradeType(types);
        if( state==_state ) return false;
        unlock();
        _state=state;
        return true;
    }
    // Materialize an object and publish real FIDX targets without rewriting
    // callers or reoptimizing their active graphs. The evaluator schedules
    // this object's checked ideal IR before entering any of its functions.
    public static ElfReader load(CodeGen code, FunNode fun) {
        assert fun.summary()!=null;
        ElfReader elf = code.getElf(fun._compunit._obj);
        assert elf._nodes==null; // Not loaded yet, or we would already have code for `fun`
        elf.loadSimple(code);    // Load all the IR, including code for `fun`
        StopNode stop = (StopNode)elf._nodes.last();
        for( Node n : stop._inputs ) {
            StopCUNode stopcu = (StopCUNode)n;
            for( Node r : stopcu._inputs ) {
                FunNode target = ((ReturnNode)r).fun();
                target._compunit._start = stopcu.start();
                target._compunit._stop  = stopcu;
                code.link(target);
            }
        }
        return elf;
    }
}
