package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.GlobalBits;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeMem;
import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.util.Utils;

import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;

/**
 * A Phi for bulk memory.  It covers all aliases except {@link #_aliases};
 * those aliases must be represented by parallel precise MemPhis at the same
 * program slice.
 */
public class BulkMemPhiNode extends PhiNode {

    private static final BitSet EMPTY = new BitSet();

    // Excluded Aliases; empty means NO exclusions.
    // Directly visible by design.  Empty sets share EMPTY; splitAlias performs
    // copy-on-first-write and removes the node from GVN before mutation.
    public BitSet _aliases;

    public BulkMemPhiNode(String label, Node... inputs) {
        this(label,EMPTY,inputs);
    }

    private BulkMemPhiNode(String label, BitSet aliases, Node... inputs) {
        super(label, inputs);
        _aliases = aliases;
    }

    public BulkMemPhiNode(BulkMemPhiNode phi) {
        super(phi,phi._label);
        _aliases = phi._aliases.isEmpty() ? EMPTY : (BitSet)phi._aliases.clone();
    }

    public BulkMemPhiNode(RegionNode r, Node sample) {
        super(r,sample);
        _aliases = EMPTY;
    }

    public boolean isSplit(int alias) { return _aliases.get(alias); }

    /**
     * Record that an alias has been split out.  The graph rewrite installing
     * its parallel precise Phi must happen before this semantic change.
     */
    public void splitAlias(int alias) {
        assert alias > 1;
        if( _aliases.get(alias) ) return;
        unlock();
        if( _aliases==EMPTY )
            _aliases = new BitSet();
        _aliases.set(alias);
    }

    @Override public Tag serialTag() { return Tag.BulkMemPhi; }

    @Override
    public void packed(BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types,
                       IdentityHashMap<Node,Integer> anodes) {
        super.packed(baos,strs,types,anodes);
        baos.packed2(_aliases.cardinality());
        for( int alias = _aliases.nextSetBit(0); alias >= 0; alias = _aliases.nextSetBit(alias+1) )
            baos.packed2(alias);
    }

    static Node make(BAOS bais, String[] strs, Type[] types, GlobalBits fileAliases, GlobalBits aliases) {
        Node[] ins = new Node[bais.packed1()];
        String label = strs[bais.packed2()];
        BitSet bits = new BitSet();
        for( int i=bais.packed2(); i>0; i-- ) {
            int alias = bais.packed2();
            if( alias >= GlobalBits.RESERVED ) alias = aliases.map(fileAliases,alias);
            bits.set(alias);
        }
        return new BulkMemPhiNode(label,bits.isEmpty() ? EMPTY : bits,ins);
    }

    @Override
    public Type compute() {
        Type t = super.compute();
        return t instanceof TypeMem mem ? mem :
            t==Type.BOTTOM ? TypeMem.BOT :
            TypeMem.TOP;
    }

    @Override
    public Node idealize() {
        Node progress = super.idealize();
        if( progress != null ) return progress;

        if( !(region() instanceof RegionNode r ) )
            return in(1);       // Input has collapse to e.g. starting control.
        if( r.inProgress() || r.nIns()<=1 )
            return null;        // Input is in-progress
        if( nOuts()==0 ) return null;
        if( nIns() <= 2 ) return null;

        // "Peek through" a MemMerge that covers this alias set on its default
        for( int i=1; i<nIns(); i++ )
            if( in(i) instanceof MemMergeNode mmm && canPeek(mmm) ) {
                setDef(i,mmm.in(1));
                return this;
            }

        // If any input or output uses or defines a specific alias we cover,
        // make a private MemPhi for it, and add it to the excluded list.
        for( int i=1; i<nIns(); i++ ) {
            int alias = inputAlias(in(i));
            if( alias!=0 )
                return slice(alias);
        }

        for( int i=0; i<nOuts(); i++ ) {
            int alias = outputAlias(out(i));
            if( alias!=0 )
                return slice(alias);
        }

        return null;
    }

