package com.seaofnodes.simple.type;

/**
 * Represents a field in a struct
 */
public class TypeField {

    /**
     * Struct type that owns this field
     */
    final TypeStruct _structType;
    /**
     * Type of the field, for now can only be int
     */
    final Type _fieldType;
    /**
     * Field name
     */
    final String _fieldName;

    public TypeField(TypeStruct _structType, Type _fieldType, String _fieldName) {
        this._structType = _structType;
        this._fieldType = _fieldType;
        this._fieldName = _fieldName;
    }
}
