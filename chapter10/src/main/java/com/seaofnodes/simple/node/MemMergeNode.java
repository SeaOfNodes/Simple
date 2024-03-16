package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeMem;

import java.util.BitSet;

/**
 * Merges all memory slices.
 */
public class MemMergeNode extends Node {

    public MemMergeNode(ScopeNode scope) {
        super((Node)null);
        // We lookup memory slices by the naming convention that they start with $
        // We could also use implicit knowledge that all memory projects are at offset >= 2
        String[] names = scope.reverseNames();
        for (String name: names) {
            if (!name.equals("$ctrl") && name.startsWith("$"))
                addDef(scope.lookup(name));
        }
    }

    @Override
    public String label() {
        return "MergeMem";
    }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return sb;
    }

    @Override
    public Type compute() {
        return TypeMem.TALLMEM;
    }

    @Override
    public Node idealize() {
        return null;
    }
}
