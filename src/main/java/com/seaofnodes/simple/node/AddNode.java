package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.*;

import java.util.BitSet;

import static com.seaofnodes.simple.Parser.con;

public class AddNode extends Node {
    public AddNode(Node lhs, Node rhs) { super(null, lhs, rhs); }

    @Override public String label() { return "Add"; }

    @Override public String glabel() { return "+"; }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        in(1)._print0(sb.append("("), visited);
        in(2)._print0(sb.append("+"), visited);
        return sb.append(")");
    }


    @Override
    public Type compute() {
        Type t1 = in(1)._type, t2 = in(2)._type;
        if( t1.isHigh() || t2.isHigh() )
            return TypeInteger.TOP;
        if( t1 instanceof TypeInteger i1 &&
            t2 instanceof TypeInteger i2 ) {
            if (i1.isConstant() && i2.isConstant())
                return TypeInteger.constant(i1.value()+i2.value());
            // Fold ranges like {0-1} + {2-3} into {2-4}.
            if( !overflow(i1._min,i2._min) &&
                !overflow(i1._max,i2._max) )
                return TypeInteger.make(i1._min+i2._min,i1._max+i2._max);
        }
        return TypeInteger.BOT;
    }

    static boolean overflow( long x, long y ) {
        if( (x ^    y ) < 0 ) return false; // unequal signs, never overflow
        return (x ^ (x + y)) < 0; // sum has unequal signs, so overflow
    }
    @Override
    public Node idealize () {
        Node lhs = in(1);
        Node rhs = in(2);
        if( rhs instanceof AddNode add && add.err()!=null )
            return null;
        Type t2 = rhs._type;

        // Add of 0.  We do not check for (0+x) because this will already
        // canonicalize to (x+0)
        if( t2 == TypeInteger.ZERO )
            return lhs;

        // Add of same to a multiply by 2
        if( lhs==rhs )
            return new ShlNode(null,lhs,con(1));

        // Goal: a left-spine set of adds, with constants on the rhs (which then fold).

        // Move non-adds to RHS
        if( !(lhs instanceof AddNode) && rhs instanceof AddNode )
            return swap12();

        // x+(-y) becomes x-y
        if( rhs instanceof MinusNode minus )
            return new SubNode(lhs,minus.in(1));

        // Now we might see (add add non) or (add non non) or (add add add) but never (add non add)

        // Do we have  x + (y + z) ?
        // Swap to    (x + y) + z
        // Rotate (add add add) to remove the add on RHS
        if( rhs instanceof AddNode add )
            return new AddNode(new AddNode(lhs,add.in(1)).peephole(), add.in(2));

        // Now we might see (add add non) or (add non non) but never (add non add) nor (add add add)
        if( !(lhs instanceof AddNode) )
            // Rotate; look for (add (phi cons) con/(phi cons))
            return spine_cmp(lhs,rhs,this) ? swap12() : phiCon(this,true);

        // Now we only see (add add non)

        // Dead data cycle; comes about from dead infinite loops.  Do nothing,
        // the loop will peep as dead after a bit.
        if( lhs.in(1) == lhs )
            return null;

        // Do we have (x + con1) + con2?
        // Replace with (x + (con1+con2) which then fold the constants
        // lhs.in(2) is con1 here
        // If lhs.in(2) is not a constant, we add ourselves as a dependency
        // because if it later became a constant then we could make this
        // transformation.
        if( addDep(lhs.in(2))._type.isConstant() && rhs._type.isConstant() )
            return new AddNode(lhs.in(1),new AddNode(lhs.in(2),rhs).peephole());


        // Do we have ((x + (phi cons)) + con) ?
        // Do we have ((x + (phi cons)) + (phi cons)) ?
        // Push constant up through the phi: x + (phi con0+con0 con1+con1...)
        Node phicon = phiCon(this,true);
        if( phicon!=null ) return phicon;

        // Now we sort along the spine via rotates, to gather similar things together.

        // Do we rotate (x + y) + z
        // into         (x + z) + y ?
        if( spine_cmp(lhs.in(2),rhs,this) )
            return new AddNode(new AddNode(lhs.in(1),rhs).peephole(),lhs.in(2));

        return null;
    }

    // Rotation is only valid for associative ops, e.g. Add, Mul, And, Or, Xor.
    // Do we have ((phi cons)|(x + (phi cons)) + con|(phi cons)) ?
    // Push constant up through the phi: x + (phi con0+con0 con1+con1...)
    static Node phiCon(Node op, boolean rotate) {
        Node lhs = op.in(1);
        Node rhs = op.in(2);
        if( rhs._type==TypeInteger.TOP ) return null;
        // LHS is either a Phi of constants, or another op with Phi of constants
        PhiNode lphi = pcon(lhs,op);
        if( rotate && lphi==null && lhs.nIns() > 2 ) {
            // Only valid to rotate constants if both are same associative ops
            if( lhs.getClass() != op.getClass() ) return null;
            lphi = pcon(lhs.in(2),op); // Will rotate with the Phi push
        }
        if( lphi==null ) return null;
        if( lphi.region().nIns() <=2 ) return null; // Phi is collapsing

        // RHS is a constant or a Phi of constants
        if( !(rhs instanceof ConstantNode) && pcon(rhs,op)==null )
            return null;

        // If both are Phis, must be same Region
        if( rhs instanceof PhiNode && lphi.in(0) != rhs.in(0) )
            return null;

        // Note that this is the exact reverse of Phi pulling a common op down
        // to reduce total op-count.  We don't get in an endless push-up
        // push-down peephole cycle because the constants all fold first.
        Node[] ns = new Node[lphi.nIns()];
        ns[0] = lphi.in(0);
        // Push constant up through the phi: x + (phi con0+con0 con1+con1...)
        for( int i=1; i<ns.length; i++ )
            ns[i] = op.copy(lphi.in(i), rhs instanceof PhiNode ? rhs.in(i) : rhs).peephole();
        String label = lphi._label + (rhs instanceof PhiNode rphi ? rphi._label : "");
        Node phi = new PhiNode(label,lphi._declaredType,ns).peephole();
        // Rotate needs another op, otherwise just the phi
        return lhs==lphi ? phi : op.copy(lhs.in(1),phi);
    }

    /**
     * Tests if the op is a phi and has all constant inputs.
     * If not, returns null.
     * If op is a phi, but its inputs are not all constants, then dep is added as
     * a dependency to the phi's non-const input, because if later the input turn into a constant
     * dep can make progress.
     */
    static PhiNode pcon(Node op, Node dep) {
        return op instanceof PhiNode phi && phi.allCons(dep) ? phi : null;
    }

    // Compare two off-spine nodes and decide what order they should be in.
    // Do we rotate ((x + hi) + lo) into ((x + lo) + hi) ?
    // Generally constants always go right, then Phi-of-constants, then muls, then others.
    // Ties with in a category sort by node ID.
    // TRUE if swapping hi and lo.
    static boolean spine_cmp( Node hi, Node lo, Node dep ) {
        if( lo._type.isConstant() ) return false;
        if( hi._type.isConstant() ) return true ;

        if( lo instanceof PhiNode lphi && lphi.region()._type==Type.XCONTROL ) return false;
        if( hi instanceof PhiNode hphi && hphi.region()._type==Type.XCONTROL ) return false;

        if( lo instanceof PhiNode && lo.allCons(dep) ) return false;
        if( hi instanceof PhiNode && hi.allCons(dep) ) return true ;

        if( lo instanceof PhiNode && !(hi instanceof PhiNode) ) return true;
        if( hi instanceof PhiNode && !(lo instanceof PhiNode) ) return false;

        // Same category of "others"
        return lo._nid > hi._nid;
    }

    @Override Node copy(Node lhs, Node rhs) { return new AddNode(lhs,rhs); }
    @Override Node copyF() { return new AddFNode(null,null); }
    @Override public Parser.ParseException err() {
        if( in(1)._type.isHigh() || in(2)._type.isHigh() ) return null;
        if( !(in(1)._type instanceof TypeInteger) ) return Parser.error("Cannot '"+label()+"' " + in(1)._type,null);
        if( !(in(2)._type instanceof TypeInteger) ) return Parser.error("Cannot '"+label()+"' " + in(2)._type,null);
        return null;
    }
}
