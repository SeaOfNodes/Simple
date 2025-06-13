package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Utils;

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

    public static PhiNode make(String label, Type type, Node... inputs) {
        return type instanceof TypeMem ? new BulkMemPhiNode(label,inputs) : new PhiNode(label,type,inputs);
    }

    @Override public String label() { return "Phi_"+MemOpNode.mlabel(_label); }

    @Override
    protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        if( !(region() instanceof RegionNode r) || r.inProgress() )
            p.p("Z");
        p.p("Phi(");
        for( Node in : _inputs ) p.n(in).p(",");
        return p.unchar(',').close();
    }

    public CFGNode region() { return (CFGNode)in(0); }
    @Override public boolean isMem() { return _minType instanceof TypeMem; }
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

        // Above-center ranges may still fall to unwidened constants.
        // Widen only below-center ranges to preserve monotonicity.
        if( region() instanceof LoopNode && // Only around loops
            newt  instanceof TypeInteger newi &&
            // Types changed and are falling (the optimistic case, expected to fall forever)
            newi != _type ) {
            if( !newi.isHigh() && !newi.isConstant() && (!(_type instanceof TypeInteger oldi) || newi._widen <= oldi._widen) )
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
            // Let the bulk input split this alias before collapsing a precise Phi.
            if( live instanceof BulkMemPhiNode ) return null;
            // Keep the Phi upcast
            return new CastNode(_type,null,live);
        }

        // No bother if region is going to fold dead paths soon
        for( int i=1; i<nIns(); i++ )
            if( r.in(i)._type == Type.XCONTROL )
                return null;

        // Phi(op(a,b,...),op(c,d,...)) -> op(Phi(a,c),Phi(b,d),...).
        Node down;
        if( same_op() && (down=drop_same_op())!=null ) return down;

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
        Node op = in(1);
        if( op instanceof CFGNode || op instanceof ConstantNode || op instanceof PhiNode ||
            op instanceof ProjNode || op instanceof NewNode || op instanceof ScopeNode || op instanceof MemMergeNode ) return false;
        // A bulk Phi must split its aliases before factoring precise Stores.
        if( this instanceof BulkMemPhiNode ) return false;
        // Bulk splitting currently identifies parallel slices by Region/alias.
        // Factoring can introduce another slice at a different memory point;
        // wait until that Region's bulk partitioning has finished.
        if( op instanceof MemOpNode )
            for( Node use : region()._outputs )
                if( use instanceof BulkMemPhiNode ) {
                    addDepForwards(use);
                    return false;
                }
        Node busy = null;
        for( int i=1; i<nIns(); i++ ) {
            Node n = in(i);
            if( addDep(region().in(i))._type==Type.XCONTROL ||
                op.getClass()!=n.getClass() || n.nIns()!=op.nIns() || n.in(0)!=null || !op.eq(n) ) return false;
            if( n instanceof MemOpNode mem && !mem.canDrop((MemOpNode)op,this) ) return false;
            // Moving a Store must remove the old effect, not duplicate it.
            // Allow one shared arm: the other arms disappear into the single
            // factored operation, usually reducing the total operation count.
            addDepForwards(n);
            if( n.nOuts()>1 ) {
                for( Node use : n._outputs )
                    if( use!=null && use!=this ) addDepForwards(use);
                if( n instanceof StoreNode || busy!=null ) return false;
                busy=n;
            }
            for( int j=1; j<n.nIns(); j++ )
                if( n.in(j) instanceof ScopeNode || ((n.in(j)==null) != (op.in(j)==null)) ) return false;
        }
        return true;
    }

    private Node drop_same_op() {
        Node op = in(1), cp = op.copyEmpty();
        cp._type = null;
        cp.addDef(null);
        for( int j=1; j<op.nIns(); j++ ) {
            Node x = op.in(j);
            boolean different = false;
            for( int i=2; i<nIns(); i++ )
                if( in(i).in(j)!=x ) different=true;
            if( different ) {
                Node[] ins = new Node[nIns()];
                ins[0] = region();
                Type t = Type.TOP;
                for( int i=1; i<nIns(); i++ ) {
                    ins[i] = in(i).in(j);
                    t = t.meet(ins[i]._type);
                }
                // Not accepts both pointers and integers, but a Phi cannot mix them.
                if( t==Type.BOTTOM ) {
                    for( int i=1; i<ins.length; i++ ) addDep(ins[i]);
                    cp.kill();
                    return null;
                }
                PhiNode phi = j==1 && op instanceof MemOpNode mem
                    ? new MemPhiNode(_label,mem._alias,ins)
                    : PhiNode.make(_label,t.glb(false),ins);
                x = phi.peephole();
            }
            cp.addDef(x);
        }
        // Factoring must not widen the result (e.g. correlated And operands).
        if( cp.compute().isa(compute()) ) return cp;
        for( int i=1; i<nIns(); i++ )
            for( int j=1; j<in(i).nIns(); j++ )
                if( in(i).in(j)!=null ) addDep(in(i).in(j));
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
