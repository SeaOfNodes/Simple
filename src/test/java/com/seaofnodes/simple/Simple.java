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
    static final int DUMP_AFTER_TYPE_CHECK = 1<<Phase.TypeCheck.ordinal();
    static final int DUMP_AFTER_LOOP_TREE = 1<<Phase.LoopTree.ordinal();
    static final int DUMP_AFTER_INSTR_SELECT = 1<<Phase.InstSelect.ordinal();
    static final int DUMP_AFTER_GCM = 1<<Phase.Schedule.ordinal();
    static final int DUMP_AFTER_LOCAL_SCHED = 1<<Phase.LocalSched.ordinal();
    static final int DUMP_AFTER_REG_ALLOC = 1<<Phase.RegAlloc.ordinal();
    static final int DUMP_AFTER_ENCODE = 1<<Phase.Encoding.ordinal();

    static final int DUMP_FINAL              = 1<<16;

    static final int DUMP_PASS_NAME          = 1<<30;
    static final int DUMP_AFTER_ALL = ((1<<Phase.values().length)-1) | DUMP_PASS_NAME;

    static final int DUMP_DISASSEMBLE        = 1<<31;

    static String system_cpu = null;
    static String system_abi = null;

    static void usage() {
        System.out.println(
"""
simple <input-file> [options] [--eval|--run ...]
Options:
  --dump                     - dump final intermediate representation
  --dump-after-parse           - dump intermediate representation after parsing
  --dump-after-iter            - dump intermediate representation after Iter
  --dump-after-type-check      - dump intermediate representation after type checking
  --dump-after-loop-tree       - dump intermediate representation after loop-tree construction
  --dump-after-instr-select    - dump intermediate representation after instruction selection
  --dump-after-gcm             - dump intermediate representation after GCM
  --dump-after-local-sched     - dump intermediate representation after local scheduling
  --dump-after-reg-alloc       - dump intermediate representation after register allocation
  --dump-after-encode          - dump intermediate representation after encoding
  --dump-after-all             - dump intermediate representation after all passes
  -S                         - dump generated assembler code
  --eval ...                 - evaluate the compiled code in emulator
  --run ...                  - run the compiled code natively
  --dump-size                - print the size of generated code
  --dump-time                - print compilation and execution times
  --cpu <cpu-name>           - use specific CPU (x86_64_v2, riscv, arm)
  --abi <abi-name>           - use speific ABI variant (SystemV)
  --target                   - print native CPU and ABI
  --version
  --help
"""
);
        System.exit(0);
    }

    static void bad_usage() {
        System.err.println("ERROR: Invalid usage (use --help)");
        System.exit(1);
    }

    static void target() {
        if (system_cpu == null || system_abi == null) {
            System.err.println("ERROR: Unknown target");
            System.exit(1);
        }
        System.out.print(system_cpu);
        System.out.print("-");
        System.out.println(system_abi);
        System.exit(0);
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
        boolean use_stdin = false;
        boolean do_eval = false;
        boolean do_run = false;
        boolean do_codegen = false;
        boolean do_print_size = false;
        boolean do_print_time = false;
        int dump = 0;
        int first_arg = 0;
        String src = null;
        String cpu = null;
        String abi = null;

        // TODO: autodetect
        system_cpu = "x86_64_v2";
        system_abi = "SystemV";

        // Parse command line
loop:   for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.charAt(0) == '-') {
                switch (arg) {
                    case "-":                         use_stdin = true; break;
                    case "--dump":                    dump |= DUMP_FINAL; break;
                    case "--dump-after-parse":          dump |= DUMP_AFTER_PARSE; break;
                    case "--dump-after-iter":           dump |= DUMP_AFTER_ITER; break;
                    case "--dump-after-type-check":     dump |= DUMP_AFTER_TYPE_CHECK; break;
                    case "--dump-after-loop-tree":      dump |= DUMP_AFTER_LOOP_TREE; break;
                    case "--dump-after-instr-select":   dump |= DUMP_AFTER_INSTR_SELECT; break;
                    case "--dump-after-gcm":            dump |= DUMP_AFTER_GCM; break;
                    case "--dump-after-local-sched":    dump |= DUMP_AFTER_LOCAL_SCHED; break;
                    case "--dump-after-reg-alloc":      dump |= DUMP_AFTER_REG_ALLOC; break;
                    case "--dump-after-encode":         dump |= DUMP_AFTER_ENCODE; break;
                    case "--dump-after-all":            dump |= DUMP_AFTER_ALL; break;
                    case "-S":                        dump |= DUMP_DISASSEMBLE; break;
                    case "--eval":                    do_eval = true; first_arg = i + 1; break loop;
                    case "--run":                     do_run = true; first_arg = i + 1; break loop;
                    case "--dump-size":               do_print_size = true; break;
                    case "--dump-time":               do_print_time = true; break;
                    case "--cpu":                     if (cpu != null
                                                       || i + 1 >= args.length
                                                       || args[i + 1].charAt(0) == '-') bad_usage();
                                                      cpu = args[i + 1];
                                                      i++;
                                                      break;
                    case "--abi":                     if (abi != null
                                                       || i + 1 >= args.length
                                                       || args[i + 1].charAt(0) == '-') bad_usage();
                                                      abi = args[i + 1];
                                                      i++;
                                                      break;
                    case "--target":                  target();
                    case "--version":                 throw Utils.TODO();
                    case "--help":                    usage();
                    default:                          bad_usage();
                }
            } else {
                if (input_filename != null || use_stdin) bad_usage();
                input_filename = arg;
            }
        }

        if (input_filename == null && !use_stdin) {
            System.err.println("ERROR: no input file' (use --help)");
            System.exit(1);
        }

        if (do_run || (dump & DUMP_DISASSEMBLE) != 0 || do_print_size) {
            if (cpu == null) cpu = system_cpu;
            if (abi == null) abi = system_abi;
            if (cpu == null || abi == null) {
                System.err.println("ERROR: Cannot compile for unknown target");
                System.exit(1);
            }
            do_codegen = true;
        }

        // Read input file
        try {
            src = use_stdin ? getInput() : Files.readString(Path.of(input_filename));
        } catch(IOException e) {
            System.err.println("ERROR: Cannot read input file");
            System.exit(1);
        }

        // Compilation pipeline
        CodeGen code = new CodeGen(src);

        code.driver(do_codegen ? Phase.Encoding : Phase.LocalSched,
                    do_codegen ? cpu : null, abi, dump);

        dump(code, dump, DUMP_FINAL);

        if (do_codegen && (dump & DUMP_DISASSEMBLE) != 0) {
            System.out.println(code.asm());
        }

        if (do_print_size) {
             System.out.println(String.format("Code Size: %d", code._encoding._bits.size()));
        }

        if (do_print_time) {
            print_compilation_times(code);
        }

        if (do_eval) {
            // TODO: Support for evaluation of functions with different argument numbers and types
            long t = System.currentTimeMillis();
            long arg = (first_arg < args.length) ? Integer.valueOf(args[first_arg]) : 0;
            System.out.println(Eval2.eval(code, arg, 100000));
            if (do_print_time) {
                System.out.println(String.format("EXECUTION TIME:             %.3f sec",
                    (System.currentTimeMillis() - t) / 1e3));
            }
        } else if (do_run) {
            if (cpu != system_cpu || abi != system_abi) {
                System.err.println("ERROR: cannot run code on not native target");
                System.exit(1);
            }

            throw Utils.TODO();
        }
    }
}
