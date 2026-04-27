package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeStruct;
import com.seaofnodes.simple.util.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static com.seaofnodes.simple.codegen.CodeGen.CODE;

// A compilation-unit, which is an external reference and source and object
// files.  These are mirrored over a file system tree (so form a tree), both
// the source tree and build/object tree.
public class CompUnit {
    final CompUnit _par;        // Parent, e.g. A/B
    final String _fname;        // Full slashed file name starting from module root, e.g. A/B/C
    final public String _cname; // Full dotted class name starting from module root, e.g. A.B.C
    final String _name;         // Base name, e.g. C
    final ExternNode _ext;      // Found in external libs; e.g. libc
    final File _smp;            // Simple source file, e.g. module_root/A/B/C.smp
    final File _obj;            // ELF output file, e.g. build/A/B/C.o
    final public String _src;   // Source code, from file or test case
    public Encoding _encoding;  // Encoding for output

    public TypeStruct _clz; // The one TypeStruct being published
    BAOS _serial;           // Serialized IR for this ELF file
    Ary<CompUnit> _deps;    // CompUnits that this CompUnit depends on
    boolean _didWrite;      // Used to topo-sort a collection of CompUnits to write all at once

    // List of symbols exported by this compilation unit, and their Node
    // definitions.
    public HashMap<String,Node> _exported;
    // Per-Compilation-Unit StopNode, keeping alive all exported Nodes.
    public StopNode _stop;

    // Discovered source file.  Source is loaded if required to parse, and null
    // otherwise.
    CompUnit( CompUnit par, String fname, String cname, String name ) throws IOException {
        _par  = par;
        _fname= fname;
        _cname= cname;
        _name = name;
        _ext  = null;
        _smp  = new File( CODE.  _modDir + "/" + fname + ".smp");
        _obj  = new File( CODE._buildDir + "/" + fname + ".o"  );
        _src  = !_obj.exists() || // No object file?
            _smp.lastModified() > _obj.lastModified() || // Out-of-date with source?
            checkDependentObjs()                         // Out-of-date with other objs?
            ? new String(Files.readAllBytes(_smp.toPath()))
            : null;
    }

    // Test case.  No source file.  Source code passed in as a String.  Fake
    // class name.
    CompUnit( String src ) {
        _par  = null;
        _name = "Test";
        _fname= _name;
        _cname= _name;
        _ext  = null;
        _smp  = null;
        _obj  = null;
        _src  = src;
    }

    // External CompUnit, pulled in from an external library like libc
    CompUnit( ExternNode ext, String symbol ) {
        _par  = null;
        _name = symbol;
        _fname= _name;
        _cname= null;
        _ext  = ext;
        _smp  = null;
        _obj  = null;
        _src  = null;
    }

    // Return true if _obj is out-of-date relative to dependents.
    private boolean checkDependentObjs() {
        ElfReader elf = CODE.getElf(_obj);
        elf.loadPublicSymbols();
        for( String dep : elf._deps ) {
            File obj = new File(CODE._buildDir+"/"+dep+".o");
            if( obj.lastModified() > _obj.lastModified() ) // Out-of-date with deps?
                return true;
        }
        return false;
    }

    // Add a discovered dependency, for later writing out in an ELF file, ".simple" section
    void addDep(CompUnit dep) {
        if( _deps == null ) _deps = new Ary<>(CompUnit.class);
        _deps.add(dep);
    }

    // Add a function which might be exported later; need to keep it alive
    // right now but can kill it if later we find no live FIDXs refer to it.
    public void addFun(CodeGen code, FunNode fun) {
        if( _stop == null )
            code._stop.addDef(_stop = new StopNode());
        code.add(_stop);
        _stop.addDep(fun);
        _stop.addDef(fun.ret());
    }

    @Override public String toString() {
        String x = "{"+_cname;
        if( _smp!=null && _smp.exists()) x += ",hasSMP";
        if( _obj!=null && _obj.exists()) x += ",hasObj";
        if( _src!=null   ) x += ",hasSrc";
        if( _ext != null ) x += ",isExt";
        x += "}";
        return x;
    }
}
