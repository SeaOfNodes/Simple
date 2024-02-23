package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;

import java.util.BitSet;

public class NotNode extends IntDataNode {
    public NotNode(Node in) { super(null, in); }

    @Override public String label() { return "Not"; }
  
    @Override public String glabel() { return "!"; }
  
    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        in(1)._print0(sb.append("(!"), visited);
        return sb.append(")");
    }
  
    @Override
    public Type intCompute(TypeInteger i1, TypeInteger i2) {
        if( i1._is_con )
            return TypeInteger.constant( i1._con==0 ? 1 : 0 );
        return TypeInteger.BOT;
    }

    @Override
    public Node idealize() { return null; }
}
