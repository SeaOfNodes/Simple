package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.*;
import java.util.*;

public class Serialize {

    // Shared by all objects in this serialization pass.  The graph and its
    // declarations stay unchanged while each compilation unit is written.
    private final ClassInitDependencies _init;
    private final HashSet<TypeStruct> _pubTypes = new HashSet<>();
    private final HashMap<CompUnit,Ary<TypeStruct>> _classes = new HashMap<>();

    private Serialize(CodeGen code) {
        _init = code._classInitDependencies;
        assert _init!=null; // TypeCheck checked this same, still-unmodified graph.
        // Preserve the canonical declarations, distinct from sharper graph
        // types with the same names.  Gather/sort them once for all objects.
        TreeMap<String,TypeStruct> named = new TreeMap<>();
        for( Type type : Parser.TYPES.values() )
            if( type instanceof TypeStruct ts ) named.put(ts._name,ts);
        for( TypeStruct ts : named.values() ) {
            _pubTypes.add(ts);
            CompUnit cu = classOwner(code,ts);
            if( cu!=null && cu._src!=null )
                _classes.computeIfAbsent(cu,k -> new Ary<>(TypeStruct.class)).add(ts);
        }
    }

    // Encoding/emulation need only machine code. File export also needs ideal
    // IR for a later compiler to import; an explicit Serialize request stops here.
    static void forTarget(CodeGen code, CodeGen.Phase target, boolean inMemory) {
        if( target==CodeGen.Phase.Serialize || (target.ordinal()>=CodeGen.Phase.Export.ordinal() && !inMemory) )
            code.serialize();
    }

    static void serialize( CodeGen code ) {
        // Anonymous functions also need names when another object refers to them.
        for( FunNode fun : code._linker )
            if( fun!=null && !fun.isDead() && fun._name==null )
                fun._name=code._fidxs.symbol(fun.sig().fidx());
        ArrayList<CompUnit> units = new ArrayList<>(code._compunits.values());
        units.sort(Comparator.comparing(cu -> cu._fname));
        Serialize writer = new Serialize(code);
        for( CompUnit cu : units ) {
            if( cu._src==null ) continue;
            BAOS serial = writer.serialize(code,cu);
            code._serials.put(cu,serial);
            if( cu._fname.equals(code._srcName==null ? "Test" : code._srcName) ) code._serial=serial;
        }
    }

    private BAOS serialize(CodeGen code, CompUnit top) {
        Ary<Node> nodes = nodeOrder(code,top);
        HashSet<String> xdeps = new HashSet<>();
        // Retain build dependencies even when their calls were inlined away.
        if( top._deps!=null ) for( CompUnit dep : top._deps )
            if( dep!=top ) xdeps.add(dep._fname);
        if( top._par!=null ) xdeps.add(top._par._fname);
        String[] deps = xdeps.toArray(new String[0]);
        Arrays.sort(deps);
        String[] units = {top._fname};
        TypeStruct clz = top._clz;

        // Compress into bytes
        Ary<TypeStruct> classes = _classes.get(top);
        BAOS baos = write(nodes, clz, classes, deps, units, code._aliases, code._fidxs, code._rpcs, code._externFunc);

        // --- Expensive bijection assert
        if( false ) {
            // Inflate into POJOs; renumbers everything
            ElfReader elf = new ElfReader(new BAOS(baos.toByteArray()));
            readAll(code,elf, code._aliases, code._fidxs, code._rpcs);
            BAOS baos2 = write(elf._nodes,elf._clz,classes,elf._deps,elf._units, code._aliases, code._fidxs, code._rpcs, code._externFunc);

            // Bi-jection
            for( int i=0; i<baos.size(); i++ )
                assert baos.buf()[i]==baos2.buf()[i] : "bijection mismatch at "+i+": "+(baos.buf()[i]&0xFF)+" != "+(baos2.buf()[i]&0xFF);
            assert baos.size()==baos2.size() : "bijection size mismatch: "+baos.size()+" != "+baos2.size();
        }
        // --- Expensive bijection assert

        // Record serialized IR for later ELF writing
        return baos;
    }

