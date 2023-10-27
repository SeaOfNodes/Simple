package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;

public class PhiNode extends Node {

    final String _label;

    public PhiNode(String label, Node... inputs) { super(inputs); _label = label; }

    @Override public String label() { return "Phi_"+_label; }

    @Override public String glabel() { return "&phi;_"+_label; }

    @Override
    StringBuilder _print1(StringBuilder sb) {
        sb.append("Phi(");
        for( Node in : _inputs )
            in._print0(sb).append(",");
        sb.setLength(sb.length()-1);
        sb.append(")");
        return sb;
    }

    Node region() { return in(0); }

    @Override
    public Type compute() {
        return Type.BOTTOM;
    }

    @Override
    public Node idealize() {
        // Remove a "junk" Phi: Phi(x,x) is just x
        if( same_inputs() )
            return in(1);

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

    private boolean same_inputs() {
        for( int i=2; i<nIns(); i++ )
            if( in(1) != in(i) )
                return false;
        return true;
    }

}
