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
    private static String graphics, text, game, clock;

    @BeforeClass public static void compilePrograms() throws Exception {
        var paths = new Ary<>(new String[]{TestC.RELEASE_SYS_DIR});
        graphics = TestC.compile(Files.readString(Path.of("docs/examples/Graphics.smp")),
            "graphics_missing",paths,TestC.CALL_CONVENTION,null,null,-1);
        text = TestC.compile("val main={ -> sys.io.p(\"text still works\\n\"); return 0; };",
            "graphics_text",paths,TestC.CALL_CONVENTION,null,null,-1);
        game = TestC.compile(Files.readString(Path.of("docs/examples/TicTacToeGraphical.smp")),
            "graphics_game",paths,TestC.CALL_CONVENTION,null,null,-1);
        clock = TestC.compile(Files.readString(Path.of("docs/examples/Clock.smp")),
            "graphics_clock",paths,TestC.CALL_CONVENTION,null,null,-1);
    }

    private static String run(String exe, String backend, int exit) throws Exception {
        return run(exe,backend,exit,null);
    }

    private static String run(String exe, String backend, int exit, Path cwd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(Path.of(exe).toAbsolutePath().toString()).redirectErrorStream(true);
        if (cwd!=null) pb.directory(cwd.toFile());
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
        Path dir=Files.createTempDirectory(Path.of("build/objs"),"graphics-missing-");
        Path bin=Files.createDirectory(dir.resolve("bin"));
        Path exe=Files.copy(Path.of(graphics),bin.resolve(Path.of(graphics).getFileName()));
        String output = run(exe.toString(),null,1,dir);
        assertTrue(output,output.contains("MiniFB backend not found"));
        assertTrue(output,output.contains("make graphics"));
    }

    @Test public void discoverBackendWithoutConfiguration() throws Exception {
        Path root=Files.createTempDirectory(Path.of("build/objs"),"graphics-discovery-");
        String abi=TestC.OS.startsWith("Windows") ? "x86_64_v2_win64" : "x86_64_v2_SystemV";
        String name=TestC.OS.startsWith("Windows") ? "simple_minifb.dll" : "libsimple_minifb.so";
        Path source=root.resolve("backend.c");
        Files.writeString(source,"""
            #include "simple_graphics.h"
            #include <stdio.h>
            static int32_t open_window(const char *s,uint32_t w,uint32_t h) {
                puts("discovered mock MiniFB"); return -1;
            }
            static int32_t present(const uint32_t *p,uint32_t n) { return 0; }
            static int32_t poll_events(int32_t n) { return 0; }
            static int32_t position(void) { return 0; }
            static int32_t wait_events(int64_t n) { return 0; }
            static void close_window(void) {}
            static const struct simple_graphics_api api={SIMPLE_GRAPHICS_VERSION,
                open_window,present,poll_events,position,position,poll_events,poll_events,close_window,wait_events};
            const struct simple_graphics_api *simple_minifb_api(void) { return &api; }
            """);
        Path mock=root.resolve(name);
        TestC.exec(30,"gcc","-shared","-fPIC","-Isrc/main/c",source.toString(),"-o",mock.toString());
        Path cwd=Files.createDirectory(root.resolve("empty"));
        Path bin=Files.createDirectory(root.resolve("bin"));
        Path exe=Files.copy(Path.of(graphics),bin.resolve(Path.of(graphics).getFileName()));
        Path beside=Files.copy(mock,bin.resolve(name));
        assertTrue(run(exe.toString(),null,1,cwd).contains("discovered mock MiniFB"));
        // Explicit bad overrides must not silently fall back to the mock.
        assertTrue(run(exe.toString(),"no-such-backend",1,cwd).contains("cannot load MiniFB"));
        Files.delete(beside);
        Path release=Files.createDirectories(root.resolve("build/release/"+abi));
        Files.copy(mock,release.resolve(name));
        Path examples=Files.createDirectories(root.resolve("build/examples/"+abi));
        Path example=Files.copy(exe,examples.resolve(exe.getFileName()));
        assertTrue(run(example.toString(),null,1,cwd).contains("discovered mock MiniFB"));
        assertTrue(run(exe.toString(),null,1,root).contains("discovered mock MiniFB"));
    }

    @Test public void disabledGraphics() throws Exception {
        assertTrue(run(graphics,"off",1).contains("disabled"));
    }

    @Test public void gameWithoutBackend() throws Exception {
        assertTrue(run(game,"off",1).contains("disabled"));
    }

    @Test public void clockWithoutBackend() throws Exception {
        assertTrue(run(clock,"off",1).contains("disabled"));
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
