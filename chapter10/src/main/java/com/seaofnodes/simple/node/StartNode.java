package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.*;

import java.util.Arrays;
import java.util.BitSet;

/**
 * The Start node represents the start of the function.  For now, we do not
 * have any inputs to Start because our function does not yet accept
 * parameters.  When we add parameters the value of Start will be a tuple, and
 * will require Projections to extract the values.  We discuss this in detail
 * in Chapter 9: Functions and Calls.
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
        Type[] args = Arrays.copyOf(_args._types, _args._types.length + structType.numFields());
        int i = _args._types.length;
        for (TypeField field: structType.fields()) {
            args[i++] = new TypeMem(field);
        }
        _args = TypeTuple.make(args);
        _type = compute(); // FIXME this is a hack because of chicken and egg situation, proj needs input node to have types already set
        for (TypeField field: structType.fields()) {
            String name = field.aliasName();
            Node n = new MemProjNode(this, field._alias, name, field).peephole();
            scopeNode.define(name, n);
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
