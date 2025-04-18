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
        _run(src,CALL_CONVENTION,"",cfile,efile,"S",expected,spills);
    }

    static void _run( String src, String simple_conv, String c_conv, String cfile, String efile, String xtn, String expected, int spills ) throws IOException {
        String bin = efile+xtn;
        String obj = bin+".o";
        // Compile simple, emit ELF
        CodeGen code = new CodeGen(src).driver( CPU_PORT, simple_conv, obj);

        // Compile the C program
        var params = new String[] {
            //if (USE_WSL) "wsl.exe";
            "gcc",
            cfile,
            obj,
            "-lm", // Picks up 'sqrt' for newtonFloat tests to compare
            "-g",
            "-o",
            bin,
            "-D",
            "CALL_CONV="+c_conv,
        };
        Process gcc = new ProcessBuilder(params).redirectErrorStream(true).start();
        byte error;
        try { error = (byte)gcc.waitFor(); } catch( InterruptedException e ) { throw new IOException("interrupted"); }
        String result = new String(gcc.getInputStream().readAllBytes());
        if( error!=0 ) {
            System.err.println("gcc error code: "+error);
            System.err.println(result);
        }
        assertEquals( 0, error );
        //assertTrue(result.isEmpty()); // No data in error stream

        // Execute results
        Process smp = new ProcessBuilder(bin).redirectErrorStream(true).start();
        try { error = (byte)smp.waitFor(); } catch( InterruptedException e ) { throw new IOException("interrupted"); }
        result = new String(smp.getInputStream().readAllBytes());
        if( error!=0 ) {
            System.err.println("exec error code: "+error);
            System.err.println(result);
        }
        assertEquals( 0, error );
        assertEquals(expected,result);

        // Allocation quality not degraded
        int delta = spills>>3;
        if( delta==0 ) delta = 1;
        if( spills != -1 )
            assertEquals("Expect spills:",spills,code._regAlloc._spillScaled,delta);

    }
}
