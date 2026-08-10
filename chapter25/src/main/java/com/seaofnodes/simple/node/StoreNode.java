package com.seaofnodes.simple.node;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.GlobalBits;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.util.Utils;
import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;

/**
 * Store represents setting a value to a memory based object, in chapter 10
 * this means a field inside a struct.
 */
public class StoreNode extends MemOpNode {

    final boolean _init; // Initializing writes are allowed to write null
    // Semantic store width in bytes.  Zero is the sole undecided state;
    // otherwise hardware and ideal Stores are the 1/2/4/8-byte variants.
    private byte _size;

    /**
     * @param name  The struct field we are assigning to
     * @param mem   The memory alias node - this is updated after a Store
     * @param ptr   The ptr to the struct base where we will store a value
     * @param off   The offset inside the struct base
     * @param value Value to be stored
     */
    public StoreNode(Parser.Lexer loc, String name, int alias, Type glb, Node ctrl, Node mem, Node ptr, Node off, Node value, boolean init) {
        this(loc,name,alias,glb,ctrl,mem,ptr,off,value,init,(byte)0);
    }
    private StoreNode(Parser.Lexer loc, String name, int alias, Type glb, Node ctrl, Node mem, Node ptr, Node off, Node value, boolean init, byte size) {
        super(loc, name, alias, false, glb, ctrl, mem, ptr, off, value);
        _init = init;
        _size = size;
    }
    StoreNode( BAOS bais, String[] strs, Type[] types, GlobalBits fileAliases, GlobalBits aliases ) {
        super(bais,strs,types,fileAliases,aliases,false);
        addDef(null);
        _init = bais.read() != 0;
        _size = (byte)bais.read();
    }
    @Override public Tag serialTag() { return Tag.Store; }
    @Override public void packed( BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, IdentityHashMap<Node, Integer> anodes ) {
        super.packed(baos,strs,types, anodes );
        baos.write(_init ? 1 : 0);
        baos.write(_size);
    }

    // GraphVis DOT code and debugger labels
    @Override public String  label() { return "st_"+mlabel(); }
    // GraphVis node-internal labels
    @Override public String glabel() { return "." +_name+"="; }
    @Override public boolean isMem() { return true; }

