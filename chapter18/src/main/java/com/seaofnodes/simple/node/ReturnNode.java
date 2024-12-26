package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.*;

import java.util.BitSet;

/**
 * The Return node has two inputs.  The first input is a control node and the
 * second is the data node that supplies the return value.
 * <p>
 * In this presentation, Return functions as a Stop node, since multiple <code>return</code> statements are not possible.
 * The Stop node will be introduced in Chapter 6 when we implement <code>if</code> statements.
 * <p>
 * The Return's output is the value from the data node.
 */
public class ReturnNode extends CFGNode {

    public ReturnNode(Node ctrl, Node mem, Node data, FunNode fun) {
        super(ctrl, mem, data, fun);
    }

    public Node ctrl() { return in(0); }
    public Node mem () { return in(1); }
    public Node expr() { return in(2); }
    public FunNode fun() { return (FunNode)in(3); }

    @Override
    public String label() { return "Return"; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        sb.append("return ");
        if( expr()==null ) return sb.append("INPROGRESS");
        expr()._print0(sb, visited);
        return sb.append(";");
    }

    @Override
    public Type compute() {
        if( inProgress() ) return TypeTuple.RET; // In progress
        return TypeTuple.make(ctrl()._type,mem()._type,expr()._type);
    }

    @Override public Node idealize() { return null; }

    public boolean inProgress() {
        return ctrl() instanceof RegionNode r && r.inProgress();
    }

    // Add a return exit to the current parsing function
    void addReturn(Node ctrl, Node rmem, Node expr) {
        assert inProgress();
        RegionNode r = (RegionNode)ctrl();
        // Assert that the Phis are in particular outputs; not reordered or shuffled
        PhiNode mem = (PhiNode)r.out(0); assert mem._declaredType == TypeMem.BOT;
        PhiNode rez = (PhiNode)r.out(1); assert rez._declaredType == Type.BOTTOM;
        // Pop "inProgress" null off
        r  ._inputs.pop();
        mem._inputs.pop();
        rez._inputs.pop();
        // Add new return point
        r  .addDef(ctrl);
        mem.addDef(rmem);
        rez.addDef(expr);
        // Back to being inProgress
        r  .addDef(null);
        mem.addDef(null);
        rez.addDef(null);
    }
}
