package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeMem;

import java.util.HashMap;
import java.util.IdentityHashMap;

/** A Phi for one precise memory alias. */
public class MemPhiNode extends PhiNode {

    public final int _alias;

    public MemPhiNode(String label, int alias, Node... inputs) {
        super(label, inputs);
        assert alias > 1;
        _alias = alias;
    }

    public MemPhiNode(MemPhiNode phi) {
        super(phi,phi._label);
        _alias = phi._alias;
    }


    @Override
    public Type compute() {
        Type t = super.compute();
        assert BulkMemPhiNode.checkMem(this,_alias);
        if( !(t instanceof TypeMem tmem) ) return t;
        assert tmem._alias==1 || tmem._alias==_alias;
        // This Phi structurally knows its alias, but not whether the field is
        // final.  Do not infer slice finality from evolving input types.
        return TypeMem.make(_alias,tmem._t,tmem._one,tmem._clz,false,tmem._escFs,tmem._escAs);
    }

    @Override
    public Node idealize() {
        // A precise memory Phi can always bypass a MemMerge and select its
        // matching slice.
        for( int i=1; i<nIns(); i++ ) {
            if( in(i) instanceof MemMergeNode mem ) {
                setDef(i,mem.alias(_alias));
                return this;
            }
            Node slice = CLInitNode.slice(in(i),_alias,this);
            if( slice!=in(i) ) { setDef(i,slice); return this; }
        }
        if( nIns()==3 && region() instanceof LoopNode loop && !loop.inProgress() &&
            in(2) instanceof StoreNode st && st.mem()==this ) {
            Node sink = st.sink(this);
            if( sink!=null ) return sink;
        }
        return super.idealize();
    }

    @Override public boolean eq(Node n) {
        return _alias==((MemPhiNode)n)._alias && super.eq(n);
    }

    @Override int hash() { return _alias; }
}
