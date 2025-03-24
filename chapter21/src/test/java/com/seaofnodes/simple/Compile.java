package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

import static com.seaofnodes.simple.Main.PORTS;

public class Compile {

    @Test
    @Ignore
    public void testCompile() throws IOException, InterruptedException {
        boolean USE_WSL = System.getProperty("os.name").startsWith("Windows");
        CodeGen code = new CodeGen("return 13;");
        code.parse().opto().typeCheck().instSelect(PORTS,"x86_64_v2", "SystemV").GCM().localSched().regAlloc().encode();
        code.exportELF("test.o");
        Process p = Runtime.getRuntime().exec(USE_WSL ? new String[]{"wsl.exe", "gcc", "-o", "test", "test.o"}:new String[]{"gcc", "-o", "test", "test.o"});
        assert p.waitFor() == 0;
        p = Runtime.getRuntime().exec(USE_WSL ? new String[]{"wsl.exe", "./test"}:new String[]{"./test"});
        System.out.println(p.waitFor());
        assert p.waitFor() == 13;
    }

}
