package com.seaofnodes.simple.fuzzer;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.Eval2;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.Node;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Implementation of the fuzzer.
 * Random testcases are generated using the ScriptGenerator.
 * This will generate a script similar to how the parser parses it but instead of parsing it will
 * randomly choose a variation the parser would parse and generate it.
 * These scripts are the parsed by the parser and all exceptions are caught and filtered
 * to only show one occurrence of one problem. This includes script which generate different
 * results when the compiled graph with and without peeps is executed.
 * To aid debugging scripts that cause errors are then reduced by applying rules and checking
 * that the same issue persists.
 */
public class Fuzzer {

    private static final int EVAL_TIMEOUT = 10000;

    /**
     * List of exceptions already encountered.
     * This is used to filter new exceptions and discard them if they are already found.
     */
    private final ArrayList<Throwable> exceptions = new ArrayList<>();

    /**
     * Filter and record an exception in a script.
     * @param e The exception caused by the script
     * @param script The script causing the exception
     * @param reproducer Reproducer which should be used to reproduce the test case.
     */
    private void recordException(Throwable e, String script, Consumer<String> reproducer, long seed) {
        for (var ex : exceptions) {
            if (FuzzerUtils.isExceptionFromSameCause(ex, e)) return;
        }
        exceptions.add(e);
        System.out.println("========== Stack ==========");
        e.printStackTrace(System.out);
        System.out.println("========== Seed ==========");
        System.out.println(seed);
        //System.out.println("========== Code ===========");
        //System.out.println(script);
        System.out.println("========= Reduced =========");
        System.out.println(Reducer.reduce(script, e, reproducer));
        System.out.println("===========================");
        System.out.flush();
    }

    private static boolean neq(Object a, Object b) {
        if (Objects.equals(a, b)) return false;
        return true;
    }

    /**
     * Check that two graphs result in the same output when supplied with a value
     * @param e1 Evaluator for the first graph
     * @param e2 Evaluator for the second graph
     * @param in The input value to both graphs to test for an equal output
     */
    private static void checkGraphs(CodeGen code1, CodeGen code2, long in) {
        var r1 = Eval2.eval(code1, in, EVAL_TIMEOUT);
        var r2 = Eval2.eval(code2, in, EVAL_TIMEOUT);
        if( r1 == null || r2 == null ) return; // Timeout test invalid
        if( neq(r1, r2) )
            throw new RuntimeException("Different calculations values " + r1 + " vs " + r2);
    }

    /**
     * Run checks for script. Compile the script with peeps enabled and disabled.
     * Check that exceptions raised in the parser by both methods are the same and only happens if the script may be invalid.
     * If the script was successfully parsed check that both version behave the same.
     * @param script The script to test
     * @param valid If the script is definitely valid. If not some exceptions may be suppressed.
     */
    private static void runCheck(String script, boolean valid) {
        CodeGen code1;
        try {
            code1 = FuzzerUtils.parse(script, 123, true);
        } catch (RuntimeException e1) {
            try {
                FuzzerUtils.parse(script, 456, false);
            } catch (RuntimeException e2) {
                if (FuzzerUtils.isExceptionFromSameCause(e1, e2)) {
                    if (!valid || e1.getClass() == Parser.ParseException.class) return;
                } else {
                    e1.addSuppressed(e2);
                }
            }
            throw e1;
        }
        CodeGen code2 = FuzzerUtils.parse(script, 456, false);
        checkGraphs(code1, code2, 0);
        checkGraphs(code1, code2, 1);
        checkGraphs(code1, code2, 10);
    }

    /**
     * Check that the script does not result in exceptions.
     * @param script The script to test.
     * @param valid If the script is valid or if it might contain syntax errors.
     */
    private void check(String script, boolean valid, long seed) {
        try {
            runCheck(script, valid);
        } catch (Throwable e) {
            recordException(e, script, s->runCheck(s, valid), seed);
        }
    }

    /**
     * Run one test with the given seed, and check for peephole counts.
     * @param seed The seed to use for generating this test case
     */
    public int fuzzPeepTiming(long seed, int max_nids) {
        var rand = new Random(seed);
        var sb = new StringBuilder();
        new ScriptGenerator(rand, sb, true).genProgram();
        try {
            var code = new CodeGen(sb.toString());

            code.parse();
            int nids = code.UID();
            if( nids <= max_nids ) return max_nids; // Smaller, looking for trends as we grow
            int parse_peeps= code._iter_cnt;
            int parse_nops = code._iter_nop_cnt;
            double parse_nop_ratio = (double)parse_nops/parse_peeps;
            double parse_peeps_per_node = (double)parse_peeps/nids;

            code.opto();
            int iter_peeps= code._iter_cnt;
            int iter_nops = code._iter_nop_cnt;
            double iter_nop_ratio = (double)iter_nops/iter_peeps;
            double iter_peeps_per_node = (double)iter_peeps/nids;

            System.out.printf("%6d | Parsing: peeps: %5d, nops: %5d, ratio: %5.3f, last UID: %5d ps/node: %5.3f  | Iter: peeps: %6d, nops: %6d, ratio: %5.3f, ps/node: %5.3f\n",
                              seed,
                              parse_peeps,parse_nops,parse_nop_ratio,nids,parse_peeps_per_node,
                              iter_peeps , iter_nops, iter_nop_ratio,      iter_peeps_per_node);
            return nids;
        } catch (Throwable e) {
            // ignore exceptions, let the normal fullPeeps catch and reduce,
            // we're interested in valid program optimizations
        }
        return max_nids;
    }


    /**
     * Run one test with the given seed.
     * @param seed The seed to use for generating this test case
     */
    public void fuzzPeeps(long seed) {
        var rand = new Random(seed);
        var sb = new StringBuilder();
        var valid = new ScriptGenerator(rand, sb, true).genProgram();
        check(sb.toString(), valid, seed);
    }


    /**
     * Check that no exceptions happened.
     * @return true if no exception happened
     */
    public boolean noExceptions() {
        return exceptions.isEmpty();
    }
}
