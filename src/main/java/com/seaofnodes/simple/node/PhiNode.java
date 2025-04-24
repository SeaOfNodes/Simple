package com.seaofnodes.simple.node;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.type.*;
import java.util.BitSet;

public class PhiNode extends Node {

    public final String _label;

    // The Phi type we compute must stay within the domain of the Phi.
    // Example Int stays Int, Ptr stays Ptr, Control stays Control, Mem stays Mem.
    final Type _declaredType;

    public PhiNode(String label, Type declaredType, Node... inputs) { super(inputs); _label = label;  assert declaredType!=null; _declaredType = declaredType; }
    public PhiNode(PhiNode phi, String label, Type declaredType) { super(phi); _label = label; _type = _declaredType = declaredType; }
    public PhiNode(PhiNode phi) { super(phi); _label = phi._label; _declaredType = phi._declaredType;  }

    public PhiNode(RegionNode r, Node sample) {
        super(new Node[]{r});
        _label = "";
        _declaredType = sample._type;
        while( nIns() < r.nIns() )
            addDef(sample);
    }

    @Override public String label() { return "Phi_"+MemOpNode.mlabel(_label); }

    @Override public String glabel() { return "&phi;_"+_label; }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        if( !(region() instanceof RegionNode r) || r.inProgress() )
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

    public CFGNode region() { return (CFGNode)in(0); }
    @Override public boolean isMem() { return _declaredType instanceof TypeMem; }
    @Override public boolean isPinned() { return true; }

    @Override
    public Type compute() {
        if( !(region() instanceof RegionNode r) )
            return region()._type==Type.XCONTROL ? (_type instanceof TypeMem ? TypeMem.TOP : Type.TOP) : _type;
        // During parsing Phis have to be computed type pessimistically.
        if( r.inProgress() ) return _declaredType;
        // Set type to local top of the starting type
        Type t = _declaredType.glb().dual();//Type.TOP;
        for (int i = 1; i < nIns(); i++)
            // If the region's control input is live, add this as a dependency
            // to the control because we can be peeped should it become dead.
            if( addDep(r.in(i))._type != Type.XCONTROL )
                t = t.meet(in(i)._type);
        return t;
    }

    @Override
    public Node idealize() {
        if( !(region() instanceof RegionNode r ) )
            return in(1);       // Input has collapse to e.g. starting control.
        if( r.inProgress() || r.nIns()<=1 )
            return null;        // Input is in-progress

        // If we have only a single unique input, become it.
        Node live = singleUniqueInput();
        if (live != null)
            return live;

        // No bother if region is going to fold dead paths soon
        for( int i=1; i<nIns(); i++ )
            if( r.in(i)._type == Type.XCONTROL )
                return null;

        // Generic "pull down op"
        Node progress;
        if( same_op() && (progress = drop_same_op()) != null )
            return progress;

        // If merging Phi(N, cast(N)) - we are losing the cast JOIN effects, so just remove.
        if( nIns()==3 ) {
            if( in(1) instanceof CastNode cast && addDep(cast.in(1))==in(2) ) return in(2);
            if( in(2) instanceof CastNode cast && addDep(cast.in(1))==in(1) ) return in(1);
        }
        // If merging a null-checked null and the checked value, just use the value.
        // if( val ) ..; phi(Region,False=0/null,True=val);
        // then replace with plain val.
        if( nIns()==3 ) {
            int nullx = -1;
            if( in(1)._type == in(1)._type.makeZero() ) nullx = 1;
            if( in(2)._type == in(2)._type.makeZero() ) nullx = 2;
            if( nullx != -1 ) {
                Node val = in(3-nullx);
                if( val instanceof CastNode cast )
                    val = cast.in(1);
                if( addDep(r.idom(this)) instanceof IfNode iff && addDep(iff.pred())==val ) {
                    // Must walk the idom on the null side to make sure we hit False.
                    CFGNode idom = (CFGNode)r.in(nullx);
                    while( idom != null && idom.nIns() > 0 && idom.in(0) != iff ) idom = idom.idom();
                    if( idom instanceof CProjNode proj && proj._idx==1 )
                        return val;
                }
            }
        }

        return null;
    }

    // Same op on all Phi paths; all ops have only the Phi as a use.
    // None have a control input.
    private boolean same_op() {
        for( int i=1; i<nIns(); i++ ) {
            Node op = in(i);
            if( in(1).getClass() != op.getClass() || op.in(0)!=null || in(1).nIns() != op.nIns() )
                return false;      // Wrong class or CFG bound or mismatched inputs
            if( op.nOuts() > 1 ) { // Too many users, but addDep in case lose users
                for( Node out : op._outputs )
                    if( out!=null && out!=this )
                        addDep(out);
                return false;
            }
        }
        return true;
    }

    private Node drop_same_op() {
        assert !(in(1) instanceof CFGNode);
        Node op = in(1);
        Node cp = op.copy();
        cp._type = null;    // Fresh type
        cp.addDef(null);    // No control

        for( int j=1; j<op.nIns(); j++ ) {
            boolean needsPhi = false;
            Node x = op.in(j); // Jth input from sample #1
            for( int i=2; i<nIns(); i++ )
                if( in(i).in(j) != x )
                    { needsPhi=true; break; }
            if( needsPhi ) {
                x = new PhiNode(_label,op.in(j)._type.glb());
                x.addDef(region());
                for( int i=1; i<nIns(); i++ )
                    x.addDef(in(i).in(j));
                x = x.peephole();
            }
            cp.addDef(x);
        }
        // Test not running backwards, which can happen for e.g. And's
        if( cp.compute().isa(compute()) )
            return cp;
        cp.kill();
        return null;
    }

    /**
     * If only single unique input, return it
     */
    private Node singleUniqueInput() {
        if( region() instanceof LoopNode loop && loop.entry()._type == Type.XCONTROL )
            return null;    // Dead entry loops just ignore and let the loop collapse
        Node live = null;
        for( int i=1; i<nIns(); i++ ) {
            // If the region's control input is live, add this as a dependency
            // to the control because we can be peeped should it become dead.
            if( addDep(region().in(i))._type != Type.XCONTROL && in(i) != this )
                if( live == null || live == in(i) ) live = in(i);
                else return null;
        }
        return live;
    }

    @Override
    boolean allCons(Node dep) {
        if( !(region() instanceof RegionNode r) ) return false;
        // When the region completes (is no longer in progress) the Phi can
        // become a "all constants" Phi, and the "dep" might make progress.
        dep.addDep(this);
        if( r.inProgress() ) return false;
        return super.allCons(dep);
    }

    // True if last input is null
    public boolean inProgress() {
        return in(nIns()-1) == null;
    }

    // Never equal if inProgress
    @Override public boolean eq( Node n ) {
        return !inProgress();
    }

    @Override
    public Parser.ParseException err() {
        if( _type != Type.BOTTOM ) return null;

        // BOTTOM means we mixed e.g. int and ptr
        for( int i=1; i<nIns(); i++ )
            // Already an error, but better error messages come from elsewhere
            if( in(i)._type == Type.BOTTOM )
                return null;

        // Gather a minimal set of types that "cover" all the rest
        boolean ti=false, tf=false, tp=false, tn=false;
        for( int i=1; i<nIns(); i++ ) {
            Type t = in(i)._type;
            ti |= t instanceof TypeInteger x;
            tf |= t instanceof TypeFloat   x;
            tp |= t instanceof TypeMemPtr  x;
            tn |= t==Type.NIL;
        }
        return ReturnNode.mixerr(ti,tf,tp,tn, ((RegionNode)region())._loc);
    }
}