    // --------------------------------------------------
    private BAOS write(Ary<Node> nodes, TypeStruct clz, Ary<TypeStruct> classes, String[] depobjs, String[] units, GlobalBits aliases, GlobalBits fidxs, GlobalBits rpcs, HashMap<Integer,String> externFunc) {
        // Initialize the mapping from bits/tags to types
        Type.TAGOFFS();

        IdentityHashMap<Node,Integer> anodes = new IdentityHashMap<>();
        for( Node n : nodes ) anodes.put(n,anodes.size()+1); // 0 is null.
        // The saved wrappers summarize this object's units only. Keep the
        // live whole-program graph intact for subsequent lowering.
        TypeTuple stopType = TypeTuple.STOP_HIGH;
        for( Node n : nodes.last()._inputs )
            if( anodes.containsKey(n) ) stopType = (TypeTuple)stopType.meet(n._type);
        Type startType = TypeTuple.make(Type.CONTROL,stopType.mem(),((StartNode)nodes.at(0)).arg());

        BAOS baos = new BAOS();
        // A - Print the C0DE header. The format is not versioned yet.
        baos.write('C').write('0').write('D').write('E');

        // Count unique Types
        var types = new HashMap<Type,Integer>();
        IdentityHashMap<FunNode,TypeTuple> summaries = new IdentityHashMap<>();
        // Count published types *first*, so can align with published strings
        types.put(Type.TOP,0);
        types.put( clz, types.size() );
        // Have to visit the innards specially, since they did not get the
        // normal recursive visit when first touching a type, because the
        // published types are packed early to align with their string names so
        // lookup in ElfReader can be fast.
        for( int i=0; i<clz.nkids(); i++ )
            clz.at(i).gather(types);
        // Gather types from all sources
        for( Node n : nodes ) {
            n._type.gather(types);
            if( n instanceof CallEndNode cend ) cend._rpc.gather(types);
            if( n instanceof FunNode fun ) {
                fun.sig().gather(types);
                TypeTuple state = (TypeTuple)fun.ret()._type;
                TypeMem mem = (TypeMem)state.mem();
                // Retention can happen on a path that never returns. The
                // unit's escape summary includes those native-call effects.
                TypeMem escaped = (TypeMem)((TypeTuple)fun._compunit._stop._type).mem();
                mem=mem.makeFrom(XInt.meet(mem._escFs,escaped._escFs),XInt.meet(mem._escAs,escaped._escAs));
                state=state.makeFrom(1,mem);
                summaries.put(fun,state);
                state.gather(types);
            }
            if( n instanceof ParmNode parm ) parm._declaredType.gather(types);
            if( n instanceof TypeNode con ) con._con .gather(types);
        }
        stopType.gather(types);
        startType.gather(types);
        // A defined class remains importable even when its initializer body dies.
        // Nested structs also need their class declaration: <new> and <ctor>
        // can be absent from graph types even while their function bodies live.
        // Keep the nearest file owner, so an imported child is not re-exported
        // merely because its parent was compiled here.
        if( classes!=null )
            for( TypeStruct ts : classes )
                ts.gather(types);

        // Only record canonical declarations already reachable from this
        // object's graph.  Pulling unrelated parser types into this closure
        // can include open or partially built types from other units.
        Ary<TypeStruct> writtenPubs = new Ary<>(TypeStruct.class);
        for( Type type : types.keySet() )
            if( type instanceof TypeStruct ts && _pubTypes.contains(ts) )
                writtenPubs.add(ts);
        Arrays.sort(writtenPubs._es,0,writtenPubs._len,Comparator.comparing(ts -> ts._name));
        // Radix sort by Type ID#
        Type[] atypes = new Type[types.size()];
        for( Type t : types.keySet() )
            atypes[types.get(t)] = t;

        // Count all unique Strings
        var strs = new HashMap<String,Integer>();
        strs.put("",0);         // Null string is always 0
        // Find and count *published* Strings first
        gather(strs,clz._name);
        // Find and count dependent object file string names
        if( depobjs != null )
            for( String fname : depobjs )
                gather(strs,fname);
        for( String fname : units ) gather(strs,fname);
        // Count strings from all types
        for( Type t : atypes ) {
            if( t instanceof Field fld     ) gather(strs,fld._fname);
            if( t instanceof TypeStruct ts ) gather(strs,ts . _name);
        }
        // Count strings used by local->global bit mappings.
        aliases.gather(strs);
        fidxs  .gather(strs);
        rpcs   .gather(strs);
        for( String extern : externFunc.values() )
            gather(strs,extern);
        // Count strings from nodes
        for( Node n : nodes )
            n.gather(strs);
        // Radix sort by string ID#
        String[] astrs = new String[strs.size()];
        for( String s : strs.keySet() )
            astrs[strs.get(s)] = s;

        // Check that published strings and types align
        assert astrs[1] == ((TypeStruct)atypes[1])._name;
        if( depobjs != null )
            for( int i=0; i<depobjs.length; i++ )
                assert astrs[1+i+1] == depobjs[i];


        // B - Write unique strings
        baos.packed4(depobjs==null ? 0 : depobjs.length);
        baos.packed4(strs.size());
        // Write in ID# order; the first depobjs.length of these are dependent strings
        for( String s : astrs )
            baos.packedS(s);

        // Defined units are loader metadata, not initialization dependencies.
        baos.packed4(units.length);
        for( String fname : units ) baos.packed4(strs.get(fname));

        // B.2 - Write the local->global mappings
        aliases.packed(baos,strs);
        fidxs  .packed(baos,strs);
        rpcs   .packed(baos,strs);
        ArrayList<Integer> externFidxs = new ArrayList<>(externFunc.keySet());
        Collections.sort(externFidxs);
        baos.packed2(externFidxs.size());
        for( int fidx : externFidxs ) {
            baos.packed2(fidx);
            baos.packed2(strs.get(externFunc.get(fidx)));
        }

        // C - Write unique Types
        baos.packed4(atypes.length);
        baos.packed2(writtenPubs._len);
        for( TypeStruct ts : writtenPubs )
            baos.packed4(types.get(ts));
        // Write Types in ID# order, no children
        for( Type t : atypes )
            t.packed(baos,strs);
        // Write Types in ID# order, only child IDs
        for( Type t : atypes ) {
            int nkids = t.nkids();
            for( int i=0; i<nkids; i++ )
                baos.packed4(types.get(t.at(i)));
        }


        // Function summaries precede the graph, so declarations and call
        // behavior are available without allocating the implementation nodes.
        int nfuncs = 0;
        for( Node n : nodes ) if( n instanceof FunNode ) nfuncs++;
        baos.packed4(nfuncs);
        for( Node n : nodes ) if( n instanceof FunNode fun ) {
            baos.packed2(types.get(fun.sig()));
            baos.packed2(types.get(summaries.get(fun)));
            baos.packed2(fun._name==null ? 0 : strs.get(fun._name));
            baos.packed4(fun._approxUIDs);
            int[] edges = _init._edgess[fun.sig().fidx()];
            baos.packed4(edges==null ? 0 : edges.length);
            if( edges!=null ) for( int edge : edges ) baos.packed4(edge);
        }

        // D - Write the nodes.  Since this is expected to be the bulk of the
        // data, we might want to explore various options.

        // How many nodes?
        baos.packed4(nodes._len);

        // First cut: 1 byte opcode, optional per-node info, includes variable nIns
        for( Node n : nodes ) {
            baos.packed1(n.serialTag().ordinal());
            baos.packed2(types.get(n==nodes.at(0) ? startType : n==nodes.last() ? stopType : n._type));
            n.packed(baos,strs,types,anodes);
        }
        // Write out the input indices packed.
        for( Node n : nodes ) {
            for( int j=0; j<n.nIns(); j++ ) {
                if( !writeInput(n,j,anodes) ) continue;
                Node def = n.in(j);
                Integer idx = def==null ? Integer.valueOf(0) : anodes.get(def);
                // Imported function constants retain their symbol, not a body.
                if( idx==null && n instanceof FunPtrNode && j==1 ) idx=0;
                assert idx != null : "Missing serialized input for "+n+" input "+j+" def "+def;
                baos.packed2(idx);
            }
            if( n instanceof FunNode fun )
                baos.packed2(anodes.get(fun.ret()));
        }

        return baos;
    }

