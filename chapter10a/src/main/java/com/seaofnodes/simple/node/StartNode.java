package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.*;
import java.util.BitSet;

/** The entry tuple: control, initial whole-memory state, and argument. */
public class StartNode extends MultiNode {
    final TypeTuple _args;

    public StartNode(Type[] args) {
        super();
        _type = _args = TypeTuple.make(args);
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
    @Override int idepth() { return 0; }
    @Override Node idom() { return null; }
}
