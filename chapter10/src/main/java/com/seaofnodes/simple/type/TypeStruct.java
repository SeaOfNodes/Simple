package com.seaofnodes.simple.type;

import com.seaofnodes.simple.Utils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Represents a struct type.
 */
public class TypeStruct extends Type {

    // For now in terms of the lattice
    // a struct type just stays as is

    public final String _name;
    private final StructField[] _fields;

    private TypeStruct(String name, List<StructField> fields) {
        super(Type.TSTRUCT);
        _name = name;
        _fields = fields.toArray(new StructField[fields.size()]);
        for (int i = 0; i < fields.size(); i++) {
            StructField field = fields.get(i);
            // We copy the field because parser does not have this, we also add an alias
            _fields[i] = new StructField(this, field._fieldType, field._fieldName, Utils.nextAliasId());
        }
    }

    public static TypeStruct make(String name, List<StructField> fields) { return new TypeStruct(name, fields).intern(); }

    public StructField getField(String fieldName) {
        for (StructField field: _fields)
            if (field._fieldName.equals(fieldName))
                return field;
        return null;
    }

    public int numFields() { return _fields.length; }
    public StructField[] fields() { return _fields; }

    @Override
    protected Type xmeet(Type t) {
        TypeStruct other = (TypeStruct) t;
        if (equals(other)) return this;
        else return Type.BOTTOM; // This means parser or syntax error as it is not legal
    }

    @Override
    public Type dual() { return this; }

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
            // FIXME this may not work when we have self referential structs
            return Arrays.equals(_fields, other._fields );
        }
        return false;
    }

    @Override
    public StringBuilder _print(StringBuilder sb) {
        return sb.append(_name);
    }

}
