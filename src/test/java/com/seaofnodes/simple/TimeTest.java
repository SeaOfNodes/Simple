package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.Ary;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

public class TimeTest extends TestC {
    private static void checks(String body, String name) throws Exception {
        runSYS("val main={ -> "+body+" return 0; };",name,"",-1);
    }

    @Test public void builtInFormats() throws Exception {
        runSYS("""
            val main={ ->
                i64 t=sys.time.parse("2026-11-12T00:34:56.789Z");
                sys.io.p("%t(t)|%tF(t)|%tr(t)|%tR(t)|%tM(83456)|%tD(t)|%tT(t)\\n");
                sys.io.p("[%30t(0)][%-10tr(sys.time.hours(12))]\\n");
                sys.io.p("%tr(0)|%tr(sys.time.hours(13))|%tM(-1)|%tM(-9223372036854775807-1)");
                return 0;
            };
            ""","timeFormats",
            "2026-11-12T00:34:56.789Z|2026-11-12T00:34:56.789Z|12:34am|00:34|01:23.456|12/11/26|00:34:56\n"+
            "[      1970-01-01T00:00:00.000Z][12:00pm   ]\n"+
            "12:00am|1:00pm|-00:00.001|-153722867280912:55.808",-1);
    }

    @Test public void calendarAgainstJava() throws Exception {
        long[] values={-62135596800000L,-2203977600000L,-2203891200000L,-86400001,-86400000,-1,0,1,
            951782400000L,1709164800123L,1794443696789L,253402300799999L};
        DateTimeFormatter iso=DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
        StringBuilder src=new StringBuilder("val show_noInline={i64 t -> sys.io.p(\"%t(t)|\"); val d=sys.time.utc(t); sys.io.p(\"%(d.weekday)\\n\"); }; val main={ -> ");
        StringBuilder expected=new StringBuilder();
        for (long value:values) {
            src.append("show_noInline(").append(value).append(");");
            var dt=Instant.ofEpochMilli(value).atOffset(ZoneOffset.UTC);
            expected.append(iso.format(dt)).append('|').append(dt.getDayOfWeek().getValue()%7).append('\n');
        }
        src.append("return 0; };");
        runSYS(src.toString(),"timeCalendar",expected.toString(),-1);
    }

    @Test public void parseAndConstruction() throws Exception {
        checks("""
            if( sys.time.parse("1970-01-01T00:00:00Z")!=0 ) return 1;
            if( sys.time.parse("1970-01-01T01:00:00.123+01:00")!=123 ) return 2;
            if( sys.time.parse("1969-12-31T16:00:00-08:00")!=0 ) return 3;
            if( sys.time.parse("1969-12-31T23:59:59.999Z")!=-1 ) return 4;
            if( sys.time.parse("1969-12-31T23:59:59.999+01:00")!=-3600001 ) return 5;
            if( sys.time.parse("1970-01-01T00:00:30+00:00:30")!=0 ) return 6;
            if( sys.time.parse("2000-02-29T00:00:00Z")!=951782400000 ) return 7;
            if( sys.time.makeUTC(2024,2,29,0,0,0,123)!=1709164800123 ) return 8;
            if( sys.time.makeUTC(1900,2,29,0,0,0,0)!=-1 || sys.time.makeUTC(2024,2,29,24,0,0,0)!=-1 ) return 9;
            if( sys.time.utc(sys.time.MIN-1).year!=0 || sys.time.utc(sys.time.MAX+1).year!=0 ) return 10;
            ""","timeParse");
        String[] bad={"","now","2026-11-12","2026-11-12T00:00:00","0000-01-01T00:00:00Z",
            "1900-02-29T00:00:00Z","2026-02-29T00:00:00Z","2026-13-01T00:00:00Z",
            "2026-11-00T00:00:00Z","2026-11-12T24:00:00Z","2026-11-12T00:60:00Z",
            "2026-11-12T00:00:60Z","2026-11-12T00:00:00.1Z","2026-11-12T00:00:00.1234Z",
            "2026-11-12T00:00:00+24:00","2026-11-12T00:00:00+01:60","2026-11-12T00:00:00Zx",
            "2026-11-12T00:00:00.00xZ","0001-01-01T00:00:00+01:00"};
        StringBuilder src=new StringBuilder("val main={ -> ");
        for (int i=0;i<bad.length;++i)
            src.append("if(sys.time.parse(\"").append(bad[i]).append("\")!=-1) return ").append(i+1).append(';');
        src.append("return 0; };");
        runSYS(src.toString(),"timeBadParse","",-1);
    }

