package com.seaofnodes.simple.codegen;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import static org.junit.Assert.*;
public class PrintRegTestSupport {
    public static void checkDataAliases(java.nio.file.Path file, int size, String... names) {
        ElfReader elf=ElfReader.load(file.toFile(),null);
        assertNotNull(elf);
        ElfReader.Section symbols=java.util.Arrays.stream(elf._sections)
            .filter(s -> ".symtab".equals(s._name)).findFirst().orElseThrow();
        ElfReader.Section strings=elf._sections[symbols._link];
        var bytes=java.nio.ByteBuffer.wrap(elf._buf).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        var wanted=new java.util.HashSet<>(java.util.Arrays.asList(names));
        Long address=null;
        for( int i=0; i<symbols._size/symbols._esize; i++ ) {
            int off=(int)symbols._offset+i*symbols._esize;
            String name=strings.str(bytes.getInt(off));
            if( !wanted.remove(name) ) continue;
            assertTrue("Debug aliases precede global symbols",i<symbols._info);
            assertEquals("Local object symbol",1,bytes.get(off+4)&255);
            assertEquals(".rodata",elf._sections[bytes.getShort(off+6)&65535]._name);
            assertEquals(size,bytes.getLong(off+16));
            long value=bytes.getLong(off+8);
            if( address!=null ) assertEquals("Aliases share storage",address.longValue(),value);
            address=value;
        }
        assertTrue("Missing debug symbols: "+wanted,wanted.isEmpty());
    }

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
            com.seaofnodes.simple.util.BAOS.class,java.util.Map.class,boolean.class,String.class);
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
