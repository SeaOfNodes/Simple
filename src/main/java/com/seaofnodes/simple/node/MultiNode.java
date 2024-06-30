package com.seaofnodes.simple.node;

public interface MultiNode extends OutNode {

    // Find a projection by index
    default ProjNode proj( int idx ) {
        for( Node out : outs() )
            if( out instanceof ProjNode prj && prj._idx==idx )
                return prj;
        return null;
    }
}
