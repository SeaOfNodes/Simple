package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.parser.Parser;
import com.seaofnodes.simple.node.CallNode;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.Ary;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

public class StringFormatTest extends TestC {
    // Share a few native programs, retaining a separately reported assertion
    // for each case. Blocks isolate locals; the delimiter preserves embedded
    // NULs and newlines in the expected output.
    private enum Format {
        DefaultsAndNestedStrings("sys.io.p(\"CPU plays %(2+3). %(\"nested %(7)\") %(true) %(1.5)\\n\");",
               "CPU plays 5. nested 7 1 1.5\n"),
        Formats("sys.io.p(\"%+06d(17)|%#08x(42)|%-5.3s(\"abcdef\")|%.2f(1.25)|%c(65)|%b(false)\\n\");",
               "+00017|0x00002a|abc  |1.25|A|false\n"),
        RemainderInInterpolation("int mod=17; sys.io.p(\"%(mod%5)|%02d(mod%5)|%%|\\%|%(mod %= 5)|%(mod)\");",
               "2|02|%|%|2|2"),
        IntegerLimits("i64 lo=-9223372036854775807-1; sys.io.p(\"%(lo)|%(9223372036854775807)|%u(-1)|%X(-1)\");",
               "-9223372036854775808|9223372036854775807|18446744073709551615|FFFFFFFFFFFFFFFF"),
        IntegerPaddingRules("sys.io.p(\"%#.0o(0)|%#.3o(1)|%#x(0)|%-08d(17)|%08.4d(-17)|%+d(0)|% d(0)\");",
               "0|001|0|17      |   -0017|+0| 0"),
        BuilderNumberUsesFormatter("val b=new sys.aryu8(); b.num(-9223372036854775807-1).add('|').num(9223372036854775807); sys.io.p(sys.text.end(b));",
               "-9223372036854775808|9223372036854775807"),
        EvaluationOrder("int x=0; val s=\"%(x++)%(x++)%(x++)\"; sys.io.p(\"%(s):%(s#):%(x)|%%|\\%\");",
               "012:3:3|%|%"),
        EvaluationMutatesBytes("val a=new u8[1]; a[0]='A'; sys.io.p(\"%(a)%(a[0]='B')%(a)\");", "A66B"),
        Converters("sys.io.p(sys.text.int(17)); sys.io.p(sys.text.char(65)); sys.io.p(sys.text.bool(true)); sys.io.p(sys.text.flt(1.5));",
               "17Atrue1.5"),
        FieldNamedInt("struct S { int int=7; }; sys.io.p(\"%((new S).int)\");", "7"),
        FloatAndAlternateFormats("f32 x=1.25f; sys.io.p(\"%(x)|%f(x)|%.2e(x)|%.2G(x)|%#o(9)|% d(7)|%.0d(0)|%a(1.5)\");",
               "1.25|1.250000|1.25e+00|1.2|011| 7||0x1.8p+0"),
        BufferGrowth("sys.io.p(\"%0100d(17)|%.100f(1.0)\");", "0".repeat(98)+"17|1."+"0".repeat(100)),
        BytesAndEmptyStrings("val empty=\"%(\"\")\"; val bytes=\"a\\0b\"; sys.io.p(\"%(empty#):%s(bytes):%.2s(bytes):%c(0)\");",
               "0:a\0b:a\0:\0"),
        Utf8String("val s=\"π\"; sys.io.p(\"%(s):%(s#):%(s[0]):π\");", "π:2:207:π"),
        DeferredParsing("""
            struct S { u8[~] s; new S={ -> s="%("}) %(3)")"; }; };
            val s=new S;
            sys.io.p(s.s);
            for(int i=0; i<2; sys.io.p("%(i++) %("(")")) { }
            """, "}) 30 (1 (");

        final String body, expected;
        Format(String body, String expected) { this.body=body; this.expected=expected; }
    }
    private static final String END="\n--end-format-case--\n";
    private static final int BATCH_SIZE=5;
    private static final String[] OUTPUTS=new String[Format.values().length];

