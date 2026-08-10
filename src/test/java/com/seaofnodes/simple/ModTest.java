package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegAllocTestSupport.CheckedCodeGen;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.isa.eval.EvalArm64;
import com.seaofnodes.isa.eval.EvalRisc5;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.Ary;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

public class ModTest {
    private static String literal(long x) {
        return x==Long.MIN_VALUE ? "(-9223372036854775807-1)" : Long.toString(x);
    }
    @Test public void integers() {
        long[] values={Long.MIN_VALUE,-17,-1,0,1,17,Long.MAX_VALUE};
        long[] divisors={Long.MIN_VALUE,-5,-1,0,1,5,Long.MAX_VALUE};
        for( long y : divisors ) {
            for( long x : values ) {
                CodeGen code=new CodeGen("return arg%"+literal(y)+";");
                String expected=Long.toString(y==0 ? 0 : x%y);
                try {
                    assertEquals(x+"%"+y,expected,Eval2.eval(code,x));
                    assertEquals(expected,Eval2.eval(new CodeGen("return "+literal(x)+"%"+literal(y)+";"),0));
                } catch(Throwable e) { throw new AssertionError(x+"%"+y,e); }
            }
        }
    }
    @Test public void precedenceAndIdentifiers() {
        assertEquals("6",Eval2.eval(new CodeGen("return 20%6*3;"),0));
        assertEquals("2",Eval2.eval(new CodeGen("return 20/3%4;"),0));
        assertEquals("21",Eval2.eval(new CodeGen("int mod=20; return mod+7%3;"),0));
    }
    @Test public void compoundAssignment() {
        assertEquals("-2",Eval2.eval(new CodeGen("int x=arg; x %= 5; return x;"),-17));
        assertEquals("0",Eval2.eval(new CodeGen("int x=17; x %= arg; return x;"),0));
        assertEquals("1.5",Eval2.eval(new CodeGen("f64 x=7.5; x %= 2; return x;"),0));
        assertEquals("21",Eval2.eval(new CodeGen("""
            val a=new int[1]; a[0]=17;
            int i=0; a[i++] %= 5;
            return a[0]*10+i;
            """),0));
    }
    @Test public void floats() {
        for( double y : new double[]{-5.5,-1.0,0.0,1.0,5.5} ) {
            CodeGen code=new CodeGen("return (arg+0.0)%"+y+";");
            for( long x : new long[]{-17,-1,0,1,17} )
                assertEquals(Double.isNaN(x%y) ? "Double.NaN" : Double.toString(x%y),Eval2.eval(code,x));
        }
        assertEquals("-0.0",Eval2.eval(new CodeGen("return -10.0%5.0;"),0));
        assertEquals("1.5",Eval2.eval(new CodeGen("return 7.5%2;"),0));
    }
    @Test public void zeroAndDeadBranches() {
        assertEquals("0",Eval2.eval(new CodeGen("return 1%arg;"),0));
        assertEquals("0",Eval2.eval(new CodeGen("return 1%0;"),0));
        CodeGen discarded=new CodeGen("1%arg; return 42;").driver(CodeGen.Phase.TypeCheck);
        assertNull(discarded._stop.walk(n -> n instanceof ModNode || n instanceof CallNode ? n : null));
        assertEquals("42",Eval2.eval(discarded,0));
        CodeGen common=new CodeGen("return (arg%7)-(arg%7);").driver(CodeGen.Phase.TypeCheck);
        assertNull(common._stop.walk(n -> n instanceof ModNode || n instanceof CallNode ? n : null));
        assertEquals("0",Eval2.eval(common,17));
        assertEquals("42",Eval2.eval(new CodeGen("if(arg) return 1%0; return 42;"),0));
        assertEquals("42",Eval2.eval(new CodeGen("if(false) return 1%0; return 42;"),0));
    }
    @Test public void operandEffects() {
        assertEquals("709",Eval2.eval(new CodeGen("""
            int x=7;
            int r=x++%++x;
            return r*100+x;
            """),0));
        assertEquals("42",Eval2.eval(new CodeGen("""
            val a=new int[1];
            val next={ int[] a -> a[0]++; return a[0]+10; };
            int r=next(a)%next(a);
            return r*2+a[0]*10;
            """),0));
    }
    @Test public void loopsAndRecursiveModes() {
        String source="""
            int sum=0;
            for(int i=0;i<arg;i++) sum=sum+(i+1)%5;
            return sum;
            """;
        assertEquals("40",Eval2.eval(new CodeGen(source),20));
        assertEquals("41",Eval2.eval(new CodeGen(source),21));
        assertEquals("1.5",Eval2.eval(new CodeGen("""
            val f={ i64 n -> n==0 ? 7.5 : f(n-1)%2; };
            return f(arg);
            """),3));
    }
    @Test public void typeErrors() {
        for( String source : new String[]{"return \"abc\"%2;", "return 2%new int[1];"} ) {
            try {
                new CodeGen(source).driver(CodeGen.Phase.TypeCheck);
                fail("% needs numeric operands: "+source);
            } catch(RuntimeException expected) {
                assertTrue(expected.getMessage(),expected.getMessage().startsWith("Cannot"));
            }
        }
    }
    @Test public void allBackends() {
        for( String cpu : new String[]{"x86_64_v2","arm","riscv"} )
            new CheckedCodeGen("return arg%7 + (arg+0.5)%2.5;")
                .driver(CodeGen.Phase.Encoding,cpu,"SystemV");
    }
    @Test public void nativeRemainders() throws Exception {
        TestC.runSYS("""
            val rem_noInline={ i64 x,i64 y -> x%y; };
            val frem_noInline={ f64 x,f64 y -> x%y; };
            val mixed_noInline={ f64 x,f64 y,i64 i -> x*13+(x%y)+y*17+i; };
            val main={ ->
                if(rem_noInline(-17,5)!=-2) return 1;
                if(rem_noInline(17,-5)!=2) return 2;
                if(rem_noInline(sys.integer.MIN_I64,-1)!=0) return 3;
                if(rem_noInline(sys.integer.MIN_I64,3)!=-2) return 4;
                if(frem_noInline(-7.5,2.0)!=-1.5) return 5;
                if(sys.math.f64Bits(frem_noInline(-10.0,5.0))!=sys.integer.MIN_I64) return 6;
                sys.libc.set_errno(123);
                f64 nan=frem_noInline(1.0,0.0);
                if(nan==nan) return 7;
                if(sys.libc.errno()!=123) return 8;
                if(mixed_noInline(7.5,2.0,31)!=164.0) return 9;
                return 0;
            };
            ""","modNative","",-1);
    }
    @Test public void importedRemainders() throws Exception {
        Path dir=Files.createTempDirectory(Path.of("build/objs"),"mod-import-");
        new CodeGen(null,dir.toString(),null,"Remainders","val rem_noInline={ i64 x -> x%7; };",126,true,TypeInteger.BOT)
            .driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
        var paths=new Ary<>(new String[]{dir.toString()});
        CodeGen client=new CodeGen(null,dir.toString(),paths,"Client","return Remainders.rem_noInline(arg);",126,true,TypeInteger.BOT);
        assertEquals("-3",Eval2.eval(client,-17));
    }
    @Test public void nativeDivisionAndRemainderEdges() throws Exception {
        TestC.runSYS(integerEdges(),"modDivEdges","",-1);
    }
    @Test public void emulatedDivisionAndRemainderEdges() throws Exception {
        long[] values={Long.MIN_VALUE,Long.MIN_VALUE+1,-17,-1,0,1,17,Long.MAX_VALUE};
        for( String op : new String[]{"%","/"} ) {
            String source="val main={ i64 x,i64 y -> x "+op+" y; };";
            EvalArm64 image=TestArm64.build("main",source,0,-1,false);
            for( long x : values ) for( long y : values ) {
                EvalArm64 arm=new EvalArm64(image._buf,1<<16);
                arm._pc=image._pc;
                arm.regs[0]=x; arm.regs[1]=y;
                assertEquals(0,arm.step(1000));
                assertEquals(x+" "+op+" "+y,y==0 ? 0 : op.equals("%") ? x%y : x/y,arm.regs[0]);
            }
            // The shared RISC-V emulator's DIV/REM still operate on 32-bit values.
            EvalRisc5 rimage=TestRisc5.build(source,"main",0,-1,false);
            for( long x : new long[]{-17,-1,0,1,17} ) for( long y : new long[]{-17,-1,0,1,17} ) {
                EvalRisc5 riscv=new EvalRisc5(rimage._buf,1<<16);
                riscv._pc=rimage._pc;
                riscv.regs[10]=x; riscv.regs[11]=y;
                assertEquals(0,riscv.step(1000));
                assertEquals(x+" "+op+" "+y,y==0 ? 0 : op.equals("%") ? x%y : x/y,riscv.regs[10]);
            }
        }
    }
    private static String integerEdges() {
        return integerEdges(new long[]{Long.MIN_VALUE,Long.MIN_VALUE+1,-17,-5,-1,0,1,5,17,Long.MAX_VALUE});
    }
    private static String integerEdges(long[] values) {
        StringBuilder source=new StringBuilder("""
            val rem_noInline={ i64 x,i64 y -> x%y; };
            val div_noInline={ i64 x,i64 y -> x/y; };
            val main={ ->
            """);
        int failure=1;
        for( long x : values )
            for( long y : values ) {
                source.append("if(rem_noInline(").append(literal(x)).append(',').append(literal(y))
                    .append(")!=").append(literal(y==0 ? 0 : x%y)).append(") return ").append(failure++).append(';');
                source.append("if(div_noInline(").append(literal(x)).append(',').append(literal(y))
                    .append(")!=").append(literal(y==0 ? 0 : x/y)).append(") return ").append(failure++).append(';');
            }
        return source.append("return 0; };").toString();
    }
    @Test public void nativeFloatingEdges() throws Exception {
        double[][] cases={{Double.MAX_VALUE,0.1},{-Double.MAX_VALUE,0.1},
            {Double.MIN_VALUE,1.0},{1.0,Double.POSITIVE_INFINITY},
            {-0.0,Double.NEGATIVE_INFINITY},{-5.0,2.5},
            {Double.POSITIVE_INFINITY,1.0},{Double.NaN,2.0},{2.0,Double.NaN},{1.0,-0.0}};
        StringBuilder source=new StringBuilder("val rem_noInline={ f64 x,f64 y -> x%y; }; val main={ -> ");
        for( int i=0; i<cases.length; i++ ) {
            double x=cases[i][0],y=cases[i][1],r=x%y;
            source.append("f64 r").append(i).append("=rem_noInline(sys.math.f64FromBits(")
                .append(literal(Double.doubleToRawLongBits(x))).append("),sys.math.f64FromBits(")
                .append(literal(Double.doubleToRawLongBits(y))).append(")); if(");
            if( Double.isNaN(r) ) source.append('r').append(i).append("==r").append(i);
            else source.append("sys.math.f64Bits(r").append(i).append(")!=").append(literal(Double.doubleToRawLongBits(r)));
            source.append(") return ").append(i+1).append(";");
        }
        TestC.runSYS(source.append("return 0; };").toString(),"modFloatEdges","",-1);
    }
}
