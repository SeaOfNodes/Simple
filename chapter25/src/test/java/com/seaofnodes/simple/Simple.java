package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.CompUnit;
import com.seaofnodes.simple.codegen.CodeGen.Phase;
import com.seaofnodes.simple.print.*;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.simple.util.Utils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

public class Simple {
    static void usage() {
        System.out.println(
"""
simple [options] input-file.smp ...program args...
Options are applied in-order.
Options:
  --help - This text

  --root root/ - Project root directory; this must end in a '/' and input
         files are relative paths to this.  If missing, the one input
         file path is treated as its own root directory.

  -o output - Project output directory; similar to --root, if 'output' ends in a
     '/', then all result files use relative paths to 'output', using a input file
     root-relative path.  If --root is missing, the one result file is named 'output'

  --eval                   - (slowly) evaluate the compiled code in emulator
  --run                    - run the compiled code natively; this is the default
  --cpu <cpu-name>         - use specific CPU (x86_64_v2, riscv, arm)
  --abi <abi-name>         - use specific ABI variant (SystemV)
  --target                 - print native CPU and ABI
  --version
  -S                       - dump generated assembler code
  --dump-size              - print the size of generated code
  --dump-time              - print compilation and execution times
  --dump-after-parse       - dump intermediate representation after parse
  --dump-after-opto        - dump intermediate representation after opto pass
  --dump-after-type-check  - dump intermediate representation after type check pass
  --dump-after-select      - dump intermediate representation after instruction selection pass
  --dump-after-gcm         - dump intermediate representation after GCM pass
  --dump-after-local-sched - dump intermediate representation after local scheduling pass
  --dump-after-reg-alloc   - dump intermediate representation after register allocation pass
  --dump-after-encode      - dump intermediate representation after encoding pass
  --dump-after-all         - dump intermediate representation after all passes
  --dump                   - dump final intermediate representation only
  --dot                    - dump grapical representation of intermediate code into *.dot file(s)
"""
);
        System.exit(0);
    }

    static void bad_usage() { throw bad("Invalid usage (use --help)"); }
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

    static void print_compilation_times(CodeGen code) {
        long[] times = code._times;
        for( int i=0; i<code._times.length; i++ )
            System.out.printf("%20s %.3f sec%n", Phase.values()[i], times[i]);
    }

    static String getInput() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        return reader.lines().collect(Collectors.joining("\n"));
    }

    public static void main(String[] args) throws Exception {
        String root = null;
        String input_filename = null;
        boolean do_eval = false;
        boolean do_run = true;
        boolean do_codegen = false;
        boolean dump_dot = false;
        boolean print_time = false;
        boolean print_size = false;
        boolean print_asm = false;
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
            case "--dump-after-parse":        dump |=  1<<Phase.Parse     .ordinal()   ; break;
            case "--dump-after-opto":         dump |=  1<<Phase.Opto      .ordinal()   ; break;
            case "--dump-after-type-check":   dump |=  1<<Phase.TypeCheck .ordinal()   ; break;
            case "--dump-after-loop-tree":    dump |=  1<<Phase.LoopTree  .ordinal()   ; break;
            case "--dump-after-select":       dump |=  1<<Phase.Select    .ordinal()   ; break;
            case "--dump-after-gcm":          dump |=  1<<Phase.Schedule  .ordinal()   ; break;
            case "--dump-after-local-sched":  dump |=  1<<Phase.LocalSched.ordinal()   ; break;
            case "--dump-after-reg-alloc":    dump |=  1<<Phase.RegAlloc  .ordinal()   ; break;
            case "--dump-after-encode":       dump |=  1<<Phase.Encoding  .ordinal()   ; break;
            case "--dump-after-export":       dump |=  1<<Phase.Export    .ordinal()   ; break;
            case "--dump":                    dump |=  1<<Phase.LastPhase .ordinal()   ; break;
            case "--dump-after-all":          dump |= (2<<Phase.LastPhase .ordinal())-1; break;
            case "--dot":                     dump_dot = true; break;
            case "-S":                        print_asm = true; break;
            case "--eval":                    do_eval = true ; do_run = false; break;
            case "--run":                     do_run  = true ; break;
            case "--norun":                   do_run  = false; break;
            case "--dump-size":               print_size = true; break;
            case "--dump-time":               print_time = true; break;
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
            // If no output given, the build dir is the modPath
            outPath = modPath;
        } else {
            // If the output is a directory, that is our out path
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

        if( do_run || print_asm || print_size ) {
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
        code.driver(Phase.LastPhase,cpu,abi, false, do_run || do_eval, dump);

        if( do_codegen && print_asm )
            System.out.println(code.asm());

        if( print_size ) {
            int sum = 0;
            for( CompUnit cu : code._compunits )
                sum += cu._encoding._bits.size();
            System.out.printf( "Code Size: %d%n", sum);
        }

        if( print_time )
            print_compilation_times(code);

        if( do_eval ) {
            // TODO: Support for evaluation of functions with different argument numbers and types
            long t = System.currentTimeMillis();
            long arg = (i+1 < args.length) ? Integer.parseInt(args[i+1]) : 0;
            System.out.println(Eval2.eval(code, arg, 100000));
            if( print_time )
                System.out.printf( "EXECUTION TIME:             %.3f sec%n", (System.currentTimeMillis() - t) / 1e3);
        }
        if( do_run ) {
            if ( !TestC.CPU_PORT.equals( cpu ) || !TestC.CALL_CONVENTION.equals( abi ) )
                throw bad("cannot run code on not native target");
            String exe = TestC.OS.startsWith("Windows") ? out+".exe" : out;
            String result = TestC.gcc(out+".o", null, null, true, exe);
            System.out.print(result);
        }
    }
}
