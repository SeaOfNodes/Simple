package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.type.*;
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
        if( t0.isHigh() ) return TypeInteger.BOOL.dual();
        switch( t0 ) {
        case TypeInteger i0:  return i0._max < 0 || i0._min > 0 ? TypeInteger.FALSE : (i0==TypeInteger.ZERO ? TypeInteger.TRUE : TypeInteger.BOT);
        case TypeFloat   i0:  return i0.isConstant() ? TypeInteger.constant(i0.value()==0 ? 1 : 0) : i0;
        case TypeMemPtr p0:
            // top->top, bot->bot, null->1, *void->0, not-null ptr->0, ptr/nil->bot
            // If input in null then true
            // If input is not null ptr then false
            if( p0 == TypeMemPtr.TOP  )    return TypeInteger.TOP;
            if( p0 == TypeMemPtr.NULLPTR ) return TypeInteger.constant(1);
            if( !p0._nil )                 return TypeInteger.constant(0);
            return TypeInteger.BOT;
        case Type t:
            if( t0.getClass() != Type.class )
                // Only doing NOT on ints and ptrs
                throw Utils.TODO();
            return t0==Type.TOP ? Type.TOP : Type.BOTTOM;
        }
    }

    @Override
    public Node idealize() { return null; }
}
