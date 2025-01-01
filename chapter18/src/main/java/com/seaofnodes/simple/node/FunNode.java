package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.IterPeeps;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFunPtr;

public class FunNode extends RegionNode {

    // When set true, this Call/CallEnd/Fun/Return is being trivially inlined
    boolean _folding;

    private TypeFunPtr _sig;    // Initial signature
    private ReturnNode _ret;    // Return pointer

    public FunNode( StartNode start, TypeFunPtr sig ) { super(null,start); _sig=sig; }

    @Override
    public String label() { return _sig._name; }

    // Find the one CFG user from Fun.  It's not always the Return, but always
    // the Return *is* a CFG user of Fun.
    @Override public CFGNode uctrl() {
        for( Node n : _outputs )
            if( n instanceof CFGNode cfg &&
                (cfg instanceof RegionNode || cfg.cfg0()==this) )
                return cfg;
        return null;
    }

    // Cannot create the Return and Fun at the same time; one has to be first.
    // So setting the return requires a second step.
    public void setRet(ReturnNode ret) { _ret=ret; }
    ReturnNode ret() { assert _ret!=null; return _ret; }

    // Signature can improve over time
    public TypeFunPtr sig() { return _sig; }
    void setSig( TypeFunPtr sig ) {
        assert sig.isa(_sig);
        if( _sig != sig )
            IterPeeps.add(this);
        _sig = sig;
    }

    @Override
    public Type compute() {
        // Only dead if no callers after SCCP
        return Type.CONTROL;
    }

    @Override
    public Node idealize() {
        // When can we assume no callers?  Or no other callers (except main)?
        // In a partial compilation, we assume Start gets access to any/all
        // top-level public structures and recursively what they point to.
        // This in turn is valid arguments to every callable function.
        //
        // In a total compilation, we can start from Start and keep things
        // more contained.


        // If no default/unknown caller, use the normal RegionNode ideal rules
        // to collapse
        if( unknownCallers() ) return null;
        return super.idealize();
    }

    // Always in-progress until we run out of unknown callers
    public boolean unknownCallers() { return in(1) instanceof StartNode; }

    @Override public boolean inProgress() { return unknownCallers(); }

    // Add a new function exit point.
    public void addReturn(Node ctrl, Node mem, Node rez) {  _ret.addReturn(ctrl,mem,rez);  }
}
