package com.seaofnodes.simple.node;

import com.seaofnodes.simple.IterPeeps;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.type.TypeTuple;
import java.util.BitSet;
import java.util.HashSet;

public class IfNode extends CFGNode implements MultiNode {

    public IfNode(Node ctrl, Node pred) {
        super(ctrl, pred);
        IterPeeps.add(this);    // Because idoms are complex, just add it
    }

    @Override
    public String label() { return "If"; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        sb.append("if( ");
        return in(1)._print0(sb, visited).append(" )");
    }

    @Override public boolean isMultiHead() { return true; }

    public Node ctrl() { return in(0); }
    public Node pred() { return in(1); }

    @Override
    public Type compute() {
        // If the If node is not reachable then neither is any following Proj
        if (ctrl()._type != Type.CONTROL && ctrl()._type != Type.BOTTOM )
            return TypeTuple.IF_NEITHER;
        Node pred = pred();
        Type t = pred._type;
        // High types mean NEITHER side is reachable.
        // Wait until the type falls to decide which way to go.
        if( t == Type.TOP || t == TypeInteger.TOP )
            return TypeTuple.IF_NEITHER;
        // If constant is 0 then false branch is reachable
        // Else true branch is reachable
        if (t instanceof TypeInteger ti && ti.isConstant())
            return ti==TypeInteger.ZERO ? TypeTuple.IF_FALSE : TypeTuple.IF_TRUE;

        return TypeTuple.IF_BOTH;
    }

    @Override
    public Node idealize() {
        // Hunt up the immediate dominator tree.  If we find an identical if
        // test on either the true or false branch, that side wins.
        if( !pred()._type.isHighOrConst() )
            for( CFGNode dom = idom(), prior=this; dom!=null;  prior = dom, dom = dom.idom() )
                if( dom.addDep(this) instanceof IfNode iff && iff.pred().addDep(this)==pred() && prior instanceof CProjNode prj ) {
                    setDef(1,new ConstantNode(TypeInteger.make(true,prj._idx==0?1:0)).peephole());
                    return this;
                }
        return null;
    }

    @Override void _walkUnreach( BitSet visit, HashSet<CFGNode> unreach ) {
        for( Node proj : _outputs )
            if( ((CProjNode)proj)._loopDepth == 0 )
                unreach.add((CProjNode)proj);
        super._walkUnreach(visit,unreach);
    }

    @Override public Node getBlockStart() { return ctrl().getBlockStart(); }
}
