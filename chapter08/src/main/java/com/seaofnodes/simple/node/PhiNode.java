package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;


public class PhiNode extends Node {

    final String _label;

    public PhiNode(String label, Node... inputs) { super(inputs); _label = label; }

    @Override public String label() { return "Phi_"+_label; }

    @Override
    protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        if( region().inProgress() )
            p.p("Z");
        p.p("Phi(");
        for( Node in : _inputs ) p.n(in).p(",");
        return p.unchar(',').close();
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
        Node op = factoringOp();
        if( op.nIns()==3 && op.in(0)==null && !op.isCFG() && same_op(op) ) {
            Node[] lhss = new Node[nIns()];
            Node[] rhss = new Node[nIns()];
            lhss[0] = rhss[0] = in(0); // Set Region
            for( int i=1; i<nIns(); i++ ) {
                lhss[i] = factoringInput(op,in(i),1);
                rhss[i] = factoringInput(op,in(i),2);
            }
            Node phi_lhs = new PhiNode(_label,lhss).peephole();
            Node phi_rhs = new PhiNode(_label,rhss).peephole();
            return op.copy(phi_lhs,phi_rhs);
        }

        return null;
    }

    // View x*2 as x+x only while matching mixed Phi arms. Do not
    // expand the graph into Adds that would immediately strength-reduce again.
    private Node factoringOp() {
        Node op = in(1);
        if( doubled(op) )
            for( int i=2; i<nIns(); i++ )
                if( in(i) instanceof AddNode ) return in(i);
        return op;
    }

    private boolean doubled(Node n) {
        return n instanceof MulNode &&
            n.in(2)._type instanceof TypeInteger con && con.isConstant() && con.value()==2;
    }

    private static Node factoringInput(Node op, Node n, int j) {
        return op instanceof AddNode && !(n instanceof AddNode) ? n.in(1) : n.in(j);
    }

    private boolean same_op(Node op) {
        for( int i=1; i<nIns(); i++ )
            if( op.getClass()!=in(i).getClass() && !(op instanceof AddNode && doubled(in(i))) )
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
