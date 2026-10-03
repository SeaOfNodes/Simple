package com.seaofnodes.simple.type;

import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;
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

    public boolean _one;  // Singleton instance

    private static final Ary<TypeMemPtr> FREE = new Ary<>(TypeMemPtr.class);
    private TypeMemPtr(byte nil, TypeStruct obj, boolean one) { super(TMEMPTR,nil); init(nil,obj,one); }
    private TypeMemPtr init(byte nil, TypeStruct obj, boolean one) { _nil=nil; _obj=obj; _ro=obj==TypeStruct.BOT; _one=one; return this; }
    // Return a filled-in TypeMemPtr; either from free list or alloc new.
    private static TypeMemPtr malloc(byte nil, TypeStruct obj, boolean one) {
        return FREE.isEmpty() ? new TypeMemPtr(nil,obj,one) : FREE.pop().init(nil,obj,one);
    }

    // All fields directly listed
    public static TypeMemPtr make(byte nil, TypeStruct obj, boolean one) {
        return make(nil,obj,one,obj==TypeStruct.BOT);
    }
    private static TypeMemPtr make(byte nil, TypeStruct obj, boolean one, boolean ro) {
        TypeMemPtr tmp = malloc(nil, obj, one).access(ro);
        TypeMemPtr t2 = tmp.intern();
        if( t2==tmp ) return tmp;
        return VISIT.isEmpty() ? t2.free(tmp) : t2.delayFree(tmp);
    }
    @Override TypeMemPtr free(Type t) {
        TypeMemPtr tmp = (TypeMemPtr)t;
        tmp._nil  = -99;
        tmp._obj  = null;
        tmp._dual = null;
        tmp._hash = 0;
        FREE.push(tmp);
        return this;
    }
    @Override boolean isFree() { return _obj==null; }

    static TypeMemPtr make(byte nil, TypeStruct obj) { return make(nil, obj, false); }
    public static TypeMemPtr make        (TypeStruct obj) { return make((byte)2, obj); }
    public static TypeMemPtr makeNullable(TypeStruct obj) { return make((byte)3, obj); }

    public TypeMemPtr makeFrom(TypeStruct obj) { return obj==_obj ? this : make(_nil, obj, _one,_ro); }
    public TypeMemPtr makeNullable() { return makeFrom((byte)3); }
    @Override TypeMemPtr makeFrom(byte nil) { return nil==_nil ? this : make(nil, _obj, _one,_ro); }

    private TypeMemPtr access(boolean ro) { _ro=ro; return this; }

    public TypeMemPtr withAccess(boolean ro) {
        if( _ro==ro ) return this;
        TypeMemPtr ptr = malloc(_nil, _obj, _one);
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
        return make(xmeet0(that), (TypeStruct)_obj.meet(that._obj), _one & that._one,_ro | that._ro);
    }

    @Override
    TypeMemPtr xdual() { return malloc( dual0(), _obj.dual(), _one).access(!_ro); }

    @Override TypeMemPtr rdual() {
        if( _dual!=null ) return dual();
        assert !_terned;
        TypeMemPtr d = malloc(dual0(), null, _one).access(!_ro);
        (_dual = d)._dual = this; // Cross link duals
        d._obj = _obj._terned ? _obj.dual() : _obj.rdual();
        return d;
    }

    // RHS is NIL; do not deep-dual when crossing the center line
    @Override Type meet0() { return _nil==3 ? this : make((byte)3,_obj,false,_ro); }

    @Override boolean _isConstant() { return _one && _obj._isConstant(); }
    @Override boolean _isFinal() { return _ro; }
    @Override TypeMemPtr _makeRO() { return withAccess(true); }
    @Override boolean _isGLB(boolean mem) { return _ro && _nil==3 && !_one && _obj.isGLB2(); }
    @Override TypeMemPtr _glb(boolean mem) { return make((byte)3,_obj.glb2(),false,true); }
    @Override TypeMemPtr _close() { return makeFrom(_obj._close()); }


    // True if this "isa" t up to named structures
    @Override public boolean shallowISA( Type t ) {
        if( !(t instanceof TypeMemPtr that) ) return false;
        if( this==that ) return true;
        if( xmeet0(that)!=that._nil || (_ro && !that._ro) ) return false;
        if( _obj==that._obj ) return true;
        if( _obj._name.equals(that._obj._name) )
            return true;        // Shallow, do not follow matching names, just assume ok
        throw Utils.TODO(); // return _obj.shallowISA(that._obj);
    }

    // Is forward-reference
    @Override public boolean isFRef() { return _obj.isFRef(); }

    @Override public int log_size() { return 3; } // (1<<3)==8-byte pointers

    @Override int hash() { return (_ro ? 8192 : 0) ^ _obj.hashCode() ^ super.hash() ^ (_one ? 2048 : 0); }

    @Override boolean eq(Type t) {
        TypeMemPtr ptr = (TypeMemPtr)t; // Invariant
        return _ro==ptr._ro && super.eq(ptr) && _one == ptr._one && _obj == ptr._obj;
    }
    @Override boolean cycle_eq(Type t) {
        if( t._type != TMEMPTR ) return false;
        TypeMemPtr ptr = (TypeMemPtr)t; // Invariant
        return _ro==ptr._ro && super.eq(ptr) && _one == ptr._one && _obj.cycle_eq(ptr._obj);
    }

    @Override int nkids() { return 1; }
    @Override Type at( int idx ) { return _obj; }
    @Override void set( int idx, Type t ) { _obj = (TypeStruct)t; }

    @Override public String str() {
        if( this==BOT ) return "PtrBot";
        if( this==TOP ) return "PtrTop";
        if( this== NOTBOT) return "*void";
        return x()+"*"+_obj.str()+q();
    }

    @Override SB _print(SB sb, BitSet visit, boolean html ) {
        if( this==BOT ) return sb.p("PtrBot");
        if( this==TOP ) return sb.p("PtrTop");
        if( this== NOTBOT) return sb.p("*void");
        return _obj.print(sb.p(x()).p("*"),visit,html).p(q());
    }
}
