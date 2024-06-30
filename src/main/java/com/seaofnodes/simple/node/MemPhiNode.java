package com.seaofnodes.simple.node;

import com.seaofnodes.simple.IterPeeps;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeMem;

/** A Phi for one field alias, parallel to a bulk Phi at the same Region. */
public class MemPhiNode extends PhiNode {
    public final int _alias;

    public MemPhiNode(String label, int alias, Node... inputs) {
        super(label,TypeMem.make(alias),inputs);
        assert alias > 1;
        _alias = alias;
    }

    @Override public String label() { return "MemPhi_"+_alias; }

    @Override public Type compute() {
        assert BulkMemPhiNode.checkMem(this,_alias);
        Type t = super.compute();
        return t==Type.TOP || t==TypeMem.TOP ? TypeMem.TOP : TypeMem.make(_alias);
    }

    @Override public Node idealize() {
        for( int i=1; i<nIns(); i++ )
            if( in(i) instanceof MemMergeNode mem ) {
                setDef(i,IterPeeps.add(mem.alias(_alias)));
                return this;
            }
        return super.idealize();
    }

    @Override boolean eq(Node n) { return _alias==((MemPhiNode)n)._alias && super.eq(n); }
    @Override int hash() { return _alias; }
}
