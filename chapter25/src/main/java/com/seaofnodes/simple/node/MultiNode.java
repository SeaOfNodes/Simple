package com.seaofnodes.simple.node;

import com.seaofnodes.simple.util.Ary;


public interface MultiNode {

    abstract Ary<Node> outs();

    // Find a projection by index
    default ProjNode proj( int idx ) {
        for( Node out : outs() )
            if( out instanceof ProjNode prj && prj._idx==idx )
                return prj;
        return null;
    }

    // Find a projection by index
    default CProjNode cproj( int idx ) {
        for( Node out : outs() )
            if( out instanceof CProjNode prj && prj._idx==idx )
                return prj;
        return null;
    }

    // Return not-null if this projection index is a ideal copy.
    // Called by ProjNode ideal and used to collapse Multis.
    default Node pcopy(int idx) { return null; }
}
