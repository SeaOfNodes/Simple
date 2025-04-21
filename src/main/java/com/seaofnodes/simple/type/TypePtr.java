package com.seaofnodes.simple.type;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import java.util.ArrayList;

/**
 * Represents a Scalar; a single register-sized value.
 */
public class TypePtr extends TypeNil {

    private TypePtr(byte nil) { super(TPTR,nil);  }

    // An abstract pointer, pointing to either a Struct or an Array.
    // Can also be null or not, so 4 choices {TOP,BOT} x {nil,not}
    public static TypePtr XPTR = new TypePtr((byte)0).intern();
    public static TypePtr XNPTR= new TypePtr((byte)1).intern();
    public static TypePtr NPTR = new TypePtr((byte)2).intern();
    public static TypePtr PTR  = new TypePtr((byte)3).intern();
    private static final TypePtr[] PTRS = new TypePtr[]{XPTR,XNPTR,NPTR,PTR};

    public static void gather(ArrayList<Type> ts) { ts.add(PTR); ts.add(NPTR); }

    TypeNil makeFrom(byte nil) { throw Utils.TODO(); }

    @Override public TypeNil xmeet(Type t) {
        TypePtr that = (TypePtr) t;
        return PTRS[xmeet0(that)];
    }

    @Override public TypePtr dual() { return PTRS[dual0()]; }

    // High scalar loses, low scalar wins
    @Override TypeNil nmeet(TypeNil tn) {
        if( _nil==0 ) return tn; // High scalar loses
        if( _nil==1 ) return tn.makeFrom(xmeet0(tn)); // High scalar loses
        if( _nil==2 ) return tn._nil==3 ? PTR : NPTR; // Low scalar wins
        return PTR; // this
    }


    // RHS is  NIL
    @Override Type meet0() { return isHigh() ? NIL : PTR; }
    // RHS is XNIL
    // 0->xscalar, 1->nscalar, 2->nscalar, 3->scalar
    @Override Type meetX() { return _nil==0 ? XNIL : (_nil==3 ? PTR : NPTR); }

    @Override public TypePtr glb(boolean mem) { return PTR; }

    private static final String[] STRS = new String[]{"~ptr","~nptr","nptr","ptr"};
    @Override public String str() { return STRS[_nil]; }
    @Override public SB print(SB sb) { return sb.p(str()); }
}
