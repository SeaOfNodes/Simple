package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.*;
import java.util.BitSet;

public class IfNode extends CFGNode implements MultiNode {

    public IfNode(Node ctrl, Node pred) {
        super(ctrl, pred);
        CodeGen.CODE.add(this); // Because idoms are complex, just add it
    }
    public IfNode(IfNode iff) { super(iff); }

    @Override
    public String label() { return "If"; }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        sb.append("if( ");
        if( in(1)==null ) sb.append("never");
        else in(1)._print0(sb, visited);
        return sb.append(" )");
    }

    public Node ctrl() { return in(0); }
    public Node pred() { return in(1); }

    // No one unique control follows
    @Override public CFGNode uctrl() { return null; }

    @Override
    public Type compute() {
        // If the If node is not reachable then neither is any following Proj
        if (ctrl()._type != Type.CONTROL && ctrl()._type != Type.BOTTOM )
            return TypeTuple.IF_NEITHER;
        Node pred = pred();
        Type t = pred._type;
        // High types mean NEITHER side is reachable.
        // Wait until the type falls to decide which way to go.
        if( t.isHigh() )
            return TypeTuple.IF_NEITHER;
        // If constant is 0 then false branch is reachable
        // Else true branch is reachable
        if( t.isConstant() )
            return (t==Type.NIL || t==TypeInteger.ZERO || (t instanceof TypeFunPtr tfp && tfp._fidxs==0) ) ? TypeTuple.IF_FALSE : TypeTuple.IF_TRUE;
        // If adding a zero makes a difference, the predicate must not have a zero/null
        if( !t.makeZero().isa(t) )
            return TypeTuple.IF_TRUE;

        return TypeTuple.IF_BOTH;
    }

    @Override
    public Node idealize() {
        // Hunt up the immediate dominator tree.  If we find an identical if
        // test on either the true or false branch, that side wins.
        if( !pred()._type.isHighOrConst() )
            for( CFGNode dom = idom(), prior=this; dom!=null;  prior = dom, dom = dom.idom() )
                if( addDep(dom) instanceof IfNode iff && addDep(iff.pred())==pred() && prior instanceof CProjNode prj ) {
                    setDef(1,con( prj._idx==0 ? 1 : 0 ));
                    return this;
                }
        return null;
    }

    // MachNode variants need to support this and negate the conditional test.
    // The following CProjs will be inverted by the caller.
    public void negate() { throw Utils.TODO(); }

    // Negate the sense of a test
    public static String negate( String bop ) {
        return switch( bop ) {
        case "<"  -> ">=";
        case "<=" -> ">" ;
        case "==" -> "!=";
        case "!=" -> "==";
        case ">"  -> "<=";
        case ">=" -> "<" ;
        default -> throw Utils.TODO();
        };
    }

    // Swap compare operands
    public static String swap( String bop ) {
        return switch( bop ) {
        case "<"  -> ">" ;
        case "<=" -> ">=";
        case "==" -> "!=";
        case "!=" -> "==";
        case ">"  -> "<" ;
        case ">=" -> "<=";
        default -> throw Utils.TODO();
        };
    }
}
