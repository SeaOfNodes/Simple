package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.print.*;
import com.seaofnodes.simple.util.Utils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

public class Simple {
    public static final String PORTS = "com.seaofnodes.simple.node.cpus";

    static final int DUMP_AFTER_PARSE        = 1<<0;
    static final int DUMP_AFTER_OPTO         = 1<<1;
    static final int DUMP_AFTER_TYPE_CHECK   = 1<<2;
    static final int DUMP_AFTER_LOOP_TREE    = 1<<3;
    static final int DUMP_AFTER_SELECT       = 1<<4;
    static final int DUMP_AFTER_GCM          = 1<<5;
    static final int DUMP_AFTER_LOCAL_SCHED  = 1<<6;
    static final int DUMP_AFTER_REG_ALLOC    = 1<<7;
    static final int DUMP_AFTER_ENCODE       = 1<<8;

    static final int DUMP_FINAL              = 1<<16;

    static final int DUMP_DOT                = 1<<29;
    static final int DUMP_PASS_NAME          = 1<<30;
    static final int DUMP_AFTER_ALL          = 0xff | DUMP_PASS_NAME;
    static final int DUMP_DOT_AFTER_ALL      = 0xff | DUMP_DOT;

    static final int DUMP_DISASSEMBLE        = 1<<31;

