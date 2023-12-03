package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;

import java.util.BitSet;

public class PhiNode extends Node {

    final String _label;

    public PhiNode(String label, Node... inputs) { super(inputs); _label = label; }

    @Override public String label() { return "Phi_"+_label; }

    @Override public String glabel() { return "&phi;_"+_label; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        if( region().inProgress() )
            sb.append("Z");
        sb.append("Phi(");
        for( Node in : _inputs ) {
            if (in == null) sb.append("____");
            else in._print0(sb, visited);
            sb.append(",");
        }
        sb.setLength(sb.length()-1);
        sb.append(")");
        return sb;
    }

    RegionNode region() { return (RegionNode)in(0); }
    @Override public boolean isMultiTail() { return true; }

    @Override
    public Type compute() {
        if( !(region() instanceof RegionNode r) || r.inProgress() )
            return Type.BOTTOM;
        Type t = Type.TOP;
        for (int i = 1; i < nIns(); i++)
            t = t.meet(in(i)._type);
        return t;
    }

    @Override
    public Node idealize() {
        if( !(region() instanceof RegionNode r ) || r.inProgress() )
            return null;

        // If we have only a single unique input, become it.
        Node live = singleUniqueInput();
        if (live != null)
            return live;

        // Pull "down" a common data op.  One less op in the world.  One more
        // Phi, but Phis do not make code.
        //   Phi(op(A,B),op(Q,R),op(X,Y)) becomes
        //     op(Phi(A,Q,X), Phi(B,R,Y)).
        Node op = in(1);
        if( op.nIns()==3 && op.in(0)==null && !op.isCFG() && same_op() ) {
            Node[] lhss = new Node[nIns()];
            Node[] rhss = new Node[nIns()];
            lhss[0] = rhss[0] = in(0); // Set Region
            for( int i=1; i<nIns(); i++ ) {
                lhss[i] = in(i).in(1);
                rhss[i] = in(i).in(2);
            }
            Node phi_lhs = new PhiNode(_label,lhss).peephole();
            Node phi_rhs = new PhiNode(_label,rhss).peephole();
            return op.copy(phi_lhs,phi_rhs);
        }

        return null;
    }

    private boolean same_op() {
        for( int i=2; i<nIns(); i++ )
            if( in(1).getClass() != in(i).getClass() )
                return false;
        return true;
    }

    /**
     * If only single unique input, return it
     */
    private Node singleUniqueInput() {
        Node live = null;
        for( int i=1; i<nIns(); i++ ) {
            if( region() instanceof LoopNode loop && loop.entry()._type == Type.XCONTROL )
                return null;    // Dead entry loops just ignore and let the loop collapse
            if( region().in(i)._type != Type.XCONTROL && in(i) != this )
                if( live == null || live == in(i) ) live = in(i);
                else return null;
        }
        return live;
    }

    @Override
    boolean allCons() {
        if( !(region() instanceof RegionNode r) || r.inProgress() )
            return false;
        return super.allCons();
    }
}
