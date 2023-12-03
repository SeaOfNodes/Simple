package com.seaofnodes.simple.fuzzer;

import com.seaofnodes.simple.GraphEvaluator;
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
 * to only show one occurrence of one problem. Generated graphs are evaluated
 * to detect compiler/evaluator failures; this chapter has no differential oracle.
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

    /** Compile and evaluate generated programs with normal peepholes. */
    private static void runCheck(String script, boolean valid) {
        StopNode stop;
        try {
            stop = FuzzerUtils.parse(script);
        } catch (RuntimeException e) {
            if (!valid || e.getClass() == RuntimeException.class) return;
            throw e;
        }
        for (int input : new int[] {0, 1, 10})
            GraphEvaluator.evaluateWithResult(stop, input, EVAL_TIMEOUT);
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

    /** Run a fixed chapter-local seed without reducing or swallowing failures. */
    public void fuzzPeepsRegression(long seed) {
        var rand = new Random(seed);
        var sb = new StringBuilder();
        var valid = new ScriptGenerator(rand, sb, false).genProgram();
        try {
            runCheck(sb.toString(), valid);
        } catch (Throwable e) {
            AssertionError ae = new AssertionError("Fuzzer regression seed failed: " + seed);
            ae.initCause(e);
            throw ae;
        }
    }
}
