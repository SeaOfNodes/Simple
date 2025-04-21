package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.CodeGen.Phase;
import com.seaofnodes.simple.print.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.stream.Collectors;

public class Simple {
    public static final String PORTS = "com.seaofnodes.simple.node.cpus";

    static final int DUMP_AFTER_PARSE = 1<<Phase.Parse.ordinal();
    static final int DUMP_AFTER_ITER = 1<<Phase.Iter.ordinal();
    static final int DUMP_AFTER_FINALIZE_IR = 1<<Phase.FinalizeIR.ordinal();
    static final int DUMP_AFTER_TYPE_CHECK = 1<<Phase.TypeCheck.ordinal();
    static final int DUMP_AFTER_LOOP_TREE = 1<<Phase.LoopTree.ordinal();
    static final int DUMP_AFTER_SELECT = 1<<Phase.Select.ordinal();
    static final int DUMP_AFTER_GCM = 1<<Phase.Schedule.ordinal();
    static final int DUMP_AFTER_LOCAL_SCHED = 1<<Phase.LocalSched.ordinal();
    static final int DUMP_AFTER_REG_ALLOC = 1<<Phase.RegAlloc.ordinal();
    static final int DUMP_AFTER_ENCODE = 1<<Phase.Encoding.ordinal();

    static final int DUMP_FINAL              = 1<<16;

    static final int DUMP_PASS_NAME          = 1<<30;
    static final int DUMP_AFTER_ALL = ((1<<Phase.values().length)-1) | DUMP_PASS_NAME;

    static final int DUMP_DISASSEMBLE        = 1<<31;

    static void usage() {
        System.out.println(
"""
simple [options] <input-file> ...args...
Options:
  --dump                   - dump final intermediate representation
  --dump-after-parse           - dump intermediate representation after parsing
  --dump-after-iter            - dump intermediate representation after Iter
  --dump-after-finalize-ir     - dump intermediate representation after FinalizeIR
  --dump-after-type-check      - dump intermediate representation after type checking
  --dump-after-loop-tree       - dump intermediate representation after loop-tree construction
  --dump-after-select          - dump intermediate representation after instruction selection
  --dump-after-gcm             - dump intermediate representation after GCM
  --dump-after-local-sched     - dump intermediate representation after local scheduling
  --dump-after-reg-alloc       - dump intermediate representation after register allocation
  --dump-after-encode          - dump intermediate representation after encoding
  --dump-after-all             - dump intermediate representation after all passes
  -S                       - dump generated assembler code
  --eval                   - evaluate the compiled code in emulator
  --run                    - run the compiled code natively; this is the default
  --dump-size              - print the size of generated code
  --dump-time              - print compilation and execution times
  --cpu <cpu-name>         - use specific CPU (x86_64_v2, riscv, arm)
  --abi <abi-name>         - use speific ABI variant (SystemV)
  --target                 - print native CPU and ABI
  --version
  --help
"""
);
        System.exit(0);
    }

    static void bad_usage() { bad("Invalid usage (use --help)"); }
    static RuntimeException bad(String err) {
        System.err.println("ERROR: "+err);
        System.exit(1);
        return new RuntimeException(err);
    }

    static void target() {
        System.out.print(TestC.CPU_PORT);
        System.out.print("-");
        System.out.println(TestC.CALL_CONVENTION);
    }

    static void dump(CodeGen code, int dump, int pass) {
        if( (dump & pass)==0 ) return;
        if( (dump & DUMP_PASS_NAME)!=0 ) System.err.println("Final:");
        System.err.println(code);
    }

    static void print_compilation_times(CodeGen code) {
        long total=0;
        for( Phase phase : Phase.values() ) {
            long time=code._times[phase.ordinal()];
            total+=time;
            System.out.printf("%-24s %.3f sec%n",phase+" Time:",time/1e3);
        }
        System.out.printf("TOTAL COMPILATION TIME:   %.3f sec%n",total/1e3);
    }

