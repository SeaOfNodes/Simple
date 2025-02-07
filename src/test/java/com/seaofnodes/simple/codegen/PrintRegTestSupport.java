package com.seaofnodes.simple.codegen;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import static org.junit.Assert.*;
public class PrintRegTestSupport {
    public static void check() throws Exception {
        var code = new CodeGen("return 0;").parse();
        var alloc = new RegAlloc(code);
        var n = new ConstantNode(TypeInteger.ZERO);
        var a = new LRG((short)1);
        var b = new LRG((short)2);
        var c = new LRG((short)3);
        a._reg = -1;
        c._leader = b;
        b._leader = a;
        var field = RegAlloc.class.getDeclaredField("_lrgs");
        field.setAccessible(true);
        var map = (java.util.Map<Node,LRG>)field.get(alloc);
        map.put(n,c);
        assertEquals("V1",alloc.reg(n));
        assertEquals(-1,alloc.regnum(n));
        assertSame(c,map.get(n));
        assertSame(b,c._leader);
        assertSame(a,b._leader);
    }
}
