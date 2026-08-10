package com.seaofnodes.simple.node;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.Serialize;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.*;
import java.util.BitSet;
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
    @Override public boolean isMem() { return _type instanceof TypeMem; }
    @Override public boolean isPinned() { return true; }

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

        // phi loop widening part
        if( r instanceof LoopNode && // Only around loops
            t != _type && // Types changed and are falling (the optimistic case, expected to fall forever)
            !t.isConstant() &&  // No need to widen constants
            t instanceof TypeInteger newi && // Only widen integers
            (!(_type instanceof TypeInteger oldi) || newi._widen <= oldi._widen) ) {
            // Widen, to prevent infinite falling of TypeIntegers
            return newi.same_but_slightly_wider_than();
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
        Node n = cast.in(1);
        while( n instanceof GuardNode guard ) {
            n = guard.in(1);
            throw Utils.TODO("test and remove TODO");
        }
        CFGNode early = CFGNode.earlyCFG(n,null);
        return iff.pred()==cast.in(1) && (early==null || early.dominates(r)) ? n : null;
    }

    // Same op on all Phi paths; all ops have only the Phi as a use.
    // None have a control input.
    private boolean same_op() {
        Node busy=null;
        for( int i=1; i<nIns(); i++ ) {
            Node op = in(i);
            if( in(1).getClass() != op.getClass() || op.in(0)!=null || in(1).nIns() != op.nIns() )
                return false;      // Wrong class or CFG bound or mismatched inputs
            if( in(1) instanceof MemOpNode mem ) {
                // Mismatched aliases
                if( mem._alias != ((MemOpNode)op)._alias ) return false;
                // Load is clobbered somewhere, and can not be pulled forward past the Phi?
                if( mem instanceof LoadNode )
                    for( Node use : op.outs() )
                        if( use instanceof StoreNode )
                            return false;
            }
            if( in(1) instanceof EscapeNode )
                return false;
            if( in(1) instanceof MemMergeNode )
                return false;   // Have to keep aliases straight
            if( op.nOuts() > 1 ) {
                if( busy==null ) busy = op;
                else {         // Too many users, but addDep in case lose users
                    for( Node out : op._outputs )
                        if( out!=null && out!=this )
                            addDep(out);
                    for( Node out : busy._outputs )
                        if( out!=null && out!=this )
                            addDep(out);
                    return false;
                }
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
                x = make(_label,op.in(j)._type);
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
