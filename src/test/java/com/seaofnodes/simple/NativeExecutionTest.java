package com.seaofnodes.simple;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class NativeExecutionTest {
    // A subprocess with deterministic stdout, stderr, and process status.
    public static class Child {
        public static void main(String[] args) {
            System.out.println("before exit");
            System.err.println("child diagnostic");
            System.exit(Integer.parseInt(args[0]));
        }
    }

    private static String child(int exit) throws IOException {
        String java = Path.of(System.getProperty("java.home"),"bin","java").toString();
        return TestC.exec(java,"-cp",System.getProperty("java.class.path"),
                          Child.class.getName(),Integer.toString(exit));
    }

    private static void checkFailure(int exit) throws IOException {
        try {
            child(exit);
            fail("Accepted process exit "+exit);
        } catch( IOException e ) {
            assertTrue(e.getMessage(),e.getMessage().contains("exit code: "+exit));
            assertTrue(e.getMessage(),e.getMessage().contains("before exit"));
            assertTrue(e.getMessage(),e.getMessage().contains("child diagnostic"));
        }
    }

    @Test public void testNonzeroExit() throws IOException { checkFailure(7); }

    @Test public void testFullWindowsExitCode() throws IOException {
        assumeTrue(TestC.OS.startsWith("Windows"));
        checkFailure(256); // Narrowing to a byte incorrectly reports success.
        checkFailure(2816); // Observed Cygwin status for the Hello World crash.
    }

    @Test public void testSuccessfulOutput() throws IOException {
        assertEquals("before exit"+System.lineSeparator(),child(0));
    }

    @Test public void testHelloWorldArtifacts() throws IOException {
        new Chapter22Test().testHelloWorld();
        new Chapter25Test().testHelloWorld();
        // Model a parallel run paused between Chapter 22's link and execution.
        // Chapter 25 must not replace the executable Chapter 22 is about to run.
        String exe = "build/objs/helloWorld"+(TestC.OS.startsWith("Windows") ? ".exe" : "");
        assertEquals("Hello, World!",TestC.exec(exe));
    }

    @Test public void testDriverNonzeroExit() throws Exception {
        Path dir = Files.createTempDirectory(Path.of("build/objs"),"driver-exit-");
        Path src = dir.resolve("fail.smp");
        Files.writeString(src,"return 7;");
        try {
            Simple.main(new String[]{src.toString()});
            fail("Driver accepted a failed executable");
        } catch( IOException e ) {
            assertTrue(e.getMessage(),e.getMessage().contains("exit code: 7"));
        }
    }
}