    static public boolean checkMem(Node n, int alias) {
        for( Node m : n._inputs )
            // Bulk memory supplies this alias
            if( m instanceof BulkMemPhiNode bulk ) {
                if( bulk._aliases.get(alias) )
                    return false;
            } else if( m instanceof MemMergeNode mmm &&
                       mmm.in(1) instanceof BulkMemPhiNode bulk &&
                       bulk._aliases.get(alias) &&
                       (alias >= mmm.nIns() || mmm.in(alias)==null) )
                return false;
        return true;
    }

    // True if the MemMerge has no precise slice still covered by this Phi.
    private boolean canPeek(MemMergeNode mmm) {
        return missingAlias(mmm,false)==0;
    }

    // Return an alias required by an input but still covered by this Phi, or 0.
    private int inputAlias(Node n) {
        return switch(n) {
        case ParmNode p -> { assert p._idx==1; yield 0; }
        case ScopeNode scope -> 0;
        case ReturnNode ret -> 0;
        case ProjNode proj -> 0;
        case ConstantNode con -> {
            assert !(con._con instanceof TypeMem tmem && tmem._alias != 1);
            yield 0;
        }
        case MemMergeNode mmm -> missingAlias(mmm,false);
        case MemOpNode mem -> unsplit(mem._alias);
        case BulkMemPhiNode bulk -> missingAlias(bulk);
        default -> throw Utils.TODO();
        };
    }

    // Return an alias required by a user but still covered by this Phi, or 0.
    private int outputAlias(Node use) {
        use = addDep(use);      // User alias sharpening changes this decision
        return switch(use) {
        case ScopeNode scope -> 0;
        case ParmNode parm -> 0;
        case MemMergeNode mmm -> mmm.in(1)==this ? missingAlias(mmm,true) : 0;
        case BulkMemPhiNode bulk -> missingAlias(bulk);
        case MemOpNode mem -> unsplit(mem._alias);
        case MemPhiNode phi -> unsplit(phi._alias);
        case EscapeNode esc -> esc.pub()==this ? unsplit(esc.fld()._alias) : 0;
        default -> {
            assert use instanceof ReturnNode || use instanceof CallNode
                : "Unexpected bulk-memory user "+use.getClass().getSimpleName()+"#"+use._nid+": "+use;
            yield 0;
        }
        };
    }

    // Alias zero/one is bulk, and an already excluded precise alias cannot
    // trigger another split of this Phi.
    private int unsplit(int alias) {
        return alias <= 1 || _aliases.get(alias) ? 0 : alias;
    }

    // First alias excluded by the neighboring bulk Phi but not by this one.
    private int missingAlias(BulkMemPhiNode bulk) {
        for( int alias = bulk._aliases.nextSetBit(0); alias >= 0; alias = bulk._aliases.nextSetBit(alias+1) )
            if( !_aliases.get(alias) )
                return alias;
        return 0;
    }

    // First precise MemMerge slice still covered by this Phi.  When inspecting
    // a user, ignore slots which point back to this Phi.
    private int missingAlias(MemMergeNode mmm, boolean user) {
        for( int alias=2; alias<mmm.nIns(); alias++ )
            if( mmm.in(alias)!=null &&
                mmm.alias(alias)!=mmm.in(1) &&
                (!user || mmm.in(alias)!=this) &&
                !_aliases.get(alias) )
                return alias;
        return 0;
    }

