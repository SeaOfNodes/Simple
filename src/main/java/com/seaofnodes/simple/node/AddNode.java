package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.*;

public class AddNode extends Node {
    public AddNode(Node lhs, Node rhs) { super(null, lhs, rhs); }

    @Override public String label() { return "Add"; }

    @Override public String glabel() { return "+"; }

    @Override
    StringBuilder _print1(StringBuilder sb) {
        in(1)._print0(sb.append("("));
        in(2)._print0(sb.append("+"));
        return sb.append(")");
    }


    @Override
    public Type compute() {
        if( in(1)._type instanceof TypeInteger i0 &&
            in(2)._type instanceof TypeInteger i1 ) {
            if (i0.isConstant() && i1.isConstant())
                return TypeInteger.constant(i0.value()+i1.value());
            return i0.meet(i1);
        }
        return Type.BOTTOM;
    }

    @Override
    public Node idealize () {
        Node lhs = in(1);
        Node rhs = in(2);
        Type t1 = lhs._type;
        Type t2 = rhs._type;

        // Already handled by peephole constant folding
        assert !(t1.isConstant() && t2.isConstant());

        // Add of 0.  We do not check for (0+x) because this will already
        // canonicalize to (x+0)
        if( t2 instanceof TypeInteger i && i.value()==0 )
            return lhs;

        // Add of same to a multiply by 2
        if( lhs==rhs )
            return new MulNode(lhs,new ConstantNode(TypeInteger.constant(2)).peephole());

        // Goal: a left-spine set of adds, with constants on the rhs (which then fold).

        // Move non-adds to RHS
        if( !(lhs instanceof AddNode) && rhs instanceof AddNode )
            return swap12();

        // Now we might see (add add non) or (add non non) or (add add add) but never (add non add)

        // Do we have  x + (y + z) ?
        // Swap to    (x + y) + z
        // Rotate (add add add) to remove the add on RHS
        if( rhs instanceof AddNode add )
            return new AddNode(new AddNode(lhs,add.in(1)).peephole(), add.in(2));

        // Now we might see (add add non) or (add non non) but never (add non add) nor (add add add)
        if( !(lhs instanceof AddNode) )
            return spline_cmp(lhs,rhs) ? swap12() : null;

        // Now we only see (add add non)

        // Do we have (x + con1) + con2?
        // Replace with (x + (con1+con2) which then fold the constants
        if( lhs.in(2)._type.isConstant() && t2.isConstant() )
            return new AddNode(lhs.in(1),new AddNode(lhs.in(2),rhs).peephole());

        if( lhs.in(2) instanceof PhiNode phi && phi.allCons() &&
            // Do we have ((x + (phi cons)) + con) ?
            // Do we have ((x + (phi cons)) + (phi cons)) ?
            // Push constant up through the phi: x + (phi con0+con0 con1+con1...)

            // Note that this is the exact reverse of Phi pulling a common op
            // down to reduce total op-count.  We don't get in an endless push-
            // up push-down peephole cycle because the constants all fold first.
            (t2.isConstant() || (rhs instanceof PhiNode && phi.in(0) == rhs.in(0) && rhs.allCons()) ) ) {
            Node[] ns = new Node[phi.nIns()];
            ns[0] = phi.in(0);
            // Push constant up through the phi: x + (phi con0+con0 con1+con1...)
            for( int i=1; i<ns.length; i++ )
                ns[i] = new AddNode(phi.in(i),t2.isConstant() ? rhs : rhs.in(i)).peephole();
            String label = phi._label + (rhs instanceof PhiNode rphi ? rphi._label : "");
            return new AddNode(lhs.in(1),new PhiNode(label,ns).peephole());
        }

        // Now we sort along the spline via rotates, to gather similar things together.

        // Do we rotate (x + y) + z
        // into         (x + z) + y ?
        if( spline_cmp(lhs.in(2),rhs) )
            return new AddNode(new AddNode(lhs.in(1),rhs).peephole(),lhs.in(2));

        return null;
    }

    // Compare two off-spline nodes and decide what order they should be in.
    // Do we rotate ((x + hi) + lo) into ((x + lo) + hi) ?
    // Generally constants always go right, then Phi-of-constants, then muls, then others.
    // Ties with in a category sort by node ID.
    // TRUE if swapping hi and lo.
    static boolean spline_cmp( Node hi, Node lo ) {
        if( lo._type.isConstant() ) return false;
        if( hi._type.isConstant() ) return true ;

        if( lo instanceof PhiNode && lo.allCons() ) return false;
        if( hi instanceof PhiNode && hi.allCons() ) return true ;

        if( lo instanceof PhiNode && !(hi instanceof PhiNode) ) return true;
        if( hi instanceof PhiNode && !(lo instanceof PhiNode) ) return false;

        // Same category of "others"
        return lo._nid > hi._nid;
    }

    @Override Node copy(Node lhs, Node rhs) { return new AddNode(lhs,rhs); }
}
