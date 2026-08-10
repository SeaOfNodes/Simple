package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.StaticData;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.parser.Parser;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Ary;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConstantArrayTest {
    private static StaticData.ObjectData array(CodeGen code, TypeInteger storage, int len) {
        var arrays=code._encoding._data.entries().stream().filter(o ->
            o.type instanceof TypeStruct ts && ts.isConAry() &&
            ts.field("[]")._t instanceof TypeConAry c && c.storageType()==storage && c.len()==len).toList();
        assertEquals(1,arrays.size());
        assertTrue(arrays.getFirst().readOnly);
        return arrays.getFirst();
    }

    @Test public void typedDataAndSharing() throws Exception {
        String source="""
            val a=new u16[~]{025755,65535,65538};
            val b=new u16[~]{025755,65535,65538};
            val fresh={ -> new u16[~]{025755,65535,65538}; };
            val main={ ->
                if(a!=b || a!=fresh() || a#!=3 || a[0]!=025755 || a[1]!=65535 || a[2]!=2) return 1;
                val bytes=new u8[~]{65,66,67};
                if(bytes!="ABC") return 2;
                val signed=new i16[~]{-32768,-1,32767};
                if(signed[0]!=-32768 || signed[1]!=-1 || signed[2]!=32767) return 3;
                val ints=new i32[~]{-2147483648,2147483647};
                if(ints[0]!=-2147483648 || ints[1]!=2147483647) return 4;
                val uints=new u32[~]{0,4294967295};
                if(uints[1]!=4294967295) return 5;
                val longs=new i64[~]{1<<63,0x7fffffffffffffff};
                if(longs[0]!=(1<<63) || longs[1]!=0x7fffffffffffffff) return 6;
                val empty=new u16[~]{};
                if(empty#!=0) return 7;
                return 0;
            };
            """;
        TestC.runSF(source,"constantArrayWidths","",-1);
        CodeGen code=new CodeGen(source).driver(CodeGen.Phase.Encoding,TestC.CPU_PORT,TestC.CALL_CONVENTION);
        StaticData.ObjectData obj=array(code,TypeInteger.U16,3);
        assertEquals("Test.a, Test.b",obj.label());
        assertEquals(12,obj.size); // Four-byte length, six data bytes, alignment padding.
        byte[] bytes=code._encoding._cpool.buf();
        assertEquals(3,bytes[obj.offset]);
        assertEquals(025755&255,bytes[obj.offset+4]&255);
        assertEquals(025755>>>8,bytes[obj.offset+5]&255);
        assertEquals(255,bytes[obj.offset+6]&255);
        assertEquals(255,bytes[obj.offset+7]&255);
        assertNull(code._stop.walk(n -> n instanceof NewNode ? n : null));
        com.seaofnodes.simple.codegen.PrintRegTestSupport.checkDataAliases(
            Path.of("build/objs/constantArrayWidths.o"),12,"constantArrayWidths.a","constantArrayWidths.b");
    }

    @Test public void arbitraryProducerUsesStaticData() {
        for(String cpu:new String[]{"x86_64_v2","arm","riscv"}) {
            CodeGen code=new CodeGen("u16[~]? unused=null; return 0;").parse();
            TypeStruct layout=(TypeStruct)Parser.TYPES.get("[]u16");
            TypeConAry contents=TypeConAry.make(TypeInteger.U16,new long[]{1,65535});
            TypeStruct array=TypeStruct.makeAry(layout._name,layout.field("#")._alias,layout.field("[]")._alias,contents);
            Type ptr=TypeMemPtr.make((byte)2,array,true).makeRO();
            // No initializer or allocation node: only compute() supplies the type.
            Node producer=new ReadOnlyNode(code.ZERO) {
                @Override public String label() { return "constant-array producer"; }
                @Override public Type compute() { return ptr; }
            };
            ReturnNode ret=code._stop.walk(n -> n instanceof ReturnNode r && r.fun().isClz() ? r : null);
            assertNotNull(ret);
            ret.setDef(2,producer.peephole());
            code.add(ret);
            code.driver(CodeGen.Phase.Encoding,cpu,"SystemV");
            StaticData.ObjectData obj=array(code,TypeInteger.U16,2);
            assertEquals(8,obj.size);
        }
    }

    @Test public void sideEffectsAndMutableArrays() throws Exception {
        CodeGen eval=new CodeGen("""
            val same_noInline={u16[~] a -> a==new u16[~]{1,2};};
            return same_noInline(new u16[~]{1,2});
            """).driver(CodeGen.Phase.TypeCheck);
        assertEquals("1",Eval2.eval(eval,0));
        TestC.runSF("""
            val next={int[] counter -> counter[0]++; return 7;};
            val main={ ->
                val count=new int[1];
                val a=new int[~]{next(count),next(count)};
                if(count[0]!=2 || a[0]!=7 || a[1]!=7) return 1;
                val b=new int[]{7,7}, c=new int[]{7,7};
                if(b==c) return 2;
                int[~] view=b;
                b[0]=9;
                if(view[0]!=9 || c[0]!=7) return 3;
                return 0;
            };
            ""","constantArrayEffects","",-1);
    }

    @Test public void importedTypedConstants() throws Exception {
        Path root=Path.of("build/objs/separate");
        Files.createDirectories(root);
        Path lib=Files.createTempDirectory(root,"constant-arrays-");
        new CodeGen(null,lib.toString(),null,"Table","""
            val small=new u16[~]{1,65535};
            val wide=new i64[~]{1<<63,0x7fffffffffffffff};
            val uints=new u32[~]{4294967295};
            val signed=new i32[~]{-2147483648};
            """,126,true,TypeInteger.BOT).driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
        var paths=new Ary<>(new String[]{lib.toString()});
        CodeGen code=new CodeGen(null,"build/objs",paths,"TableClient","""
            return Table.small[1]==65535 && Table.wide[0]==(1<<63) &&
                Table.wide[1]==0x7fffffffffffffff && Table.uints[0]==4294967295 && Table.signed[0]==-2147483648;
            """,126,true,TypeInteger.BOT).driver(CodeGen.Phase.TypeCheck);
        assertEquals("1",Eval2.eval(code,0));
    }
}
