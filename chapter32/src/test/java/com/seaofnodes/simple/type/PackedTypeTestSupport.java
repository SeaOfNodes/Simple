package com.seaofnodes.simple.type;

import com.seaofnodes.simple.util.BAOS;
import java.util.HashMap;
import static org.junit.Assert.*;

public class PackedTypeTestSupport {
    public static void singleton(int id) {
        Type.TAGOFFS();
        TypeFunPtr fun=TypeFunPtr.make((byte)2,true,new Type[0],TypeInteger.BOT,XInt.make(id));
        BAOS out=new BAOS();
        fun.packed(out,new HashMap<>());
        assertEquals(id<=254 ? 2 : 4,out.size());
        BAOS in=new BAOS(out.toByteArray());
        TypeFunPtr copy=(TypeFunPtr)Type.packed(in,new String[]{""});
        assertEquals(id,copy.fidx());
        assertEquals(0,copy.nargs());
        assertEquals(out.size(),in.size());
    }
}
