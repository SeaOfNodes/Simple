package com.seaofnodes.simple.type;

import com.seaofnodes.simple.Parser;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * Represents a struct type.
 */
public class TypeStruct extends Type {

    /**
     * Represents the starting alias ID - which is 2 because then it nicely
     * slots into Start's projections. Start already uses slots 0-1.
     */
    static final int _RESET_ALIAS_ID = 2;

    /**
     * Alias ID generator - we start at 2 because START uses 0 and 1 slots,
     * by starting at 2, our alias ID is nicely mapped to a slot in Start.
     */
    static int _ALIAS_ID = _RESET_ALIAS_ID;

    public final String _name;
    private LinkedHashMap<String, TypeField> _fields = new LinkedHashMap<>();

    public TypeStruct(String name) {
        super(Type.TSTRUCT);
        this._name = name;
    }

    public void addField(String fieldName, Type fieldType) {
        if (_fields.containsKey(fieldName))
            throw Parser.error("Field '" + fieldName + "' already present in struct '" + _name + "'");
        _fields.put(fieldName, new TypeField(this, fieldType, fieldName, _ALIAS_ID++));
    }

    public TypeField getField(String fieldName) { return _fields.get(fieldName); }

    public int numFields() { return _fields.size(); }
    public Collection<TypeField> fields() { return _fields.values(); }

    @Override
    protected Type xmeet(Type t) {
        TypeStruct other = (TypeStruct) t;
        if (equals(other)) return this;
        else throw new RuntimeException("Unexpected meet of struct types");
    }

    @Override
    int hash() { return Objects.hash(_type, _name); }

    @Override
    boolean eq(Type t) {
        // We must do a deep equals because of interning
        if (t instanceof TypeStruct other) {
            if ( !_name.equals(other._name) )
                return false;
            if ( _fields.size() != other._fields.size() )
                return false;
            for (TypeField field : _fields.values()) {
                if ( !field.equals(other._fields.get(field)) )
                    return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public StringBuilder _print(StringBuilder sb) {
        return sb.append(_name);
    }

    /**
     * Resets the alias IDs for new parse
     */
    public static void resetAliasId() { _ALIAS_ID = _RESET_ALIAS_ID; }
}
