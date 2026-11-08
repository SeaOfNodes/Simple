package com.seaofnodes.simple.codegen;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import static org.junit.Assert.*;
public class PrintRegTestSupport {
    public static void check() throws Exception {
        var code = new CodeGen("return 0;").parse();
        var alloc = new RegAlloc(code);
        var n = new ConstantNode(TypeInteger.ZERO);
        var a = new LRG((short)1,null);
        var b = new LRG((short)2,null);
        var c = new LRG((short)3,null);
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
    public static void checkConstantPool() throws Exception {
        var code = new CodeGen("return 0;").parse();
        code._phase = CodeGen.Phase.Encoding;
        var enc = new Encoding(code);
        var t = com.seaofnodes.simple.type.TypeStruct.make("PrintPool",false,
            com.seaofnodes.simple.type.Field.make("x",TypeInteger.ZERO,2,true));
        var n = new ConstantNode(TypeInteger.ZERO);
        enc.largeConstant(n,t,0,2);
        int size = t.size(); // Encoding lays out data before printing the pool.
        for( int i=0; i<size; i++ ) enc._cpool.write(0);
        // Layout queries are forbidden during printing, even if they would
        // happen to return a cached answer without changing the type.
        boolean[] printing = {false};
        var scalar = new com.seaofnodes.simple.type.Type(com.seaofnodes.simple.type.Type.BOTTOM._type) {
            @Override public int alignment() {
                assertFalse("alignment queried by printer",printing[0]);
                return 0;
            }
            @Override public int size() {
                assertFalse("size queried by printer",printing[0]);
                return 1;
            }
            @Override public String str() { return "LayoutProbe"; }
        };
        var scalarNode = new ConstantNode(TypeInteger.TRUE);
        enc._bigCons.put(scalarNode,new Encoding.Relo(scalarNode,scalar,(byte)0,(byte)2));
        enc._cpool.write(0);
        printing[0] = true;
        var offsets = com.seaofnodes.simple.type.TypeStruct.class.getDeclaredField("_offs");
        offsets.setAccessible(true);
        offsets.set(t,null); // Printer must not lazily rebuild the cached layout.
        var field = com.seaofnodes.simple.type.Type.class.getDeclaredField("VISIT");
        field.setAccessible(true);
        var visit = (java.util.Map<Object,Object>)field.get(null);
        var marker = new Object();
        visit.put(marker,t);
        var method = com.seaofnodes.simple.print.ASMPrinter.class.getDeclaredMethod(
            "printConstantPool",int.class,com.seaofnodes.simple.util.SB.class,
            com.seaofnodes.simple.util.BAOS.class,java.util.HashMap.class,boolean.class,String.class);
        method.setAccessible(true);
        try {
            var sb = new com.seaofnodes.simple.util.SB();
            method.invoke(null,0,sb,enc._cpool,enc._bigCons,true,"Constant Pool");
            assertTrue(sb.toString().contains("PrintPool"));
            assertTrue(sb.toString().contains("LayoutProbe"));
            assertNull(offsets.get(t));
            assertSame(t,visit.get(marker));
            assertEquals(1,visit.size());
        } finally { visit.clear(); }
    }
}