    public Node nnptr() { return ptr() instanceof GuardNode cast && cast._nonZero ? cast.in(1) : ptr(); }
    public Node val() { return in(4); }
    public int storeSize() { assert _size==1 || _size==2 || _size==4 || _size==8; return _size; }
    @Override public int log_size() { return Integer.numberOfTrailingZeros(storeSize()); }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return sb.append(".").append(_name).append("=").append( val()).append(";");
    }

    @Override
    public Type compute() {
        Type val  = val()._type;
        Type mem0 = aliasMem()._type;
        Type ptr0 = ptr()._type;
        // Validate argument types
        if( ptr0.isHigh() )
            return TypeMem.TOP;
        if( !(mem0 instanceof TypeMem    mem) ||
            !(ptr0 instanceof TypeMemPtr ptr) )
            return mem0.isHigh() || ptr0==Type.NIL ? TypeMem.TOP : TypeMem.BOT;

        // Sharpen memory value; required for narrowing stores where the parser
        // inserts zero/sign masking and somebody reads the TypeMem type.
        Type decl = declaredType();
        if( decl != Type.BOTTOM && decl != Type.TOP )
            val = val.join(decl);
        //if( err()!=null )
        //    val = Type.BOTTOM;
        // Allocation uses a private TypeMem and nothing else does.  This
        // memory is truly private; a temporary singleton until it escapes -
        // which is never does in a constructor.
        if( !_name.equals("[]") && mem._one )
            // Just track the stored value
            return TypeMem.make(_alias,val,true,false,_init,null,null).escapesFrom(val);

        // Normal aliasing Store.
        assert mem._alias==1 || mem._alias==_alias; // Perfect aliasing

        // Single alias store.  If memory is fat, attempt to get a narrow slice.
        Type tfld = mem._alias==1 && mem._t instanceof TypeStruct ts
            ? ts._fields[ts.findAlias(_alias)]._t
            : mem._t;

        // Same alias, meet into other fields.
        Type t = val.meet(tfld);
        // Bulk memory's final bit describes its conservative remainder, not
        // this newly precise slice.  Slice finality comes from the field.
        Field fld = ptr._obj.field(_name);
        boolean xfinal = fld != null && fld._final;
        return TypeMem.make(_alias,t,mem._one,mem._clz,xfinal,mem._escFs,mem._escAs).escapesFrom(val);
    }

    @Override
    public Node idealize() {
        assert !(mem() instanceof CheckCastNode);

        // Freeze the semantic Store variant as soon as the pointer exposes a
        // canonical field declaration.  Waiting until after structural memory
        // peeps can lose the declaration to a flow-sensitive constant field
        // state.  Imported Stores already carry a serialized non-zero width.
        Type storage = storageType();
        if( _size==0 && storage!=Type.BOTTOM && storage!=Type.TOP ) {
            unlock();           // Width participates in GVN identity.
            _size = storeSize(storage);
            return init();
        }

        // A precise Store can confirm its alias from a precise memory input.
        // Do not sharpen an alias-#1 (whole-memory) Store in place: users of
        // that Store already treat it as the complete memory state.  The
        // pointer/field path below replaces it with a precise Store wrapped in
        // a MemMerge, preserving any other precise aliases in its input.
        if( mem()._type instanceof TypeMem mem ) {
            assert mem._alias > 0;
            if( _alias != 1 && mem._alias != 1 )
                assert _alias == mem._alias;
        }

        // Forward-ref loads eventually sharpen to a declared type
        Field fld;
        if( _alias==1 && ptr()._type instanceof TypeMemPtr tmp && (fld=tmp._obj.field(_name)) != null ) {
            // Expand Store(bulkMem,alias#1) into MemMerge(bulkMem,#N:Store(bulkMem,alias#N)).

            // Normally I'd like to just drop in this xform and move on, but
            // users of the bulk Store memory might nontheless depend on the
            // sharp memory type (e.g. I64) from the stores memory.  Using the
            // MemMerge means using the bulk memory which e.g. might be BOT.
            // Check for store users with the same alias, and use the new store
            // directly, preserving the sharper graph.
            Type decl = storageType();
            byte size = _size==0 && decl!=Type.BOTTOM && decl!=Type.TOP ? storeSize(decl) : _size;
            Node st = new StoreNode(_loc,_name,fld._alias,fld._t,in(0),mem(),ptr(),off(),val(),_init,size).peephole();
            for( int i=0; i<nOuts(); i++ ) {
                Node use = out(i);
                if( useAlias(use)==fld._alias ) {
                    int idx = use._inputs.find(this);
                    assert idx != -1;
                    use.setDef(idx,st);
                    i--;        // setDef removed use from this Store's outputs
                }
            }
            MemMergeNode mmm = mem() instanceof BulkMemPhiNode bulk
                ? bulk.aggregate(mem(),fld._alias,st)
                : new MemMergeNode(false,null,mem());
            if( !(mem() instanceof BulkMemPhiNode) )
                mmm.alias(fld._alias,st);
            return mmm.init();
        }
        // Expose the same effective memory input already observed by compute.
        Node aliasMem = aliasMem();
        if( aliasMem != mem() ) {
            setDef(1,aliasMem);
            CodeGen.CODE.add(aliasMem);
            return this;
        }

        // Simple store-after-store on same address.  Should pick up the
        // required init-store being stomped by a first user store.
        if( mem() instanceof StoreNode st &&
            ptr()==st.ptr() &&  // Must check same object
            off()==st.off() &&  // And same offset (could be "same alias" but this handles arrays to same index)
            ptr()._type instanceof TypeMemPtr && // No bother if weird dead pointers
            // Must have exactly one use of "this" or you get weird
            // non-serializable memory effects in the worse case.
            checkOnlyUse(st) ) {
            assert _name==st._name; // Equiv class aliasing is perfect
            setDef(1,st.mem());
            return this;
        }

        // Simple store-after-MemMerge to a known alias can bypass.  Happens when inlining.
        if( _alias != 1 && mem() instanceof MemMergeNode mem ) {
            setDef(1,mem.alias(_alias));
            return this;
        }

        // Resolve the semantic Store variant exactly once, after structural
        // memory/alias peeps.  This avoids publishing a half-sharpened Store
        // while BulkMemPhi is constructing precise slices.
        if( storage != Type.BOTTOM && storage != Type.TOP )
            assert _size==storeSize(storage) : "Store width changed for '"+_name+"': "+_size+" from "+storage;

        // Value is automatically truncated by narrow store
        if( _size!=0 && val() instanceof AndNode and && and.in(2)._type.isConstant()  ) {
            int log = log_size();
            if( log<3 ) {       // And-mask vs narrow store
                long mask = ((TypeInteger)and.in(2)._type).value();
                long bits = (1L<<(8<<log))-1;
                // Mask does not mask any of the stored bits
                if( (bits&mask)==bits )
                    // So and-mask is already covered by the store
                    { setDef(4,and.in(1)); return this; }
            }
        }

        // Store will chop high order bits off; math to change those bits can be dropped.
        if( _size!=0 && val() instanceof SarNode shr &&
            shr.in(1) instanceof ShlNode shl &&
            shr.in(2)._type.isConstant() &&
            shl.in(2)._type.isConstant() ) {
            TypeInteger shrC = (TypeInteger) shr.in(2)._type;

            // size of the thing that sign-extends
            int base_size = (1 << shr.in(1)._type.log_size()) << 3;
            int not_affected_bits = base_size - (int) shrC.value();
            int store_size = (1 << log_size()) << 3;
            // if the store is unrelated to the shift amount, then get rid of the shift
            if( shl.in(2)._type == shr.in(2)._type && shrC.value() >= store_size && not_affected_bits >= store_size) {
                setDef(4, shl.in(1));
                return this;
            }
        }

        return null;
    }

    private static byte storeSize(Type decl) {
        // `decl` is the target field declaration, not the stored value.  In
        // particular a constant zero stored into u32 is still a 4-byte Store;
        // taking the constant's GLB here would incorrectly select i64.
        byte size = (byte)(1 << decl.log_size());
        assert size==1 || size==2 || size==4 || size==8;
        return size;
    }

    // Flow-sensitive pointer types can carry a current field value (e.g. the
    // constant zero in a u32 field).  Storage width comes from the canonical
    // struct declaration.  If the pointer/definition is not available yet,
    // leave the Store undecided instead of consulting the value or `_con`.
    private Type storageType() {
        // Freshly parsed field stores retain the declaring structure on their
        // symbolic offset even after the pointer's flow type sharpens.
        if( off() instanceof ConFldOffNode coff ) {
            Field fld = coff._ts.field(_name);
            if( fld != null && fld._t != null ) return fld._t;
        }
        if( !(ptr()._type instanceof TypeMemPtr tmp) ) return Type.BOTTOM;
        Type base = Parser.TYPES.get(tmp._obj._name);
        TypeStruct obj = base instanceof TypeStruct ts ? ts : tmp._obj;
        Field fld = obj.field(_name);
        // Imported optimized structs can expose the current constant field
        // value instead of its declaration.  A constant has no storage width;
        // an imported Store already carries its serialized, frozen `_size`.
        return fld == null || fld._t == null || fld._t.isConstant() ? Type.BOTTOM : fld._t;
    }

    // Alias required by a direct consumer of this Store's memory result.
    // Keep in sync with BulkMemPhiNode.outputAlias.
    private int useAlias(Node use) {
        return switch( use ) {
        case MemOpNode mem -> mem._alias;
        case MemPhiNode phi -> phi._alias;
        case EscapeNode esc -> esc.pub()==this ? esc.fld()._alias : 0;
        default -> 0;
        };
    }

    // Select a precise alias from a whole-memory partition.  Alias #1 is the
    // bulk remainder itself.
    private static Node memSlice( Node mem, int alias ) {
        return alias != 1 && mem instanceof MemMergeNode merge
            ? merge.alias(alias)
            : mem;
    }

    // Semantic memory input for this Store.  Compute defines behavior from
    // this view; ideal exposes the same edge in the graph.
    private Node aliasMem() {
        Node mem = mem();
        if( _alias != 1 && mem instanceof MemMergeNode merge )
            mem = merge.alias(_alias);
        return mem;
    }

    // Check that "mem" has no uses except "this"
    private boolean checkOnlyUse(Node mem) {
        if( mem.nOuts()==1 ) return true;
        // Add deps on the other uses (can be e.g. ScopeNode mid-parse) so that
        // when the other uses go away we can retry.
        for( Node use : mem._outputs )
            if( use != this )
                addDep(use);
        return false;
    }

    @Override
    public Parser.ParseException err() {
        Parser.ParseException err = super.err();
        if( err != null ) return err;
        if( _size==0 && CodeGen.CODE._phase.ordinal() > CodeGen.Phase.Opto.ordinal() )
            throw Utils.TODO("Failed to decide Store size");
        if( ptr()._type == Type.TOP )
            return null; // This means we have an error input, report elsewhere
        TypeMemPtr tmp = (TypeMemPtr)ptr()._type;
        Field f = tmp._obj.field(_name);
        if( f!=null && f._final && !_init )
            return Parser.error("Cannot modify final field '"+_name+"'",_loc);
        return null;
    }

    @Override public boolean eq(Node n) { return _size==((StoreNode)n)._size && super.eq(n); }
    @Override int hash() { return super.hash() ^ _size; }
}
