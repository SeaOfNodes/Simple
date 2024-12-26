package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFunPtr;

public class FunNode extends RegionNode {

    // When set true, this Call/CallEnd/Fun/Return is being trivially inlined
    boolean _folding;

    TypeFunPtr _sig;            // Initial signature may be missing return
    String _name;               // Debug label, optional

    public FunNode( StartNode start, TypeFunPtr sig, String name ) { super(null,start); _sig=sig; _name=name; }

    @Override
    public String label() { return _name; }

    ReturnNode ret() {
        ReturnNode ret=null;
        for( Node n : _outputs )
            if( n instanceof ReturnNode ret0 ) {
                assert ret==null || ret==ret0; // Found more than one return
                ret=ret0;
            }
        return ret;
    }


    @Override
    public Type compute() {
        // Only dead if no callers after SCCP
        return Type.CONTROL;
    }

    @Override
    public Node idealize() {
        // If no default/unknown caller, use the normal RegionNode ideal rules
        // to collapse
        if( inProgress() ) return null;
        return super.idealize();
    }

    // Always in-progress until we run out of unknown callers
    @Override public boolean inProgress() { return in(1) instanceof StartNode; }
}
