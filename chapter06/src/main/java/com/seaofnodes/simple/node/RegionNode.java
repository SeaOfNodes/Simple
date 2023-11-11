package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.type.Type;

public class RegionNode extends Node {
    public RegionNode(Node... inputs) { super(inputs); }

    @Override
    public String label() { return "Region"; }

    @Override
    StringBuilder _print1(StringBuilder sb) {
        return sb.append("Region").append(_nid);
    }

    @Override public boolean isCFG() { return true; }

    @Override
    public Type compute() {
        Type t = Type.XCONTROL;
        for (int i = 1; i < nIns(); i++)
            t = t.meet(in(i)._type);
        return t;
    }

    @Override
    public Node idealize() {
        int path = findDeadInput();
        if( path != 0 ) {
            for( Node phi : _outputs )
                if( phi instanceof PhiNode )
                    phi.delDef(path);
            delDef(path);

            // If down to a single input, become that input - but also make all
            // Phis an identity on *their* single input.
            if( nIns()==2 ) {
                for( Node phi : _outputs )
                    if( phi instanceof PhiNode )
                        // Currently does not happen, because no loops
                        throw Utils.TODO();
                return in(1);
            }
            return this;
        }
        return null;
    }

    private int findDeadInput() {
        for( int i=1; i<nIns(); i++ )
            if( in(i)._type==Type.XCONTROL )
                return i;
        return 0;               // All inputs alive
    }
}
