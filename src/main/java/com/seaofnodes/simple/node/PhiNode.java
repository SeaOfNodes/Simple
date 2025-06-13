package com.seaofnodes.simple.node;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Utils;
import java.util.BitSet;

public class PhiNode extends Node {

    public final String _label;

    // The Phi type we compute must stay within the domain of the Phi.  Example
    // Int stays Int, Ptr stays Ptr, Control stays Control, Mem stays Mem.
    Type _minType;

    int lattice_drop;

    public PhiNode(String label, Type minType, Node... inputs) {
        super(inputs);
        _label = label;
        assert minType!=null;
        _minType = minType;
    }
    // Used by ParmNode
    public PhiNode(PhiNode phi, String label, Type minType) { super(phi); _label = label; _type = _minType = minType; }
    // Used by instruction Selection
    public PhiNode(PhiNode phi) { this(phi,phi._label,phi._minType );  }
    // Used by the infinite-loop exit breaker
    public PhiNode(RegionNode r, Node sample) {
        super(new Node[]{r});
        _label = "";
        _minType = sample._type;
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
    @Override public boolean isMem() { return _minType instanceof TypeMem; }
    @Override public boolean isPinned() { return true; }
    boolean isRPC() { return false; }

    @Override
    public Type compute() {
        if( !(region() instanceof RegionNode r) )
            return region()._type==Type.XCONTROL || region()._type==Type.TOP ? (_type instanceof TypeMem ? TypeMem.TOP : Type.TOP) : _type;
        // During parsing Phis have to be computed type pessimistically.
        if( r.inProgress() )
            // Loop-Phis must lift to the declared type, because that is how
            // the Parser keeps precise types until the loop finishes parsing.
            // Similar, ParmNodes use precise minType until all calls are
            // linked (post opto).
            return r instanceof LoopNode || (this instanceof ParmNode) ? _minType : Type.BOTTOM;
        // Set type to local top of the starting type
        Type t = Type.TOP;
        for (int i = 1; i < nIns(); i++) {
            // If the region's control input is live, add this as a dependency
            // to the control because we can be peeped should it become dead.
            Type ctrl = addDep(r.in(i))._type;
            if( ctrl != Type.XCONTROL && ctrl != Type.TOP ) {
                if( in(i)._type==Type.BOTTOM )
                    return Type.BOTTOM;
                t = t.meet(in(i)._type);
            }
        }
        Type newt = t.join( _minType );

        // phi loop widening part
        if( region() instanceof LoopNode && // Only around loops
            newt  instanceof TypeInteger newi &&
            // Types changed and are falling (the optimistic case, expected to fall forever)
            newi != _type ) {
            if( !newi.isConstant() && (!(_type instanceof TypeInteger oldi) || newi._widen <= oldi._widen) )
                return newi.same_but_slightly_wider_than(_minType);
        }

        return newt;
    }

    @Override
    public Node idealize() {
        if( !(region() instanceof RegionNode r ) )
            return in(1);       // Input has collapse to e.g. starting control.
        // Can upgrade minType even while in-progress
        if( _minType instanceof TypeMemPtr tmp && _minType.isFRef() ) {
            TypeMemPtr tmp2 = (TypeMemPtr) Parser.TYPES.get(tmp._obj._name);
            if( tmp2!=null && tmp2 != _minType ) {
                _minType = tmp2;
                return this;
            }
        }
        if( r.inProgress() || r.nIns()<=1 )
            return null;        // Input is in-progress

        // If we have only a single unique input, become it.
        Node live = singleUniqueInput();
        if( live != null ) {
            if( live._type.isa(_type) )
                return live;
            // Keep the Phi upcast
            return new CastNode(_type,null,live);
        }

        // No bother if region is going to fold dead paths soon
        for( int i=1; i<nIns(); i++ )
            if( r.in(i)._type == Type.XCONTROL )
                return null;

        // Simple Phi-after-MemMerge to a known alias can bypass.  Happens when inlining.
        if( _type instanceof TypeMem tmem && tmem._alias!=1 ) {
            for( int i=1; i<nIns(); i++ )
                if( in(i) instanceof MemMergeNode mem ) {
                    setDef(i,mem.alias(tmem._alias));
                    return this;
                }
        }

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
                Node ridom = r.idom(this);
                if( ridom instanceof IfNode iff && addDep(iff.pred())==val ) {
                    // Must walk the idom on the null side to make sure we hit False.
                    CFGNode idom = (CFGNode)r.in(nullx);
                    while( idom != null && idom.nIns() > 0 && idom.in(0) != iff ) idom = idom.idom();
                    if( idom instanceof CProjNode proj && proj._idx==1 )
                        return val;
                } else if( ridom != null ) addDep(ridom);
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
            if( in(1) instanceof MemOpNode mem && mem._alias != ((MemOpNode)op)._alias )
                return false;
            if( op.nOuts() > 1 ) { // Too many users, but addDep in case lose users
                for( Node out : op._outputs )
                    if( out!=null && out!=this )
                        addDep(out);
                return false;
            }
            for( int j=1; j<in(1).nIns(); j++ )
                if( op.in(j) instanceof ScopeNode || (op.in(j)==null ^ in(1).in(j)==null) )
                    return false; // Lazy Phi input
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
                x = new PhiNode(_label,op.in(j)._type.glb(false));
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

    // Never equal if inProgress.
    // Also, joins
    @Override public boolean eq( Node n ) {
        if( inProgress() ) return false;
        Type min = ((PhiNode)n)._minType;
        if( _minType==min ) return true;
        Type mt = min.meet(_minType);
        if( min!=mt && _minType!=mt ) return false;
        //// Theory says these 2 Phis CAN be merged/GVNd, but I need to pick the
        //// most general minType.
        //_minType = ((PhiNode)n)._minType = mt;
        //return true;
        return false;
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
