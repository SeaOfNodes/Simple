package com.seaofnodes.simple.type;

import com.seaofnodes.simple.Utils;
import java.util.ArrayList;

/**
 * Represents an array type
 */
public class TypeArray extends TypeStruct {

    // An Array has a type and a size; the size is any integer type.
    // The type can be any scalar type, so not e.g. TypeMem.

    private TypeArray(TypeInteger len, int lenAlias, Type body, int bodyAlias) {
        super(TARRAY, "array", new Field[]{Field.make("#",lenAlias,len), Field.make("[]",bodyAlias,body)});
    }

    // All fields directly listed
    public static TypeArray make(TypeInteger len, int lenAlias, Type body, int bodyAlias) { return new TypeArray(len,lenAlias,body,bodyAlias).intern(); }
    public static TypeArray make(int lenAlias, Type body, int bodyAlias) { return make(TypeInteger.BOT,lenAlias,body,bodyAlias); }

    //public static final TypeArray BOT = make(Type.BOTTOM,TypeInteger.BOT);
    //public static final TypeArray FLTS = make(TypeFloat.BOT,TypeInteger.U32);
    //
    //public static void gather(ArrayList<Type> ts) { ts.add(BOT); ts.add(FLTS); }

    //@Override
    //public StringBuilder print(StringBuilder sb) {
    //    _type.print(sb).append("[");
    //    _len .print(sb).append("]");
    //    return sb;
    //}
    //
    //@Override public String str() {
    //    return _type.str() + "[" + _len.str() + "]";
    //}
}
