package com.seaofnodes.simple;

import com.seaofnodes.simple.util.Ary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

// These tests never open a window and do not need MiniFB installed.
public class GraphicsTest {
    private static String graphics, text;

    @BeforeClass public static void compilePrograms() throws Exception {
        var paths = new Ary<>(new String[]{TestC.RELEASE_SYS_DIR});
        graphics = TestC.compile(Files.readString(Path.of("docs/examples/Graphics.smp")),
            "graphics_missing",paths,TestC.CALL_CONVENTION,null,null,-1);
        text = TestC.compile("val main={ -> sys.io.p(\"text still works\\n\"); return 0; };",
            "graphics_text",paths,TestC.CALL_CONVENTION,null,null,-1);
    }

    private static String run(String exe, String backend, int exit) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(exe).redirectErrorStream(true);
        if( backend == null ) pb.environment().remove("SIMPLE_MINIFB");
        else pb.environment().put("SIMPLE_MINIFB",backend);
        Process child = pb.start();
        try {
            assertTrue("native program timed out",child.waitFor(10,TimeUnit.SECONDS));
            String output = new String(child.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(output,exit,child.exitValue());
            return output;
        } finally { if( child.isAlive() ) child.destroyForcibly(); }
    }

    @Test public void missingConfiguration() throws Exception {
        String output = run(graphics,null,1);
        assertTrue(output,output.contains("MiniFB backend not configured"));
        assertTrue(output,output.contains("make graphics"));
    }

    @Test public void disabledGraphics() throws Exception {
        assertTrue(run(graphics,"off",1).contains("disabled"));
    }

    @Test public void missingLibrary() throws Exception {
        String missing = Path.of("build","no-such-minifb-backend").toAbsolutePath().toString();
        String output = run(graphics,missing,1);
        assertTrue(output,output.contains("cannot load MiniFB backend"));
    }

    @Test public void textIgnoresGraphicsConfiguration() throws Exception {
        assertEquals("text still works\n",run(text,"off",0));
        assertEquals("text still works\n",run(text,"no-such-backend",0));
    }

    @Test public void incompatibleLibrary() throws Exception {
        Path source = Path.of("build/objs/graphics_incompatible.c");
        Path library = Path.of("build/objs/graphics_incompatible" +
            (TestC.OS.startsWith("Windows") ? ".dll" : ".so"));
        Files.writeString(source,"""
            #include "simple_graphics.h"
            static const struct simple_graphics_api api = {999};
            const struct simple_graphics_api *simple_minifb_api(void) { return &api; }
            """);
        TestC.exec(30,"gcc","-shared","-fPIC","-Isrc/main/c",source.toString(),"-o",library.toString());
        assertTrue(run(graphics,library.toAbsolutePath().toString(),1).contains("incompatible MiniFB backend"));
    }
}
