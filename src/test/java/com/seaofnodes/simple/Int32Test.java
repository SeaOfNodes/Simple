package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.cpus.arm.arm;
import com.seaofnodes.simple.node.cpus.riscv.riscv;
import com.seaofnodes.simple.parser.Parser;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.type.TypeStruct;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class Int32Test {
    @Test public void testAliasAndLayout() {
        new CodeGen("struct Pair { int x; int y; }; return new Pair;")
            .driver(CodeGen.Phase.TypeCheck);
        assertSame(TypeInteger.I32,Parser.INIT_TYPES.get("int"));
        var pair=(TypeStruct)Parser.TYPES.get("Test.Pair");
        assertSame(TypeInteger.I32,pair.field("x")._t);
        assertEquals(8,pair.size());
        assertEquals(4,Math.abs(pair.offset(1)-pair.offset(0)));
    }

    @Test public void testNarrowAssignmentsAndUpdates() {
        for(String source:new String[]{
            "int x=arg; return x;",
            "int x=0; x=arg; return x;",
            "struct S { int x; }; val s=new S; s.x=arg; return s.x;",
            "val a=new int[2]; a[1]=arg; return a[1];"
        }) {
            var code=new CodeGen(source).driver(CodeGen.Phase.TypeCheck);
            for(long x:new long[]{0,2147483647L,2147483648L,-2147483649L,Long.MAX_VALUE,Long.MIN_VALUE})
                assertEquals(source,Integer.toString((int)x),Eval2.eval(code,x));
        }
        var increment=new CodeGen("int x=arg; x++; return x;").driver(CodeGen.Phase.TypeCheck);
        assertEquals("-2147483648",Eval2.eval(increment,2147483647));
        var compound=new CodeGen("int x=arg; x+=2; return x;").driver(CodeGen.Phase.TypeCheck);
        assertEquals("-2147483647",Eval2.eval(compound,2147483647));
    }

    @Test public void testWideExpressionsAndInference() {
        for(String source:new String[]{
            "int x=2147483647; return x+1;",
            "var x=2147483647; x++; return x;",
            "val x=2147483648; return x;",
            "i64 x=2147483648; return x;"
        }) assertEquals(source,"2147483648",Eval2.eval(new CodeGen(source).driver(CodeGen.Phase.TypeCheck),0));
    }

    @Test public void testLoopBoundSurvivesWidening() {
        for(String type:new String[]{"int","u32"}) {
            String source="val f_noInline={"+type+" x -> x;}; "+type+" x=0; while(x<arg) x++; return f_noInline(x);";
            for(long seed:new long[]{1,123,456}) {
                var code=new CodeGen(source,seed,true).driver(CodeGen.Phase.TypeCheck);
                assertEquals("7",Eval2.eval(code,7));
            }
        }
        var high=new CodeGen("val f_noInline={u32 x -> x;}; u32 x=2147483646; while(x<arg) x++; return f_noInline(x);")
            .driver(CodeGen.Phase.TypeCheck);
        assertEquals("2147483650",Eval2.eval(high,2147483650L));
        var wrap=new CodeGen("val f_noInline={u32 x -> x;}; u32 x=4294967294; while(x) x++; return f_noInline(x);")
            .driver(CodeGen.Phase.TypeCheck);
        assertEquals("0",Eval2.eval(wrap,0));
    }

    @Test public void testNativeLayoutAndParameters() throws IOException {
        TestC.runC("""
            val int_echo={int x -> x;};
            val int_wrap={int x -> x++; return x;};
            val wide_echo={i64 x -> x;};
            val narrow_sum={i8 a,u8 b,i16 c,u16 d,int e,u32 f,int g,u32 h -> a+b+c+d+e+f+g+h;};
            struct Pair { int x; int y; };
            val int_pair={ -> val p=new Pair; p.x=-1; p.y=2147483647; return p; };
            val int_array={ -> val a=new int[3]; a[0]=-1; a[1]=2147483647; a[2]=-2147483648; return a; };
            ""","int32","ok\n",-1);
    }

    @Test public void testNarrowParameterBackends() throws IOException {
        long input=0xa5a5a5a580008080L;
        String[] types={"i8","u8","i16","u16","int","u32"};
        long[] expected={(byte)input,input&255,(short)input,input&65535,(int)input,input&0xffffffffL};
        for(int i=0; i<types.length; i++) {
            String source="val probe={"+types[i]+" x -> x;};";
            var a=TestArm64.build("probe",source,0,-1,false);
            a.regs[arm.X0]=input;
            assertEquals(0,a.step(1000));
            assertEquals(types[i],expected[i],a.regs[arm.X0]);
            var r=TestRisc5.build(source,"probe",0,-1,false);
            r.regs[riscv.A0]=input;
            assertEquals(0,r.step(1000));
            assertEquals(types[i],expected[i],r.regs[riscv.A0]);
        }
    }
}
