package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.cpus.x86_64_v2.StoreX86;
import com.seaofnodes.simple.type.TypeInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class X86StoreTest {
    @Test public void testConstantStores() {
        check("u8",255,false);
        check("u16",65535,false);
        check("u32",Integer.MAX_VALUE,false);
        check("u32",2147483648L,true);
        check("u32",4294967295L,true);
        check("int",Integer.MIN_VALUE,false);
        check("int",Integer.MAX_VALUE,false);
        for( long value : new long[]{2147483648L,4294967295L,-2147483649L,
                                    0x123456789abcdefL,Long.MIN_VALUE,Long.MAX_VALUE} )
            check("int",value,true);
    }

    private static void check(String type, long value, boolean register) {
        // Returning the object keeps the Store; returning its field could fold.
        String literal = value==Long.MIN_VALUE ? "(-9223372036854775807-1)" : Long.toString(value);
        String src = "struct S { "+type+" x="+literal+"; }; return new S;";
        CodeGen code = new CodeGen(src).driver(CodeGen.Phase.Select,"x86_64_v2","SystemV");
        StoreX86 store = code._stop.walk(n -> n instanceof StoreX86 st && st._name.equals("x") ? st : null);
        assertNotNull(src,store);
        if( register ) {
            assertNotNull(src,store.in(4));
            assertEquals(src,value,((TypeInteger)store.in(4)._type).value());
        } else assertNull(src,store.in(4));
    }
}
