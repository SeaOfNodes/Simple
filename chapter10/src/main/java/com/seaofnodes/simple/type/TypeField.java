package com.seaofnodes.simple.type;

import java.util.Objects;

/**
 * Represents a field in a struct
 */
public class TypeField implements AliasSource {

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

    public TypeField(TypeStruct structType, Type fieldType, String fieldName, int alias) {
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypeField typeField = (TypeField) o;
        return Objects.equals(_structType, typeField._structType)
                && Objects.equals(_fieldName, typeField._fieldName)
                && Objects.equals(_alias, typeField._alias);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_structType, _fieldName, _alias);
    }
}
