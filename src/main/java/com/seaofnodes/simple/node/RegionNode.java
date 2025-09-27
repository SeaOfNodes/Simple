package com.seaofnodes.simple.node;

import com.seaofnodes.simple.IterPeeps;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.type.Type;

import java.util.ArrayList;
import java.util.Arrays;
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
        // Delete dead paths into a Region
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
            // unrelated Phi. So on rare occasions we repeat the loop to get
            // all the Phis.
            int nouts = 0;
            while( nouts != nOuts() ) {
                nouts = nOuts();
                for( int i=0; i<nOuts(); i++ )
                    if( out(i) instanceof PhiNode phi && phi.nIns()==nIns() ) {
                        phi.delDef(path);
                        IterPeeps.addAll(phi._outputs);
                    }
            }
            return isDead() ? new ConstantNode(Type.XCONTROL) : delDef(path);
        }
        // If down to a single input, become that input
        if( nIns()==2 && !hasPhi() )
            return in(1);       // Collapse if no Phis; 1-input Phis will collapse on their own

        // If a CFG diamond with no merging, delete: "if( pred ) {} else {};"
        if( !hasPhi() &&       // No Phi users, just a control user
            in(1) instanceof ProjNode p1 &&
            in(2) instanceof ProjNode p2 &&
            p1.in(0)==p2.in(0) &&
            p1.in(0) instanceof IfNode iff )
            return iff.ctrl();

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
    @Override int idepth() {
        if( _idepth!=0 ) return _idepth;
        int d=0;
        for( Node n : _inputs )
            if( n!=null )
                d = Math.max(d,n.idepth());
        return _idepth=d;
    }

    @Override Node idom() {
        if( nIns()==2 ) return in(1); // 1-input is that one input
        if( nIns()!=3 ) return null;  // Fails for anything other than 2-inputs
        // Walk the LHS & RHS idom trees in parallel until they match, or either fails.
        // Because this does not cache, it can be linear in the size of the program.
        Node lhs = in(1);
        Node rhs = in(2);
        while( lhs != rhs ) {
          if( lhs==null || rhs==null ) return null;
          var comp = lhs.idepth() - rhs.idepth();
          if( comp >= 0 ) lhs = lhs.idom();
          if( comp <= 0 ) rhs = rhs.idom();
        }
        return lhs;
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
