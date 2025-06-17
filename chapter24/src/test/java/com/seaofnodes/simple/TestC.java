package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.simple.util.Utils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

// Call a C driver program, which calls a Simple-generated .o, and self-checks.
// Expects GCC from the default CLI, compiles C, compiles Simple, links and runs.
public abstract class TestC {

    public static void run( String file, int spills ) throws IOException { run(file,"",spills); }

    public static void run( String file, String expected, int spills ) throws IOException {
        run("src/test/java/com/seaofnodes/simple/progs",file,expected,spills);
    }

    // Compile and run a simple program
    public static void run( String dir, String file, String expected, int spills ) throws IOException {
        // Files
        String  cfile = dir+"/"+file+".c"  ;
        String  sfile = dir+"/"+file+".smp";
        String  efile = "build/objs/"+file;

        // Compile and export Simple
        String src = Files.readString(Path.of(sfile));
        run(src,CodeGen.CALL_CONVENTION,"",cfile,efile,"S",expected,spills);
    }

    public static void run( String src, String simple_conv, String c_conv, String cfile, String efile, String xtn, String expected, int spills ) throws IOException {
        String bin = efile+xtn;
        String obj = bin+".o";
        String exe = CodeGen.OS.startsWith("Windows") ? bin+".exe" : bin;
        // Compile simple, emit ELF
        CodeGen code = new CodeGen(src).driver( CodeGen.CPU_PORT, simple_conv, obj);
        String result = gcc(obj, c_conv, cfile, false, exe );
        assertEquals(expected,result);

        // Allocation quality not degraded
        int delta = spills>>3;
        if( delta==0 ) delta = 1;
        if( spills != -1 )
            assertEquals("Expect spills:",spills,code._regAlloc._spillScaled,delta);
    }

    public static String gcc( String obj, String c_conv, String cfile, boolean stdin, String... args ) throws IOException {

        // Compile the C program.  Compiling code and constants in the low
        // 2Gig.  Pointers are 64b BUT since always in the low 2G all the
        // high bits are zero - and Simple code can be emitted treating
        // pointers as 4bytes.
        var params = new Ary<>(String.class);
        params.add("gcc");
        if( cfile!=null ) params.add(cfile); // Associated C driver, usually has a `main`
        params.addAll(new String[] {
                obj,
                "-lm", // Picks up 'sqrt' for newtonFloat tests to compare
                "-g",
                "-o",
                args[0],
        });
        // Calling convention for C calls, if any
        if( cfile!=null ) {
            params.add("-D");
            params.add("CALL_CONV="+c_conv);
        }

        // Run GCC to link (optionally compile C driver code)
        Process gcc = new ProcessBuilder(params.asAry()).redirectErrorStream(true).start();
        int exit;
        try {
            boolean normal = gcc.waitFor(2, TimeUnit.SECONDS);
            exit = normal ? gcc.exitValue() : -1; // no exit???
        }  catch( InterruptedException e ) {
            throw new IOException("interrupted");
        }
        String result = new String(gcc.getInputStream().readAllBytes());
        if( exit!=0 ) {
            System.err.println("gcc error code: "+exit);
            System.err.println(result);
        }
        assertEquals( 0, exit );
        //assertTrue(result.isEmpty()); // No data in error stream

        // Execute results
        ProcessBuilder smp = new ProcessBuilder(args);
        if( stdin ) smp.redirectInput(ProcessBuilder.Redirect.INHERIT);
        Process p = smp.start();
        try { exit = (byte)p.waitFor(); } catch( InterruptedException e ) { throw new IOException("interrupted"); }
        result = new String(p.getInputStream().readAllBytes());
        if( exit!=0 )
            System.err.println("exec exit code: "+exit);
        return result;
    }
}
