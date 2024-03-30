package com.seaofnodes.simple.type;

import java.util.Objects;

/**
 * Represents a Ptr. Ptrs are either null, or Ptr to a struct type
 * or a union of the two. Because Simple is a safe language we do not
 * have an untyped Ptr other than null.
 */
public class TypeMemPtr extends Type {

    /*
                      ANY
                   /      \
                 NULL    PTRCON
                  \       /
                  PTRCON|NULL
                     |
                    ALL
    */

    private static final int ANY = 0;
    private static final int NOTNULL = 1;
    private static final int NULL = 2;
    private static final int ALL = 3;      // Actually cannot happen as Simple doesn't allow void*, but useful to get BOT

    // All ptrs are typed ptrs or null
    // A typed ptr has _structType set

    TypeStruct _structType;
    int _tptr;

    public static final TypeMemPtr TOP     = TypeMemPtr.make(null, ANY);
    public static final TypeMemPtr NULLPTR = TypeMemPtr.make(null, NULL);
    public static final TypeMemPtr BOT     = TypeMemPtr.make(null, ALL);

    private TypeMemPtr(TypeStruct structType) {
        this(structType, NOTNULL);
    }

    private TypeMemPtr(TypeStruct structType, int tptr)
    {
        super(TMEMPTR);
        if (tptr == NOTNULL && structType == null)
            throw new AssertionError("Not null ptr must have struct type");
        _structType = structType;
        _tptr = tptr;
    }

    public static TypeMemPtr make(TypeStruct structType) { return TypeMemPtr.make(structType, NOTNULL); }
    public static TypeMemPtr make(TypeStruct structType, boolean maybeNull) { return TypeMemPtr.make(structType, maybeNull?NULL:NOTNULL); }
    private static TypeMemPtr make(TypeStruct structType, int tptr) { return new TypeMemPtr(structType, tptr).intern(); }

    public TypeStruct structType() { return _structType; }

    @Override
    public boolean isNull() { return _structType == null && _tptr == NULL; }

    @Override
    public boolean maybeNull() { return _tptr == NULL; }

    @Override
    protected Type xmeet(Type t) {
        TypeMemPtr other = (TypeMemPtr) t;
        if (this == other) return this;
        if (isNull() && !other.isNull()) return TypeMemPtr.make(other._structType, true);
        if (!isNull() && other.isNull()) return TypeMemPtr.make(_structType, true);
        if (_structType == other._structType) {
            if (other.maybeNull()) return other;
            return                 this;
        }
        return BOT;
    }

    @Override
    public Type dual() {
        if( this == TOP ) return BOT;
        if( this == BOT ) return TOP;
        return this;
    }

    @Override
    public Type glb() {
        if ( this == TOP ) return BOT;
        // If we are not null ptr - then return ptr|null
        if ( _structType != null && _tptr == NOTNULL ) return TypeMemPtr.make(_structType, true);
        return this;
    }

    @Override
    int hash() { return Objects.hash(_structType != null ? _structType.hash() : TMEMPTR, _tptr); }

    @Override
    boolean eq(Type t) {
        // a ptr is equal to itself
        if (this == t) return true;
        if (t instanceof TypeMemPtr ptr) {
            return _structType == ptr._structType
                    && _tptr == ptr._tptr;
        }
        return false;
    }

    @Override
    public StringBuilder _print(StringBuilder sb) {
        if (isNull()) return sb.append("null");
        if (this == TOP) return sb.append("PtrTop");
        if (this == BOT) return sb.append("PtrBot");
        sb.append("Ptr(" + _structType._name + ")");
        if (maybeNull()) sb.append("|null");
        return sb;
    }
}
