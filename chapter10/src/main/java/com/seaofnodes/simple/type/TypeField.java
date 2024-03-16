package com.seaofnodes.simple.type;

/**
 * Represents a field in a struct
 */
public class TypeField {

    /**
     * Alias ID generator - we start at 2 because START uses 0 and 1 slots,
     * by starting at 2, our alias ID is nicely mapped to a slot in Start.
     */
    static int _ALIAS_ID = 2;

    /**
     * Struct type that owns this field
     */
    final TypeStruct _structType;
    /**
     * Type of the field, for now can only be int
     */
    public final Type _fieldType;
    /**
     * Field name
     */
    public final String _fieldName;

    public final int _alias;

    public TypeField(TypeStruct _structType, Type _fieldType, String _fieldName) {
        this._structType = _structType;
        this._fieldType = _fieldType;
        this._fieldName = _fieldName;
        this._alias = _ALIAS_ID++;
    }

    public String aliasName() { return "$mem#" + _alias; }

}
