package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.type.TypeMemPtr;
import java.util.BitSet;

public class NotNode extends Node {
    public NotNode(Node in) { super(null, in); }

    @Override public String label() { return "Not"; }

    @Override public String glabel() { return "!"; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        in(1)._print0(sb.append("(!"), visited);
        return sb.append(")");
    }

    @Override
    public Type compute() {
        Type t0 = in(1)._type;
        if( t0 instanceof TypeInteger i0 )
            return i0.isConstant() ? TypeInteger.constant(i0.value()==0 ? 1 : 0) : i0;
        if( t0 instanceof TypeMemPtr p0 ) {
            // top->top, bot->bot, null->1, void->0, ptr/NOT->0, ptr/nil->bot
            if( p0 == TypeMemPtr.TOP  ) return TypeInteger.TOP;
            if( p0 == TypeMemPtr.NULL ) return TypeInteger.constant(1);
            if( !p0._nil )              return TypeInteger.constant(0);
            return TypeInteger.BOT;
        }
        // Only doing NOT on ints and ptrs
        throw Utils.TODO();
    }

    @Override
    public Node idealize() { return null; }
}
