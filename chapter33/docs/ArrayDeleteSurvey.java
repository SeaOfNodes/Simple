import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.parser.Parser;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.SB;
import java.nio.file.*;
import java.util.ArrayDeque;

/** Compile-only source migrations. Originals are never changed. Run from chapter33. */
public class ArrayDeleteSurvey {
    private static String change(String src, String from, String to) {
        if( !src.contains(from) ) throw new IllegalArgumentException("Survey source changed: "+from);
        return src.replace(from,to);
    }

    private static String migrate(String name, String src) {
        switch(name) {
        case "aryu8":
            src=change(src,"u8[] !buf;","u8[^] !buf;");
            src=change(src,"new u8[1]","new u8[^1]");
            src=change(src,"u8[] !buf2 = new u8[newlen];","val old = buf;\n        u8[^] !buf2 = new u8[^newlen];");
            return change(src,"buf = buf2;","buf = buf2;\n        old.del();");
        case "aryi64":
            src=change(src,"i64[] !buf;","i64[^] !buf;");
            src=change(src,"i64[] b ->","i64[^] b ->");
            src=change(src,"new i64[","new i64[^");
            src=change(src,"i64[] !buf2 =","val old = buf;\n        i64[^] !buf2 =");
            return change(src,"buf = buf2;","buf = buf2;\n        old.del();");
        case "bitset":
            src=change(src,"i64[] !_bits;","i64[^] !_bits;");
            src=change(src,"new i64[","new i64[^");
            src=change(src,"var bits =","val old = _bits;\n        var bits =");
            return change(src,"_bits = bits;","_bits = bits;\n        old.del();");
        case "Timers":
            src=change(src,"u32[]","u32[^]");
            src=change(src,"i64[]","i64[^]");
            src=change(src,"new u32[","new u32[^");
            src=change(src,"new i64[","new i64[^");
            src=change(src,"val ids =","val oldIds = _ids, oldDue = _due_ms, oldPeriod = _period_ms;\n            val ids =");
            return change(src,"_period_ms = periods_ms;","_period_ms = periods_ms;\n            oldIds.del(); oldDue.del(); oldPeriod.del();");
        case "Canvas":
            src=change(src,"u32[] !pixels;","u32[^] !pixels;");
            src=change(src,"new u32[","new u32[^");
            return change(src,"pixels = new u32[^capacity];",
                "val old = pixels;\n            pixels = new u32[^capacity];\n            old.del();");
        default: throw new IllegalArgumentException(name);
        }
    }

    private static void copyTree(Path src, Path dst) throws Exception {
        ArrayDeque<Path> work=new ArrayDeque<>();
        work.push(src);
        while( !work.isEmpty() ) {
            Path path=work.pop(), target=dst.resolve(src.relativize(path));
            if( Files.isDirectory(path) ) {
                Files.createDirectories(target);
                try( DirectoryStream<Path> children=Files.newDirectoryStream(path) ) {
                    for( Path child : children ) work.push(child);
                }
            } else Files.copy(path,target);
        }
    }

    private static String clean(String s) { return s.replace('\t',' ').replace('\n',' ').replace('\r',' '); }

    public static void main(String[] args) throws Exception {
        Path build=Path.of("build/array-delete-survey");
        Files.createDirectories(build);
        Path run=Files.createTempDirectory(build,"run-");
        SB report=new SB().p("case\tfunction\tstatus\tdetail\n");
        String[] names={"aryu8","aryi64","bitset","Timers","Canvas"};
        String[] files={"sys/aryu8.smp","sys/aryi64.smp","sys/adt/bitset.smp","sys/time/Timers.smp","sys/draw/Canvas.smp"};
        for( int i=0; i<names.length; i++ ) {
            String name=names[i];
            Path out=run.resolve(name), src=out.resolve("src");
            copyTree(Path.of("src/main/smp"),src);
            Path file=src.resolve(files[i]);
            Files.writeString(file,migrate(name,Files.readString(file)));
            CodeGen code=new CodeGen(src.toString(),out.resolve("objects").toString(),null,"sys",null,456,true,TypeInteger.BOT);
            try {
                code.driver(CodeGen.Phase.Allocation);
                if( Boolean.getBoolean("simple.delete.graph") ) {
                    Files.writeString(out.resolve("graph.txt"),code.toString());
                    Files.writeString(out.resolve("allocation.txt"),code._allocation.toString());
                }
                for( ArrayDelete deletion : code._allocation._deletes ) {
                    String status=deletion._reject!=null ? "REJECT" : deletion._unique!=null ? "NEEDS UNIQUE ENTRY" : "PASS";
                    report.p(name).p('\t').p(deletion._call.fun()._name).p('\t').p(status).p('\t').p(clean(deletion.toString())).p('\n');
                }
                if( code._allocation._deletes.isEmpty() ) throw new IllegalStateException("No deletion sites found");
                code.driver(CodeGen.Phase.TypeCheck);
                System.out.println(name+": TypeCheck PASS");
            } catch( Parser.ParseException e ) {
                Files.writeString(out.resolve("rejection.txt"),e.diagnostic());
                report.p(name).p("\t<TypeCheck>\tREJECT\t").p(clean(e.getMessage())).p('\n');
                System.out.println(name+": REJECT at "+code._phase+": "+e.getMessage());
            } catch( Throwable e ) {
                SB stack=new SB().p(e.toString()).p('\n');
                for( StackTraceElement frame : e.getStackTrace() ) stack.p(frame.toString()).p('\n');
                Files.writeString(out.resolve("crash.txt"),stack.toString());
                report.p(name).p("\t<compiler>\tCRASH\t").p(clean(e.toString())).p('\n');
                System.out.println(name+": CRASH at "+code._phase+": "+e);
            }
        }
        Files.writeString(run.resolve("results.tsv"),report.toString());
        System.out.println("Results: "+run.resolve("results.tsv"));
    }
}
