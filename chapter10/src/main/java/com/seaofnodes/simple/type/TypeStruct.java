package com.seaofnodes.simple.type;

import com.seaofnodes.simple.Utils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Represents a struct type.
 */
public class TypeStruct extends Type {

    public final String _name;
    private final TypeField[] _fields;

    private TypeStruct(String name, List<TypeField> fields) {
        super(Type.TSTRUCT);
        _name = name;
        _fields = fields.toArray(new TypeField[fields.size()]);
        for (int i = 0; i < fields.size(); i++) {
            TypeField field = fields.get(i);
            // We copy the field because parser does not have this, we also add an alias
            _fields[i] = new TypeField(this, field._fieldType, field._fieldName, Utils.nextAliasId());
        }
    }

    public static TypeStruct make(String name, List<TypeField> fields) { return new TypeStruct(name, fields).intern(); }

    public TypeField getField(String fieldName) {
        for (TypeField field: _fields)
            if (field._fieldName.equals(fieldName))
                return field;
        return null;
    }

    public int numFields() { return _fields.length; }
    public TypeField[] fields() { return _fields; }

    @Override
    protected Type xmeet(Type t) {
        TypeStruct other = (TypeStruct) t;
        if (equals(other)) return this;
        else throw new RuntimeException("Unexpected meet of struct types");
    }

    @Override
    public Type glb() { return this; }

    @Override
    int hash() { return Objects.hash(_type, _name, _fields); }

    @Override
    boolean eq(Type t) {
        // We must do a deep equals because of interning
        if (t instanceof TypeStruct other) {
            if ( !_name.equals(other._name) )
                return false;
            return Arrays.equals(_fields, other._fields );
        }
        return false;
    }

    @Override
    public StringBuilder _print(StringBuilder sb) {
        return sb.append(_name);
    }

}