    // Omit cross-object call-graph links without unlinking the live IR.
    // A parameter follows exactly the same retained callers as its function.
    private static boolean writeInput(Node n, int i, IdentityHashMap<Node,Integer> nodes) {
        if( n instanceof ParmNode parm ) n=parm.fun();
        if( (n instanceof FunNode || n instanceof CallEndNode) && i>0 )
            return nodes.containsKey(n.in(i));
        if( n instanceof StopNode ) return nodes.containsKey(n.in(i));
        return true;
    }

    public static int nIns(Node n, IdentityHashMap<Node,Integer> nodes) {
        int len=0;
        for( int i=0; i<n.nIns(); i++ ) if( writeInput(n,i,nodes) ) len++;
        return len;
    }
    public static <T> void gather( HashMap<T,Integer> strs, T s) {
        if( s!=null && !strs.containsKey(s) )
            strs.put(s,strs.size());
    }
    public static void gather( AryInt is, int s) {
        if( is.atX(s)==0 ) {
            int cnt=0;
            for( int i=1; i<is._len; i++ )
                if( is._es[i] != 0 )
                    cnt++;
            is.setX(s,cnt+1);
        }
    }

    // A nested declaration belongs to its nearest enclosing file.
    private static CompUnit classOwner(CodeGen code, TypeStruct ts) {
        if( !Parser.startsClzPrefix(ts._name) ) return null;
        String name = ts._name.substring(Parser.CLZ.length()).replace('.','/');
        while( true ) {
            CompUnit cu = code._compunits.get(name);
            if( cu!=null ) return cu;
            int slash = name.lastIndexOf('/');
            if( slash<0 ) return null;
            name = name.substring(0,slash);
        }
    }

