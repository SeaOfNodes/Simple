package com.seaofnodes.simple.fuzzer;

import com.seaofnodes.simple.GraphEvaluator;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.StopNode;

import java.util.ArrayList;
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

    private static final int EVAL_TIMEOUT = 1000;

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
    private void recordException(Throwable e, String script, Consumer<String> reproducer) {
        for (var ex : exceptions) {
            if (FuzzerUtils.isExceptionFromSameCause(ex, e)) return;
        }
        exceptions.add(e);
        System.out.println("========== Stack ==========");
        e.printStackTrace(System.out);
        System.out.println("========== Code ===========");
        System.out.println(script);
        System.out.println("========= Reduced =========");
        System.out.println(Reducer.reduce(script, e, reproducer));
        System.out.println("===========================");
        System.out.flush();
    }

    /**
     * Check that two graphs result in the same output when supplied with a value
     * @param stop1 Stop node of the first graph
     * @param stop2 Stop node of the second graph
     * @param in The input value to both graphs to test for an equal output
     */
    private static void checkGraphs(StopNode stop1, StopNode stop2, long in) {
        var e1 = GraphEvaluator.evaluateWithResult(stop1, in, EVAL_TIMEOUT);
        var e2 = GraphEvaluator.evaluateWithResult(stop2, in, EVAL_TIMEOUT);
        if (e1.type() == GraphEvaluator.ResultType.TIMEOUT || e2.type() == GraphEvaluator.ResultType.TIMEOUT) return;
        if (e1.type() != e2.type())
            throw new RuntimeException("Different calculations types " + e1.type() + " vs " + e2.type());
        if (e1.value() != e2.value())
            throw new RuntimeException("Different calculations values " + e1.value() + " vs " + e2.value());
    }

    /**
     * Run checks for script. Compile the script with peeps enabled and disabled.
     * Check that exceptions raised in the parser by both methods are the same and only happens if the script may be invalid.
     * If the script was successfully parsed check that both version behave the same.
     * @param script The script to test
     * @param valid If the script is definitely valid. If not some exceptions may be suppressed.
     */
    private static void runCheck(String script, boolean valid) {
        StopNode stop1;
        try {
            stop1 = FuzzerUtils.parse(script, false);
        } catch (RuntimeException e1) {
            try {
                FuzzerUtils.parse(script, true);
            } catch (RuntimeException e2) {
                if (FuzzerUtils.isExceptionFromSameCause(e1, e2)) {
                    if (!valid || e1.getClass() == RuntimeException.class) return;
                } else {
                    e1.addSuppressed(e2);
                }
            }
            throw e1;
        }
        var stop2 = FuzzerUtils.parse(script, true);
        checkGraphs(stop1, stop2, 0);
        checkGraphs(stop1, stop2, 1);
        checkGraphs(stop1, stop2, 10);
    }

    /**
     * Check that the script does not result in exceptions.
     * @param script The script to test.
     * @param valid If the script is valid or if it might contain syntax errors.
     */
    private void check(String script, boolean valid) {
        try {
            runCheck(script, valid);
        } catch (Throwable e) {
            recordException(e, script, s->runCheck(s, valid));
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
            var parser = new Parser(sb.toString());

            var stop = parser.parse();
            int parse_peeps= Node.ITER_CNT;
            int parse_nops = Node.ITER_NOP_CNT;
            double parse_nop_ratio = (double)parse_nops/parse_peeps;
            int nids = Node.UID();
            if( nids <= max_nids ) return max_nids;

            double parse_peeps_per_node = (double)parse_peeps/nids;

            stop.iterate();
            int iter_peeps= Node.ITER_CNT;
            int iter_nops = Node.ITER_NOP_CNT;
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
        var valid = new ScriptGenerator(rand, sb, false).genProgram();
        check(sb.toString(), valid);
    }


    /**
     * Check that no exceptions happened.
     * @return true if no exception happened
     */
    public boolean noExceptions() {
        return exceptions.isEmpty();
    }
}
