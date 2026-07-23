package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.util.BAOS;

import java.util.HashMap;
import java.util.IdentityHashMap;

/** A Phi for one precise memory alias. */
public class MemPhiNode extends PhiNode {

    public final int _alias;

    public MemPhiNode(String label, Type minType, int alias, Node... inputs) {
        super(label,minType,inputs);
        assert alias > 1;
        _alias = alias;
    }

    public MemPhiNode(MemPhiNode phi) {
        super(phi,phi._label,phi._type);
        _alias = phi._alias;
    }

    @Override public Tag serialTag() { return Tag.MemPhi; }

    @Override
    public void packed(BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types,
                       IdentityHashMap<Node,Integer> anodes) {
        super.packed(baos,strs,types,anodes);
        baos.packed2(_alias);
    }

    static Node make(BAOS bais, String[] strs, Type[] types) {
        Node[] ins = new Node[bais.packed1()];
        String label = strs[bais.packed2()];
        Type type = types[bais.packed2()];
        return new MemPhiNode(label,type,bais.packed2(),ins);
    }

    @Override
    public Node idealize() {
        // A precise memory Phi can always bypass a MemMerge and select its
        // matching slice.
        for( int i=1; i<nIns(); i++ )
            if( in(i) instanceof MemMergeNode mem ) {
                setDef(i,mem.alias(_alias));
                return this;
            }
        return super.idealize();
    }

    @Override public boolean eq(Node n) {
        return _alias==((MemPhiNode)n)._alias && super.eq(n);
    }

    @Override int hash() { return _alias; }
}