    static void read_public_strs(ElfReader elf) {
        BAOS bais = elf._bais;
        Type.TAGOFFS();

        // A - Read a header
        if( bais.read()!='C' || bais.read()!='0' || bais.read()!='D' || bais.read()!='E' )
            throw new IllegalArgumentException("Unsupported serialized IR format; rebuild the object file");

        // B - Packed read of #strings, then strings
        int ndependents = bais.packed4(); // Number of dependent object files
        int nstrs = bais.packed4();       // Total number of strings
        String[] strs = new String[nstrs];
        for( int i=0; i<nstrs; i++ )
            strs[i] = bais.packedS();
        elf._strs = strs;

        // Collect dependent file names
        elf._deps = new String[ndependents];
        for( int i=0; i<ndependents; i++ )
            elf._deps[i] = strs[1+i+1];
        elf._units = new String[bais.packed4()];
        for( int i=0; i<elf._units.length; i++ )
            elf._units[i] = strs[bais.packed4()];
    }

    // --------------------------------------------------
    record TypeRead(Type[] types, String[] strs, GlobalBits fileAliases, GlobalBits fileFidxs, GlobalBits fileRpcs) {}

    static TypeRead readTypes( CodeGen code, ElfReader elf, GlobalBits aliases, GlobalBits fidxs, GlobalBits rpcs ) {
        if( elf._types!=null ) return elf._types;
        BAOS bais = elf._bais;
        // Initialize the mapping from bits/tags to types
        Type.TAGOFFS();

        // If not read the strings already, read them now
        String[] strs = elf._strs;
        if( strs == null ) {
            // A - Read a header
            if( bais.read() != 'C' || bais.read() != '0' || bais.read() != 'D' || bais.read() != 'E' )
                throw new IllegalArgumentException( "Unsupported serialized IR format; rebuild the object file" );

            // B - Packed read of #strings, then strings
            int ndependents = bais.packed4(); // Number of dependent object files
            int nstrs = bais.packed4();       // Total number of strings
            elf._strs = strs = new String[nstrs];
            for( int i = 0; i < nstrs; i++ )
                strs[i] = bais.packedS();

            // Collect dependent file names
            elf._deps = new String[ndependents];
            for( int i = 0; i < ndependents; i++ )
                elf._deps[i] = strs[1 + i + 1];
            elf._units = new String[bais.packed4()];
            for( int i=0; i<elf._units.length; i++ )
                elf._units[i] = strs[bais.packed4()];
        }

        // B.2 - Read the local->global mappings.  Further down we will map
        // Elf file-local -> global, then global -> CodeGen.CODE local
        GlobalBits fileAliases = GlobalBits.packed(bais,strs);
        GlobalBits fileFidxs   = GlobalBits.packed(bais,strs);
        GlobalBits fileRpcs    = GlobalBits.packed(bais,strs);
        int nexterns = bais.packed2();
        for( int i=0; i<nexterns; i++ ) {
            int fileFidx = bais.packed2();
            String extern = strs[bais.packed2()];
            int fidx = fileFidx < GlobalBits.RESERVED ? fileFidx : fidxs.map(fileFidxs,fileFidx);
            String old = code.externFunc(fidx);
            if( old == null )
                code.externFunc(fidx,extern);
            else
                assert old.equals(extern);
        }


        // C - Packed read of #types, then types
        int ntypes = bais.packed4();
        int npubTypes = bais.packed2();
        int[] pubTypeIds = new int[npubTypes];
        for( int i=0; i<npubTypes; i++ )
            pubTypeIds[i] = bais.packed4();
        Type[] types = Type.packed(bais,strs,ntypes, Parser.TYPES,
                                   fileAliases,aliases,
                                   fileFidxs  ,fidxs,
                                   fileRpcs   ,rpcs);

        // aliases.set(0,0);
        // Check and collect first published symbols map to TypeStructs
        elf._clz = (TypeStruct)types[1/*skip zero*/];
        assert elf._clz._name==strs[1/*skip null ptr*/];
        for( Type type : types )
            if( type instanceof TypeStruct ts )
                Parser.TYPES.put(ts._name,ts);
        for( Type type : types ) {
            if( !(type instanceof TypeStruct ts) || !Parser.startsClzPrefix(ts._name) )
                continue;
            String cname = ts._name.substring(Parser.CLZ.length());
            int dot = cname.lastIndexOf('.');
            String name = dot == -1 ? cname : cname.substring(dot+1);
            Field inst = ts.field(name.intern());
            if( inst != null && inst._t instanceof TypeFunPtr tfp && tfp._ret instanceof TypeMem mem && mem._t instanceof TypeStruct its )
                Parser.TYPES.put(cname,its);
        }
        elf._pubTypes = new TypeStruct[npubTypes];
        for( int i=0; i<npubTypes; i++ ) {
            TypeStruct ts = elf._pubTypes[i] = (TypeStruct)types[pubTypeIds[i]];
            Parser.TYPES.put(ts._name,ts);
        }
        elf._functions = new FunctionSummary[bais.packed4()];
        for( int i = 0; i<elf._functions.length; i++ ) {
            TypeFunPtr sig = (TypeFunPtr)types[bais.packed2()];
            TypeTuple state = (TypeTuple)types[bais.packed2()];
            String name = strs[bais.packed2()];
            int size = bais.packed4();
            int[] edges = new int[bais.packed4()];
            for( int j = 0; j<edges.length; j++ ) edges[j]=fidxs.map(fileFidxs,bais.packed4());
            elf._functions[i]=new FunctionSummary(sig,state,name.isEmpty() ? fidxs.symbol(sig.fidx()) : name,size,edges);
        }
        return elf._types = new TypeRead(types,strs,fileAliases,fileFidxs,fileRpcs);
    }

