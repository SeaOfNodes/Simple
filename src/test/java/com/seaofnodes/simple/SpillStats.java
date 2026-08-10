package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegAllocTestSupport.CheckedCodeGen;
import com.seaofnodes.simple.type.TypeInteger;
import java.nio.file.*;
import java.util.Map;
import java.util.TreeMap;
import org.junit.runner.*;
import org.junit.runner.notification.*;

// Earlier cohorts freeze source/target membership; Chapter 25 retains native checks.
public class SpillStats extends RunListener {
    private static SpillStats ACTIVE;
    private Description _test;
    private int _qualityFailures;
    private final Map<String,long[]> _totals = new TreeMap<>();

    @Override public void testStarted(Description test) { _test = test; }
    @Override public void testFinished(Description test) { _test = null; }

    public static void record25(CodeGen code) {
        if( ACTIVE==null || ACTIVE._test==null || ACTIVE._test.getTestClass()!=Chapter25Test.class ) return;
        ACTIVE.record(code,"Chapter25",ACTIVE._test.getMethodName(),code._mach.getClass().getSimpleName(),code._callingConv);
    }

    private void record(CodeGen code, String cohort, String test, String cpu, String abi) {
        for( String key : new String[]{cohort+","+cpu+","+abi,cohort+",ALL,ALL"} ) {
            long[] sum = _totals.computeIfAbsent(key,k -> new long[3]);
            sum[0]++; sum[1] += code._regAlloc._spills; sum[2] += code._regAlloc._spillScaled;
        }
        System.out.println("allocation,"+cohort+","+test+","+cpu+","+abi+","+
                           code._regAlloc._spills+","+code._regAlloc._spillScaled);
    }

    // Keep native result checks running during a quality comparison.
    public static void checkSpills(int expected, int actual) {
        if( expected==-1 || CodeGen.iterSeedOverridden() ) return;
        try { org.junit.Assert.assertEquals("Expect spills:",expected,actual,Math.max(1,expected>>3)); }
        catch( AssertionError error ) {
            if( ACTIVE==null ) throw error;
            ACTIVE._qualityFailures++;
            System.err.println(ACTIVE._test+": "+error.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        ACTIVE = new SpillStats();
        Path dir = Path.of("src/test/java/com/seaofnodes/simple/spill");
        int failures=0, attempts=0;
        for( String line : Files.readAllLines(dir.resolve("cohorts.tsv")) ) {
            if( line.startsWith("#") || line.isBlank() ) continue;
            attempts++;
            String[] row = line.split("\t");
            try {
                String src = Files.readString(dir.resolve(row[5]));
                CodeGen code = new CheckedCodeGen(null,null,null,null,src,123L,true,TypeInteger.BOT)
                    .driver(CodeGen.Phase.RegAlloc,row[2],row[3]);
                ACTIVE.record(code,row[0],row[1],row[2],row[3]);
            } catch( Throwable error ) {
                failures++;
                System.err.println(line);
                error.printStackTrace();
            }
        }
        // Include the library's allocation even when make's sys.o is up to date.
        try {
            CodeGen code = new CheckedCodeGen("src/main/smp","build/spill-sys",null,"sys",
                Files.readString(Path.of("src/main/smp/sys.smp")),456L,true,TypeInteger.BOT)
                .driver(CodeGen.Phase.Encoding,TestC.CPU_PORT,TestC.CALL_CONVENTION);
            ACTIVE.record(code,"Chapter25","sys",TestC.CPU_PORT,TestC.CALL_CONVENTION);
        } catch( Throwable error ) {
            failures++;
            error.printStackTrace();
        }
        JUnitCore junit = new JUnitCore();
        junit.addListener(ACTIVE);
        Result result = junit.run(Chapter25Test.class);
        for( Failure failure : result.getFailures() ) System.err.println(failure.getTrace());
        failures += result.getFailureCount();
        if( failures!=0 ) System.out.println("FAILED: totals are not a complete comparison.");
        System.out.println("cohort,cpu,abi,allocations,splits,scaled");
        for( var row : ACTIVE._totals.entrySet() ) {
            long[] sum = row.getValue();
            System.out.println(row.getKey()+","+sum[0]+","+sum[1]+","+sum[2]);
        }
        System.out.println("Historical allocations: "+attempts+"; native tests: "+result.getRunCount()+"; failures: "+failures+"; spill golden failures: "+ACTIVE._qualityFailures);
        failures += ACTIVE._qualityFailures;
        ACTIVE = null;
        if( failures!=0 ) System.exit(1);
    }
}