    static void usage() {
        System.out.println(
"""
simple [options] <input-file> ...args...
Options:
  --dump                   - dump final intermediate representation
  --dump-after-parse       - dump intermediate representation after parse
  --dump-after-opto        - dump intermediate representation after opto pass
  --dump-after-type-check  - dump intermediate representation after type check pass
  --dump-after-select      - dump intermediate representation after instrution selection pass
  --dump-after-gcm         - dump intermediate representation after GCM pass
  --dump-after-local-sched - dump intermediate representation after local scheduling pass
  --dump-after-reg-alloc   - dump intermediate representation after register allocation pass
  --dump-after-encode      - dump intermediate representation after encoding pass
  --dump-after-all         - dump intermediate representation after all passes
  --dot                    - dump grapical representation of intermediate code into *.dot file(s)
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
        if ((dump & pass) != 0) {
            if ((dump & DUMP_DOT) != 0) {
                String fn = switch (pass) {
                case DUMP_AFTER_PARSE        -> "01-parse.dot";
                case DUMP_AFTER_OPTO         -> "02-opto.dot";
                case DUMP_AFTER_TYPE_CHECK   -> "03-type_check.dot";
                case DUMP_AFTER_LOOP_TREE    -> "04-loop_tree.dot";
                case DUMP_AFTER_SELECT       -> "05-instr_select.dot";
                case DUMP_AFTER_GCM          -> "06-gcm.dot";
                case DUMP_AFTER_LOCAL_SCHED  -> "07-local_sched.dot";
                case DUMP_AFTER_REG_ALLOC    -> "08-reg_allos.dot";
                case DUMP_AFTER_ENCODE       -> "09-local_sched.dot";
                case DUMP_FINAL              -> "10-final.dot";
                default                      -> throw Utils.TODO();
                };

                try {
                    Files.writeString(Path.of(fn),
                                      new GraphVisualizer().generateDotOutput(code._stop, null, null));
                } catch(IOException e) { throw bad("Cannot write DOT file"); }
            } else {
                if ((dump & DUMP_PASS_NAME) != 0) {
                    System.err.println(switch (pass) {
                        case DUMP_AFTER_PARSE        -> "After Parse:";
                        case DUMP_AFTER_OPTO         -> "After OPTO:";
                        case DUMP_AFTER_TYPE_CHECK   -> "After Type Check:";
                        case DUMP_AFTER_LOOP_TREE    -> "After Loop Tree:";
                        case DUMP_AFTER_SELECT       -> "After Code Selection:";
                        case DUMP_AFTER_GCM          -> "After GCM:";
                        case DUMP_AFTER_LOCAL_SCHED  -> "After Local Scheduling:";
                        case DUMP_AFTER_REG_ALLOC    -> "After Register Allocation:";
                        case DUMP_AFTER_ENCODE       -> "After Encoding:";
                        case DUMP_FINAL              -> "Final:";
                        default                      -> throw Utils.TODO();
                    });
                }

                System.err.println(IRPrinter.prettyPrint(code._stop, 9999));
            }
        }
    }

    static void print_compilation_times(CodeGen code) {
        double t, total = 0;

        total += t = code._tParse / 1e3;
        System.out.printf( "Parsing Time:             %.3f sec%n", t);
        total += t = code._tOpto / 1e3;
        System.out.printf( "Optimization Time:        %.3f sec%n", t);
        total += t = code._tTypeCheck / 1e3;
        System.out.printf( "Type Checking Time:       %.3f sec%n", t);
        total += t = code._tLoopTree / 1e3;
        System.out.printf( "Loop Tree Time:           %.3f sec%n", t);
        total += t = code._tInsSel / 1e3;
        System.out.printf( "Code Selection Time:      %.3f sec%n", t);
        total += t = code._tGCM / 1e3;
        System.out.printf( "GCM Time:                 %.3f sec%n", t);
        total += t = code._tLocal / 1e3;
        System.out.printf( "Local Scheduling Time:    %.3f sec%n", t);
        total += t = code._tRegAlloc / 1e3;
        System.out.printf( "Register Allocation Time: %.3f sec%n", t);
        total += t = code._tEncode / 1e3;
        System.out.printf( "Encoding Time:            %.3f sec%n", t);
        System.out.printf( "TOTAL COMPILATION TIME:   %.3f sec%n", total);
    }

    static String getInput() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        return reader.lines().collect(Collectors.joining("\n"));
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
            case "--dump-after-parse":        dump |= DUMP_AFTER_PARSE; break;
            case "--dump-after-opto":         dump |= DUMP_AFTER_OPTO; break;
            case "--dump-after-type-check":   dump |= DUMP_AFTER_TYPE_CHECK; break;
            case "--dump-after-loop-tree":    dump |= DUMP_AFTER_LOOP_TREE; break;
            case "--dump-after-select":       dump |= DUMP_AFTER_SELECT; break;
            case "--dump-after-gcm":          dump |= DUMP_AFTER_GCM; break;
            case "--dump-after-local-sched":  dump |= DUMP_AFTER_LOCAL_SCHED; break;
            case "--dump-after-reg-alloc":    dump |= DUMP_AFTER_REG_ALLOC; break;
            case "--dump-after-encode":       dump |= DUMP_AFTER_ENCODE; break;
            case "--dump-after-all":          dump |= DUMP_AFTER_ALL; break;
            case "--dot":                     dump |= DUMP_DOT; break;
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

        code.parse();
        dump(code, dump, DUMP_AFTER_PARSE);

        code.opto();
        dump(code, dump, DUMP_AFTER_OPTO);

        code.typeCheck();
        dump(code, dump, DUMP_AFTER_TYPE_CHECK);

        code.loopTree();
        dump(code, dump, DUMP_AFTER_LOOP_TREE);

        if (do_codegen) {
            code.instSelect(cpu, abi, PORTS);
            dump(code, dump, DUMP_AFTER_SELECT);
        }

        code.GCM();
        dump(code, dump, DUMP_AFTER_GCM);

        code.localSched();
        dump(code, dump, DUMP_AFTER_LOCAL_SCHED);

        if (do_codegen) {
            code.regAlloc();
            dump(code, dump, DUMP_AFTER_REG_ALLOC);

            code.encode();
            dump(code, dump, DUMP_AFTER_ENCODE);

            code.exportELF(base+".o");
        }

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
            String result = TestC.gcc(base+".o", null, null, true, exe);
            System.out.print(result);
        }
    }
}
