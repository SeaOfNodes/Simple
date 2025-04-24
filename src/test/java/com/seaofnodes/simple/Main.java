package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    // Compile and run a simple program
    public static void main( String[] args ) throws Exception {
        // First arg is file, 2nd+ args are program args
        String src = Files.readString(Path.of(args[0]));
        int arg = Integer.valueOf(args[1]);
        CodeGen code = new CodeGen(src);
        code.parse().opto().typeCheck().GCM().localSched();
        System.out.println(code._stop);
        System.out.println(Eval2.eval(code,arg,100000));
    }
}