    @org.junit.BeforeClass public static void resetNativeBatches() {
        java.util.Arrays.fill(OUTPUTS,null);
    }

    private static void prints(Format test) throws Exception {
        int index=test.ordinal();
        if( OUTPUTS[index]==null ) {
            Format[] cases=Format.values();
            int first=index/BATCH_SIZE*BATCH_SIZE, limit=Math.min(first+BATCH_SIZE,cases.length);
            StringBuilder source=new StringBuilder("val main={ -> ");
            for( int i=first; i<limit; i++ ) {
                assertFalse("Reserved batch delimiter",cases[i].expected.contains(END));
                source.append('{').append(cases[i].body).append('}');
                source.append("sys.io.p(\"\\n--end-format-case--\\n\");");
            }
            source.append("return 0; };");
            String exe=compile(source.toString(),"stringFormat"+first,
                new Ary<>(new String[]{RELEASE_SYS_DIR}),CALL_CONVENTION,null,null,-1);
            String[] outputs=exec(TEST_TIMEOUT_SECONDS,exe).split(java.util.regex.Pattern.quote(END),-1);
            assertEquals("Each format case must complete",limit-first+1,outputs.length);
            assertEquals("No output after final case", "",outputs[outputs.length-1]);
            for( int i=first; i<limit; i++ ) OUTPUTS[i]=outputs[i-first];
        }
        assertEquals(test.name(),test.expected,OUTPUTS[index]);
    }

    @Test public void testDefaultsAndNestedStrings() throws Exception {
        prints(Format.DefaultsAndNestedStrings);
    }

    @Test public void testFormats() throws Exception {
        prints(Format.Formats);
    }

    @Test public void testRemainderInInterpolation() throws Exception {
        prints(Format.RemainderInInterpolation);
    }

    @Test public void testIntegerLimits() throws Exception {
        prints(Format.IntegerLimits);
    }

    @Test public void testSignedAndUnsignedRadices() throws Exception {
        long[] values={Long.MIN_VALUE,Long.MIN_VALUE+1,Long.MAX_VALUE,-1,-17,0,1,7,8,9,15,16,17,1L<<32};
        StringBuilder source=new StringBuilder("val show_noInline={i64 x -> sys.io.p(\"%(x)|%u(x)|%o(x)|%x(x)\\n\"); }; val main={ -> ");
        StringBuilder expected=new StringBuilder();
        for(long x:values) {
            source.append("show_noInline(").append(x==Long.MIN_VALUE ? "(-9223372036854775807-1)" : Long.toString(x)).append(");");
            expected.append(x).append('|').append(Long.toUnsignedString(x)).append('|')
                .append(Long.toUnsignedString(x,8)).append('|').append(Long.toUnsignedString(x,16)).append('\n');
        }
        source.append("return 0; };");
        runSYS(source.toString(),"stringFormatRadices",expected.toString(),-1);
    }

    @Test public void testIntegerPaddingRules() throws Exception {
        prints(Format.IntegerPaddingRules);
    }

    @Test public void testBuilderNumberUsesFormatter() throws Exception {
        prints(Format.BuilderNumberUsesFormatter);
    }

    @Test public void testFormattingInEvaluatorFromSource() {
        // No installed sys objects and no native execution: the library builds
        // from source, and all non-float formatting executes as ordinary IR.
        String source="""
            val s="%#08x(arg)|%b(true)|%-4s("ok")|%c(65)";
            val expected="0x00002a|true|ok  |A";
            if(s#!=expected#) return 1;
            for(int i=0; i<s#; i++) if(s[i]!=expected[i]) return 2;
            return 0;
            """;
        CodeGen code=new CodeGen("src/main/smp",null,null,"FreshText",source,123,true,TypeInteger.BOT)
            .driver(CodeGen.Phase.TypeCheck);
        assertEquals("0",Eval2.eval(code,42,10000));
    }

