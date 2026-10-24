package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeMem;
import java.util.BitSet;

/**
 * A Phi for bulk memory.  It covers all aliases except {@link #_aliases};
 * those aliases must be represented by parallel precise MemPhis at the same
 * program slice.
 */
public class BulkMemPhiNode extends PhiNode {

    private static final BitSet EMPTY = new BitSet();

    // Excluded Aliases; empty means NO exclusions.
    // Each split clones the exclusions; published sets are never mutated.
    public final BitSet _aliases;

    public BulkMemPhiNode(String label, Node... inputs) {
        this(label,EMPTY,inputs);
    }

    private BulkMemPhiNode(String label, BitSet aliases, Node... inputs) {
        super(label,TypeMem.BOT,inputs);
        _aliases = aliases;
    }

    public boolean isSplit(int alias) { return _aliases.get(alias); }

    public BulkMemPhiNode(BulkMemPhiNode phi) { super(phi); _aliases = phi._aliases; }

    @Override public String label() { return "BulkPhi"+_aliases; }

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
        if( ((RegionNode)region()).inProgress() || region().nIns()<=1 )
            return null;        // Input is in-progress

        // "Peek through" a MemMerge that covers this alias set on its default
        for( int i=1; i<nIns(); i++ )
            if( in(i) instanceof MemMergeNode mmm && canPeek(mmm) ) {
                setDef(i,CodeGen.CODE.add(mmm.in(1)));
                return this;
            }

        // If any input or output uses or defines a specific alias we cover,
        // make a private MemPhi for it, and add it to the excluded list.
        for( int i=1; i<nIns(); i++ ) {
            int alias = inputAlias(in(i));
            if( alias!=0 )
                return slice(alias);
        }

        for( int i=0; i<_outputs.size(); i++ ) {
            int alias = outputAlias(_outputs.get(i));
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
        return mmm.in(1)!=null && missingAlias(mmm)==0;
    }

    // Return an alias required by an input but still covered by this Phi, or 0.
    private int inputAlias(Node n) {
        return switch(n) {
        case ProjNode proj -> 0;
        case ParmNode parm -> 0;
        case ConstantNode con -> 0;
        case MemMergeNode mmm -> missingAlias(mmm);
        case MemOpNode mem -> unsplit(mem._alias);
        case BulkMemPhiNode bulk -> missingAlias(bulk);
        default -> throw new AssertionError("Unexpected memory input "+n);
        };
    }

    // Return an alias required by a user but still covered by this Phi, or 0.
    private int outputAlias(Node use) {
        // This decision depends backwards on an immediate user's alias/set.
        // Normal graph-neighbor enqueueing flows from defs to uses, so retain
        // an explicit forward dependency to revisit this Phi when the user
        // sharpens.
        addDepForwards(use);
        return switch(use) {
        case ScopeNode scope -> 0;
        case MemMergeNode mmm -> missingAlias(mmm);
        case BulkMemPhiNode bulk -> missingAlias(bulk);
        case MemOpNode mem -> unsplit(mem._alias);
        case MemPhiNode phi -> unsplit(phi._alias);
        default -> {
            assert use instanceof ReturnNode || use instanceof CallNode || use instanceof ParmNode
                : "Unexpected bulk-memory user "+use;
            yield 0;
        }
        };
    }

    // Alias zero/one is bulk, and an already excluded precise alias cannot
    // trigger another split of this Phi.
    private int unsplit(int alias) {
        if( alias <= 1 ) return 0;
        assert !_aliases.get(alias);
        return alias;
    }

    // First alias excluded by the neighboring bulk Phi but not by this one.
    private int missingAlias(BulkMemPhiNode bulk) {
        for( int alias = bulk._aliases.nextSetBit(0); alias >= 0; alias = bulk._aliases.nextSetBit(alias+1) )
            if( !_aliases.get(alias) )
                return alias;
        return 0;
    }

    // Every explicit slot requests that alias, including a partial allocation
    // input whose slot still points to this bulk Phi.
    private int missingAlias(MemMergeNode mmm) {
        for( int alias=2; alias<mmm.nIns(); alias++ )
            if( mmm.in(alias)!=null &&
                mmm.alias(alias)!=mmm.in(1) &&
                !_aliases.get(alias) )
                return alias;
        return 0;
    }

    // Slice out given alias
    private MemMergeNode slice( int alias ) {
        assert !_aliases.get(alias);
        // Inputs are the same; MemPhi will sharpen his own inputs.  If a
        // parallel precise Phi already exists, reuse it instead of building a
        // duplicate for the same Region/alias pair.
        Node mem = precisePhi(alias);

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
        // Do not peephole bphi here, as BulkMemPhi can recursively start a
        // second bulk rewrite before this one has finished.  Let the worklist
        // discover any further splits
        bphi.setType(TypeMem.BOT);
        Node bulk = bphi;
        CodeGen.CODE.add(bulk);
        CodeGen.CODE.add(mem);
        return aggregate(bulk,alias,mem);
    }

    // Build aggregate memory over a bulk Phi, preserving every precise slice
    // which the bulk has already excluded.  The named alias is newly supplied
    // (or replaces its old slice).
    MemMergeNode aggregate(Node bulk, int alias, Node precise) {
        MemMergeNode mmm = new MemMergeNode(bulk);
        for( int old = _aliases.nextSetBit(0); old >= 0; old = _aliases.nextSetBit(old+1) )
            { assert old != alias; mmm.alias(old,precisePhi(old));}
        mmm.alias(alias,precise);
        assert checkMerge(mmm);
        return mmm;
    }

    // Find or make the precise Phi parallel to this bulk Phi at the same merge.
    private Node precisePhi(int alias) {
        MemPhiNode mphi = _findPhi(alias);
        if( mphi!=null ) return mphi;

        mphi = new MemPhiNode("$"+alias,alias);
        mphi.addDef(region());
        // Due to cycles, must set before calling peephole
        mphi.setType(TypeMem.make(alias,Type.BOTTOM));
        for( int i=1; i<nIns(); i++ )
            mphi.addDef(CodeGen.CODE.add(preciseInput(in(i),alias)));
        return CodeGen.CODE.add(mphi);
    }

    MemPhiNode _findPhi(int alias) {
        for( Node use : region()._outputs )
            if( use instanceof MemPhiNode mphi && mphi._alias==alias )
                return mphi;
        return null;
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

    @Override public boolean eq(Node n) {
        return _aliases.equals(((BulkMemPhiNode)n)._aliases) && super.eq(n);
    }

    @Override int hash() { return _aliases.hashCode(); }
}
