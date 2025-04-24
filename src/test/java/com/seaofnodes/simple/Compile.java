package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.FunNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

public class Compile {

    private static Process run(String simple, String c) throws IOException, InterruptedException {
        boolean USE_WSL = System.getProperty("os.name").startsWith("Windows");
        CodeGen code = new CodeGen(simple);
        code.parse().opto().typeCheck().instSelect("x86_64_v2", "SystemV").GCM().localSched().regAlloc().encode();
        if (c != null) for (var n:code._start._outputs) if (n instanceof FunNode f && f._name.equals("main")) f._name = "simple_main";
        code.exportELF("test.o");
        var params = new ArrayList<String>();
        if (USE_WSL) params.add("wsl.exe");
        params.add("gcc");
        params.add("-o");
        params.add("test");
        if (c != null) {
            Files.writeString(Path.of("test.c"), c);
            params.add("test.c");
        }
        params.add("test.o");
        Process p = Runtime.getRuntime().exec(params.toArray(String[]::new));
        assertEquals(0, p.waitFor());
        return Runtime.getRuntime().exec(USE_WSL ? new String[]{"wsl.exe", "./test"}:new String[]{"./test"});
    }

    @Test
    @Ignore
    public void testCompile() throws IOException, InterruptedException {
        var p = run("""
                val testMy = { flt x ->
                    flt guess = x;
                    while( 1 ) {
                        flt next = (x/guess + guess)/2;
                        if( next == guess ) return guess;
                        guess = next;
                    }
                };
                ""","""

                #include <stdio.h>

                extern double testMy(double);

                int main(int argc, char** argv) {
                    printf("%f\\n", testMy(2.0));
                    return 0;
                }

                """);
        assertEquals(0, p.waitFor());
        assertEquals("1.414214\n", new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

}