    static void readAll( CodeGen code, ElfReader elf, GlobalBits aliases, GlobalBits fidxs, GlobalBits rpcs ) {
        BAOS bais = elf._bais;
        TypeRead tread = readTypes(code, elf, aliases, fidxs, rpcs);
        Type[] types = tread.types();
        String[] strs = tread.strs();
        GlobalBits fileAliases = tread.fileAliases();

        // D - Read the nodes

        // Number of nodes
        int num = bais.packed4();
        // Packed array of nodes
        Ary<Node> nodes = new Ary<>(Node.class);
        CompUnit cu = null;
        for( int i = 0; i<num; i++ ) {
            Node.Tag tag = Node.Tag.VALS[bais.packed1()];
            Type t = types[bais.packed2()];
            Node n = tag.make(bais,strs,types,fileAliases,aliases);
            n._type = t;
            nodes.push(n);
            // A little extra for FunNodes
            if( n instanceof StartCUNode startcu ) {
                cu = code._compunits.get(startcu._fname==null ? (code._srcName==null ? "Test" : code._srcName) : startcu._fname);
                assert cu != null;
            }
            if( n instanceof StopCUNode stopcu ) {
                cu = null;
            }
            if( n instanceof FunNode fun ) {
                assert cu != null;
                fun._compunit = cu;
                if( fun._name==null || fun._name.isEmpty() ) fun._name=fidxs.symbol(fun.sig().fidx());
            }
            if( n instanceof ExternNode ext && ext._con instanceof TypeFunPtr tfp && tfp.isConstant() ) {
                String old = code.externFunc(tfp.fidx());
                if( old == null )
                    code.externFunc(tfp.fidx(), ext._extern);
                else
                    assert old.equals(ext._extern);
            }
        }

        // Node edges
        for( Node n : nodes ) {
            int len = n.nIns();
            for( int i = 0; i<len; i++ ) {
                int idx = bais.packed2();
                Node def = idx==0 ? null : nodes.at(idx-1);
                // MemMerge precise aliases are encoded by their input slot.
                // Remap populated file-local slots into this compilation's
                // local alias numbering; null holes carry no identity.
                int local = i;
                if( n instanceof MemMergeNode && i >= GlobalBits.RESERVED ) {
                    if( def == null )  continue;
                    local = aliases.map(fileAliases,i);
                }
                n.setDefX(local,def);
                // Call's output[0] is always the CallEnd
                if( n instanceof CallEndNode cend && i==0 ) {
                    var outs = cend.call()._outputs;
                    outs.swap(0,outs._len-1);
                }
            }
            // A little extra for FunNodes
            if( n instanceof FunNode fun ) {
                // Return is hooked like an edge
                ReturnNode ret = (ReturnNode)nodes.at(bais.packed2()-1);
                fun.setRet(ret);
                ret._fun = fun;
            }
        }
        // Add edge from Start to Stop
        StartNode start = (StartNode)nodes.at(0);
        StopNode stop = (StopNode)nodes.last();
        start.setDef(1,stop);

        elf._nodes = nodes;
    }

