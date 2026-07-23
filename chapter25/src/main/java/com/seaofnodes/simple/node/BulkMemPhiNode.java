package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.util.BAOS;

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

    // Directly visible by design.  Empty sets share EMPTY; splitAlias performs
    // copy-on-first-write and removes the node from GVN before mutation.
    public BitSet _aliases;

    public BulkMemPhiNode(String label, Type minType, Node... inputs) {
        this(label,minType,EMPTY,inputs);
    }

    private BulkMemPhiNode(String label, Type minType, BitSet aliases, Node... inputs) {
        super(label,minType,inputs);
        _aliases = aliases;
    }

    public BulkMemPhiNode(BulkMemPhiNode phi) {
        super(phi,phi._label,phi._type);
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

    static Node make(BAOS bais, String[] strs, Type[] types) {
        Node[] ins = new Node[bais.packed1()];
        String label = strs[bais.packed2()];
        Type type = types[bais.packed2()];
        BitSet aliases = new BitSet();
        for( int i=bais.packed2(); i>0; i-- )
            aliases.set(bais.packed2());
        return new BulkMemPhiNode(label,type,aliases.isEmpty() ? EMPTY : aliases,ins);
    }

    @Override
    public Node idealize() {
        /*
         * Bulk Phi sharpening belongs here, but needs the full All-E
         * invariant first.  Every incoming default edge must cover the same
         * alias set, and every excluded alias must have a parallel precise Phi
         * at this merge.  Users describe demand, not this edge's coverage.
         */
        return super.idealize();
    }

    @Override public boolean eq(Node n) {
        return _aliases.equals(((BulkMemPhiNode)n)._aliases) && super.eq(n);
    }

    @Override int hash() { return _aliases.hashCode(); }
}
