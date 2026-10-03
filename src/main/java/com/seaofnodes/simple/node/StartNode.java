package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.type.*;
import java.util.HashSet;
import static com.seaofnodes.simple.Utils.TODO;

/** Start supplies control, whole memory, and the argument, in that order. */
public class StartNode extends LoopNode implements MultiNode {

    final Type _arg;

    public StartNode(Type arg) { super(null); _arg = arg; _type = compute(); }

    @Override public String label() { return "Start"; }

    @Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        return p.p(label());
    }

    @Override public boolean isMultiHead() { return true; }
    @Override public boolean blockHead() { return true; }
    @Override public CFGNode cfg0() { return this; }

    @Override public TypeTuple compute() {
        return TypeTuple.make(Type.CONTROL,TypeMem.BOT,_arg);
    }

    @Override public Node idealize() { return null; }

    // No immediate dominator, and idepth==0
    @Override public int idepth() { return 0; }
    @Override public CFGNode idom(Node dep) { return null; }

}
