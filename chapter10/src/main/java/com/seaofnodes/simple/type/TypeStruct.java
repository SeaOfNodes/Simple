package com.seaofnodes.simple.type;

import com.seaofnodes.simple.Parser;

import java.util.LinkedHashMap;

/**
 * Represents a struct type.
 */
public class TypeStruct {
    public final String _name;
    private LinkedHashMap<String, TypeField> _fields = new LinkedHashMap<>();

    public TypeStruct(String name) {
        this._name = name;
    }

    public void addField(String fieldName, Type fieldType) {
        if (_fields.containsKey(fieldName))
            throw Parser.error("Field " + fieldName + " already present in struct " + _name);
        _fields.put(fieldName, new TypeField(fieldType, fieldName));
    }

    public int numFields() { return _fields.size(); }
}
