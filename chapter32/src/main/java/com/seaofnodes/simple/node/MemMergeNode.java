package com.seaofnodes.simple.node;

import com.seaofnodes.simple.parser.Var;
import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Utils;
import com.seaofnodes.simple.util.AryInt;
import com.seaofnodes.simple.util.IntHashMap;

import java.util.*;

/**
 * Memory merge: default bulk memory plus dense inputs for explicit alias slices.
 */
public class MemMergeNode extends Node {

    public MemMergeNode( Node ...nodes) { super(nodes); _type = TypeMem.BOT; }
    // Inputs 0/1 are control/default; the remaining inputs are dense precise slices.
    // Keep alias order so graph walks retain the old sparse representation's order.
    private AryInt _aliases;
    private IntHashMap _aliasToInput;
    private int _readSize;
    // Retain the old sparse extent for encoding and fold eligibility, without its holes.
    private int _aliasLimit=2;

    public MemMergeNode(MemMergeNode mem) { super(mem); copyAliases(mem); _aliasLimit=mem._aliasLimit; }

    private void copyAliases(MemMergeNode mem) {
        if( mem._aliases==null ) return;
        _aliases = new AryInt().addAll(mem._aliases);
        _aliasToInput = new IntHashMap(0);
        reindex();
    }

    @Override protected Object clone() throws CloneNotSupportedException {
        MemMergeNode copy = (MemMergeNode)super.clone();
        copy.copyAliases(this);
        return copy;
    }

    public int aliasAt(int input) { return _aliases.at(input-2); }

    // Explicit input, or null when the default supplies this alias.
    public Node explicitAlias(int alias) {
        int input = _aliasToInput==null ? 0 : _aliasToInput.get(alias);
        return input==0 ? null : in(input);
    }

    // Preserve the existing sparse object encoding; input indices on disk are aliases.
    public int packedSize() { return _aliasLimit; }
    public int readSize() { return _readSize; }
    public Node packedInput(int alias) { return alias<2 ? in(alias) : explicitAlias(alias); }

    private void reindex() {
        _aliasToInput.clear();
        for( int i=0; i<_aliases.len(); i++ ) _aliasToInput.put(_aliases.at(i),i+2);
    }

    private void removeAlias(int input) {
        setDef(input,null);
        _inputs.remove(input);
        _aliases.remove(input-2);
        reindex();
    }
    @Override public Tag serialTag() { return Tag.MemMerge; }
    public void packed( BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, IdentityHashMap<Node, Integer> anodes ) {
        // A memory merge always has inputs. Zero escapes a wide alias table,
        // preserving every existing nonzero single-byte count.
        assert nIns()>0;
        baos.packed1(packedSize()<=255 ? packedSize() : 0);
        if( packedSize()>255 ) baos.packed4(packedSize());
    }
    static Node make( BAOS bais ) {
        int count=bais.packed1();
        if( count==0 ) count=bais.packed4();
        MemMergeNode mem=new MemMergeNode(null,null);
        mem._readSize=count;
        mem._aliasLimit=count;
        return mem;
    }

    @Override public String label() { return "ALLMEM"; }
    @Override public boolean isMem() { return true; }

    @Override
    protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        p.p("MEM[ ");
        for( int j=2; j<nIns(); j++ ) {
            p.p(aliasAt(j));
            p.p(":");
            Node n = in(j);
            while( n instanceof ScopeNode loop ) {
                p.p("Lazy_");
                n = loop.mem();
            }
            if( n==null ) p.p("___ ");
            else p.n(n).p(" ");
        }
        return p.unchar(' ').p("]");
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
            if( XInt.bit(mem._escAs,aliasAt(i)) &&
                in(i) != null && !in(i)._type.isHigh() ) {
                mem = (TypeMem)mem.meet(in(i)._type);
            }
        }
        return mem;
    }

    @Override public Node idealize() {
        assert nIns() != 0 && in(1)!=null; // Always have a default.  if( nIns()==0 ) return null;

        // Preserve the existing fold rule for this storage experiment: every alias
        // slot must explicitly equal the default. Do not also change which empty
        // aggregates fold away; that changes optimization order and spill decisions.
        boolean progress=false, allDefault=nIns()==_aliasLimit;
        for( int i=2; i<nIns(); i++ ) {
            if( in(1)==in(i) ) { removeAlias(i--); progress=true; }
            else allDefault=false;
        }

        // If not merging any memory (all memory is just the default)
        if( allDefault )
            return in(1);       // Become default memory

        // Collapse stacked merged-mem
        if( in(1) instanceof MemMergeNode mem ) {
            // A dead memory cycle can lose its last use when we bypass mem.
            // Keep the peephole's result alive until the worklist sees it.
            keep();
            // Goal is to swap my default mem with mem's default mem
            for( int i=2; i<mem.nIns(); i++ ) {
                if( mem.in(i) != null ) {
                    // deeper default mem has a non-default
                    int alias = mem.aliasAt(i);
                    if( explicitAlias(alias)==null )
                        alias(alias,mem.in(i));
                }
            }
            CodeGen.CODE.add(mem.in(1));
            setDef(1,mem.in(1));
            return unkeep();
        }

        return progress ? this : null;
    }


    public Node in( Var v ) { return in(v._idx); }

    public Node alias( int alias ) {
        Node precise = explicitAlias(alias);
        assert !(in(1) instanceof BulkMemPhiNode bulk && bulk.isSplit(alias) && precise==null);
        return precise==null ? in(1) : precise;
    }

    public Node alias( int alias, Node st ) {
        if( alias==1 ) return setDef(1,st);
        assert alias>1;
        _aliasLimit=Math.max(_aliasLimit,alias+1);
        int input = _aliasToInput==null ? 0 : _aliasToInput.get(alias);
        if( st==null ) {
            if( input!=0 ) removeAlias(input);
            return st;
        }
        if( input!=0 ) return setDef(input,st);
        if( _aliases==null ) {
            _aliases = new AryInt();
            _aliasToInput = new IntHashMap(0);
        }
        int pos = Arrays.binarySearch(_aliases._es,0,_aliases.len(),alias);
        assert pos<0;
        pos = -pos-1;
        _aliases.insert(pos,alias);
        for( int i=pos; i<_aliases.len(); i++ ) _aliasToInput.put(_aliases.at(i),i+2);
        insertDef(pos+2,st);
        return st;
    }

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
