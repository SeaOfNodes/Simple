package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

public class Chapter23Test {

    @Test @Ignore
    public void testJig() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/jig.smp"));
        testCPU(src,"x86_64_v2", "Win64"  ,-1,null);
        testCPU(src,"riscv"    , "SystemV",-1,null);
        testCPU(src,"arm"      , "SystemV",-1,null);
    }

    static CodeGen testCPU( String src, String cpu, String os, int spills, String stop ) {
        CodeGen code = new CodeGen(src).driver(CodeGen.Phase.Encoding,cpu,os);
        int delta = spills>>3;
        if( delta==0 ) delta = 1;
        if( spills != -1 )
            assertEquals("Expect spills:",spills,code._regAlloc._spillScaled,delta);
        if( stop != null )
            assertEquals(stop, code._stop.toString());
        return code;
    }

    @Test
    public void testFRefFields() {
        String src =
"""
// A hypothetical scanner class
struct Scan {
    int !x;
    u8[~] buf;
    // Skip whitespace
    val skip = { Scan s ->
        while( s.buf[s.x] <= ' ' )
            s.x++;
    };
    // Peek a character; if matched consume it, else false.
    val peek = { Scan s, u8 c ->
        skip(s);
        if( s.buf[s.x] != c ) return false;
        s.x++;
        return true;
    };
};
Scan !s = new Scan{ buf = "  q"; };
return Scan.peek(s,'q');
        """;

        CodeGen code = new CodeGen(src).parse().opto().typeCheck();
        assertEquals("return Phi(Region,0,1);", code._stop.toString());
        assertEquals("1", Eval2.eval(code, 0));
        testCPU(src,"x86_64_v2", "Win64"  ,16,null);
    };

    @Test
    public void testFRefFields2() {
        String src =
"""
// A hypothetical scanner class
struct Scan {
    int !x;
    u8[~] buf;
    // Skip whitespace
    val skip = { Scan s ->
        while( s.buf[s.x] <= ' ' )
            s.x++;
    };
};
val s = new Scan{ buf = "  q"; };
Scan.skip(s);
return s.x;
        """;

        try { new CodeGen(src).parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Argument #0 isa *Scan {i64 x; *[]u8 buf; { *Scan -> 0 #8} skip; }, but must be a *Scan {i64 !x; *[]u8 buf; ... }",e.getMessage()); }
    };


}