    @Test public void durationArithmetic() throws Exception {
        checks("""
            if( sys.time.seconds(2)!=2000 || sys.time.minutes(2)!=120000 || sys.time.hours(2)!=7200000 || sys.time.days(2)!=172800000 ) return 1;
            if( sys.time.toSeconds(-1999)!=-1 || sys.time.toMinutes(119999)!=1 || sys.time.toHours(7200000)!=2 || sys.time.toDays(172800000)!=2 ) return 2;
            if( sys.time.seconds(9223372036854775)!=9223372036854775000 || sys.time.seconds(-9223372036854775)!=-9223372036854775000 ) return 3;
            if( sys.time.seconds(9223372036854776)!=-1 || sys.time.seconds(-9223372036854776)!=-1 ) return 4;
            if( sys.time.remaining(5,10)!=0 || sys.time.remaining(10,5)!=5 || sys.time.remaining(9223372036854775807,-1)!=9223372036854775807 ) return 5;
            if( sys.time.fromNanos(-1999999)!=-1 || sys.time.toNanos(1234)!=1234000000 || sys.time.toNanos(9223372036855)!=-1 ) return 6;
            ""","timeArithmetic");
    }

    @Test public void timerOrderingAndMissedTicks() throws Exception {
        checks("""
            val q=new sys.time.Timers();
            if( q.poll(0)!=0 || q.nextDeadline()!=-1 ) return 1;
            u32 a=q.afterAt(100,20), b=q.afterAt(100,10), c=q.afterAt(100,10);
            if( a==0 || b==0 || c==0 ) return 10;
            if( q.nextDeadline()!=110 || q.poll(109)!=0 || q.poll(110)!=b || q.poll(110)!=c || q.poll(110)!=0 ) return 2;
            if( !q.cancel(a) || q.cancel(a) || q.cancel(0) || q.nextDeadline()!=-1 ) return 3;
            u32 repeat=q.everyAt(100,10);
            if( q.poll(135)!=repeat || q.nextDeadline()!=140 || q.poll(135)!=0 ) return 4;
            if( q.poll(140)!=repeat || q.nextDeadline()!=150 || !q.cancel(repeat) ) return 5;
            for( int i=0; i<20; i++ ) if( q.afterAt(200,i)==0 ) return 11;
            u32 prev=0;
            for( int i=0; i<20; i++ ) { u32 id=q.poll(300); if( id<=prev ) return 6; prev=id; }
            if( q.nextDeadline()!=-1 || q.poll(1000)!=0 ) return 7;
            if( q.afterAt(-1,1)!=0 || q.afterAt(0,-1)!=0 || q.everyAt(0,0)!=0 || q.afterAt(9223372036854775807,1)!=0 ) return 8;
            u32 last=q.everyAt(9223372036854775806,1);
            if( last==0 || q.poll(9223372036854775807)!=last || q.nextDeadline()!=-1 ) return 9;
            // Exhaust IDs without wrapping or letting an old ID cancel a new timer.
            q._next=0xfffffffe;
            u32 high=q.afterAt(0,10), max=q.afterAt(0,10);
            if( high!=0xfffffffe || max!=0xffffffff || q.afterAt(0,0)!=0 ) return 12;
            if( q.cancel(a) || q.poll(10)!=high || q.poll(10)!=max || q.poll(10)!=0 ) return 13;
            if( q.afterAt(0,0)!=0 || q.nextDeadline()!=-1 ) return 14;
            ""","timeTimers");
    }

