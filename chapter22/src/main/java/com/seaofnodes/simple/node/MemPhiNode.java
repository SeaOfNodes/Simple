package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeMem;

/** A Phi for one field alias, parallel to a bulk Phi at the same Region. */
public class MemPhiNode extends PhiNode {
    public final int _alias;

    public MemPhiNode(String label, int alias, Node... inputs) {
        super(label,TypeMem.make(alias,Type.BOTTOM),inputs);
        assert alias > 1;
        _alias = alias;
    }

    public MemPhiNode(MemPhiNode phi) { super(phi); _alias = phi._alias; }

    @Override public String label() { return "MemPhi_"+_alias; }

    @Override public Type compute() {
        assert BulkMemPhiNode.checkMem(this,_alias);
        if( !(region() instanceof RegionNode r) )
            return region()._type==Type.XCONTROL ? TypeMem.TOP : _type;
        if( r.inProgress() ) return _declaredType;
        Type t = Type.TOP;
        for( int i=1; i<nIns(); i++ )
            if( addDep(r.in(i))._type!=Type.XCONTROL )
                t = t.meet(MemMergeNode.contents(in(i),_alias,this));
        return TypeMem.make(_alias,t);
    }

    @Override public Node idealize() {
        for( int i=1; i<nIns(); i++ )
            if( in(i) instanceof MemMergeNode mem ) {
                setDef(i,CodeGen.CODE.add(mem.alias(_alias)));
                return this;
            }
        return super.idealize();
    }

    @Override public boolean eq(Node n) { return _alias==((MemPhiNode)n)._alias && super.eq(n); }
    @Override int hash() { return _alias; }
}
