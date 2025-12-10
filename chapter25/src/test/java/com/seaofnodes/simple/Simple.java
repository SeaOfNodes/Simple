package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.print.*;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.simple.util.Utils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
Options are applied in-order.
Options:
  --help - This text

  --root root/ - Project root directory; this must end in a '/' and input
         files are relative paths to this.  If missing, the one input
         file path is treated as its own root directory.

  -o output - Project output directory; similar to --root, if this ends in a
     '/', then all output files are relative paths to this using the input file
     root-relative path.  If --root is missing, the one result file is placed
     here, using this path as the output directory.

  --eval                   - (slowly) evaluate the compiled code in emulator
  --run                    - run the compiled code natively; this is the default
  --cpu <cpu-name>         - use specific CPU (x86_64_v2, riscv, arm)
  --abi <abi-name>         - use specific ABI variant (SystemV)
  --target                 - print native CPU and ABI
  --version
  -S                       - dump generated assembler code
  --dump-size              - print the size of generated code
  --dump-time              - print compilation and execution times
  --dump                   - dump final intermediate representation
  --dump-after-parse       - dump intermediate representation after parse
  --dump-after-opto        - dump intermediate representation after opto pass
  --dump-after-type-check  - dump intermediate representation after type check pass
  --dump-after-select      - dump intermediate representation after instruction selection pass
  --dump-after-gcm         - dump intermediate representation after GCM pass
  --dump-after-local-sched - dump intermediate representation after local scheduling pass
  --dump-after-reg-alloc   - dump intermediate representation after register allocation pass
  --dump-after-encode      - dump intermediate representation after encoding pass
  --dump-after-all         - dump intermediate representation after all passes
  --dot                    - dump grapical representation of intermediate code into *.dot file(s)
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

    static void print_compilation_times(CodeGen code, long total) {
        long[] times = code._times;

        System.out.printf( "Parsing Time:             %.3f sec%n", times[CodeGen.Phase.Parse     .ordinal()]);
        System.out.printf( "Optimization Time:        %.3f sec%n", times[CodeGen.Phase.Opto      .ordinal()]);
        System.out.printf( "Type Checking Time:       %.3f sec%n", times[CodeGen.Phase.TypeCheck .ordinal()]);
        System.out.printf( "Loop Tree Time:           %.3f sec%n", times[CodeGen.Phase.LoopTree  .ordinal()]);
        System.out.printf( "Code Selection Time:      %.3f sec%n", times[CodeGen.Phase.Select    .ordinal()]);
        System.out.printf( "Unlink Time:              %.3f sec%n", times[CodeGen.Phase.Unlink    .ordinal()]);
        System.out.printf( "GCM Time:                 %.3f sec%n", times[CodeGen.Phase.Schedule  .ordinal()]);
        System.out.printf( "Local Scheduling Time:    %.3f sec%n", times[CodeGen.Phase.LocalSched.ordinal()]);
        System.out.printf( "Register Allocation Time: %.3f sec%n", times[CodeGen.Phase.RegAlloc  .ordinal()]);
        System.out.printf( "Encoding Time:            %.3f sec%n", times[CodeGen.Phase.Encoding  .ordinal()]);
        System.out.printf( "Export Time:              %.3f sec%n", times[CodeGen.Phase.Export    .ordinal()]);
        System.out.printf( "TOTAL COMPILATION TIME:   %.3f sec%n", total);
    }

    static String getInput() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        return reader.lines().collect(Collectors.joining("\n"));
    }

    public static void main(String[] args) throws Exception {
        long t0 = System.currentTimeMillis();

        String root = null;
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
        String out = null;

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
            case "-o":                        if (out != null || i + 1 >= args.length || args[i + 1].charAt(0) == '-') bad_usage();
                                              out = args[++i];  do_codegen=true;
                                              break;
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

        // Break the paths into these parts:
        // - A module path, given from "--root" or inferred from the one input file
        // - Some sources [modpath/relpath/file.smp]*, all relative to the module path.
        // - A build path, given from "-o" or inferred from the one input file
        //
        // All the sources are compiled, with outputs in [buildpath/relpath/file.o]*

        if (input_filename == null) throw bad("no input file' (use --help)");
        if( !input_filename.isEmpty() && !input_filename.endsWith(".smp") )
            throw bad("File extension must be .smp");
        Path inPath = Path.of(input_filename);

        // Figure out the module path
        Path modPath;
        if( root == null ) {
            modPath = inPath.getParent();
        } else {
            throw Utils.TODO();
        }

        // Compute the module-relative paths for all sources
        Path srcRelPath = modPath.relativize(inPath);
        int n = srcRelPath.getNameCount();
        Path relPath = n==1 ? null : srcRelPath.subpath(0,n-1);
        // Compute source/class name
        String srcNameExt = srcRelPath.getName(n-1).toString();
        String srcName = srcNameExt.substring(0,srcNameExt.length()-4);

        // Compute the build path.
        Path outPath;
        if( out == null ) {
            // If no output given, the build dir is the modPath plus "build"
            outPath = Path.of(modPath.toString(),"build");
        } else {
            // If the output is a directory, thats our out path
            outPath = Path.of(out);
            if( !out.endsWith("/") ) {
                // If the output is a file, the directory is our outPath
                Path fullOut = outPath;
                outPath = outPath.getParent();
                Path foo = outPath.relativize(fullOut);
                if( !foo.toString().equals(srcName+".o") )
                    throw bad("Output path needs to be of the form "+srcName+".o");
            }
        }

        if( do_run || (dump & DUMP_DISASSEMBLE) != 0 || do_print_size ) {
            if (cpu == null) cpu = TestC.CPU_PORT;
            if (abi == null) abi = TestC.CALL_CONVENTION;
            do_codegen = true;
        }

        // Read input file
        try {
            src = input_filename.isEmpty() ? getInput() : Files.readString(Path.of(input_filename));
        } catch( IOException e ) { throw bad("Cannot read input file: "+input_filename);  }

        // Compilation pipeline
        Ary<String> externPaths = null;
        CodeGen code = new CodeGen(modPath.toString(), outPath.toString(), externPaths, srcName, src, 456, TypeInteger.BOT );

        code.parse();
        dump(code, dump, DUMP_AFTER_PARSE);

        code.opto();
        dump(code, dump, DUMP_AFTER_OPTO);

        code.typeCheck();
        dump(code, dump, DUMP_AFTER_TYPE_CHECK);

        code.loopTree();
        dump(code, dump, DUMP_AFTER_LOOP_TREE);

        if (do_codegen) {
            code.serialize();

            code.instSelect(cpu, abi, PORTS);
            dump(code, dump, DUMP_AFTER_SELECT);
        }

        code.unlink();

        code.GCM();
        dump(code, dump, DUMP_AFTER_GCM);

        code.localSched();
        dump(code, dump, DUMP_AFTER_LOCAL_SCHED);

        if (do_codegen) {
            code.regAlloc();
            dump(code, dump, DUMP_AFTER_REG_ALLOC);

            code.encode();
            dump(code, dump, DUMP_AFTER_ENCODE);

            code.exportELF(out);
        }

        dump(code, dump, DUMP_FINAL);

        if (do_codegen && (dump & DUMP_DISASSEMBLE) != 0) {
            System.out.println(code.asm());
        }

        if (do_print_size) {
             System.out.printf( "Code Size: %d%n", code._encoding._bits.size());
        }

        long total = System.currentTimeMillis() - t0;
        if (do_print_time) {
            print_compilation_times(code,total);
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
            String exe = TestC.OS.startsWith("Windows") ? out+".exe" : out;
            String result = TestC.gcc(out+".o", null, null, true, exe);
            System.out.print(result);
        }
    }
}