    // --------------------------------------------------
    public static Ary<Node> nodeOrder( CodeGen code, CompUnit cu ) {
        BitSet visit = code.visit();
        Ary<Node> nodes = new Ary<>(Node.class);
        visit.set(code._stop._nid);
        // FinalizeIR attached every surviving function to its unit's Stop.
        for( Node ret : cu._stop._inputs )
            _funWalk(((ReturnNode)ret).fun(),nodes,visit);

        // Gather constants and shared expressions through this unit's inputs.
        // Walking their users instead would search other units and miss new
        // kinds of shared expressions unless we special-cased each one.
        Ary<Node> cons = new Ary<>(Node.class);
        cons.add(code._start);
        visit.set(code._start._nid);
        visit.set(cu._start._nid);
        for( Node n : cu._start._outputs )
            if( n instanceof ProjNode ) visit.set(n._nid);
        for( Node n : nodes )
            for( int i = 0; i<n.nIns(); i++ ) {
                // Parameters omit arguments from callers in other objects,
                // just as writeInput omits those call-graph edges.
                if( n instanceof ParmNode parm && i>0 && !visit.get(parm.fun().in(i)._nid) ) continue;
                constantInputs(n.in(i),cons,visit);
            }
        // StartCU and memory projection
        cons.add(cu._start);
        for( Node n : cu._start._outputs )
            if( n instanceof ProjNode ) cons.add(n);
        // All the innards of functions
        cons.addAll(nodes);
        // StopCU, Stop
        cons.add(cu._stop);
        cons.add(code._stop);
        visit.clear();
        return cons;
    }

    private static void constantInputs(Node n, Ary<Node> cons, BitSet visit) {
        if( n==null || visit.get(n._nid) ) return;
        // Call graph edges lead to other functions, not shared values.
        if( n instanceof CFGNode && !n.isConst() ) return;
        visit.set(n._nid);
        cons.add(n);
        // A FunPtr's Return edge must not pull in the callee's body.
        if( !n.isConst() )
            for( Node def : n._inputs ) constantInputs(def,cons,visit);
    }

    // --------------------------------------------------
    public static Ary<Node> nodeOrder( CodeGen code ) {
        Ary<Node> nodes = new Ary<>(Node.class);
        BitSet visit = code.visit();
        nodes.add(code._start);

        // All the global constants
        for( Node n : code._start._outputs ) {
            if( n.isConst() ) {
                visit.set( n._nid );
                nodes.add( n );
                // Special for Cast:BOT
                for( Node cast : n._outputs )
                    if( cast != null && cast.nIns() == 2 && cast.in( 0 ) == null ) {
                        visit.set( cast._nid );
                        nodes.add( cast );
                        // Array pointer conversion adds the body offset after
                        // the zero-code PtrToInt.  Both nodes are global-value
                        // descendants of the constant and otherwise have no
                        // function-local root from which to walk them.
                        if( cast instanceof PtrToIntNode )
                            for( Node add : cast.outs() )
                                if( add instanceof AddNode && !visit.get(add._nid) ) {
                                    visit.set(add._nid);
                                    nodes.add(add);
                                }
                    }
            }
        }

        // All the CompUnits
        for( CompUnit cu : code._compunits.values() ) {
            // Whole CompUnits have no users, went dead
            if( cu._src!=null && cu._start != null && !cu._start.isDead() ) {
                nodes.add(cu._start);
                // A memory-free, nonreturning unit can lose its memory projection.
                ProjNode mem=cu._start.proj(1);
                if( mem!=null ) nodes.add(mem);
                // All the functions, including internal ones, in comp-unit order
                for( FunNode fun : code._linker )
                    if( fun!=null && !fun.isDead() && fun._compunit == cu )
                        _funWalk(fun,nodes,visit);
                nodes.add(cu._stop);
            }
        }

        nodes.add(code._stop);
        visit.clear();
        return nodes;
    }