    static String getInput() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    public static void main(String[] args) throws Exception {
        String input_filename = null;
        boolean do_eval = false;
        boolean do_run = true;
        boolean do_codegen = false;
        boolean do_print_size = false;
        boolean do_print_time = false;
        int dump = 0;
        int first_arg = 0;
        String src = null;
        String cpu = null;
        String abi = null;

        // Parse command line
        int i; for( i = 0; i < args.length; i++ ) {
            String arg = args[i];
            if( arg.charAt(0) != '-' ) {
                input_filename = arg;
                break;
            }

            switch (arg) {
            case "-":                         input_filename=""; break;
            case "--dump":                    dump |= DUMP_FINAL; break;
            case "--dump-after-parse":          dump |= DUMP_AFTER_PARSE; break;
            case "--dump-after-finalize-ir":    dump |= DUMP_AFTER_FINALIZE_IR; break;
            case "--dump-after-iter":           dump |= DUMP_AFTER_ITER; break;
            case "--dump-after-type-check":     dump |= DUMP_AFTER_TYPE_CHECK; break;
            case "--dump-after-loop-tree":      dump |= DUMP_AFTER_LOOP_TREE; break;
            case "--dump-after-select":         dump |= DUMP_AFTER_SELECT; break;
            case "--dump-after-gcm":            dump |= DUMP_AFTER_GCM; break;
            case "--dump-after-local-sched":    dump |= DUMP_AFTER_LOCAL_SCHED; break;
            case "--dump-after-reg-alloc":      dump |= DUMP_AFTER_REG_ALLOC; break;
            case "--dump-after-encode":         dump |= DUMP_AFTER_ENCODE; break;
            case "--dump-after-all":            dump |= DUMP_AFTER_ALL; break;
            case "-S":                        dump |= DUMP_DISASSEMBLE; break;
            case "--eval":                    do_eval = true ; do_run = false; break;
            case "--run":                     do_run  = true ; break;
            case "--norun":                   do_run  = false; break;
            case "--dump-size":               do_print_size = true; break;
            case "--dump-time":               do_print_time = true; break;
            case "--cpu":                     if (cpu != null || i + 1 >= args.length || args[i + 1].charAt(0) == '-') bad_usage();
                                              cpu = args[++i];
                                              break;
            case "--abi":                     if (abi != null || i + 1 >= args.length || args[i + 1].charAt(0) == '-') bad_usage();
                                              abi = args[++i];
                                              break;
            case "--target":                  target(); break;
            case "--version":                 throw Utils.TODO();
            case "--help":                    usage();
            default:                          bad_usage();
            }
        }

        if (input_filename == null) throw bad("no input file' (use --help)");
        if( !input_filename.isEmpty() && !input_filename.endsWith(".smp") )
            throw bad("File extension must be .smp");
        String base = input_filename.substring(0,input_filename.length()-4);

        if (do_run || (dump & DUMP_DISASSEMBLE) != 0 || do_print_size) {
            if (cpu == null) cpu = TestC.CPU_PORT;
            if (abi == null) abi = TestC.CALL_CONVENTION;
            do_codegen = true;
        }

        // Read input file
        try {
            src = input_filename.isEmpty() ? getInput() : Files.readString(Path.of(input_filename));
        } catch( IOException e ) { throw bad("Cannot read input file: "+input_filename);  }

        // Compilation pipeline
        CodeGen code = new CodeGen(src);

        code.driver(do_codegen ? Phase.Encoding : Phase.LocalSched,
                    do_codegen ? cpu : null, abi, dump);
        if( do_codegen ) code.exportELF(base+".o");

        dump(code, dump, DUMP_FINAL);

        if (do_codegen && (dump & DUMP_DISASSEMBLE) != 0) {
            System.out.println(code.asm());
        }

        if (do_print_size) {
             System.out.printf( "Code Size: %d%n", code._encoding._bits.size());
        }

        if (do_print_time) {
            print_compilation_times(code);
        }

        if (do_eval) {
            // TODO: Support for evaluation of functions with different argument numbers and types
            long t = System.currentTimeMillis();
            long arg = (i+1 < args.length) ? Integer.parseInt(args[i+1]) : 0;
            System.out.println(Eval2.eval(code, arg, 100000));
            if (do_print_time) {
                System.out.printf( "EXECUTION TIME:             %.3f sec%n",
                    (System.currentTimeMillis() - t) / 1e3);
            }
        }
        if (do_run) {
            if ( !TestC.CPU_PORT.equals( cpu ) || !TestC.CALL_CONVENTION.equals( abi ) )
                throw bad("cannot run code on not native target");
            String exe = TestC.OS.startsWith("Windows") ? base+".exe" : base;
            String result = com.seaofnodes.simple.TestC.gcc(base+".o", null, null, true, exe);
            System.out.print(result);
        }
    }
}