    // Slice out given alias
    private MemMergeNode slice( int alias ) {
        assert !_aliases.get(alias);
        assert !hasDup(alias);
        // Inputs are the same; MemPhi will sharpen his own inputs
        MemPhiNode mphi = new MemPhiNode("$"+alias,alias);
        for( int i=0; i<nIns(); i++ ) mphi.addDef(in(i));
        Node mem = mphi.peephole();
        assert ((TypeMem)mem._type)._alias==alias;

        BitSet aliases = ((BitSet)_aliases.clone());
        aliases.set(alias);
        BulkMemPhiNode bphi = new BulkMemPhiNode(_label,aliases);
        for( int i=0; i<nIns(); i++ )
            bphi.addDef(in(i));
        // Installing bphi creates a new bulk user with a different exclusion
        // set.  Queue the original memory inputs before bphi itself can peep
        // away and hide those neighbor relationships.
        for( int i=1; i<bphi.nIns(); i++ )
            CodeGen.CODE.add(bphi.in(i));
        Node bulk = bphi.peephole();
        CodeGen.CODE.add(bulk);
        CodeGen.CODE.add(mphi);
        return aggregate(bulk,alias,mem);
    }

    // Build aggregate memory over a bulk Phi, preserving every precise slice
    // which the bulk has already excluded.  The named alias is newly supplied
    // (or replaces its old slice).
    MemMergeNode aggregate(Node bulk, int alias, Node precise) {
        MemMergeNode mmm = new MemMergeNode(false,null,bulk);
        for( int old = _aliases.nextSetBit(0); old >= 0; old = _aliases.nextSetBit(old+1) )
            if( old != alias )
                mmm.alias(old,precisePhi(old));
        mmm.alias(alias,precise);
        assert checkMerge(mmm);
        mmm.init();
        // Slicing changes representation, not the set of values in memory.
        // Newly exposed precise inputs can carry conservative bulk escape
        // summaries; do not let those invent escapes absent from this Phi.
        if( _type instanceof TypeMem old && mmm._type instanceof TypeMem mem )
            mmm._type = TypeMem.make(mem._alias,mem._t,mem._one,mem._clz,
                                     mem._final,old._escFs,old._escAs);
        return mmm;
    }

    // Find the precise Phi parallel to this bulk Phi at the same merge.
    private Node precisePhi(int alias) {
        Node found = null;
        for( Node use : region().outs() )
            if( use instanceof MemPhiNode mphi && mphi._alias==alias ) {
                assert found==null;
                found = mphi;
            }
        // The parallel Phi may already have collapsed to a common input.  Do
        // not recover it from downstream MemMerges: they are distinct program
        // points and can legitimately carry different values for this alias.
        // Reconstruct from this Phi's merge inputs instead.  MemPhi selects
        // the alias from each input MemMerge and collapses when, for example,
        // the alias is loop-invariant here.
        if( found==null ) {
            MemPhiNode mphi = new MemPhiNode("$"+alias,alias);
            mphi.addDef(region());
            for( int i=1; i<nIns(); i++ )
                mphi.addDef(preciseInput(in(i),alias));
            found = mphi.peephole();
            CodeGen.CODE.add(found);
        }
        return found;
    }

    // Select an alias already split out of a predecessor's bulk memory.
    private Node preciseInput(Node mem, int alias) {
        if( mem instanceof MemMergeNode mmm )
            return mmm.alias(alias);
        if( mem instanceof BulkMemPhiNode bulk && bulk.isSplit(alias) )
            return bulk.precisePhi(alias);
        return mem;
    }

    // Every alias excluded by a bulk default must be supplied explicitly.
    static boolean checkMerge(MemMergeNode mmm) {
        if( !(mmm.in(1) instanceof BulkMemPhiNode bulk) ) return true;
        for( int alias = bulk._aliases.nextSetBit(0); alias >= 0; alias = bulk._aliases.nextSetBit(alias+1) )
            if( alias >= mmm.nIns() || mmm.in(alias)==null )
                return false;
        return true;
    }

    private boolean hasDup( int alias ) {
        for( Node use : region().outs() )
            if( use instanceof MemPhiNode mphi && mphi._alias==alias )
                return true;
        return false;
    }

    @Override public boolean eq(Node n) {
        return _aliases.equals(((BulkMemPhiNode)n)._aliases) && super.eq(n);
    }

    @Override int hash() { return _aliases.hashCode(); }
}
