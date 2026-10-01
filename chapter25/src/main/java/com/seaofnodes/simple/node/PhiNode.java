package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.Serialize;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.*;
import java.util.HashMap;
import java.util.IdentityHashMap;

public class PhiNode extends Node {

    public final String _label;

    public PhiNode( String label, Node... inputs) {
        super(inputs);
        _label = label;
    }
    public static PhiNode make(String label, Type minType, Node... inputs) {
        return minType instanceof TypeMem mem
            ? mem._alias==1
                ? new BulkMemPhiNode(label,inputs)
                : new MemPhiNode(label,mem._alias,inputs)
            : new PhiNode(label, inputs);
    }
    // Used by ParmNode
    public PhiNode(PhiNode phi, String label) { super(phi); _label = label; }
    // Used by instruction Selection
    public PhiNode(PhiNode phi) { this(phi,phi._label);  }
    // Used by the infinite-loop exit breaker
    public PhiNode(RegionNode r, Node sample) {
        super(new Node[]{r});
        _label = "";
        _type = sample._type;
        while( nIns() < r.nIns() )
            addDef(sample);
    }
    @Override public Tag serialTag() { return Tag.Phi; }
    @Override public void packed( BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, IdentityHashMap<Node, Integer> anodes ) {
        baos.packed1(nIns());
        baos.packed2(_label==null ? 0 : strs.get(_label));
    }
    static Node make( BAOS bais, String[] strs, Type[] types)  {
        Node[] ins = new Node[bais.packed1()];
        return new PhiNode(strs[bais.packed2()], ins);
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
    @Override public boolean isMem() { return _type instanceof TypeMem; }

    @Override
    public Type compute() {
        if( !(region() instanceof RegionNode r) )
            return Type.TOP;
        // During parsing Phis have to be computed type pessimistically.
        if( r.inProgress() || in(nIns()-1)==null )
            return Type.BOTTOM;
        // Set type to local top of the starting type
        Type t = Type.TOP;
        for( int i = 1; i < nIns(); i++ ) {
            // If the region's control input is live, add this as a dependency
            // to the control because we can be peeped should it become dead.
            Type ctrl = addDep(r.in(i))._type;
            if( ctrl != Type.XCONTROL && ctrl != Type.TOP ) {
                if( in(i)._type==Type.BOTTOM )
                    return Type.BOTTOM;
                t = t.meet(in(i)._type);
            }
        }

        // Above-center ranges may still fall to unwidened constants.
        // Widen only below-center ranges to preserve monotonicity.
        if( r instanceof LoopNode && // Only around loops
            t instanceof TypeInteger ti && // Only widen integers
            !ti.isHigh() && !ti.isConstant() ) {  // Only widen live ranges
            // Widen, to prevent infinite falling of TypeIntegers
            return ti.same_but_slightly_wider_than();
        }

        return t;
    }

    @Override
    public Node idealize() {
        RegionNode r = (RegionNode)region();
        if( r.inProgress() || nOuts()==0 )
            return null;        // Input is in-progress

        // If we have only a single unique input, become it.
        Node live = singleUniqueInput();
        if( live != null )
            return live;

        // No bother if region is going to fold dead paths soon
        for( int i=1; i<nIns(); i++ )
            if( r.in(i)._type == Type.XCONTROL )
                return null;

        // Generic "pull down op"
        Node progress;
        if( same_op() && (progress = drop_same_op()) != null )
            return progress;

        // If merging Phi(ZERO, guardNZ(N)) at the matching `if(N)` join, the
        // Phi is exactly N.  The guard arm proves N is non-zero, and the other
        // arm contributes N's zero value.  This is only legal if N is already
        // available at the Phi region; otherwise a return-scope/live-on-exit
        // Phi can lose the zero arm and export a branch-local value upward.
        Node unguard;
        if( (unguard=matchingGuardMerge(r,1)) != null ) return unguard;
        if( (unguard=matchingGuardMerge(r,2)) != null ) return unguard;

        return null;
    }

    private Node matchingGuardMerge(RegionNode r, int nzIdx) {
        Node zero = in(3-nzIdx);
        if( !(in(nzIdx) instanceof GuardNode cast && cast._nonZero) ||
            zero._type.makeZero()!=zero._type ||
            cast.in(1)==this )
            return null;
        if( !(r.in(nzIdx) instanceof CProjNode nz && nz._idx==0 && nz.ctrl() instanceof IfNode iff) )
            return null;
        if( !(r.in(3-nzIdx) instanceof CProjNode z && z._idx==1 && z.ctrl()==iff) )
            return null;
        // Predicate was guarded?
        Node n = cast.in(1), tested = null;
        while( true ) {
            if( iff.pred()==n ) tested = n;
            if( !(n instanceof GuardNode guard && guard._nonZero) ) break;
            n = guard.in(1);
        }
        CFGNode early = CFGNode.earlyCFG(n);
        return tested!=null && (early==null || early.dominates(r,this)) ? n : null;
    }

    // Same op on all Phi paths; all ops have only the Phi as a use.
    // None have a control input.
    private boolean same_op() {
        Node op = in(1);
        if( op instanceof EscapeNode || op instanceof CFGNode || op instanceof ConstantNode || op instanceof PhiNode ||
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
                // Do not introduce an operand Phi mixing incompatible type families.
                if( t==Type.BOTTOM ) {
                    for( int i=1; i<ins.length; i++ ) addDep(ins[i]);
                    cp.kill();
                    return null;
                }
                PhiNode phi = j==1 && op instanceof MemOpNode mem
                    ? new MemPhiNode(_label,mem._alias,ins)
                    : PhiNode.make(_label,t,ins);
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
    @Override public boolean eq( Node n ) {
        return !inProgress() && super.eq(n);
    }

    @Override
    public Parser.ParseException err() {
        if( _type != Type.BOTTOM ) return null;

        // Global BOTTOM retains the same role for non-scalar families.
        for( int i=1; i<nIns(); i++ )
            // Already an error, but better error messages come from elsewhere
            if( in(i)._type == Type.BOTTOM )
                return null;

        SB sb = new SB().p("No common type amongst ");
        for( int i=1; i<nIns(); i++ )
            sb.p(in(i)._type.toString()).p(" and ");
        return Parser.error(sb.unchar(5).toString(),null);
    }

    @Override public void gather( HashMap<String,Integer> strs ) {
        Serialize.gather(strs,_label);
    }
}
