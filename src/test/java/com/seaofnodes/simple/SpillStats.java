package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import java.util.Map;
import java.util.TreeMap;
import org.junit.runner.*;
import org.junit.runner.notification.*;

// Run existing tests and sum actual allocations, including ones without goldens.
// Helpers call record after allocation; ordinary JUnit runs collect nothing.
public class SpillStats extends RunListener {
    private static SpillStats ACTIVE;
    private Description _test;
    private final Map<String,long[]> _totals = new TreeMap<>();

    @Override public void testStarted(Description test) { _test = test; }
    public static void record(CodeGen code, String cohort, String cpu, String abi) {
        if( ACTIVE==null ) return;
        String test = ACTIVE._test.getTestClass().getSimpleName()+"."+ACTIVE._test.getMethodName();
        for( String key : new String[]{cohort+","+cpu+","+abi,cohort+",ALL,ALL"} ) {
            long[] sum = ACTIVE._totals.computeIfAbsent(key,k -> new long[3]);
            sum[0]++; sum[1] += code._regAlloc._spills; sum[2] += code._regAlloc._spillScaled;
        }
        System.out.println("allocation,"+cohort+","+test+","+cpu+","+abi+","+
                           code._regAlloc._spills+","+code._regAlloc._spillScaled);
    }

    public static void main(String[] args) throws ClassNotFoundException {
        if( args.length==0 ) args = new String[]{"Chapter20Test","BrainFuckTest","MergeSortTest"};
        Class<?>[] tests = new Class<?>[args.length];
        for( int i=0; i<args.length; i++ )
            tests[i] = Class.forName("com.seaofnodes.simple."+args[i]);
        ACTIVE = new SpillStats();
        JUnitCore junit = new JUnitCore();
        junit.addListener(ACTIVE);
        Result result = junit.run(tests);
        if( !result.wasSuccessful() ) System.out.println("FAILED test run: totals are not a complete comparison.");
        System.out.println("cohort,cpu,abi,allocations,splits,scaled");
        for( var row : ACTIVE._totals.entrySet() ) {
            long[] sum = row.getValue();
            System.out.println(row.getKey()+","+sum[0]+","+sum[1]+","+sum[2]);
        }
        for( Failure failure : result.getFailures() ) System.err.println(failure.getTrace());
        System.out.println("Tests: "+result.getRunCount()+", failures: "+result.getFailureCount());
        ACTIVE = null;
        if( !result.wasSuccessful() ) System.exit(1);
    }
}
