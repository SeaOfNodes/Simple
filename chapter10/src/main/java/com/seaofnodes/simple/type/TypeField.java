package com.seaofnodes.simple.type;

/**
 * Represents a field in a struct
 */
public class TypeField {

    final Type _fieldType;
    final String _fieldName;

    public TypeField(Type _fieldType, String _fieldName) {
        this._fieldType = _fieldType;
        this._fieldName = _fieldName;
    }
}
