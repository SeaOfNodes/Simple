package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeMem;
import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.util.Utils;

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

    // Excluded Aliases; empty means NO exclusions.
    // Directly visible by design.  Empty sets share EMPTY; splitAlias performs
    // copy-on-first-write and removes the node from GVN before mutation.
    public BitSet _aliases;

    public BulkMemPhiNode(String label, Node... inputs) {
        this(label,EMPTY,inputs);
    }

    private BulkMemPhiNode(String label, BitSet aliases, Node... inputs) {
        super(label, inputs);
        _aliases = aliases;
    }

    public BulkMemPhiNode(BulkMemPhiNode phi) {
        super(phi,phi._label);
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
        BitSet aliases = new BitSet();
        for( int i=bais.packed2(); i>0; i-- )
            aliases.set(bais.packed2());
        return new BulkMemPhiNode(label,aliases.isEmpty() ? EMPTY : aliases,ins);
    }

    @Override
    public Node idealize() {
        if( !(region() instanceof RegionNode r ) )
            return in(1);       // Input has collapse to e.g. starting control.
        if( r.inProgress() || r.nIns()<=1 )
            return null;        // Input is in-progress
        if( nOuts()==0 ) return null;

        // "Peek through" a MemMerge that covers this alias set on its default
        for( int i=1; i<nIns(); i++ ) {
            if( in(i) instanceof MemMergeNode mmm ) {
                boolean peek = true;
                for( int j=2; j<mmm.nIns(); j++ )
                    if( mmm.in(j)!=null && !_aliases.get(j) )
                        { peek=false; break; }
                if( peek ) {    // Peek thru default memory
                    setDef(i,mmm.in(1));
                    return this;
                }
            }
        }

        // If any input or output uses or defines a specific alias we cover,
        // make a private MemPhi for it, and add it to the excluded list.
        for( int i=1; i<nIns(); i++ ) {
            int alias = excludedInput(in(i));
            if( alias!=0 )
                return slice(alias);
        }



        // If output defines a specific alias we cover,
        for( int i=0; i<nOuts(); i++ ) {
            Node use = out(i);
            switch(use) {
            case ScopeNode scope: break;
            case MemMergeNode mmm : {
                if( mmm.in(1)==this ) {
                    for( int j=2; j<mmm.nIns(); j++ )
                        if( mmm.alias(j)!=this && mmm.in(j)!=this && !_aliases.get(j) )
                            return slice(j);
                }
                break;
            }
            case BulkMemPhiNode bulk: {
                if( _aliases.equals(bulk._aliases) ) break;
                throw Utils.TODO();
            }
            default:
                assert use instanceof ReturnNode || use instanceof CallNode;
                break;
            };
        }

        return super.idealize();
    }

    // Return an alias not in the excluded set, or 0
    private int excludedInput(Node n) {
        return switch(n) {
            // All simple bulk users
        case ParmNode p -> { assert p._idx==1; yield 0; }
        case ScopeNode scope -> 0;
        case ReturnNode ret -> 0;
        case ConstantNode con -> {
            if( !(con._con instanceof TypeMem tmem) )
                yield 0;
            throw Utils.TODO();
        }
        case MemMergeNode mmm -> {
            // Bulk points to MemMerge and MemMerge has non-excluded not-default
            for( int i=2; i<mmm.nIns(); i++ )
                if( mmm.alias(i)!=mmm.in(1) && !_aliases.get(i) )
                    yield i;
            yield 0;
        }
        // Really, if self-set is missing bits in bulk, i.e. bulk is not a subset
        case BulkMemPhiNode bulk -> {
            if( _aliases.equals(bulk._aliases) ) yield 0;
            throw Utils.TODO();
        }
        default -> throw Utils.TODO();
        };
    }

    // Slice out given alias
    private MemMergeNode slice( int alias ) {
        assert !_aliases.get(alias);
        assert !hasDup(alias);
        // Inputs are the same; MemPhi will sharpen his own inputs
        MemPhiNode mphi = new MemPhiNode("$"+alias,alias);
        for( int i=0; i<nIns(); i++ ) mphi.addDef(in(i));
        Node mem = mphi.peephole();
        assert ((TypeMem)mem._type)._alias==alias;

        BitSet aliases = ((BitSet)_aliases.clone());
        aliases.set(alias);
        BulkMemPhiNode bphi = new BulkMemPhiNode(_label,aliases);
        for( int i=0; i<nIns(); i++ )
            bphi.addDef(in(i));
        Node bulk = bphi.peephole();
        CodeGen.CODE.add(bulk);
        MemMergeNode mmm = new MemMergeNode(false,null,bulk);
        mmm.alias(alias,mem);
        return mmm;
    }

    private boolean hasDup( int alias ) {
        for( Node use : outs() )
            if( use instanceof MemPhiNode mphi && mphi._alias==alias )
                return true;
        return false;
    }

    @Override public boolean eq(Node n) {
        return _aliases.equals(((BulkMemPhiNode)n)._aliases) && super.eq(n);
    }

    @Override int hash() { return _aliases.hashCode(); }
}
