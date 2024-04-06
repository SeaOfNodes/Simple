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

    // This field is NOT final, because the tuple expands as we add memory aliases
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
    public TypeTuple addMemProj(TypeStruct ts, ScopeNode scope) {
        // resize the tuple's type array to include all fields of the struct
        assert ts._fields.length==0 || _args._types.length == ts._fields[0]._alias;
        Type[] args = Arrays.copyOf(_args._types, _args._types.length + ts._fields.length);
        int i = _args._types.length;
        int origLength = i;
        // The new members of the tuple get a mem type with an alias
        for( Field field : ts._fields )
            args[i++] = TypeMem.make(field._alias);
        _args = TypeTuple.make(args);
        _type = compute(); // proj needs input node to have types already set
        i = origLength; // for assertion below
        // For each of the fields we now add a mem projection
        // note that the alias matches the slot of the field in the
        // tuple - this we assert below
        for( Field field : ts._fields ) {
            assert field._alias == i;
            String name = field.aliasName();
            Node n = new ProjNode(this, field._alias, name).peephole();
            scope.define(name, n);
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
