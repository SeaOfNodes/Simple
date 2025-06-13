package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.*;
import java.util.*;

/** A default slice and explicit alias overrides. A null default means partial
 *  memory: absent aliases are not covered, rather than having unknown contents.
 */
public class MemMergeNode extends Node {
    public MemMergeNode(MemMergeNode mem) { super(mem); }

    public MemMergeNode(Node bulk) { super(null,bulk); _type = TypeMem.BOT; }
    public MemMergeNode(Node bulk, int alias, Node precise) {
        this(bulk);
        alias(alias,precise);
    }
    public Node alias(int alias) {
        Node n = alias < nIns() ? in(alias) : null;
        assert n!=null || !(in(1) instanceof BulkMemPhiNode bulk) || !bulk.isSplit(alias);
        n = n==null ? in(1) : n;
        assert n!=null : "Alias not covered: "+alias;
        return n;
    }

    public void alias(int alias, Node mem) {
        assert alias > 1;
        while( nIns()<=alias ) addDef(null);
        setDef(alias,mem);
    }

    // Scalar contents of one alias. New zeroes the allocated fields;
    // join it with the incoming contents for all previously allocated objects.
    // Phis use their cached types, so this query does not recurse around loops.
    static Type contents(Node mem, int alias, Node dep) {
        dep.addDep(mem);
        if( mem instanceof CastNode cast ) return contents(cast.in(1),alias,dep);
        if( mem instanceof MemMergeNode merge )
            return contents(merge.alias(alias),alias,dep);
        if( mem instanceof ProjNode proj && proj.in(0) instanceof NewNode nnn ) {
            assert proj._idx==1 && nnn.field(alias)!=null;
            dep.addDep(nnn);
            if( dep.addDep(nnn.in(0))._type.isHigh() ) return Type.TOP;
            return contents(nnn.mem(),alias,dep).meet(nnn.field(alias)._t.makeZero());
        }
        // Function parameters and call results may contain arbitrary heap values.
        // Their bulk memory types do not imply empty or zero-filled storage.
        if( mem._type==Type.TOP || mem._type==TypeMem.TOP ) return Type.TOP;
        if( mem._type instanceof TypeMem mt && mt._alias==alias ) return mt._t;
        return Type.BOTTOM;
    }

    @Override public String label() { return "MemMerge"; }
    @Override public boolean isMem() { return true; }
    @Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        p.p("MEM[");
        for( int i=1; i<nIns(); i++ )
            if( in(i)!=null ) {
                p.p(i==1 ? "default:" : i+":");
                p.n(in(i)).p(" ");
            }
        return p.p("]");
    }


    @Override public Type compute() {
        for( Node n : _inputs )
            if( n != null && !n._type.isHigh() )
                return TypeMem.BOT;
        return TypeMem.TOP;
    }

    @Override public Node idealize() {
        boolean progress=false, allDefault=true;
        for( int i=2; i<nIns(); i++ ) {
            if( in(i) instanceof CastNode cast ) { setDef(i,cast.in(1)); progress=true; }
            if( in(i)!=null && in(i)==in(1) ) { setDef(i,null); progress=true; }
            if( in(i) instanceof MemMergeNode mem ) {
                setDef(i,CodeGen.CODE.add(mem.alias(i)));
                progress=true;
            }
            if( in(i)!=null ) allDefault=false;
        }
        if( allDefault && in(1)!=null ) return in(1);

        if( in(1) instanceof MemMergeNode mem ) {
            for( int i=2; i<mem.nIns(); i++ )
                if( mem.in(i)!=null && (i>=nIns() || in(i)==null) )
                    alias(i,mem.in(i));
            CodeGen.CODE.add(mem.in(1));
            setDef(1,mem.in(1));
            return this;
        }
        assert BulkMemPhiNode.checkMerge(this);
        return progress ? this : null;
    }
}
