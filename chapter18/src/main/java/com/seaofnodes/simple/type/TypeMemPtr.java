package com.seaofnodes.simple.type;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import java.util.ArrayList;

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

    public final TypeStruct _obj;

    private TypeMemPtr(byte nil, TypeStruct obj) {
        super(TMEMPTR,nil);
        assert obj!=null;
        _obj = obj;
    }
    public static TypeMemPtr make(byte nil, TypeStruct obj) { return new TypeMemPtr(nil, obj).intern(); }
    public static TypeMemPtr make(TypeStruct obj) { return make((byte)3, obj); }
    public TypeMemPtr makeFrom(TypeStruct obj) { return obj==_obj ? this : make(_nil, obj); }
    @Override public TypeMemPtr makeFrom(byte nil) { return nil==_nil ? this : make(nil,_obj); }
    @Override public TypeMemPtr makeRO() { return makeFrom(_obj.makeRO()); }
    @Override public boolean isFinal() { return _obj.isFinal(); }

    // An abstract pointer, pointing to either a Struct or an Array.
    // Can also be null or not, so 4 choices {TOP,BOT} x {nil,not}
    public static TypeMemPtr BOT = make((byte)3, TypeStruct.BOT);
    public static TypeMemPtr TOP = BOT.dual();
    public static TypeMemPtr NOTBOT = make((byte)2,TypeStruct.BOT);

    public static TypeMemPtr TEST= make((byte)2, TypeStruct.TEST);
    public static void gather(ArrayList<Type> ts) { ts.add(NOTBOT); ts.add(BOT); ts.add(TEST); }

    @Override
    public TypeNil xmeet(Type t) {
        TypeMemPtr that = (TypeMemPtr) t;
        return TypeMemPtr.make(xmeet0(that), (TypeStruct)_obj.meet(that._obj));
    }

    @Override
    public TypeMemPtr dual() { return TypeMemPtr.make( dual0(), _obj.dual()); }

    // RHS is NIL
    @Override Type meet0() {
        // NOT DOING DUAL
        return _nil==3 ? this : make((byte)3,_obj);
    }

    @Override public TypeMemPtr glb() { return make((byte)3,_obj.glb()); }
    // Is forward-reference
    @Override public boolean isFRef() { return _obj.isFRef(); }

    @Override public int log_size() { return 2; } // (1<<2)==4-byte pointers

    @Override int hash() { return _obj.hashCode() ^ super.hash(); }

    @Override boolean eq(Type t) {
        TypeMemPtr ptr = (TypeMemPtr)t; // Invariant
        return _obj == ptr._obj  && super.eq(ptr);
    }

    @Override public String str() {
        if( this== NOTBOT) return "*void";
        if( this==    BOT) return "*void?";
        return x()+"*"+_obj.str()+q();
    }

    @Override public SB print(SB sb) {
        if( this== NOTBOT) return sb.p("*void");
        if( this==    BOT) return sb.p("*void?");
        return _obj.print(sb.p(x()).p("*")).p(q());
    }
    @Override public SB gprint(SB sb) {
        if( this== NOTBOT) return sb.p("*void");
        if( this==    BOT) return sb.p("*void?");
        return _obj.gprint(sb.p(x()).p("*")).p(q());
    }
}
