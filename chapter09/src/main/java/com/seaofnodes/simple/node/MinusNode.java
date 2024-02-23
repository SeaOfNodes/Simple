package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;

import java.util.BitSet;

public class MinusNode extends IntDataNode {
    public MinusNode(Node in) { super(null, in); }

    @Override public String label() { return "Minus"; }
  
    @Override public String glabel() { return "-"; }
  
    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        in(1)._print0(sb.append("(-"), visited);
        return sb.append(")");
    }
  
    @Override
    public Type intCompute(TypeInteger i1, TypeInteger i2) {
        if( i1._is_con )
            return TypeInteger.constant( -i1._con );
        return TypeInteger.BOT;
    }

    @Override
    public Node idealize() { return null; }
}
