package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFunPtr;

public class FunNode extends RegionNode {

    // When set true, this Call/CallEnd/Fun/Return is being trivially inlined
    boolean _folding;

    public final TypeFunPtr _sig; // Initial signature
    public String _name;        // Debug label, optional
    private ReturnNode _ret;     // Return pointer

    public FunNode( StartNode start, TypeFunPtr sig, String name ) { super(null,start); _sig=sig; _name=name; }

    @Override
    public String label() { return _name; }

    // Find the one CFG user from Fun.  It's not always the Return, but always
    // the Return *is* a CFG user of Fun.
    @Override public CFGNode uctrl() {
        for( Node n : _outputs )
            if( n instanceof CFGNode cfg &&
                (cfg instanceof RegionNode || cfg.cfg0()==this) )
                return cfg;
        return null;
    }

    public void setRet(ReturnNode ret) { _ret=ret; }
    ReturnNode ret() { assert _ret!=null; return _ret; }

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
}
