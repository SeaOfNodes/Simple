package com.seaofnodes.simple.node;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
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

    public final FunNode _fun;

    public ReturnNode(Node ctrl, Node mem, Node data, FunNode fun) {
        super(ctrl, mem, data);
        _fun = fun;
    }

    public Node ctrl() { return in(0); }
    public Node mem () { return in(1); }
    public Node expr() { return in(2); }
    public FunNode fun() { return _fun; }

    @Override
    public String label() { return "Return"; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        sb.append("return ");
        expr()._print0(sb, visited);
        return sb.append(";");
    }

    @Override
    public Type compute() {
        if( inProgress() ) return TypeTuple.RET; // In progress
        return TypeTuple.make(ctrl()._type,mem()._type,expr()._type);
    }

    @Override public Node idealize() {
        if( inProgress() ) return null;
        // If dead (cant be reached; infinite loop), kill the exit values
        if( ctrl()._type==Type.XCONTROL &&
            !(mem() instanceof ConstantNode && expr() instanceof ConstantNode) ) {
            Node top = new ConstantNode(Type.TOP).peephole();
            setDef(1,top);
            setDef(2,top);
            return this;
        }
        return null;
    }

    public boolean inProgress() {
        return ctrl().getClass() == RegionNode.class && ((RegionNode)ctrl()).inProgress();
    }

    // Gather parse-time return types for error reporting
    private Type mt = Type.TOP;
    private boolean ti=false, tf=false, tp=false, tn=false;

    // Add a return exit to the current parsing function
    void addReturn(Node ctrl, Node rmem, Node expr) {
        assert inProgress();

        // Gather parse-time return types for error reporting
        Type t = expr._type;
        mt = mt.meet(t);
        ti |= t instanceof TypeInteger x;
        tf |= t instanceof TypeFloat   x;
        tp |= t instanceof TypeMemPtr  x;
        tn |= t==Type.NIL;

        // Merge path into the One True Return
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

    @Override public String err() {
        return mt==Type.BOTTOM ? mixerr(ti,tf,tp,tn) : null;
    }

    static String mixerr( boolean ti, boolean tf, boolean tp, boolean tn ) {
        SB sb = new SB().p("No common type amongst ");
        if( ti ) sb.p("int and ");
        if( tf ) sb.p("f64 and ");
        if( tp ) sb.p("reference and ");
        if( !tp && tn ) sb.p("nil and ");
        return sb.unchar(5).toString();
    }
}
