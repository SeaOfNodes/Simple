package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeStruct;
import com.seaofnodes.simple.util.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static com.seaofnodes.simple.codegen.CodeGen.CODE;

// A source file and its class. Parent links follow the source directory tree.
public class CompUnit {
    public final CompUnit _par; // Parent, e.g. A/B
    final String _fname;        // Full slashed file name starting from module root, e.g. A/B/C
    final public String _cname; // Full dotted class name starting from module root, e.g. A.B.C
    final String _name;         // Base name, e.g. C
    final File _smp;            // Simple source file, e.g. module_root/A/B/C.smp
    final public String _src;   // Source code, from file or test case

    public String sourceFile() {
        return _smp!=null ? _smp.getAbsolutePath() : null;
    }

    public TypeStruct _clz; // The one TypeStruct being published

    // _deps records units required for types, code, or class references,
    // without requiring their initializers to run. These source
    // dependencies may form a cycle.
    Ary<CompUnit> _deps;

    // Per-Compilation-Unit Start/StopNodes, keeping alive all exported Nodes.
    // Null means no code loaded (yet).
    public StartCUNode _start;
    public StopCUNode  _stop ;


    // Discovered source file.  Source is loaded if required to parse, and null
    // otherwise.
    CompUnit( CompUnit par, String fname, String cname, String name ) throws IOException {
        _par  = par;
        _fname= fname;
        _cname= cname;
        _name = name;
        _smp  = source(fname);

        _src = _smp.exists() ? Files.readString(_smp.toPath()) : null;
    }

    // The bundled library is ordinary source. A local sys tree can replace it;
    // this chapter never loads Simple code from an object file.
    static File source(String fname) {
        File file=new File(CODE._modDir,fname+".smp");
        if( file.exists() || !(fname.equals("sys") || fname.startsWith("sys/")) ) return file;
        return new File("src/main/smp",fname+".smp");
    }

    // Test case.  No source file.  Source code passed in as a String.  Fake
    // class name.
    CompUnit( String fname, String src ) {
        _par  = null;
        _name = fname;
        _fname= fname;
        _cname= fname;
        _smp  = null;
        _src  = src;
    }

    // Record source discovery without adding an initialization edge.
    void addDep(CompUnit dep) {
        if( _deps == null ) _deps = new Ary<>(CompUnit.class);
        _deps.add(dep);
    }

    public void addFun(CodeGen code, FunNode fun) {
        assert fun.in(1)==_start;
        _stop.addDef(fun.ret());
        code.add(_stop);
        _stop.addDep(fun);
    }

    void setStart(CodeGen code) {
        _stop  = new  StopCUNode().init();
        _start = new StartCUNode(code._start,_stop, Type.BOTTOM, _fname).init();
        code._stop.addDef(_stop);
    }

    @Override public String toString() {
        String x = "{"+_cname;
        if( _smp!=null && _smp.exists()) x += ",hasSMP";
        if( _src!=null   ) x += ",hasSrc";
        x += "}";
        return x;
    }
}
