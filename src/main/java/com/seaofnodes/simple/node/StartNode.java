package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.*;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import static com.seaofnodes.simple.Utils.TODO;

/**
 * The Start node represents the start of the function.
 *
 * Start initially has 1 input (arg) from outside and the initial control.
 * In ch10 we also add mem aliases as structs get defined; each field in struct
 * adds a distinct alias to Start's tuple.
 */
public class StartNode extends CFGNode implements MultiNode {

    final Type _arg;

    public StartNode(Type arg) { super(); _arg = arg; _type = compute(); }

    @Override public String label() { return "Start"; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
      return sb.append(label());
    }

    @Override public boolean isMultiHead() { return true; }
    @Override public boolean blockHead() { return true; }
    @Override public CFGNode cfg0() { return this; }

    @Override public TypeTuple compute() {
        return TypeTuple.make(Type.CONTROL,TypeMem.TOP,_arg);
    }

    @Override public Node idealize() { return null; }

    // No immediate dominator, and idepth==0
    @Override public int idepth() { return 0; }
    @Override public CFGNode idom(Node dep) { return null; }

    @Override void _walkUnreach( BitSet visit, HashSet<CFGNode> unreach ) { }

    @Override public int loopDepth() { return (_loopDepth=1); }

    @Override public Node getBlockStart() { return this; }
}
