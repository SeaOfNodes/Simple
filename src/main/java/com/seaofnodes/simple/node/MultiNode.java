package com.seaofnodes.simple.node;

public abstract class MultiNode extends Node {

    public MultiNode(Node... inputs) { super(inputs); }

    // Find a projection by index
    ProjNode proj( int idx ) {
        for( Node out : _outputs )
            if( out instanceof ProjNode prj && prj._idx==idx )
                return prj;
        return null;
    }
}
