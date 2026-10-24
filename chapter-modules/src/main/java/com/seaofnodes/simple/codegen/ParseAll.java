package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.IterPeeps;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.print.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

// Parse a given file, and all its dependencies
public abstract class ParseAll {

    static void parse(CodeGen code, String src) {
        Parser.TYPES.clear();
        Parser.TYPES.putAll(Parser.INIT_TYPES);
        if( src != null ) {
            // No source file, just the source itself.
            parseSource(code,code._srcName==null ? "Test" : code._srcName,src);
        } else {
            // Path from module root to source file
            parsePath(code,code._srcName);
        }
    }

    // Worklist of symbols to search and files to parse
    private static final Ary<CompUnit> WORK = new Ary<>(CompUnit.class);


    // Return a compilation unit (cached or new).  If new, it does not necessarily
    // need to be compiled.
    public static CompUnit makeCUnit( CodeGen code, CompUnit par, String name ) throws IOException {
        int idx=name.indexOf('/');
        if( idx>=0 ) return makeCUnit(code,makeCUnit(code,par,name.substring(0,idx)),name.substring(idx+1));
        String fname=par==null ? name : par._fname+"/"+name;
        CompUnit cu=code._compunits.get(fname);
        if( cu==null ) {
            String cname=par==null ? name : par._cname+"."+name;
            code._compunits.put(fname,cu=new CompUnit(par,fname,cname,name));
        }
        return cu;
    }


    // Parse a top-level string, used for e.g. testing
    static void parseSource( CodeGen code, String src ) {
        parseSource(code,"Test",src);
    }

    // Parse a top-level string with an explicit compilation-unit name.
    static void parseSource( CodeGen code, String fname, String src ) {
        CompUnit cunit = new CompUnit(fname,src);
        code._compunits.put(cunit._fname,cunit);
        parseAll(code,cunit);
    }

    // module_root/.../src.smp is source file name.
    static void parsePath(CodeGen code, String fname) {
        try {
            // Split and build the parent-path down to the source file.
            CompUnit cu = makeCUnit( code, null, fname );
            // Compile this CompUnit
            parseAll(code,cu);
        } catch( IOException ioe ) {
            throw new RuntimeException(ioe);
        }
    }

    private static void enqueue(CompUnit cu) {
        if( cu._par!=null ) enqueue(cu._par);
        if( cu._start==null && WORK.find(cu)==-1 ) WORK.add(cu);
    }

    private static void parseAll(CodeGen code, CompUnit cunit) {
        WORK.clear();           // Cleanup from prior tests
        // Prime the pump with the first CompUnit
        enqueue(cunit);
        // Work through all unparsed compilation units and parse
        while( !WORK.isEmpty() ) {
            CompUnit cu = WORK.remove(0);
            if( cu._src==null )
                throw new RuntimeException("Missing source file '"+cu._fname+".smp'");
            parseOne(code,cu);
        }

        // Many types will have been declared cyclically either internally or
        // across multiple compilation units.

        // Close over all recursive types, and upgrade TYPES
        Ary<TypeStruct> ary = new Ary<>(TypeStruct.class);
        for( Type t : Parser.TYPES.values() )
            if( t instanceof TypeStruct ts )
                ary.add(ts);
        TypeStruct[] tss = Type.closeOver(ary.asAry(),Parser.TYPES);
        for( TypeStruct ts : tss )
            Parser.TYPES.put(ts._name,ts);
        for( CompUnit cu : code._compunits.values() )
            cu._clz = (TypeStruct)Parser.TYPES.get(Parser.addClzPrefix(cu._cname));

        // Walk over all Nodes, and upgrade the internal constants to the
        // closed-over types.
        code._stop.walk( (Node n) -> {
                n.upgradeType(Parser.TYPES);
                if( n instanceof FRefNode fref ) {
                    boolean alloc = fref._name.endsWith(".<new>");
                    String clzName = alloc
                        ? fref._name.substring(0,fref._name.length()-".<new>".length()).intern()
                        : fref._name;
                    TypeStruct clz = (TypeStruct)Parser.TYPES.get(clzName);
                    if( clz == null )
                        return null;
                    Node con;
                    if( alloc ) {
                        Field ctor = clz.field("<new>");
                        if( ctor == null || !(ctor._t instanceof TypeFunPtr tfp) ) {
                            String name = clzName.startsWith(Parser.CLZ)
                                ? clzName.substring(Parser.CLZ.length()) : clzName;
                            int dot = name.lastIndexOf('.');
                            throw Parser.error("Unknown struct type '"+(dot==-1 ? name : name.substring(dot+1))+"'",fref._loc);
                        }
                        FunNode fun = code.link(tfp);
                        if( fun == null ) return null;
                        con = new FunPtrNode(tfp,code._start,fun.ret()).peephole();
                    } else {
                        // Class pointer constant:
                        TypeMemPtr clzptr = TypeMemPtr.make((byte)2,clz,true);
                        con = code.con(clzptr);
                    }
                    // Replace and optimize
                    fref.addDef(con);
                    code.add(fref);
                }
                // Adds sort by NIDs, which just got shuffled
                if( n instanceof AddNode add )
                    code.add(add);
                return null;
            } );

        // The complete public interface is now known.  Keep this independent
        // of later graph/linker liveness so SCCP computations remain local and
        // monotonic.
        code.freezePublicInterface();
    }

