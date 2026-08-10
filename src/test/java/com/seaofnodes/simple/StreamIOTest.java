package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegAllocTestSupport.CheckedCodeGen;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.Ary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

public class StreamIOTest extends TestC {
    @Test public void testReadUntilEOF() throws Exception {
        String src="""
            val main={ ->
                val bytes=sys.io.stdin();
                if(!bytes) return 1;
                return sys.io.write(1,bytes)==bytes# ? 0 : 2;
            };
            """;
        String exe=compile(src,"streamEcho",new Ary<>(new String[]{RELEASE_SYS_DIR}),
                           CALL_CONVENTION,null,null,-1);
        for(int size:new int[]{0,8192,8193,65537}) {
            String input="a\0bc\n".repeat(size/5)+"z".repeat(size%5);
            Path in=Files.createTempFile(Path.of("build/objs"),"stream-in-",".txt");
            Path out=Files.createTempFile(Path.of("build/objs"),"stream-out-",".txt");
            Process child=null;
            try {
                Files.writeString(in,input);
                ProcessBuilder builder=new ProcessBuilder(exe).redirectOutput(out.toFile());
                if(size!=8193) builder.redirectInput(in.toFile());
                child=builder.start();
                if(size==8193) { // Also exercise a real pipe, ending only when its writer closes.
                    child.getOutputStream().write(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    child.getOutputStream().close();
                }
                assertTrue(child.waitFor(TEST_TIMEOUT_SECONDS,TimeUnit.SECONDS));
                assertEquals(0,child.exitValue());
                assertEquals(input,Files.readString(out));
            } finally {
                if(child!=null) child.destroyForcibly();
                Files.deleteIfExists(in);
                Files.deleteIfExists(out);
            }
        }
    }

    @Test public void testUnsignedArrayGrowth() throws Exception {
        runSYS("""
            val main={ ->
                val b=new sys.aryu8();
                val first=b.buf;
                b.add('a');
                if(b.buf!=first || b.len!=1) return 1;
                b.addAry("bcdefghij");
                if(b.len!=10 || b.buf[0]!='a' || b.buf[9]!='j') return 2;
                b.undo(4294967295);
                if(b.len!=0) return 3;
                b.len=2147483648;
                b.undo(1);
                if(b.len!=2147483647) return 4;
                b.clear();
                val nums=new sys.aryi64(new i64[0]);
                nums.add(10).add(20);
                if(nums.len!=2 || nums.get(0)!=10 || nums.get(1)!=20) return 5;
                nums.len=4294967295;
                if(nums.len!=4294967295) return 6;
                nums.len=2;
                if(sys.ary.capacity(0,1)!=1) return 7;
                if(sys.ary.capacity(8,8)!=8 || sys.ary.capacity(8,9)!=16) return 8;
                if(sys.ary.capacity(2147483648,2147483649)!=4294967295) return 9;
                if(sys.ary.capacity(4294967295,4294967296)!=0) return 10;
                if(sys.ary.cmp("abc",4294967295,"",0,4294967295)!=0) return 11;
                nums.str().add('\\n').write(1);
                return 0;
            };
            ""","unsignedArrayGrowth","[10, 20]\n",-1);
    }

    @Test public void testShortInterruptedAndFailedIO() throws Exception {
        String src="""
            {i32 i64 -> i32} test_io_mode="C";
            {i64} test_io_count="C";
            {i32} test_io_status="C";
            val main={ ->
                test_io_mode(1,0);
                val bytes=sys.io.read(700);
                if(!bytes) return 1;
                if(bytes#!=24593) return 2;
                for(u32 i=0; i<bytes#; i++) if(bytes[i]!=i-(i/251)*251) return 3;
                test_io_mode(1,0);
                if(sys.io.write(701,bytes)!=bytes# || test_io_count()!=bytes#) return 4;
                if(test_io_status()) return 5;
                test_io_mode(1,3);
                if(sys.io.writeOff(701,bytes,3,5)!=5 || test_io_count()!=5) return 6;
                if(test_io_status()) return 7;
                test_io_mode(1,24590);
                if(sys.io.writeOff(701,bytes,24590,4294967295)!=3) return 8;
                if(test_io_status()) return 9;
                test_io_mode(1,0);
                if(sys.io.writeOff(701,bytes,24593,0)!=0 || test_io_count()!=0) return 10;
                if(sys.io.writeOff(701,bytes,4294967295,1)!=-1) return 11;
                if(sys.libc.errno()!=sys.libc.EINVAL || test_io_count()!=0) return 12;
                test_io_mode(2,0);
                if(sys.io.read(700)!=null || sys.libc.errno()!=sys.libc.EIO) return 13;
                test_io_mode(3,0);
                if(sys.io.write(701,bytes)!=-1 || sys.libc.errno()!=sys.libc.EIO) return 14;
                if(test_io_count()!=7) return 15;
                test_io_mode(4,0);
                if(sys.io.write(701,bytes)!=-1 || sys.libc.errno()!=sys.libc.EIO) return 16;
                test_io_mode(5,0);
                if(sys.io.write(701,bytes)!=-1 || test_io_status()) return 17;
                if(sys.io.read(-1)!=null) return 18;
                val b=new sys.aryu8().addAry(bytes);
                test_io_mode(1,0);
                if(b.write(701)!=b.len || test_io_count()!=b.len || test_io_status()) return 19;
                sys.io.p("ok\\n");
                return 0;
            };
            """;
        CodeGen code=new CheckedCodeGen(null,"build/objs",new Ary<>(new String[]{RELEASE_SYS_DIR}),
            "streamFaults",src,126,true,TypeInteger.BOT);
        code.driver(CPU_PORT,CALL_CONVENTION,false,true);
        String exe="build/objs/streamFaults"+(OS.startsWith("Windows") ? ".exe" : "");
        Ary<String> gcc=new Ary<>(String.class);
        gcc.add("gcc");
        gcc.add(OS.startsWith("Windows") ? "-Wl,--image-base,0x400000" : "-no-pie");
        gcc.add("-Wl,--wrap=read,--wrap=write");
        gcc.add(C_DRIVERS_DIR+"stream_io.c");
        gcc.addAll(linkObjs(code));
        gcc.add(runtimeObject("simple_crt"));
        gcc.add(runtimeObject("simple_args"));
        gcc.add(runtimeObject("simple_graphics"));
        gcc.add(runtimeObject("simple_audio"));
        if( !OS.startsWith("Windows") ) gcc.add("-ldl");
        gcc.addAll(new String[]{"-lm","-o",exe});
        exec(TEST_TIMEOUT_SECONDS,gcc.asAry());
        assertEquals("ok\n",exec(TEST_TIMEOUT_SECONDS,exe));
    }
}
