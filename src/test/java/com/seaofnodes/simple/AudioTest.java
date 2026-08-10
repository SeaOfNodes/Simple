package com.seaofnodes.simple;

import com.seaofnodes.simple.util.Ary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

// Loader and Simple/C ABI checks: no miniaudio download or audio device.
public class AudioTest {
    private static String missing, playback;

    @BeforeClass public static void compilePrograms() throws Exception {
        var paths=new Ary<>(new String[]{TestC.RELEASE_SYS_DIR});
        missing=TestC.compile("""
            val main = { ->
                if( sys.audio.open()!=sys.audio.ERROR ) return 1;
                if( sys.audio.boop()!=sys.audio.ERROR || sys.audio.playing() ) return 2;
                sys.audio.stop(); sys.audio.close(); sys.audio.close();
                sys.io.p("silent fallback\\n");
                return 0;
            };
            ""","audio_missing",paths,TestC.CALL_CONVENTION,null,null,-1);
        playback=TestC.compile("""
            val main = { ->
                if( sys.audio.open()!=sys.audio.OK ) return 1;
                val pcm = new i16[3];
                pcm[0]=-32768; pcm[1]=123; pcm[2]=32767;
                int sound = sys.audio.load(pcm);
                if( sound!=4 || sys.audio.play(sound)!=0 || !sys.audio.playing() ) return 2;
                if( sys.audio.boop()!=0 || sys.audio.win()!=0 || sys.audio.lose()!=0 ) return 3;
                sys.audio.stop();
                if( sys.audio.playing() ) return 4;
                sys.audio.close();
                return 0;
            };
            ""","audio_playback",paths,TestC.CALL_CONVENTION,null,null,-1);
    }

    private static String run(String exe, String backend) throws Exception {
        ProcessBuilder pb=new ProcessBuilder(exe).redirectErrorStream(true);
        if( backend==null ) pb.environment().remove("SIMPLE_MINIAUDIO");
        else pb.environment().put("SIMPLE_MINIAUDIO",backend);
        Process child=pb.start();
        try {
            assertTrue("audio program timed out",child.waitFor(10,TimeUnit.SECONDS));
            String out=new String(child.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
            assertEquals(out,0,child.exitValue());
            return out;
        } finally { if( child.isAlive() ) child.destroyForcibly(); }
    }

    @Test public void silentFallback() throws Exception {
        assertEquals("silent fallback\n",run(missing,null));
        assertEquals("silent fallback\n",run(missing,"off"));
        assertTrue(run(missing,"no-such-miniaudio-backend").contains("cannot load backend"));
    }

    @Test public void nativeBoundary() throws Exception {
        Path source=Path.of("build/objs/audio_stub.c");
        Path library=Path.of("build/objs/audio_stub"+(TestC.OS.startsWith("Windows") ? ".dll" : ".so"));
        Files.writeString(source,"""
            #include "simple_audio.h"
            static int active;
            static int32_t open_audio(void) { return 0; }
            static int32_t load(const int16_t *p, uint32_t n) {
                return n==3 && p[0]==-32768 && p[1]==123 && p[2]==32767 ? 4 : -1;
            }
            static int32_t play(int32_t cue) { active=1; return cue>=1 && cue<=4 ? 0 : -1; }
            static int32_t stop(void) { active=0; return 0; }
            static int32_t playing(void) { return active; }
            static void close_audio(void) { active=0; }
            static const struct simple_audio_api api={1,open_audio,load,play,stop,playing,close_audio};
            const struct simple_audio_api *simple_miniaudio_api(void) { return &api; }
            """);
        TestC.exec(30,"gcc","-shared","-fPIC","-Isrc/main/c",source.toString(),"-o",library.toString());
        assertEquals("",run(playback,library.toAbsolutePath().toString()));
    }

    @Test public void incompatibleBackend() throws Exception {
        Path source=Path.of("build/objs/audio_bad.c");
        Path library=Path.of("build/objs/audio_bad"+(TestC.OS.startsWith("Windows") ? ".dll" : ".so"));
        Files.writeString(source,"""
            #include "simple_audio.h"
            static const struct simple_audio_api api={999};
            const struct simple_audio_api *simple_miniaudio_api(void) { return &api; }
            """);
        TestC.exec(30,"gcc","-shared","-fPIC","-Isrc/main/c",source.toString(),"-o",library.toString());
        assertTrue(run(missing,library.toAbsolutePath().toString()).contains("incompatible backend"));
    }
}
