import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Ary;
import java.nio.file.*;
import java.lang.reflect.Field;
import java.util.*;

// Standalone survey, not a test or a compiler option. Run from chapter33.
public class AllocationSurvey {
    static Object field(Object object, String name) throws Exception {
        Field f=object.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(object);
    }
    static String clean(Object s) { return s==null ? "" : s.toString().replace('\t',' ').replace('\n',' '); }
    static String category(String reason) {
        if( reason==null ) return "supported";
        if( reason.contains("opaque body") ) return "opaque body";
        if( reason.contains("opaque or indirect") ) return "indirect call";
        if( reason.contains("recursive call") ) return "recursion";
        if( reason.contains("raw address") ) return "raw address";
        if( reason.contains("variable-size") ) return "variable size";
        if( reason.contains("across its backedge") ) return "loop retention";
        if( reason.contains("cyclic field-path") ) return "field paths";
        if( reason.contains("reference origin") ) return "reference origin";
        if( reason.contains("native result storage") ) return "native result";
        return "other";
    }
    static String probe(String name) {
        return switch(name) {
        case "LocalArray" -> """
            val work_noInline={ u32 n ->
                val xs=new i64[n];
                for( u32 i=0; i<n; i++ ) xs[i]=i;
                return n ? xs[n-1] : 0;
            };
            u32 len=arg;
            return work_noInline(len);
            """;
        case "ReturnedArray" -> """
            val make_noInline={ u32 n -> new i64[n]; };
            u32 len=arg;
            return make_noInline(len)#;
            """;
        case "OverwrittenSlot" -> """
            struct Thing { i64 !x; new Thing={i64 n -> x=n;}; };
            struct Holder { Thing? !c=null; };
            val work_noInline={i64 n ->
                val h=new Holder;
                while(n>0) { h.c=new Thing(n); n--; }
                return h.c ? h.c.x : 0;
            };
            return work_noInline(arg);
            """;
        case "ReusedObject" -> """
            struct Thing { i64 !x; new Thing={i64 n -> x=n;}; };
            val read_noInline={Thing t -> t.x;};
            val work_noInline={i64 n ->
                val t=new Thing(0);
                i64 sum=0;
                while(n>0) { t.x=n; sum+=read_noInline(t); n--; }
                return sum;
            };
            return work_noInline(arg);
            """;
        default -> throw new IllegalArgumentException(name);
        };
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        String name=args[0], mode=args.length>1 ? args[1] : "source";
        Path out=Path.of("build/allocation-survey",name+"-"+mode);
        Files.createDirectories(out);
        for( String file : new String[]{"error.txt","dump.txt","functions.tsv","sites.tsv","allocations.tsv","calls.tsv"} )
            Files.deleteIfExists(out.resolve(file));
        String src=mode.equals("probe") ? probe(name) : name.equals("sys") ? null :
            name.equals("RuntimeArgs") ? Files.readString(Path.of("src/main/smp/sys/rt/simple_args.smp")) :
            Files.readString(Path.of("docs/examples",name+".smp"));
        if( src!=null && !name.equals("RuntimeArgs") && mode.equals("source") ) src="val _survey_sys=sys;\n"+src;
        String root=mode.equals("source") ? "src/main/smp" : "docs/examples";
        Ary<String> libs=mode.equals("object") ? new Ary<>(new String[]{"build/release/x86_64_v2_win64"}) : null;
        CodeGen code=new CodeGen(root,out.resolve("objects").toString(),libs,name,src,456,true,TypeInteger.BOT);
        long start=System.nanoTime();
        try { code.driver(CodeGen.Phase.TypeCheck); }
        catch(Throwable t) {
            StringBuilder error=new StringBuilder(t.toString()+"\nphase="+code._phase+"\n");
            if( t instanceof com.seaofnodes.simple.parser.Parser.ParseException p ) error.append(p.diagnostic()).append('\n');
            for( StackTraceElement frame : t.getStackTrace() ) error.append(frame).append('\n');
            Files.writeString(out.resolve("error.txt"),error);
            System.out.println(name+" "+mode+" ERROR "+t);
            return;
        }
        Allocation.Summary[] summaries=(Allocation.Summary[])field(code._allocation,"_summaries");
        Files.writeString(out.resolve("dump.txt"),code._allocation.toString());
        StringBuilder rows=new StringBuilder("program\tmode\tunit\tfunction\tid\tstatus\tcategory\troot_function\troot_id\troot_unit\tnew_user\tnew_class\tvariable\tplaced_user\tplaced_class\tloops\treason\n");
        StringBuilder sites=new StringBuilder("program\tmode\tunit\tfunction\tsite\ttype\tbytes\tdestination\n");
        StringBuilder nodes=new StringBuilder("program\tmode\tunit\tfunction\tnode\tsize\ttype\n");
        StringBuilder calls=new StringBuilder("program\tmode\tunit\tfunction\tnode\ttarget\tsignature\n");
        int total=0,pass=0,allocated=0;
        for( Allocation.Summary sum : summaries ) {
            if( sum==null ) continue;
            FunNode fun=sum._fun;
            String unit=fun._compunit==null ? "" : fun._compunit._cname;
            Allocation.Summary cause=sum;
            HashSet<Allocation.Summary> seen=new HashSet<>();
            while( cause._reject!=null && cause._reject.startsWith("call #") && seen.add(cause) ) {
                int id=Integer.parseInt(cause._reject.substring(6,cause._reject.indexOf(' ',6)));
                Allocation.Summary next=null;
                for( CallNode call : (ArrayList<CallNode>)field(cause,"_calls") )
                    if( call._nid==id && call.fptr()._type instanceof TypeFunPtr fp && fp.isConstant() ) next=summaries[fp.fidx()];
                if( next==null ) break;
                cause=next;
            }
            int local=0,clz=0,variable=0,placed=0,placedClz=0;
            for( CallNode call : (ArrayList<CallNode>)field(sum,"_calls") )
                calls.append(name).append('\t').append(mode).append('\t').append(clean(unit)).append('\t').append(clean(fun._name)).append('\t').append(call._nid).append('\t').append(clean(call.name())).append('\t').append(clean(call.fptr()._type)).append('\n');
            for( Node n : (ArrayList<Node>)field(sum,"_nodes") ) if( n instanceof NewNode a ) {
                if( TypeStruct.startsClzPrefix(a._ts._name) ) clz++; else local++;
                if( !(a.size()._type instanceof TypeInteger sz) || !sz.isConstant() ) variable++;
                nodes.append(name).append('\t').append(mode).append('\t').append(clean(unit)).append('\t').append(clean(fun._name)).append('\t').append(a._nid).append('\t').append(clean(a.size()._type)).append('\t').append(clean(a._ts._name)).append('\n');
            }
            if( sum.supported() ) for( Allocation.Site site : sum._sites ) if( site._allocation!=null ) {
                if( TypeStruct.startsClzPrefix(site._allocation._ts._name) ) placedClz++; else placed++;
                sites.append(name).append('\t').append(mode).append('\t').append(clean(unit)).append('\t').append(clean(fun._name)).append('\t').append(clean(site._name)).append('\t').append(clean(site._allocation._ts._name)).append('\t').append(site._bytes).append('\t').append(clean(site.destination())).append('\n');
            }
            String status=sum.supported() ? "pass" : sum._reject.startsWith("call #") ? "blocked" : "direct";
            rows.append(name).append('\t').append(mode).append('\t').append(clean(unit)).append('\t').append(clean(fun._name)).append('\t').append(fun.sig().fidx()).append('\t').append(status).append('\t').append(category(cause._reject)).append('\t').append(clean(cause._fun._name)).append('\t').append(cause._fun.sig().fidx()).append('\t').append(clean(cause._fun._compunit==null ? null : cause._fun._compunit._cname)).append('\t').append(local).append('\t').append(clz).append('\t').append(variable).append('\t').append(placed).append('\t').append(placedClz).append('\t').append(((IdentityHashMap<?,?>)field(sum,"_loops")).size()).append('\t').append(clean(sum._reject)).append('\n');
            total++;
            if( sum.supported() ) { pass++; if( placed>0 ) allocated++; }
        }
        Files.writeString(out.resolve("functions.tsv"),rows);
        Files.writeString(out.resolve("sites.tsv"),sites);
        Files.writeString(out.resolve("allocations.tsv"),nodes);
        Files.writeString(out.resolve("calls.tsv"),calls);
        System.out.println(name+" "+mode+": "+pass+"/"+total+" pass, "+allocated+" with user allocations; "+((System.nanoTime()-start)/1000000)+" ms");
    }
}
