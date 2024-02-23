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
        if( path != 0 &&
            // Do not delete the entry path of a loop (ok to remove the back
            // edge and make the loop a single-entry Region which folds away
            // the Loop).  Folding the entry path confused the loop structure,
            // moving the backedge to the entry point.
            !(this instanceof LoopNode loop && loop.entry()==in(path)) ) {
            // Cannot use the obvious output iterator here, because a Phi
            // deleting an input might recursively delete *itself*.  This
            // shuffles the output array, and we might miss iterating an
            // unrelated Phi.
            for( int i=0; i<nOuts(); i++ ) {
                if( _outputs.get(i) instanceof PhiNode phi &&
                    phi.delDef(path).isDead() ) // Recursively deleted self
                    i--;    // Have to revisit at the same index
            }
            return isDead() ? new ConstantNode(Type.XCONTROL) : delDef(path);
        }
        // If down to a single input, become that input
        if( nIns()==2 && !hasPhi() )
            return in(1);       // Collapse if no Phis; 1-input Phis will collapse on their own
        return null;
    }

    private int findDeadInput() {
        for( int i=1; i<nIns(); i++ )
            if( in(i)._type==Type.XCONTROL )
                return i;
        return 0;               // All inputs alive
    }

    private boolean hasPhi() {
        for( Node phi : _outputs )
            if( phi instanceof PhiNode )
                return true;
        return false;
    }
    
    // Immediate dominator of Region is a little more complicated.
    private Node _idom;         // Immediate dominator cache
    @Override Node idom() {
        if( _idom != null ) {
            if( _idom.isDead() ) _idom=null;
            else return _idom; // Return cached copy
        }
        if( nIns()==2 ) return in(1); // 1-input is that one input
        if( nIns()!=3 ) return null;  // Fails for anything other than 2-inputs
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
        return nIns()>1 && in(nIns()-1) == null;
    }

    // Never equal if inProgress
    @Override boolean eq( Node n ) {
        return !inProgress();
    }
}
