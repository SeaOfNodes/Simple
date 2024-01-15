package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.type.TypeTuple;

import java.util.BitSet;

public class IfNode extends MultiNode {

    public IfNode(Node ctrl, Node pred) {
        super(ctrl, pred);
    }

    @Override
    public String label() { return "If"; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        sb.append("if( ");
        return in(1)._print0(sb, visited).append(" )");
    }

    @Override public boolean isCFG() { return true; }
    @Override public boolean isMultiHead() { return true; }

    public Node ctrl() { return in(0); }
    public Node pred() { return in(1); }

    @Override
    public Type compute() {
        // If the If node is not reachable then neither is any following Proj
        if (ctrl()._type != Type.CONTROL && ctrl()._type != Type.BOTTOM )
            return TypeTuple.IF_NEITHER;
        // If constant is 0 then false branch is reachable
        // Else true branch is reachable
        if (pred()._type instanceof TypeInteger ti && ti.isConstant()) {
            if (ti.value() == 0)   return TypeTuple.IF_FALSE;
            else                   return TypeTuple.IF_TRUE;
        }

        // Hunt up the immediate dominator tree.  If we find an identical if
        // test on either the true or false branch, then this test matches.
        for( Node dom = idom(), prior=this; dom!=null;  prior=dom, dom = dom.idom() )
          if( dom instanceof IfNode iff && iff.pred()==pred() )
            return prior instanceof ProjNode proj
              // Repeated test, dominated on one side.  Test result is the same.
              ? (proj._idx==0 ? TypeTuple.IF_TRUE : TypeTuple.IF_FALSE)
              : dom._type;      // Repeated test not dominated on one side
        
        return TypeTuple.IF_BOTH;
    }

    @Override
    public Node idealize() {
        return null;
    }
}
