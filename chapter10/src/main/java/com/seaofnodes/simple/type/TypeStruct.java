package com.seaofnodes.simple.type;

import com.seaofnodes.simple.Parser;

import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * Represents a struct type.
 */
public class TypeStruct extends Type {
    public final String _name;
    private LinkedHashMap<String, TypeField> _fields = new LinkedHashMap<>();

    public TypeStruct(String name) {
        super(Type.TSTRUCT);
        this._name = name;
    }

    public void addField(String fieldName, Type fieldType) {
        if (_fields.containsKey(fieldName))
            throw Parser.error("Field " + fieldName + " already present in struct " + _name);
        _fields.put(fieldName, new TypeField(this, fieldType, fieldName));
    }

    public TypeField getField(String fieldName) { return _fields.get(fieldName); }

    public int numFields() { return _fields.size(); }

    @Override
    int hash() { return Objects.hash(_type, _name); }

    @Override
    boolean eq(Type t) {
        if (t instanceof TypeStruct other)
            return _name.equals(other._name);
        return false;
    }

    @Override
    public StringBuilder _print(StringBuilder sb) {
        return sb.append(_name);
    }
}
