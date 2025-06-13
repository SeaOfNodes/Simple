package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import org.junit.Test;
import com.seaofnodes.simple.type.TypeInteger;
import static org.junit.Assert.assertEquals;

public class IncompleteTypesTest {
    @Test public void testLoadFromUnsettledLoopPhi() {
        String src = """
            struct C { int x; };
            val f={int n -> return new C { while(n--) if(n) x=1; }; };
            return f(arg).x;
            """;
        for( long seed : new long[]{123,456,789} ) {
            var code = new CodeGen(src,TypeInteger.BOT,seed,true).driver(CodeGen.Phase.TypeCheck);
            for( int arg=0; arg<6; arg++ )
                assertEquals("seed="+seed+" arg="+arg,arg<2 ? "0" : "1",Eval2.eval(code,arg));
        }
    }

    @Test public void testForwardFieldAndNumericMode() {
        // At the addition, neither the field's type nor its offset is known.
        String src = """
            val add = { Later p -> return p.x + 1; };
            struct Later { flt x; };
            return add(new Later { x = 2.5; });
            """;
        var code = new CodeGen(src).driver(CodeGen.Phase.TypeCheck);
        assertEquals("3.5",Eval2.eval(code,0));
    }

    @Test public void testForwardStore() {
        // Resolve the field's alias and integer width after its declaration.
        String src = """
            val set = { !Later p, int n -> p.x = n; return p.x; };
            struct Later { u8 x; };
            return set(new Later,258);
            """;
        var code = new CodeGen(src).driver(CodeGen.Phase.TypeCheck);
        assertEquals("2",Eval2.eval(code,0));
        for( String update : new String[]{"p.x += n","p.x++"} ) {
            String updates = "val set = { !Later p, int n -> p.x=255; return "+update+"; }; " +
                "struct Later { u8 x; }; return set(new Later,3);";
            // Seeds 2 and 13 let the Load resolve before the symbolic Store.
            // It must wait for that Store's alias, not read an earlier Store.
            for( long seed : new long[]{2,13,123} ) {
                var result = new CodeGen(updates,TypeInteger.BOT,seed,true).driver(CodeGen.Phase.TypeCheck);
                assertEquals(update.equals("p.x++") ? "255" : "2",Eval2.eval(result,0));
            }
        }
    }
}
