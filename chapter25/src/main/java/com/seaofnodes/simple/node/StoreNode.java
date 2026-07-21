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

    /**
     * @param name  The struct field we are assigning to
     * @param mem   The memory alias node - this is updated after a Store
     * @param ptr   The ptr to the struct base where we will store a value
     * @param off   The offset inside the struct base
     * @param value Value to be stored
     */
    public StoreNode(Parser.Lexer loc, String name, int alias, Type glb, Node ctrl, Node mem, Node ptr, Node off, Node value, boolean init) {
        super(loc, name, alias, false, glb, ctrl, mem, ptr, off, value);
        _init = init;
    }
    StoreNode( BAOS bais, String[] strs, Type[] types, GlobalBits fileAliases, GlobalBits aliases ) {
        super(bais,strs,types,fileAliases,aliases,false);
        addDef(null);
        _init = bais.read() != 0;
    }
    @Override public Tag serialTag() { return Tag.Store; }
    @Override public void packed( BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, IdentityHashMap<Node, Integer> anodes ) {
        super.packed(baos,strs,types, anodes );
        baos.write(_init ? 1 : 0);
    }

    // GraphVis DOT code and debugger labels
    @Override public String  label() { return "st_"+mlabel(); }
    // GraphVis node-internal labels
    @Override public String glabel() { return "." +_name+"="; }
    @Override public boolean isMem() { return true; }

    public Node val() { return in(4); }

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
        val = val.join(_con);
        if( err()!=null )
            val = Type.BOTTOM;
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
        return mem.escapesFrom(_alias,t);
    }

    @Override
    public Node idealize() {
        assert !(mem() instanceof CheckCastNode);

        // Alias #1 is the conservative parser-time choice.  A sharp memory
        // input can sharpen it exactly once; a later pointer discovery must
        // agree with that choice.
        if( mem()._type instanceof TypeMem mem ) {
            assert mem._alias > 0;
            if( mem._alias != 1 && sharpenAlias(mem._alias) )
                return this;
        }

        // Stores into structs do not need a ctrl edge, as null-ptr checking is
        // baked into the type system.  Stores into arrays DO need the ctrl
        // edge, at least until proper range-checking is in place.
        if( in(0)!=null && ptr()._type instanceof TypeMemPtr tmp &&
            !tmp._obj.isAry() &&
            // Never can be an array
            (!tmp._obj._open || (tmp._obj._fields.length > 1 && tmp._obj._fields[0]._fname != "#")) ) {
            setDef(0,null);     // Remove control edge
            return this;
        }

        // Forward-ref loads eventually sharpen to a declared type
        Field fld;
        if( _con == Type.BOTTOM || _alias==1 ) {
            if(  ptr()._type instanceof TypeMemPtr tmp && (fld=tmp._obj.field(_name)) != null ) {
                assert _alias == 1 || _alias == fld._alias;
                // All memory uses of self must now sharpen, as we are about to
                // no longer be a bulk memory.  Also find bulk
                for( int i=0; i<nOuts(); i++ ) {
                    Node use = out(i);
                    boolean memUse = use.nIns()>1 && use.in(1)==this;
                    if( use instanceof PhiNode phi )
                        for( int j=1; !memUse && j<phi.nIns(); j++ )
                            memUse = phi.in(j)==this;
                    if( memUse ) {
                        switch( use ) {
                        case MemOpNode mem:
                            if( mem._alias != 1 && mem._alias!=_alias ) {
                                // Unaliased mem user moves to my bulk mem input
                                mem.setDef(1,mem());
                                CodeGen.CODE.add(mem);
                                i--;
                                break;
                            } else
                                throw Utils.TODO("sharpen bulk user");
                        case MemMergeNode mem: {
                            // Assert we are the source of bulk memory, then move self
                            // to the precision alias.  Keeps self alive, in case
                            // setting the bulk user memory is the last use of self.
                            assert mem.alias(fld._alias)==this;
                            mem.alias(fld._alias,this);
                            // Merge bulk moves to my bulk mem input
                            mem.alias(1,mem());
                            CodeGen.CODE.add(mem);
                            i--;
                            break;
                        }
                        case PhiNode phi: {
                            int palias = phi.memAlias();
                            if( palias == -1 )
                                throw Utils.TODO("Store used by a non-memory Phi");
                            // The newly sharp Store remains on its own alias
                            // thread.  Bulk and unrelated alias Phis bypass it
                            // to the matching pre-Store memory slice.
                            if( palias != fld._alias ) {
                                Node prior = memSlice(mem(),palias);
                                for( int j=1; j<phi.nIns(); j++ )
                                    if( phi.in(j)==this )
                                        phi.setDef(j,prior);
                                CodeGen.CODE.add(phi);
                                i--;
                            }
                            break;
                        }
                        default:
                            throw Utils.TODO("should not reach here");
                        }
                    }
                }
                if( _con != fld._t || _alias != fld._alias ) {
                    unlock();   // Both fields participate in GVN semantics
                    _con = fld._t;
                    _alias = fld._alias;
                    CodeGen.CODE.add(this);
                    return this;
                }
            }
            // No further optimizations until alias sharpens
            return null;
        }
        assert _alias!=1;

        // Expose the same effective memory input already observed by compute.
        Node aliasMem = aliasMem();
        if( aliasMem != mem() ) {
            setDef(1,aliasMem);
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
        if( mem() instanceof MemMergeNode mem ) {
            setDef(1,mem.alias(_alias));
            return this;
        }

        // Value is automatically truncated by narrow store
        if( val() instanceof AndNode and && and.in(2)._type.isConstant()  ) {
            if( _con==Type.BOTTOM ) throw Utils.TODO("sharpen first");
            int log = _con.log_size();
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
        if( val() instanceof SarNode shr &&
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

    // Alias #1 is the sole unresolved state.  Sharpen once, and thereafter
    // every independent source of alias information must agree.
    private boolean sharpenAlias( int alias ) {
        assert alias != 1;
        assert _alias == 1 || _alias == alias;
        if( _alias == alias ) return false;
        unlock();
        _alias = alias;
        CodeGen.CODE.add(this);
        return true;
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
        if( _con==Type.BOTTOM && CodeGen.CODE._phase.ordinal() > CodeGen.Phase.Opto.ordinal() )
            throw Utils.TODO("Failed to lift");
        if( ptr()._type == Type.TOP )
            return null; // This means we have an error input, report elsewhere
        TypeMemPtr tmp = (TypeMemPtr)ptr()._type;
        Field f = tmp._obj.field(_name);
        if( f!=null && f._final && !_init )
            return Parser.error("Cannot modify final field '"+_name+"'",_loc);
        return null;
    }
}
