package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.BAOS;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import org.junit.Test;
import static org.junit.Assert.*;

public class AssemblyDumpTest {
    @Test public void libraryUnitsAndGlyphs() throws Exception {
        Path root=Path.of("build/objs/assembly-dump");
        Files.createDirectories(root);
        Path build=Files.createTempDirectory(root,"sys-");
        CodeGen code=new CodeGen("src/main/smp",build.toString(),null,"sys",null,123,true,TypeInteger.BOT)
            .driver(CodeGen.Phase.Serialize,TestC.CPU_PORT,TestC.CALL_CONVENTION)
            .driver(CodeGen.Phase.Encoding,TestC.CPU_PORT,TestC.CALL_CONVENTION);
        var entry=code._encoding;
        String before=code.asm();
        assertSame("Dumping another unit must not change the active encoding",entry,code._encoding);
        for( var unit : code._compunits.values() )
            if( unit._src!=null ) {
                String heading="=== Compilation unit: "+unit._cname+" ===";
                assertEquals("Exactly one heading for "+unit._cname,2,before.split(heading,-1).length);
            }
        int start=before.indexOf("=== Compilation unit: sys.draw.Canvas ===");
        assertTrue(start>=0);
        int end=before.indexOf("=== Compilation unit:",start+1);
        String canvas=before.substring(start,end<0 ? before.length() : end);
        assertTrue(canvas.contains("Constant Pool"));
        assertTrue(canvas.contains("sys.draw.Canvas._glyphs: []u16"));
        TypeMemPtr ptr=(TypeMemPtr)code._compunits.get("sys/draw/Canvas")._clz.field("_glyphs")._t;
        TypeConAry glyphs=(TypeConAry)ptr._obj.field("[]")._t;
        assertEquals(96,glyphs.len());
        BAOS bytes=new BAOS();
        glyphs.write(bytes);
        assertTrue("The complete font follows its u32 length header",
                   canvas.contains("60000000"+HexFormat.of().formatHex(bytes.toByteArray())));
        code.exportELF(false,false);
        com.seaofnodes.simple.codegen.PrintRegTestSupport.checkDataAliases(
            build.resolve("sys/draw/Canvas.o"),196,"sys.draw.Canvas._glyphs");
        assertSame(entry,code._encoding);
        assertEquals("Export must not truncate the dump to its entry unit",before,code.asm());
        Files.writeString(build.resolve("sys.asm"),before);
    }

    @Test public void combinedEncodingAndEarlierPhase() {
        for( String cpu : new String[]{"x86_64_v2","arm","riscv"} ) {
            CodeGen code=new CodeGen("val a=new u16[~]{5,6,7}; return a[arg&1];")
                .driver(CodeGen.Phase.RegAlloc,cpu,"SystemV");
            assertFalse(code.asm().contains("Constant Pool"));
            code.driver(cpu,"SystemV",true,false);
            String dump=code.asm();
            assertFalse(dump.contains("=== Compilation unit:"));
            assertTrue(dump.contains("Constant Pool"));
            assertTrue(dump.contains("03000000050006000700"));
        }
    }
}