    // Walk and print whole functions at a time, in CG order
    private static void _funWalk( FunNode fun, Ary<Node> rpo, BitSet visit ) {
        int start = rpo._len;
        _funRPO(fun,rpo,visit);
        // A nonreturning function still owns a Return, even before LoopTree
        // inserts synthetic exits. No loop-depth ordering is needed on disk.
        _funRPO(fun.ret(),rpo,visit);
        int len = rpo._len - start;
        for( int i=0; i< len>>1; i++ )
            rpo.swap(start+i,start+len-1-i);
    }

    // Walk and gather RPO nodes.
    private static void _funRPO(Node n, Ary<Node> rpo, BitSet visit ) {
        // Return-linked constants can have users in other compilation units.
        if( n.isConst() ) return;
        if( visit.get(n._nid) ) return; // Been there, done that
        visit.set(n._nid);              // Stop recursion
        // Walk outputs ordered
        if( n instanceof CFGNode ncfg ) {
            if( n instanceof IfNode iff ) {
                CProjNode c0 = iff.cproj(0);
                CProjNode c1 = iff.cproj(1);
                _funRPO(c0,rpo,visit);
                _funRPO(c1,rpo,visit);
            } else if( n instanceof StopNode ) {
                // nothing
            } else {
                // CFG to CFG before CFG to data
                for( Node use : n._outputs )
                    if( use instanceof CFGNode usecfg && !ncfg.skip(usecfg) ) // Do not walk from Call to Fun
                        _funRPO(use,rpo,visit);
            }
            // Walk CFG to data eventually
            for( Node use : n._outputs )
                if( !(use instanceof CFGNode) )
                    _funRPO(use,rpo,visit);
        } else {
            // Do not walk from a non-CFG to a CFG; CFGs walk to CFGs to
            // preserve CFG order.  Do not walk *into* Phis; wait for the
            // Region to walk the Phis (otherwise you visit some of the
            // Phis ahead of others forcing the Phis to be spread around).
            for( Node use : n._outputs ) {
                if( use!=null && !(use instanceof CFGNode) && !(use instanceof PhiNode) )
                    _funRPO(use,rpo,visit);
            }
        }

        // If parent is a Multi, do not add (yet).
        // If self   is a Multi, add self and children.
        if( multiChild(n) || n instanceof CallEndNode ) {
            // nothing; delay until Multi-head or CallNode
        } else if( n instanceof MultiNode ) {
            // Now lump all multi/projections together
            printMulti(n,rpo);
        } else if( n instanceof CallNode call ) {
            printMulti(call.cend(),rpo);
            rpo.add(call);
        } else if( n instanceof RegionNode ) {
            // Now lump all Phis together
            int old = rpo._len;
            for( Node use : n._outputs )
                if( use instanceof PhiNode )
                    rpo.add(use);
            // Sort by label?  Want Phis before control outputs
            Arrays.sort( rpo._es, old, rpo._len, (x,y) -> y.label().compareTo(x.label()) );
            rpo.add(n);
        } else if( n instanceof StopNode ) {
            // nothing
        } else {
            rpo.add(n);         // Post-order add
        }
    }

    private static boolean multiChild(Node n) {
        return n instanceof Proj || n instanceof PhiNode;
    }

    private static void printMulti(Node n, Ary<Node> rpo) {
        // Now lump all multi/projections together
        for( Node use : n._outputs )
            rpo.add(use);
        // Sort by projection order
        if( !(n instanceof StartNode) )
            Arrays.sort( rpo._es,rpo._len-n.nOuts(),rpo._len,
                         (x,y) -> x instanceof ProjNode px && y instanceof ProjNode py ? (py.idx() - px.idx()) : (y._nid - x._nid) );
        rpo.add(n);
    }

}
