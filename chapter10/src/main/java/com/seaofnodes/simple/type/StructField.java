package com.seaofnodes.simple.type;

import java.util.Objects;

/**
 * Represents a field in a struct. This is not a Type in the type system.
 */
public class StructField implements AliasSource {

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

    public StructField(TypeStruct structType, Type fieldType, String fieldName, int alias) {
        _structType = structType;
        _fieldType = fieldType;
        _fieldName = fieldName;
        _alias = alias;
    }

    /**
     * Alias names - requirement is that they start with $ so we can use
     * them as special var names. We rely upon this naming convention to find all the
     * mem slices
     */
    @Override
    public String aliasName() { return "$" + _structType._name + "_" + _fieldName; }

    @Override
    public int alias() { return _alias; }

    @Override
    public String toString() { return _fieldType.toString() + " " + _fieldName; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StructField typeField = (StructField) o;
        // two fields referring to same struct (or null) are the same
        // if other values are the same
        return _structType == typeField._structType
                && _fieldType == typeField._fieldType
                && Objects.equals(_fieldName, typeField._fieldName)
                && Objects.equals(_alias, typeField._alias);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_fieldName, _alias);
    }
}
