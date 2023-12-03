package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.type.Type;

import java.util.BitSet;

public class RegionNode extends Node {

    public RegionNode(Node... nodes) { super(nodes); }

    @Override
    public String label() { return "Region"; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return sb.append(label()).append(_nid);
    }

    @Override public boolean isCFG() { return true; }
    @Override public boolean isMultiHead() { return true; }

    @Override
    public Type compute() {
        if( inProgress() ) return Type.CONTROL;
        Type t = Type.XCONTROL;
        for (int i = 1; i < nIns(); i++)
            t = t.meet(in(i)._type);
        return t;
    }

    @Override
    public Node idealize() {
        if( inProgress() ) return null;
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

    // Immediate dominator of Region is a little more complicated.
    private Node _idom;         // Immediate dominator cache
    @Override Node idom() {
        if( _idom != null ) return _idom; // Return cached copy
        if( nIns()!=3 ) return null;      // Fails for anything other than 2-inputs
        // Walk the LHS & RHS idom trees in parallel until they match, or either fails
        Node lhs = in(1).idom();
        Node rhs = in(2).idom();
        while( lhs != rhs ) {
          if( lhs==null || rhs==null ) return null;
          var comp = lhs._idepth - rhs._idepth;
          if( comp >= 0 ) lhs = lhs.idom();
          if( comp <= 0 ) rhs = rhs.idom();
        }
        if( lhs==null ) return null;
        _idepth = lhs._idepth+1;
        return (_idom=lhs);
    }

    // True if last input is null
    public final boolean inProgress() {
        return in(nIns()-1) == null;
    }
}
