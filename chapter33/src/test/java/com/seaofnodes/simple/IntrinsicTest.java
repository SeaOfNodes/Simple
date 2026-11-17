package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.simple.codegen.RegAllocTestSupport.CheckedCodeGen;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

public class IntrinsicTest {
    private static final String DECL="""
        { f64 -> i64 } simple_f64_bits="C";
        { i64 -> f64 } simple_f64_from_bits="C";
        """;

    @Test public void constantsAndRawNaNs() {
        long[] patterns={0,Long.MIN_VALUE,1,0x0010000000000000L,0x3ff0000000000000L,
            0x7fefffffffffffffL,0x7ff0000000000000L,0xfff0000000000000L,
            0x7ff8000000000001L,0x7ff8000000000002L,0xfff800000000002aL,0x7ff0000000000001L};
        for(long bits:patterns) {
            String literal=bits==Long.MIN_VALUE ? "(-9223372036854775807-1)" : Long.toString(bits);
            CodeGen code=new CodeGen(DECL+"return simple_f64_bits(simple_f64_from_bits("+literal+"));")
                .driver(CodeGen.Phase.TypeCheck);
            assertNull(code._stop.walk(n -> n instanceof CallNode ? n : null));
            assertEquals(Long.toString(bits),Eval2.eval(code,0));
        }
        assertNotSame(TypeFloat.constant(Double.longBitsToDouble(patterns[8])),
                      TypeFloat.constant(Double.longBitsToDouble(patterns[9])));
    }

    @Test public void runtimeNodesAndMemory() {
        CodeGen code=new CodeGen(DECL+"""
            val bits=simple_f64_bits;
            val a=new f64[1];
            a[0]=simple_f64_from_bits(arg);
            if( arg==0 ) a[0]=-0.0;
            return bits(a[0]);
            """).driver(CodeGen.Phase.TypeCheck);
        assertNotNull(code._stop.walk(n -> n instanceof Intrinsic.BitCastNode ? n : null));
        assertNull(code._stop.walk(n -> n instanceof CallNode ? n : null));
        assertEquals(Long.toString(Long.MIN_VALUE),Eval2.eval(code,0));
        assertEquals("9221120237041090602",Eval2.eval(code,0x7ff800000000002aL));
    }

    @Test public void ordinaryFunctionWithSameName() {
        CodeGen code=new CodeGen("""
            val simple_f64_bits={ f64 x -> 42; };
            return simple_f64_bits(1.0);
            """);
        assertEquals("42",Eval2.eval(code,0));
    }

    @Test public void wrongSignature() {
        try {
            new CodeGen("{ i64 -> i64 } simple_f64_bits=\"C\"; return simple_f64_bits(arg);")
                .driver(CodeGen.Phase.TypeCheck);
            fail("The reserved C symbol has fixed semantics and a fixed signature");
        } catch(RuntimeException e) {
            assertTrue(e.getMessage(),e.getMessage().contains("Intrinsic 'simple_f64_bits' requires"));
        }
    }

    @Test public void nativeRepresentations() throws Exception {
        TestC.runSYS("""
            val bits_noInline={ f64 x -> sys.math.f64Bits(x); };
            val float_noInline={ i64 x -> sys.math.f64FromBits(x); };
            val main={ ->
                val patterns=new i64[~]{0,sys.integer.MIN_I64,1,0x0010000000000000,
                    0x3ff0000000000000,0x7fefffffffffffff,0x7ff0000000000000,
                    0x7ff8000000000001,0x7ff8000000000002,-2251799813685206,0x7ff0000000000001};
                for( int i=0; i<patterns#; i++ )
                    if( bits_noInline(float_noInline(patterns[i]))!=patterns[i] ) return 1;
                if( bits_noInline(-0.0)!=sys.integer.MIN_I64 ) return 2;
                if( float_noInline(0x4008000000000000)!=3.0 ) return 3;
                int numeric=3.75;
                if( numeric!=3 ) return 4;
                return 0;
            };
            ""","intrinsicNative","",-1);
    }

    @Test public void indirectCallsUseCFallback() throws Exception {
        String source=DECL+"""
            { f64 -> i64 } llround="C";
            val apply_noInline={ { f64 -> i64 } f, f64 x -> f(x); };
            val main={ ->
                if( apply_noInline(simple_f64_bits,-0.0)!=(-9223372036854775807-1) ) return 1;
                if( apply_noInline(llround,3.75)!=4 ) return 2;
                if( apply_noInline(simple_f64_bits,1.0)!=0x3ff0000000000000 ) return 3;
                if( apply_noInline(simple_f64_bits,simple_f64_from_bits(0x7ff800000000002a))!=0x7ff800000000002a ) return 4;
                return 0;
            };
            """;
        CodeGen code=new CodeGen(source).driver(CodeGen.Phase.TypeCheck);
        assertNotNull("The indirect C call must survive",code._stop.walk(n ->
            n instanceof CallNode c && c.external() && !c.tfp().isConstant() ? n : null));
        TestC.runSYS(source,"intrinsicFallback","",-1);
    }

