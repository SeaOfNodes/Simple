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
    public PhiNode(PhiNode phi, String label, Type declaredType) { super(phi); _label = label; _declaredType = declaredType; }
    public PhiNode(PhiNode phi) { super(phi); _label = phi._label; _declaredType = phi._declaredType;  }

    public PhiNode(RegionNode r, Node sample) {
        super(new Node[]{r});
        _label = "";
        _declaredType = sample._type;
        while( nIns() < r.nIns() )
            addDef(sample);
    }

    public static PhiNode make(String label, Type type, Node... inputs) {
        return type instanceof TypeMem ? new BulkMemPhiNode(label,inputs) : new PhiNode(label,type,inputs);
    }

    @Override public String label() { return "Phi_"+MemOpNode.mlabel(_label); }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
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
    @Override public boolean isMultiTail() { return true; }
    @Override public boolean isMem() { return _declaredType instanceof TypeMem; }

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
            if( r.in(i).addDep(this)._type != Type.XCONTROL )
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

        // Phi(op(a,b,...),op(c,d,...)) -> op(Phi(a,c),Phi(b,d),...).
        Node down;
        if( same_op() && (down=drop_same_op())!=null ) return down;

        // If merging Phi(N, cast(N)) - we are losing the cast JOIN effects, so just remove.
        if( nIns()==3 ) {
            if( in(1) instanceof CastNode cast && cast.in(1).addDep(this)==in(2) ) return in(2);
            if( in(2) instanceof CastNode cast && cast.in(1).addDep(this)==in(1) ) return in(1);
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
                if( r.idom(this).addDep(this) instanceof IfNode iff && iff.pred().addDep(this)==val ) {
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
                    use.addDepForwards(this);
                    return false;
                }
        Node busy = null;
        for( int i=1; i<nIns(); i++ ) {
            Node n = in(i);
            if( region().in(i).addDep(this)._type==Type.XCONTROL ||
                op.getClass()!=n.getClass() || n.nIns()!=op.nIns() || n.in(0)!=null || !op.eq(n) ) return false;
            if( n instanceof MemOpNode mem && !mem.canDrop((MemOpNode)op,this) ) return false;
            // Moving a Store must remove the old effect, not duplicate it.
            // Allow one shared arm: the other arms disappear into the single
            // factored operation, usually reducing the total operation count.
            n.addDepForwards(this);
            if( n.nOuts()>1 ) {
                for( Node use : n._outputs )
                    if( use!=null && use!=this ) use.addDepForwards(this);
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
                PhiNode phi = j==1 && op instanceof MemOpNode mem
                    ? new MemPhiNode(_label,mem._alias,ins)
                    : PhiNode.make(_label,t.glb(),ins);
                x = phi.peephole();
            }
            cp.addDef(x);
        }
        // Factoring must not widen the result (e.g. correlated And operands).
        if( cp.compute().isa(compute()) ) return cp;
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
            if( region().in(i).addDep(this)._type != Type.XCONTROL && in(i) != this )
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
        addDep(dep);
        if( r.inProgress() ) return false;
        return super.allCons(dep);
    }

    // True if last input is null
    public boolean inProgress() {
        return in(nIns()-1) == null;
    }

    // Never equal if inProgress
    @Override boolean eq( Node n ) {
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
