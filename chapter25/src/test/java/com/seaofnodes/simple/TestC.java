package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.simple.util.Utils;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

// Call a C driver program, which calls a Simple-generated .o, and self-checks.
// Expects GCC from the default CLI, compiles C, compiles Simple, links and runs.
public abstract class TestC {

    public static final String OS  = System.getProperty("os.name");
    public static final String CPU = System.getProperty("os.arch");

    public static final String CALL_CONVENTION = OS.startsWith("Windows") ? "win64" : "SystemV";
    public static final String CPU_PORT = switch( CPU ) {
        case "amd64" -> "x86_64_v2";
        default -> throw Utils.TODO("Map Yer CPU Port Here");
    };

    public static final String C_DRIVERS_DIR = "src/test/java/com/seaofnodes/simple/progs/";

    /**
     * Compile, link and run
     * - WITH a C driver
     * - using the default OS/CPU calling convention.
     * - no lib sys
     */
    public static void runC( String src, String base, String expected, int spills) throws IOException {
        String cfile = C_DRIVERS_DIR+base+".c";
        run(src, base, null, CALL_CONVENTION, "", cfile, expected, spills);
    }

    /**
     * Compile, link and run
     * - withOUT a C driver
     * - using the default OS/CPU calling convention.
     * - no lib sys
     */
    public static void runSF( String src, String base, String expected, int spills ) throws IOException {
        run(src, base, null, CALL_CONVENTION, null,null, expected,spills);
    }

    /**
     * Compile, link and run
     * - withOUT a C driver
     * - using the default OS/CPU calling convention.
     * - WITH a test lib sys
     */
    public static void runSYS( String src, String base, String expected, int spills ) throws IOException {
        String libsysDir = "build/objs/" + "lib_"+CPU_PORT+"_"+CALL_CONVENTION;
        Ary<String> externPaths = new Ary<>(new String[]{libsysDir});
        run(src, base, externPaths, CALL_CONVENTION, null,null, expected,spills);
    }


    /**
     * Compile, link and run - general case
     *
     * @param src         Program source to compile
     * @param base        Compiled binary final base name
     * @param simple_conv Argument convention when calling Simple code
     * @param c_conv      Argument convention when calling C code, or null if not
     *                    linking against a C driver program
     * @param cfile       Associated C program which will drive the Simple program,
     *                    or null if not linking against a C driver program
     * @param expected    Expected stdout string
     * @param spills      Expected limit on spill code, used to validate the
     *                    register allocation is reasonable
     */
    public static void run( String src, String base, Ary<String> externPaths, String simple_conv, String c_conv, String cfile, String expected, int spills ) throws IOException {
        // Simple file base-name example:
        // foo.smp ->
        //   build/objs/foo.o   - object file
        //   build/objs/foo.exe - linked executable Windows
        //   build/objs/foo     - linked executable Linux
        String pathBase = "build/objs/"+base;
        String obj = pathBase+".o";
        String exe = pathBase+(OS.startsWith("Windows") ? ".exe" : "");
        // Compile simple, emit ELF
        CodeGen code = new CodeGen(null,null,externPaths,"Test",src,123L,TypeInteger.BOT);
        code.driver( CPU_PORT, simple_conv, obj, cfile==null );

        String result = gcc(obj, c_conv, cfile, false, exe );
        assertEquals(expected,result);

        // Allocation quality not degraded
        int delta = spills>>3;
        if( delta==0 ) delta = 1;
        if( spills != -1 )
            assertEquals("Expect spills:",spills,code._regAlloc._spillScaled,delta);
    }

    // Link with gcc, and execute the resulting binary, returning stdout as a
    // String.  Any errors *assert* instead of returning some error code; this
    // utility is meant to execute inside a JUnit test which will catch the
    // assert and flag the test as failed.
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
