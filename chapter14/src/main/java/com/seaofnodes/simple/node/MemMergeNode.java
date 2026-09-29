package com.seaofnodes.simple.node;

import com.seaofnodes.simple.IterPeeps;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeMem;
import java.util.BitSet;

/** All memory: a default slice and explicit overrides for individual aliases. */
public class MemMergeNode extends Node {
    public MemMergeNode(Node bulk) { super(null,bulk); _type = TypeMem.BOT; }
    public MemMergeNode(Node bulk, int alias, Node precise) {
        this(bulk);
        alias(alias,precise);
    }

    public Node alias(int alias) {
        Node n = alias < nIns() ? in(alias) : null;
        assert n!=null || !(in(1) instanceof BulkMemPhiNode bulk) || !bulk.isSplit(alias);
        return n==null ? in(1) : n;
    }

    public void alias(int alias, Node mem) {
        assert alias > 1;
        while( nIns()<=alias ) addDef(null);
        setDef(alias,mem);
    }

    @Override public String label() { return "MemMerge"; }
    @Override public boolean isMem() { return true; }
    @Override public Type compute() { return TypeMem.BOT; }
    @Override StringBuilder _print1(StringBuilder sb, BitSet visited) {
        sb.append("MEM[");
        for( int i=1; i<nIns(); i++ )
            if( in(i)!=null ) {
                sb.append(i==1 ? "default:" : i+":");
                in(i)._print0(sb,visited).append(" ");
            }
        return sb.append("]");
    }

    @Override public Node idealize() {
        boolean progress=false, allDefault=true;
        for( int i=2; i<nIns(); i++ ) {
            if( in(i)==in(1) ) { setDef(i,null); progress=true; }
            if( in(i)!=null ) allDefault=false;
        }
        if( allDefault ) return in(1);

        if( in(1) instanceof MemMergeNode mem ) {
            for( int i=2; i<mem.nIns(); i++ )
                if( mem.in(i)!=null && (i>=nIns() || in(i)==null) )
                    alias(i,mem.in(i));
            IterPeeps.add(mem.in(1));
            setDef(1,mem.in(1));
            return this;
        }
        assert BulkMemPhiNode.checkMerge(this);
        return progress ? this : null;
    }
}
