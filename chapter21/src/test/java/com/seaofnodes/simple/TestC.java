package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.io.IOException;
import static org.junit.Assert.*;

// Call a C driver program, which calls a Simple-generated .o, and self-checks.
// Expects GCC from the default CLI, compiles C, compiles Simple, links and runs.
public abstract class TestC {

    public static final String OS  = System.getProperty("os.name");
    public static final String CPU = System.getProperty("os.arch");

    public static final String CALL_CONVENTION = OS.startsWith("Windows") ? "Win64" : "SystemV";
    public static final String CPU_PORT = switch( CPU ) {
    case "amd64" -> "x86_64_v2";
    default -> throw Utils.TODO("Map Yer CPU Port Here");
    };

    public static void run( String file ) throws Exception { run(file,""); }

    public static void run( String file, String expected ) throws Exception {
        run("src/test/java/com/seaofnodes/simple/progs",file,expected);
    }

    // Compile and run a simple program
    public static void run( String dir, String file, String expected ) throws IOException {
        // Files
        String cfile = dir+"/"+file+".c"  ;
        String sfile = dir+"/"+file+".smp";
        String sofile = "build/objs/"+file+"S.o";
        String efile = "build/objs/"+file;

        // Compile and export Simple
        String src = Files.readString(Path.of(sfile));
        CodeGen code = new CodeGen(src).parse().opto().typeCheck().instSelect( CPU_PORT, CALL_CONVENTION).GCM().localSched().regAlloc().encode().exportELF(sofile);

        // Compile the C program
        var params = new Ary<>(String.class);
        //if (USE_WSL) params.add("wsl.exe");
        params.add("gcc");
        params.add(cfile);
        params.add(sofile);
        params.add("-o");
        params.add(efile);
        Process gcc = new ProcessBuilder(params.toArray(String[]::new)).redirectErrorStream(true).start();
        byte error;
        try { error = (byte)gcc.waitFor(); } catch( InterruptedException e ) { throw new IOException("interrupted"); }
        String result = new String(gcc.getInputStream().readAllBytes());
        assertEquals( 0, error );
        assertTrue(result.isEmpty()); // No data in error stream

        // Execute results
        Process smp = new ProcessBuilder(efile).redirectErrorStream(true).start();
        try { error = (byte)smp.waitFor(); } catch( InterruptedException e ) { throw new IOException("interrupted"); }
        result = new String(smp.getInputStream().readAllBytes());
        assertEquals( 0, error );
        assertEquals(expected,result);
    }
}
