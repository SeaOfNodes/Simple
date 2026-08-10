package com.seaofnodes.simple.node;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Utils;

import java.util.*;

/**
 *  Memory Merge - a merge of many aliases into a "fat memory".  All aliases
 *  are here, but most will be lazy - take the default fat memory.
 */
public class MemMergeNode extends Node {

    public MemMergeNode( Node ...nodes) { super(nodes); _type = TypeMem.BOT; }
    public MemMergeNode(MemMergeNode mem) { super(mem); }
    @Override public Tag serialTag() { return Tag.MemMerge; }
    public void packed( BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, IdentityHashMap<Node, Integer> anodes ) { baos.packed1(nIns()); }
    static Node make( BAOS bais )  { Node mem = new MemMergeNode(); mem.setDefX(bais.packed1()-1,null); return mem; }

    @Override public String label() { return "ALLMEM"; }
    @Override public boolean isMem() { return true; }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        sb.append("MEM[ ");
        for( int j=2; j<nIns(); j++ ) {
            sb.append(j);
            sb.append(":");
            Node n = in(j);
            while( n instanceof ScopeNode loop ) {
                sb.append("Lazy_");
                n = loop.mem(j);
            }
            if( n==null ) sb.append("___ ");
            else n._print0(sb, visited).append(" ");
        }
        sb.setLength(sb.length()-1);
        return sb.append("]");
    }


    @Override public Type compute() {
        Type tmem = in(1)._type;
        if( !(tmem instanceof TypeMem defmem) )
            // A MemMerge is structurally memory even while its default input
            // is a weak, not-yet-specialized Phi.
            return tmem.isHigh() ? TypeMem.TOP : TypeMem.BOT;
        // Is this a single private instance memory?
        if( defmem._one ) {
            if( !(defmem._t instanceof TypeStruct ts) )
                return defmem;
            // Perfect singleton memory, so all updates are parallel and
            // independent and stack.
            for( int i=2; i<nIns(); i++ ) {
                Node in = in(i);
                if( in !=null && in._type.isHigh() )
                    return TypeMem.TOP;
                if( in instanceof StoreNode st ) {
                    Type val = ((TypeMem)st._type)._t;
                    Field old = ts.field(st._name);
                    // Lazy add an expected field for open structs.
                    if( old==null )  { assert ts._open;
                        old = Field.make(st._name,Type.BOTTOM,st._alias,st._init);
                        ts = ts.add(old);
                    }
                    Field fld = old.makeFrom(val);
                    // TODO: val leaks into perfect singleton memory, along with its fidxs and aliases
                    ts = ts.replace(fld);
                }
            }
            return TypeMem.makePrivate(ts);

        }

        // Normal public memory; meet across escaped inputs
        TypeMem mem = defmem;
        for( int i=2; i<nIns(); i++ ) {
            // Has this alias escaped?
            if( XInt.bit(mem._escAs,i) &&
                in(i) != null && !in(i)._type.isHigh() ) {
                mem = (TypeMem)mem.meet(in(i)._type);
            }
        }
        return mem;
    }

    @Override public Node idealize() {
        assert nIns() != 0 && in(1)!=null; // Always have a default.  if( nIns()==0 ) return null;

        // Fold defaults into the default
        boolean progress=false, allDefault=true;
        for( int i=2; i<nIns(); i++ ) {
            if( in(1) == in(i) ) { setDef(i,null); progress=true; }
            else                 { allDefault=false; }
        }

        // If not merging any memory (all memory is just the default)
        if( allDefault )
            return in(1);       // Become default memory

        // Collapse stacked merged-mem
        if( in(1) instanceof MemMergeNode mem ) {
            // Goal is to swap my default mem with mem's default mem
            for( int i=2; i<mem.nIns(); i++ ) {
                if( mem.in(i) != null ) {
                    // deeper default mem has a non-default
                    if( i>=nIns() || in(i)==null )
                        setDefX(i,mem.in(i));
                }
            }
            CodeGen.CODE.add(mem.in(1));
            setDef(1,mem.in(1));
            return this;
        }

        return progress ? this : null;
    }


    public Node in( Var v ) { return in(v._idx); }

    public Node alias( int alias ) {
        assert !(in(1) instanceof BulkMemPhiNode bulk && bulk.isSplit(alias) &&
                 (alias >= nIns() || in(alias)==null));
        return alias < nIns() && in(alias)!=null ? in(alias) : in(1);
    }

    public Node alias( int alias, Node st ) { return setDefX(alias,st); }

    // Now one-time do a useless-phi removal
    void _useless( ) {
        for( int i=1; i<nIns(); i++ ) {
            if( in(i) instanceof PhiNode phi ) {
                // Do an eager useless-phi removal
                Node in = phi.peephole();
                CodeGen.CODE.addAll(phi._outputs);
                phi.moveDepsToWorklist();
                if( in != phi ) {
                    if( !phi.iskeep() ) // Keeping phi around for parser elsewhere
                        phi.subsume(in);
                    setDef(i,in); // Set the update back into Scope
                }
            }
        }
    }

    @Override public boolean eq( Node n ) {
        return this==n;
    }
}
