package com.seaofnodes.simple;

import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class RuntimeArgsTest extends TestC {
    @Test public void testNativeArguments() throws IOException {
        runArgs("""
            val main={ u8[~][] args ->
                if(args#!=4 || args[0]#==0) return 1;
                if(args[1]#!=0) return 2;
                val words=args[2];
                if(words#!=9 || words[3]!=' ' || words[8]!='s') return 3;
                val longArg=args[3];
                if(longArg#!=4096) return 4;
                for(int i=0; i<longArg#; i++) if(longArg[i]!='x') return 5;
                return 0;
            };
            ""","runtimeArgs",null,CALL_CONVENTION,"",-1,"","two words","x".repeat(4096));
    }

    @Test public void testArgumentCopiesAndEmptyVector() throws IOException {
        String exe="build/objs/runtimeArgsCopy"+(OS.startsWith("Windows") ? ".exe" : "");
        linkExe(runtimeObject("simple_args"),OS.startsWith("Windows") ? "ms_abi" : "sysv_abi",
                C_DRIVERS_DIR+"runtime_args.c",null,exe);
        assertEquals("ok\n",exec(TEST_TIMEOUT_SECONDS,exe));
    }
}
