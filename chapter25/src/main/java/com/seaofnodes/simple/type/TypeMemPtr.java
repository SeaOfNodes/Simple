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
    public boolean _one;      // Singleton instance, a constant pointer value
    public boolean _private;  // Unescaped allocation-local pointer

    private static final Ary<TypeMemPtr> FREE = new Ary<>(TypeMemPtr.class);
    private TypeMemPtr(byte nil, TypeStruct obj, boolean one, boolean xprivate) { super(TMEMPTR,nil); init(nil,obj,one,xprivate); }
    private TypeMemPtr init(byte nil, TypeStruct obj, boolean one, boolean xprivate) { _nil=nil; _obj=obj; _one=one; _private=xprivate; return this; }
    // Return a filled-in TypeMemPtr; either from free list or alloc new.
    static TypeMemPtr malloc(byte nil, TypeStruct obj, boolean one, boolean xprivate) {
        return FREE.isEmpty() ? new TypeMemPtr(nil,obj,one,xprivate) : FREE.pop().init(nil,obj,one,xprivate);
    }

    // All fields directly listed
    public static TypeMemPtr make(byte nil, TypeStruct obj, boolean one) {
        return make(nil,obj,one,false);
    }
    public static TypeMemPtr make(byte nil, TypeStruct obj, boolean one, boolean xprivate) {
        TypeMemPtr tmp = malloc(nil, obj, one, xprivate);
        TypeMemPtr t2 = tmp.intern();
        if( t2==tmp ) return tmp;
        return VISIT.isEmpty() ? t2.free(tmp) : t2.delayFree(tmp);
    }
    @Override TypeMemPtr free(Type t) {
        TypeMemPtr tmp = (TypeMemPtr)t;
        tmp._nil  = -99;
        tmp._obj  = null;
        tmp._one  = false;
        tmp._private = false;
        tmp._dual = null;
        tmp._hash = 0;
        FREE.push(tmp);
        return this;
    }
    @Override boolean isFree() { return _obj==null; }

    static TypeMemPtr make(byte nil, TypeStruct obj) { return make(nil, obj, false); }
    public static TypeMemPtr make        (TypeStruct obj) { return make((byte)2, obj); }
    public static TypeMemPtr makeNullable(TypeStruct obj) { return make((byte)3, obj); }
    public static TypeMemPtr makePrivate (TypeStruct obj) { return make((byte)2, obj, false, true); }

    public TypeMemPtr makeFrom(TypeStruct obj) { return obj==_obj ? this : make(_nil, obj, _one, _private); }
    public TypeMemPtr makeNullable() { return makeFrom((byte)3); }
    @Override TypeMemPtr makeFrom(byte nil) { return nil==_nil ? this : make(nil, _obj, _one, _private); }
    public TypeMemPtr makeHigh(byte nil) { return make(nil,_obj.makeHigh(),false); }

    // An abstract pointer, pointing to either a Struct or an Array.
    // Can also be null or not, so 4 choices {TOP,BOT} x {nil,not}
    public static final TypeMemPtr BOT = make((byte)3, TypeStruct.BOT);
    public static final TypeMemPtr TOP = BOT.dual();
    public static final TypeMemPtr NOTBOT = make((byte)2,TypeStruct.BOT);

    public static final TypeMemPtr TEST= make((byte)2, TypeStruct.TEST);
    public static void gather(ArrayList<Type> ts) { ts.add(NOTBOT); ts.add(BOT); ts.add(TEST); }

    @Override
    public TypeNil xmeet(Type t) {
        TypeMemPtr that = (TypeMemPtr) t;
        assert !isFree() && !that.isFree();
        // Can't keep the _one if mixing 2 unequal singletons; the result is
        // not either singleton.
        boolean one = _one && that._one && (_obj==that._obj || !(_obj.isConAry() || that._obj.isConAry()));
        return make(xmeet0(that), (TypeStruct)_obj.meet(that._obj), one, _private & that._private);
    }

    @Override
    TypeMemPtr xdual() { return malloc( dual0(), _obj.dual(), !_one, !_private); }

    @Override TypeMemPtr rdual() {
        if( _dual!=null ) return dual();
        assert !_terned;
        TypeMemPtr d = malloc(dual0(), null, !_one, !_private);
        (_dual = d)._dual = this; // Cross link duals
        d._obj = _obj._terned ? _obj.dual() : _obj.rdual();
        return d;
    }

    // RHS is NIL; do not deep-dual when crossing the center line
    @Override Type meet0() { return _nil==3 ? this : make((byte)3,_obj); }

    @Override boolean _isConstant() { return _one; }
    @Override boolean _isFinal() { return _obj._isFinal(); }
    @Override TypeMemPtr _makeRO() { return makeFrom(_obj._makeRO()); }
    @Override boolean _isGLB(boolean mem) { return _obj._isGLB(true); }
    @Override TypeMemPtr _glb(boolean mem) { return make((byte)3,_obj.glb2()); }
    @Override TypeMemPtr _close( String name, HashMap<String, Type> TYPES ) { return malloc(_nil,_obj._close(name, TYPES ),_one,_private); }

    @Override Type _upgradeType(HashMap<String,Type> TYPES) {
        return makeFrom((TypeStruct)_obj._upgradeType(TYPES));
    }

    @Override public int log_size() { return 3; } // (1<<3)==8-byte pointers

    @Override int hash() { return _obj.hashCode() ^ super.hash() ^ (_one ? 2048 : 0) ^ (_private ? 4096 : 0); }

    @Override boolean eq(Type t) {
        TypeMemPtr ptr = (TypeMemPtr)t; // Invariant
        return super.eq(ptr) && _one == ptr._one && _private == ptr._private && _obj == ptr._obj;
    }
    @Override boolean cycle_eq(Type t) {
        if( t._type != TMEMPTR ) return false;
        TypeMemPtr ptr = (TypeMemPtr)t; // Invariant
        return super.eq(ptr) && _one == ptr._one && _private == ptr._private && _obj.cycle_eq(ptr._obj);
    }

    @Override public int nkids() { return 1; }
    @Override public Type at( int idx ) { return _obj; }
    @Override public void set( int idx, Type t ) { _obj = (TypeStruct)t; }

    // Reserve tags for null/not, one/general and private/general
    @Override int TAGOFF() { return 8; }
    @Override public void packed( BAOS baos, HashMap<String,Integer> strs ) {
        assert _nil>=2;
        baos.write(TAGOFFS[_type]
                   + (_nil==2 ? 0 : 1)
                   + (!_one   ? 0 : 2)
                   + (!_private ? 0 : 4) );
    }
    static TypeMemPtr packed( int tag, BAOS bais ) {
        return malloc((byte)((tag&1)+2),null,(tag&2)==2,(tag&4)==4);
    }

    @Override public String str() {
        if( this== NOTBOT) return "*void";
        if( this==    BOT) return "*void?";
        return x()+"*"+_obj.str()+q();
    }

    @Override SB _print(SB sb, BitSet visit, boolean html ) {
        if( this== NOTBOT) return sb.p("*void");
        if( this==    BOT) return sb.p("*void?");
        return _obj.print(sb.p(x()).p("*"),visit,html).p(q());
    }
}