    // Parse one Simple source code file.  Add all the FRefs produced to the worklist.
    private static void parseOne( CodeGen code, CompUnit cunit ) {
        cunit.setStart(code);
        // Parse a source file, return missing external references
        Ary<FRefNode> frefs = code.P.parse(cunit);

        // Find all missing external references, or complain.  This can trigger more parses.
        while( !frefs.isEmpty() ) {
            FRefNode fref = frefs.remove(0);
            // Symbol not found in source code (or we would not be here).
            // Search the module for the fref
            try {
                CompUnit xcunit = findCUnitModule(code,cunit,fref._name);
                if( xcunit != null ) {
                    // Replace the FRef with the discovered class name.
                    fref._name = Parser.addClzPrefix(xcunit._cname);
                    cunit.addDep(xcunit);
                } else {
                    throw new RuntimeException("Undefined name '" + fref._name +"'");
                }
            } catch( IOException ioe ) {
                throw new RuntimeException(ioe);
            }
        }
        addRequiredTypes(code,cunit);
    }

    private static void addRequiredTypes(CodeGen code, CompUnit cunit) {
        LinkedHashSet<String> required = new LinkedHashSet<>();
        code._stop.walk(n -> {
            TypeStruct ts = CodeGen.requiredStruct(n);
            if( ts!=null && ts._fref ) required.add(ts._name);
            return null;
        });
        for( String typeName : required ) {
            try {
                CompUnit xcunit = findRequiredType(code,cunit,typeName);
                if( xcunit != null )
                    cunit.addDep(xcunit);
                else
                    assert false : "expected to find required type";
            } catch( IOException ioe ) {
                throw new RuntimeException(ioe);
            }
        }
    }

    private static CompUnit findRequiredType(CodeGen code, CompUnit cunit, String typeName) throws IOException {
        for( CompUnit cu = cunit; cu != null; cu = cu._par ) {
            String symbol = typeName.startsWith(cu._cname+".")
                ? typeName.substring(cu._cname.length()+1)
                : typeName;
            CompUnit xcunit = findCUnitModule(code,cu,symbol);
            if( xcunit != null )
                return xcunit;
        }
        return null;
    }


    // Symbol not found in source code (or we would not be here).
    // Search the module for the fref
    public static CompUnit findCompUnitOrThrow(CodeGen code, CompUnit cu, String symbol) {
        CompUnit cu2 =  findCompUnit(code,cu,symbol);
        if( cu2 != null ) return cu2;
        throw new RuntimeException("Undefined name '" + symbol +"'");
    }

    public static CompUnit findCompUnit(CodeGen code, CompUnit cu, String symbol) {
        // Symbol not found in source code (or we would not be here).
        // Search the module for the fref
        try {
            CompUnit local = findCUnitModule(code,cu,symbol);
            if( local != null )
                return local;
            return null;
        } catch( IOException ioe ) {
            throw new RuntimeException(ioe);
        }
    }

    // Find the symbol in module, starting from cunit.  This searches up the nested
    // class chain and sideways 1 step, so that any prefix of the class chain can
    // be skipped as a shortcut.  Symbol can have dotted names, which then go
    // down into classes.  e.g.
    //   A/X/Y exists.
    //   A/B contains X.Y, which is a shortcut for A.X.Y

    // However if A/B contains "Y", the search would be for A.B.Y then A.Y both of
    // which do not exist.
    private static CompUnit findCUnitModule( CodeGen code, CompUnit cunit, String symbol ) throws IOException {
        if( cunit == null ) {
            if( !symbol.equals("sys") || !CompUnit.source("sys").exists() ) return null;
            CompUnit sys=makeCUnit(code,null,"sys");
            enqueue(sys);
            return sys;
        }

        // Check self
        if( cunit._name.equals(symbol) )
            return cunit;

        // If symbol is itself nested (has embedded dots), move the leading part
        // over to the CompUnit path.  So e.g.:
        //   CompUnit{A.B}, Symbol="X.Y"
        // becomes
        //   CompUnit{A.B.X}, Symbol="Y"
        int dotIdx = symbol.indexOf(".");
        if( dotIdx != -1 ) {
            String pre = symbol.substring(0,dotIdx);
            String rest = symbol.substring(dotIdx+1 );
            String fname = cunit._fname + "/" + pre;
            File f = CompUnit.source(fname);
            if( f.exists() ) {
                CompUnit sub = makeCUnit(code,cunit,pre);
                // enqueue also parses parents: their initialization must precede a child.
                CompUnit dcunit = findCUnitModule(code,sub,rest);
                if( dcunit != null )
                    return dcunit; // Nested dot search found cunit
            }
        }

        // Look in mod / fname / name / symbol.smp
        String fname = cunit._fname + "/" + symbol;
        File f = CompUnit.source(fname);
        if( f.exists() ) {
            CompUnit sub = makeCUnit(code,cunit,symbol);
            // If not seen before or out of date, parse the new nested cunit
            if( sub._clz == null && WORK.find(sub) == -1 )
                enqueue(sub);
            return sub;
        }

        // Look up the scope
        return findCUnitModule( code, cunit._par, symbol );
    }


}
