package com.seaofnodes.simple.type;

import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.simple.util.AryInt;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.BAOS;
import java.util.*;

/**
 * Represents a Pointer to memory.
 *
 * Null is generic pointer to non-existent memory.
 * *void is a non-Null pointer to all possible refs, both structs and arrays.
 * Pointers can be to specific struct and array types, or a union with Null.
 * The distinguished *$BOT ptr represents union of *void and Null.
 * The distinguished *$TOP ptr represents the dual of *$BOT.
 */
public class TypeMemPtr extends TypeNil {
    // A TypeMemPtr is pair (obj,nil)
    // where obj is a TypeStruct, possibly TypeStruct.BOT/TOP
    // where nil is an explicit null is allowed or not

    // Examples:
    // (Person,false) - a not-nil Person
    // (Person,true ) - a Person or a nil
    // (BOT   ,false) - a not-nil void* (unspecified struct)
    // (TOP   ,true ) - a nil

    public TypeStruct _obj;
    // Deep read-only access through this pointer; independent of slot finality.
    public boolean _ro;

    public boolean _one;      // Singleton pointer value; the pointer itself is constant.
    public boolean _pub;      // Escaped/public pointer.  False means constructor-private.

    private static final Ary<TypeMemPtr> FREE = new Ary<>(TypeMemPtr.class);
    private TypeMemPtr(byte nil, TypeStruct obj, boolean one, boolean pub) { super(TMEMPTR,nil); init(nil,obj,one,pub); }
    private TypeMemPtr init(byte nil, TypeStruct obj, boolean one, boolean pub) { _nil=nil; _obj=obj; _ro=obj==TypeStruct.BOT; _one=one; _pub=pub; return this; }
    // Return a filled-in TypeMemPtr; either from free list or alloc new.
    static TypeMemPtr malloc(byte nil, TypeStruct obj, boolean one, boolean pub) {
        return FREE.isEmpty() ? new TypeMemPtr(nil,obj,one,pub) : FREE.pop().init(nil,obj,one,pub);
    }

    // All fields directly listed
    public static TypeMemPtr make(byte nil, TypeStruct obj, boolean one) {
        return make(nil,obj,one,true);
    }
    public static TypeMemPtr make(byte nil, TypeStruct obj, boolean one, boolean pub) {
        return make(nil,obj,one, pub,obj==TypeStruct.BOT);
    }
    private static TypeMemPtr make(byte nil, TypeStruct obj, boolean one, boolean pub, boolean ro) {
        TypeMemPtr tmp = malloc(nil, obj, one, pub).access(ro);
        TypeMemPtr t2 = tmp.intern();
        if( t2==tmp ) return tmp;
        return VISIT.isEmpty() ? t2.free(tmp) : t2.delayFree(tmp);
    }
    @Override TypeMemPtr free(Type t) {
        TypeMemPtr tmp = (TypeMemPtr)t;
        tmp._nil  = -99;
        tmp._obj  = null;
        tmp._one  = false;
        tmp._pub = true;
        tmp._dual = null;
        tmp._hash = 0;
        FREE.push(tmp);
        return this;
    }
    @Override boolean isFree() { return _obj==null; }

    static TypeMemPtr make(byte nil, TypeStruct obj) { return make(nil, obj, false); }
    public static TypeMemPtr make        (TypeStruct obj) { return make((byte)2, obj); }
    public static TypeMemPtr makeNullable(TypeStruct obj) { return make((byte)3, obj); }
    public static TypeMemPtr makePrivate (TypeStruct obj) { return make((byte)2, obj, false, false); }

    public TypeMemPtr makeFrom(TypeStruct obj) { return obj==_obj ? this : make(_nil, obj, _one, _pub,_ro); }
    public TypeMemPtr makeNullable() { return makeFrom((byte)3); }
    /** Inferred mutable local: nullable, public and non-singleton. */
    public TypeMemPtr makeVar() { return (TypeMemPtr)makeStorage(); }
    /** Public-memory shape: nullable and non-singleton, preserving the referent. */
    @Override TypeMemPtr _makeStorage() { return make(_nil,_obj._makeStorage(),false,true,_ro); }
    @Override TypeMemPtr makeFrom(byte nil) { return nil==_nil ? this : make(nil, _obj, _one, _pub,_ro); }
    @Override public Type nonZero() { return makeFrom((byte)(_nil <= 1 ? 1 : 2)); }

    private TypeMemPtr access(boolean ro) { _ro=ro; return this; }

    public TypeMemPtr withAccess(boolean ro) {
        if( _ro==ro ) return this;
        TypeMemPtr ptr = malloc(_nil, _obj, _one, _pub);
        ptr._ro = ro;
        TypeMemPtr res = ptr.intern();
        if( res==ptr ) return ptr;
        return VISIT.isEmpty() ? res.free(ptr) : res.delayFree(ptr);
    }