    @Test public void testEvaluationOrderAndLength() throws Exception {
        prints(Format.EvaluationOrder);
        prints(Format.EvaluationMutatesBytes);
    }

    @Test public void testConverters() throws Exception {
        prints(Format.Converters);
        prints(Format.FieldNamedInt);
    }

    @Test public void testForwardReturnTypes() throws Exception {
        runSYS("val main={ -> sys.io.p(\"%(later())\"); return 0; }; val later={ -> \"ok\"; };",
               "stringFormatForward","ok",-1);
    }

    @Test public void testFloatAndAlternateFormats() throws Exception {
        prints(Format.FloatAndAlternateFormats);
        runSYS("val format_noInline={ f32 x -> \"%(x)|%f(x)\"; }; val main={ -> sys.io.p(format_noInline(1.25f)); return 0; };",
               "stringFormatFloat","1.25|1.250000",-1);
    }

    @Test public void testBufferGrowth() throws Exception {
        prints(Format.BufferGrowth);
    }

    @Test public void testLiteralRemainsConstant() {
        CodeGen code=new CodeGen("return \"100%% \\%\";").driver(CodeGen.Phase.TypeCheck);
        assertNull(code._stop.walk(n -> n instanceof CallNode ? n : null));
    }

    @Test public void testBytesAndEmptyStrings() throws Exception {
        prints(Format.BytesAndEmptyStrings);
        prints(Format.Utf8String);
    }

    @Test public void testDeferredParsing() throws Exception {
        prints(Format.DeferredParsing);
    }

    @Test public void testImportedFormatter() throws Exception {
        Path lib=Files.createTempDirectory(Path.of("build/objs"),"text-library-");
        new CodeGen(null,lib.toString(),new Ary<>(new String[]{RELEASE_SYS_DIR}),"TextLibrary",
            "val format_noInline={int x -> \"value=%04d(x)\"; };",123,true,TypeInteger.BOT)
            .driver(CPU_PORT,CALL_CONVENTION,false,false);
        run("val main={ -> sys.io.p(TextLibrary.format_noInline(7)); return 0; };",
            "stringFormatImported",new Ary<>(new String[]{lib.toString(),RELEASE_SYS_DIR}),
            CALL_CONVENTION,null,null,"value=0007",-1);
    }

    @Test public void testWrongTypesAndReservedNames() {
        rejects("return \"%d(1.5)\";", "must be a");
        rejects("return \"%t(1.5)\";", "must be a");
        rejects("return \"%tQ(0)\";", "Expected a time format");
        rejects("return \"%.2t(0)\";", "Precision is not supported");
        rejects("return \"%+t(0)\";", "Invalid flags");
        rejects("return \"%s(17)\";", "must be a");
        rejects("return \"%(null)\";", "Cannot format");
        rejects("val f={int int -> int;}; return 0;", "Expected an identifier");
        rejects("val f={ -> int int=1; return int; }; return 0;", "Expected an identifier");
    }

    private static void rejects(String source, String expected) {
        try {
            new CodeGen(null,null,new Ary<>(new String[]{RELEASE_SYS_DIR}),"Test",source,123,true,TypeInteger.BOT)
                .driver(CodeGen.Phase.TypeCheck);
            fail("Accepted invalid source: "+source);
        } catch( Parser.ParseException e ) {
            assertTrue(e.getMessage(),e.getMessage().contains(expected));
        }
    }

    @Test public void testMalformedFormats() {
        for(String format:new String[]{"%name", "%1$d(1)", "%*d(1)", "%n(1)", "%lld(1)", "%"})
            rejects("return \""+format+"\";", "Expected a string format");
        rejects("return \"%X.cliff\";", "Expected '('");
        rejects("return \"%+s(\"x\")\";", "Invalid flags");
        rejects("return \"%.2c(65)\";", "Precision is not supported");
    }

    @Test public void testUnsupportedDefault() {
        rejects("struct S { int x; }; return \"%(new S)\";", "Cannot format");
    }
}
