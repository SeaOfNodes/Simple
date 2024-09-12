package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.type.*;
import java.util.BitSet;
import java.util.HashSet;

/** Start supplies control, whole memory, and the argument, in that order. */
public class StartNode extends CFGNode implements MultiNode {

    private final TypeTuple _args;

    public StartNode(Type[] args) {
        super();
        _type = _args = TypeTuple.make(args);
    }

    @Override public String label() { return "Start"; }

    @Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        return p.p(label());
    }

    @Override public boolean isMultiHead() { return true; }
    @Override public boolean blockHead() { return true; }
    @Override public CFGNode cfg0() { return this; }

    @Override
    public TypeTuple compute() { return _args; }

    @Override
    public Node idealize() { return null; }

    // No immediate dominator, and idepth==0
    @Override public int idepth() { return 0; }
    @Override public CFGNode idom(Node dep) { return null; }

    @Override void _walkUnreach( BitSet visit, HashSet<CFGNode> unreach ) { }

    @Override public int loopDepth() { return (_loopDepth=1); }

    @Override public Node getBlockStart() { return this; }
}
