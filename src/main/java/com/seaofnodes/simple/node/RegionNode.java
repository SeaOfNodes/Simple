package com.seaofnodes.simple.node;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.Type;
import java.util.*;

public class RegionNode extends CFGNode {
    // Source location for late discovered errors
    public Parser.Lexer _loc;

    public RegionNode(Parser.Lexer loc, Node...   nodes) { super(nodes); _loc = loc; }
    public RegionNode(RegionNode r, Parser.Lexer loc) { super(r); _loc = loc; }
    public RegionNode(RegionNode r) { super(r); if( r!=null ) _loc = r._loc; }

    @Override
    public String label() { return "Region"; }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return sb.append(label());
    }

    @Override public boolean blockHead() { return true; }

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

        Node progress = deadPath();
        if( progress!=null ) return progress;

        // If down to a single input, become that input
        if( nIns()==2 && !hasPhi() )
            return in(1);       // Collapse if no Phis; 1-input Phis will collapse on their own

        // If a CFG diamond with no merging, delete: "if( pred ) {} else {};"
        if( !hasPhi() &&         // No Phi users, just a control user
            in(1) instanceof CProjNode p1 &&
            in(2) instanceof CProjNode p2 &&
            addDep(p1.in(0))==addDep(p2.in(0)) &&
            p1.in(0) instanceof IfNode iff ) {
            // Replace with the iff.ctrl directly
            if( nIns()==3 ) return iff.ctrl();
            // Just delete the path for fat Regions
            setDef(1,iff.ctrl());
            return delDef(2);
        }

        // Flatten stack regions (no loops involved)
        if( getClass() == RegionNode.class ) {
            for( int i=1; i<nIns(); i++ )
                if( cfg(i) instanceof RegionNode region && region.getClass() == RegionNode.class && region.nIns()>2 && !hasMidUser(region) ) {
                    assert !region.inProgress();
                    // Fold Phis
                    for( Node use : _outputs ) {
                        if( use instanceof PhiNode phi ) {
                            PhiNode phi2 = phi.in(i) instanceof PhiNode phi2x && phi2x.region()==region ? phi2x : null;
                            for( int j=1; j<region.nIns(); j++ )
                                phi.addDef(phi2==null ? phi.in(i) : phi2.in(j));
                            CodeGen.CODE.add(phi);
                        }
                    }

                    // Fold Region
                    for( int j=1; j<region.nIns(); j++ )
                        addDef(region.in(j));
                    setDef(i,Parser.XCTRL);
                    return this;
                }
        }

        return null;
    }

    Node deadPath() {
        // Delete dead paths into a Region
        int path = findDeadInput();
        if( path==0 ) return null;
        // Do not delete the entry path of a loop (ok to remove the back edge
        // and make the loop a single-entry Region which folds away the Loop).
        // Folding the entry path confused the loop structure, moving the
        // backedge to the entry point.
        if( this instanceof LoopNode loop && loop.entry()==in(path) )
            return null;
        // Cannot use the obvious output iterator here, because a Phi deleting
        // an input might recursively delete *itself*.  This shuffles the
        // output array, and we might miss iterating an unrelated Phi. So on
        // rare occasions we repeat the loop to get all the Phis.
        int nouts = 0;
        while( nouts != nOuts() ) {
            nouts = nOuts();
            for( int i=0; i<nOuts(); i++ )
                if( out(i) instanceof PhiNode phi && phi.nIns()==nIns() )
                    CodeGen.CODE.addAll(phi.delDef(path)._outputs);
        }
        return isDead() ? Parser.XCTRL : delDef(path);
    }


    private int findDeadInput() {
        for( int i=1; i<nIns(); i++ )
            if( in(i)._type==Type.XCONTROL )
                return i;
        return 0;               // All inputs alive
    }

    boolean hasPhi() {
        for( Node phi : _outputs )
            if( phi instanceof PhiNode )
                return true;
        return false;
    }

    // Is there a value in-between 2 Regions?  Difficult to correctly fuse
    // without extensive testing, so just ignore.
    boolean hasMidUser(RegionNode r) {
        for( Node use : r._outputs ) {
            if( use==this ) continue;
            if( use instanceof PhiNode ) {
                for( Node data : use._outputs ) {
                    if( !(data instanceof PhiNode phi2) || phi2.region()!=this )
                        { addDep(use); addDep(data); return true; }
                }
            } else
                { addDep(use);  return true; } // Control user
        }
        return false;
    }


    // Immediate dominator of Region is a little more complicated.
    @Override public int idepth() {
        if( CodeGen.CODE.validIDepth(_idepth) )
            return _idepth;
        int d=0;
        for( Node n : _inputs )
            if( n!=null )
                d = Math.max(d,CodeGen.CODE.iDepthFrom(((CFGNode)n).idepth()));
        return _idepth = d;
    }

    @Override public CFGNode idom(Node dep) {
        CFGNode lca = null;
        // Walk the LHS & RHS idom trees in parallel until they match, or either fails.
        // Because this does not cache, it can be linear in the size of the program.
        for( int i=1; i<nIns(); i++ )
            lca = cfg(i)._idom(lca,dep);
        return lca;
    }

    // True if last input is null
    public boolean inProgress() { return nIns()>1 && in(nIns()-1) == null; }

    // Never equal if inProgress
    @Override public boolean eq( Node n ) { return !inProgress(); }

}