    // Writable slots can load and store references, so their element access
    // must agree in both directions. A read-only outer view can weaken access.
    @Override public boolean accessISA(Type dst) {
        if( !(dst instanceof TypeMemPtr ptr) ) return true;
        if( _ro && !ptr._ro ) return false;
        if( ptr._ro || _obj._fields==null || ptr._obj._fields==null || !_obj.isAry() || !ptr._obj.isAry() ) return true;
        Type a = _obj._fields[1]._t, b = ptr._obj._fields[1]._t;
        return a.accessISA(b) && b.accessISA(a);
    }

    // An abstract pointer, pointing to either a Struct or an Array.
    // Can also be null or not, so 4 choices {TOP,BOT} x {nil,not}
    public static final TypeMemPtr BOT = make((byte)3, TypeStruct.BOT);
    public static final TypeMemPtr TOP = BOT.dual();
    public static final TypeMemPtr NOTBOT = make((byte)2,TypeStruct.BOT);

    public static final TypeMemPtr TEST= make((byte)2, TypeStruct.TEST);
    public static void gather(ArrayList<Type> ts) { ts.add(NOTBOT); ts.add(BOT); ts.add(TEST); ts.add(TEST.makeRO()); }

    @Override
    public TypeNil xmeet(Type t) {
        TypeMemPtr that = (TypeMemPtr) t;
        assert !isFree() && !that.isFree();
        // Can't keep _one if mixing two unequal singletons; the result is not
        // either singleton.  Once either side is public, the meet is public.
        boolean one = _one && that._one && (_obj==that._obj || !(_obj.isConAry() || that._obj.isConAry()));
        return make(xmeet0(that), (TypeStruct)_obj.meet(that._obj), one, _pub | that._pub,_ro | that._ro);
    }

    @Override
    TypeMemPtr xdual() { return malloc( dual0(), _obj.dual(), !_one, !_pub).access(!_ro); }

    @Override TypeMemPtr rdual() {
        if( _dual!=null ) return dual();
        assert !_terned;
        TypeMemPtr d = malloc(dual0(), null, !_one, !_pub).access(!_ro);
        (_dual = d)._dual = this; // Cross link duals
        d._obj = _obj._terned ? _obj.dual() : _obj.rdual();
        return d;
    }

    // RHS is NIL; do not deep-dual when crossing the center line
    @Override Type meet0() { return _nil==3 ? this : make((byte)3,_obj,false,true,_ro); }

    @Override boolean _isConstant() { return _one; }
    @Override boolean _isFinal() { return _ro; }
    @Override TypeMemPtr _makeRO() { return withAccess(true); }
    @Override TypeMemPtr _close( String name, HashMap<String, Type> TYPES ) { return malloc(_nil,_obj._close(name, TYPES ),_one,_pub).access(_ro); }

    @Override Type _upgradeType(HashMap<String,Type> TYPES) {
        return makeFrom((TypeStruct)_obj._upgradeType(TYPES));
    }

    @Override public int log_size() { return 3; } // (1<<3)==8-byte pointers

    @Override int hash() { return (_ro ? 8192 : 0) ^ _obj.hashCode() ^ super.hash() ^ (_one ? 2048 : 0) ^ (_pub ? 4096 : 0); }

    @Override boolean eq(Type t) {
        TypeMemPtr ptr = (TypeMemPtr)t; // Invariant
        return _ro==ptr._ro && super.eq(ptr) && _one == ptr._one && _pub == ptr._pub && _obj == ptr._obj;
    }
    @Override boolean cycle_eq(Type t) {
        if( t._type != TMEMPTR ) return false;
        TypeMemPtr ptr = (TypeMemPtr)t; // Invariant
        return _ro==ptr._ro && super.eq(ptr) && _one == ptr._one && _pub == ptr._pub && _obj.cycle_eq(ptr._obj);
    }

    @Override public int nkids() { return 1; }
    @Override public Type at( int idx ) { return _obj; }
    @Override public void set( int idx, Type t ) { _obj = (TypeStruct)t; }

    // Reserve tags for null/not, one/general, public/private and read-only access
    @Override int TAGOFF() { return 16; }
    @Override public void packed( BAOS baos, HashMap<String,Integer> strs ) {
        assert _nil>=2;
        baos.write(TAGOFFS[_type]
                   + (_nil==2 ? 0 : 1)
                   + (!_one   ? 0 : 2)
                   + (!_pub    ? 0 : 4)
                   + (!_ro     ? 0 : 8) );
    }
    static TypeMemPtr packed( int tag, BAOS bais ) {
        return malloc((byte)((tag&1)+2),null,(tag&2)==2,(tag&4)==4).access((tag&8)==8);
    }

    @Override public String str() {
        if( this==BOT ) return "PtrBot";
        if( this==TOP ) return "PtrTop";
        if( this== NOTBOT) return "*void";
        return x()+"*"+(_obj==null?"---":_obj.str())+q();
    }

    @Override SB _print(SB sb, BitSet visit, boolean html ) {
        if( this==BOT ) return sb.p("PtrBot");
        if( this==TOP ) return sb.p("PtrTop");
        if( this== NOTBOT) return sb.p("*void");
        return _obj.print(sb.p(x()).p("*"),visit,html).p(q());
    }
}
