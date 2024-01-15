package com.seaofnodes.simple;

import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.StopNode;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Random;

import static org.junit.Assert.assertTrue;

public class Fuzzer08Test {

    private final ArrayList<Throwable> exceptions = new ArrayList<>();

    public static StopNode parse(String script, boolean disablePeeps) {
        var parser = new Parser(script);
        Node._disablePeephole = disablePeeps;
        return parser.parse();
    }

    private static boolean sameException(Throwable e1, Throwable e2) {
        if (e1 == null || e2 == null) return e1 == e2;
        if (e1.getClass() != e2.getClass()) return false;
        if (e1.getMessage() != null && e2.getMessage() != null && !e1.getMessage().equals(e2.getMessage())) return false;
        var s1 = e1.getStackTrace();
        var s2 = e2.getStackTrace();
        if (s1 != null && s1.length > 0 && s2 != null && s2.length > 0 && !s1[0].equals(s2[0])) return false;
        if (!sameException(e1.getCause(), e2.getCause())) return false;
        var o1 = e1.getSuppressed();
        var o2 = e2.getSuppressed();
        if (o1 == null || o2 == null || o1.length != o2.length) return o1 == o2;
        for (int i=0; i<o1.length; i++) {
            if (!sameException(o1[i], o2[i])) return false;
        }
        return true;
    }

    private void recordException(Throwable e, String script) {
        for (var ex : exceptions) {
            if (sameException(ex, e)) return;
        }
        exceptions.add(e);
        System.out.println("===========================");
        System.out.println("========== Stack ==========");
        System.out.flush();
        e.printStackTrace(System.err);
        System.err.flush();
        System.out.println("========== Code ===========");
        System.out.println(script);
        System.out.println("========= Reduced =========");
        System.out.println(Reducer.reduce(script, false));
        System.out.flush();
    }

    private void checkGraphs(StopNode stop1, StopNode stop2, long in, String script) {
        long e1;
        try {
            e1 = GraphEvaluator.evaluate(stop1, in);
        } catch (RuntimeException e) {
            if (!e.getMessage().equals("Timeout")) recordException(e, script);
            return;
        }
        long e2;
        try {
            e2 = GraphEvaluator.evaluate(stop2, in);
        } catch (RuntimeException e) {
            if (!e.getMessage().equals("Timeout")) recordException(e, script);
            return;
        }
        if (e1 != e2) {
            recordException(new RuntimeException("Different calculations " + e1 + " vs " + e2), script);
        }
    }

    private void check(String script, boolean valid) {
        StopNode stop1;
        try {
            stop1 = parse(script, true);
        } catch (Throwable e1) {
            try {
                parse(script, false);
                var rt = new RuntimeException("Exception only without peeps " + e1.getMessage());
                rt.addSuppressed(e1);
                recordException(rt, script);
                return;
            } catch (Throwable e2) {
                if (e1.getClass() == e2.getClass() && e1.getMessage().equals(e2.getMessage())) {
                    if (valid || e1.getClass() != RuntimeException.class) recordException(e1, script);
                    return;
                }
                var rt = new RuntimeException("Bad different exceptions " + e1.getMessage() + " & " + e2.getMessage());
                rt.addSuppressed(e1);
                rt.addSuppressed(e2);
                recordException(rt, script);
                return;
            }
        }
        StopNode stop2;
        try {
            stop2 = parse(script, false);
        } catch (Throwable e2) {
            var rt = new RuntimeException("Exception only with peeps " + e2.getMessage());
            rt.addSuppressed(e2);
            recordException(rt, script);
            return;
        }
        checkGraphs(stop1, stop2, 0, script);
        checkGraphs(stop1, stop2, 1, script);
        checkGraphs(stop1, stop2, 10, script);
    }

    private void checkParsing(String script) {
        try {
            parse(script, false);
        } catch (Throwable e) {
            recordException(e, script);
        }
    }

    public void fuzzParser(long seed) {
        var rand = new Random(seed);
        var sb = new StringBuilder();
        new ScriptGenerator(rand, sb, true).genProgram();
        checkParsing(sb.toString());
    }

    public void fuzzPeeps(long seed) {
        var rand = new Random(seed);
        var sb = new StringBuilder();
        var valid = new ScriptGenerator(rand, sb, false).genProgram();
        check(sb.toString(), valid);
    }

    @Test
    @Ignore
    public void fuzzParser() {
        var fuzzer = new Fuzzer08Test();
        for (int i=0; i<1000000; i++)
            fuzzer.fuzzParser(i);
        assertTrue(fuzzer.exceptions.isEmpty());
    }

    @Test
    @Ignore
    public void fuzzPeeps() {
        var fuzzer = new Fuzzer08Test();
        for (int i=0; i<1000000; i++)
            fuzzer.fuzzPeeps(i);
        assertTrue(fuzzer.exceptions.isEmpty());
    }

}