    @Test public void importedNodesAndNaNConstants() throws Exception {
        Path dir=Files.createTempDirectory(Path.of("build/objs"),"intrinsic-import-");
        String library=DECL+"""
            val bits_noInline={ f64 x -> simple_f64_bits(x); };
            val float_noInline={ i64 x -> simple_f64_from_bits(x); };
            val nan=simple_f64_from_bits(0x7ff8000000000001);
            val otherNaN=simple_f64_from_bits(0x7ff8000000000002);
            """;
        new CodeGen(null,dir.toString(),null,"Bits",library,126,true,TypeInteger.BOT)
            .driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
        var paths=new Ary<>(new String[]{dir.toString()});
        String[] clients={
            "return Bits.bits_noInline(Bits.float_noInline(arg));",
            "return Bits.bits_noInline(Bits.nan);",
            "return Bits.bits_noInline(Bits.otherNaN);"
        };
        long[] expected={0x7ff800000000002aL,0x7ff8000000000001L,0x7ff8000000000002L};
        for(int i=0;i<clients.length;i++) {
            CodeGen client=new CodeGen(null,dir.toString(),paths,"Client",clients[i],126,true,TypeInteger.BOT);
            assertEquals(Long.toString(expected[i]),Eval2.eval(client,expected[0]));
        }
    }

    @Test public void allBackendsSelectRepresentationMoves() {
        // The array store/load and branch keep both directions alive, rather
        // than allowing the inverse-cast peephole to erase the whole test.
        String source=DECL+"""
            val a=new f64[1];
            a[0]=simple_f64_from_bits(arg);
            if( arg==0 ) a[0]=-0.0;
            return simple_f64_bits(a[0]);
            """;
        for(String cpu:new String[]{"x86_64_v2","arm","riscv"}) {
            CodeGen code=new CheckedCodeGen(source).driver(CodeGen.Phase.Encoding,cpu,"SystemV");
            assertNotNull(code._stop.walk(n -> n instanceof MachConcreteNode m && m.op().equals("f64bits") ? n : null));
            assertNotNull(code._stop.walk(n -> n instanceof MachConcreteNode m && m.op().equals("f64frombits") ? n : null));
        }
    }

    @Test public void invalidArgumentIsNotInlinedAway() {
        try {
            new CodeGen(DECL+"return simple_f64_bits(1);").driver(CodeGen.Phase.TypeCheck);
            fail("Intrinsic inlining must not hide the call's type error");
        } catch(RuntimeException e) {
            assertTrue(e.getMessage(),e.getMessage().contains("Argument #1 isa"));
        }
    }

    @Test public void discardedValueKeepsArgumentEffects() throws Exception {
        TestC.runSYS(DECL+"""
            val next={ i64[] a -> a[0]++; return 1.0; };
            val main={ ->
                val a=new i64[1];
                simple_f64_bits(next(a));
                return a[0]!=1;
            };
            ""","intrinsicEffects","",-1);
    }

    @Test public void emulatedRepresentationMoves() throws Exception {
        long[] patterns={0,Long.MIN_VALUE,1,0x3ff0000000000000L,0x7ff0000000000000L,
            0xfff0000000000000L,0x7ff800000000002aL,0x7ff0000000000001L};
        for(boolean toBits:new boolean[]{true,false}) {
            String source=toBits
                ? "val cast={ f64 x -> { f64 -> i64 } simple_f64_bits=\"C\"; return simple_f64_bits(x); };"
                : "val cast={ i64 x -> { i64 -> f64 } simple_f64_from_bits=\"C\"; return simple_f64_from_bits(x); };";
            var arm=TestArm64.build("cast",source,0,-1,false);
            var risc=TestRisc5.build(source,"cast",0,-1,false);
            int armStart=arm._pc, riscStart=risc._pc;
            for(long bits:patterns) {
                arm._pc=armStart; risc._pc=riscStart;
                if(toBits) {
                    arm.fregs[0]=Double.longBitsToDouble(bits);
                    risc.fregs[10]=Double.longBitsToDouble(bits);
                } else {
                    arm.regs[0]=bits;
                    risc.regs[10]=bits;
                }
                assertEquals(0,arm.step(1000));
                assertEquals(0,risc.step(1000));
                assertEquals(bits,toBits ? arm.regs[0] : Double.doubleToRawLongBits(arm.fregs[0]));
                assertEquals(bits,toBits ? risc.regs[10] : Double.doubleToRawLongBits(risc.fregs[10]));
            }
        }
    }
}
