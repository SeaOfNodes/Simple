package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.*;

import java.util.Arrays;
import java.util.BitSet;

/**
 * The Start node represents the start of the function.
 *
 * Start initially has 1 input (arg) from outside and the initial control.
 * In ch10 we also add mem aliases as structs get defined; each field in struct
 * adds a distinct alias to Start's tuple.
 */
public class StartNode extends MultiNode {

    TypeTuple _args;
    
    public StartNode(Type[] args) {
        super();
        _args = TypeTuple.make(args);
        _type = _args;
    }

    /**
     * Creates a projection for each of the struct's fields, using the field alias
     * as the key.
     */
    public TypeTuple addMemProj(TypeStruct structType, ScopeNode scopeNode) {
        // resize the tuple's type array to include all fields of the struct
        Type[] args = Arrays.copyOf(_args._types, _args._types.length + structType.numFields());
        int i = _args._types.length;
        int origLength = i;
        // The new members of the tuple get a mem type with an alias
        for (StructField field: structType.fields()) {
            args[i++] = TypeMem.make(field);
        }
        _args = TypeTuple.make(args);
        _type = compute(); // proj needs input node to have types already set
        i = origLength; // for assertion below
        // For each of the fields we now add a mem projection
        // note that the alias matches the slot of the field in the
        // tuple - this we assert below
        for (StructField field: structType.fields()) {
            String name = field.aliasName();
            assert field.alias() == i;
            Node n = new ProjNode(this, field.alias(), name).peephole();
            scopeNode.define(name, n);
            i++;
        }
        return (TypeTuple) _type;
    }

    @Override
    public String label() { return "Start"; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
      return sb.append(label());
    }

    @Override public boolean isCFG() { return true; }
    @Override public boolean isMultiHead() { return true; }

    @Override
    public TypeTuple compute() { return _args; }

    @Override
    public Node idealize() { return null; }

    // No immediate dominator, and idepth==0
    @Override
    Node idom() { return null; }

}