    private static String run(String source, String name, String zone) throws Exception {
        String exe=compile(source,name,new Ary<>(new String[]{RELEASE_SYS_DIR}),CALL_CONVENTION,null,null,-1);
        ProcessBuilder pb=new ProcessBuilder(exe).redirectErrorStream(true);
        if (zone!=null) pb.environment().put("TZ",zone);
        Process child=pb.start();
        try {
            assertTrue("time program timed out",child.waitFor(10,TimeUnit.SECONDS));
            String out=new String(child.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
            assertEquals(out,0,child.exitValue());
            return out;
        } finally { if (child.isAlive()) child.destroyForcibly(); }
    }

    @Test public void localZoneAndDaylightSaving() throws Exception {
        String out=run("""
            val show_noInline={u8[~] s ->
                i64 t=sys.time.parse(s); val d=sys.time.local(t);
                sys.io.p("%(sys.time.formatLocal(t,sys.time.ISO))|%(d.offsetSeconds)|%b(d.daylight)|%(sys.time.zoneName(t))\\n");
            };
            val main={ -> show_noInline("2026-01-01T00:00:00Z"); show_noInline("2026-07-01T00:00:00Z"); return 0; };
            ""","timeZone","EST5EDT,M3.2.0/2,M11.1.0/2");
        assertEquals("2025-12-31T19:00:00.000-05:00|-18000|false|EST\n2026-06-30T20:00:00.000-04:00|-14400|true|EDT\n",out);
    }

    @Test public void liveClocksAndWaits() throws Exception {
        String out=run("""
            val main={ ->
                i64 start=sys.time.monotonic();
                i64 ns=sys.time.nanoTime();
                if( start<0 || ns/1000000<start || ns/1000000>sys.time.monotonic() ) return 1;
                if( sys.time.sleep(10)!=0 || sys.time.monotonic()-start<10 ) return 2;
                i64 deadline=sys.time.monotonic()+10;
                if( sys.time.sleepUntil(deadline)!=0 || sys.time.monotonic()<deadline ) return 3;
                if( sys.time.sleepUntil(0)!=0 || sys.time.sleep(-1)!=-1 || sys.time.sleepUntil(-1)!=-1 ) return 4;
                i64 wall=sys.time.now();
                if( wall<1700000000000 || wall>4102444800000 ) return 5;
                return 0;
            };
            ""","timeClocks",null);
        assertEquals("",out);
    }

    @Test public void pureCalendarInEvaluator() {
        CodeGen code=new CodeGen("src/main/smp",null,null,"TimeEval","""
            val s="%t(-1)|%tM(1234)";
            val expected="1969-12-31T23:59:59.999Z|00:01.234";
            if( s#!=expected# ) return 1;
            for( int i=0; i<s#; i++ ) if( s[i]!=expected[i] ) return 2;
            return 0;
            """,123,true,TypeInteger.BOT).driver(CodeGen.Phase.TypeCheck);
        assertEquals("0",Eval2.eval(code,0,100000));
    }

    @Test public void wideLibrarySerialization() throws Exception {
        // A growing stdlib crossed the historical byte-sized alias/count limit.
        // Exercise a separately compiled library whose live memory spans it.
        StringBuilder source=new StringBuilder("struct Wide { ");
        for (int i=0;i<300;++i) source.append("int !f").append(i).append("=0;");
        source.append("}; val make_noInline={int x -> val v=new Wide();");
        for (int i : new int[]{0,255,299}) source.append("v.f").append(i).append("=x+").append(i).append(';');
        source.append("return v; };");
        Path library=Files.createTempDirectory(Path.of("build/objs"),"wide-memory-");
        new CodeGen(null,library.toString(),null,"WideMemory",source.toString(),123,true,TypeInteger.BOT)
            .driver(CPU_PORT,CALL_CONVENTION,false,false);
        run("val main={ -> val v=WideMemory.make_noInline(7); return v.f0!=7 || v.f255!=262 || v.f299!=306; };",
            "timeWideMemory",new Ary<>(new String[]{library.toString(),RELEASE_SYS_DIR}),
            CALL_CONVENTION,null,null,"",-1);
    }
}
