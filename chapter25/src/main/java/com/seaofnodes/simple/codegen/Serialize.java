package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.*;
import java.util.*;

abstract public class Serialize {

    static void serialize( CodeGen code ) {

        for( CompUnit ref : code._compunits ) {
            // Get all Nodes in a sane order
            Ary<Node> nodes = nodeOrder(code,ref._funs);

            // Compress into bytes
            BAOS baos = write(nodes, ref._clzs);

            //// --- Expensive bijection assert
            //if( true ) {
            //    // Inflate into POJOs; renumbers everything
            //    Results r = readAll(new BAOS(baos.toByteArray()));
            //    BAOS baos2 = write(r.nodes(), r.published());
            //
            //    // Bi-jection
            //    for( int i=0; i<baos.size(); i++ )
            //        assert baos.buf()[i]==baos2.buf()[i];
            //    assert baos.size()==baos2.size();
            //    assert Arrays.equals(baos.buf(),baos2.buf());
            //}
            //// --- Expensive bijection assert

            // Record serialized IR for later ELF writing
            ref._serial = baos;

            Node stop = nodes.pop();
            stop.kill();
        }
    }

    // --------------------------------------------------
    static BAOS write(Ary<Node> nodes, Ary<TypeStruct> published /*other CodeGen inputs, #aliases, #fidxs*/) {
        // Initialize the mapping from bits/tags to types
        Type.TAGOFFS();

        BAOS baos = new BAOS();
        // A - Print a header
        baos.write('C').write('0').write('D').write('E');

        // Count unique Types
        var types = new HashMap<Type,Integer>();
        // Count published types *first*, so can align with published strings
        types.put(Type.TOP,0);
        for( TypeStruct ts : published )
            types.put( ts, types.size() );
        // Have to visit the innards specially, since they did not get the
        // normal recursive visit when first touching a type, because the
        // published types are packed early to align with their string names so
        // lookup in ElfReader can be fast.
        for( TypeStruct ts : published ) {
            int nkids = ts.nkids();
            for( int i=0; i<nkids; i++ )
                ts.at(i).gather(types);
        }
        // Gather types from all sources
        for( Node n : nodes ) {
            n._type.gather(types);
            if( n instanceof  FunNode fun ) fun.sig().gather(types);
            if( n instanceof TypeNode con ) con._con .gather(types);
        }
        // Radix sort by Type ID#
        Type[] atypes = new Type[types.size()];
        for( Type t : types.keySet() )
            atypes[types.get(t)] = t;

        // Count unique aliases; they should all be in Types
        var aliases = new HashMap<Integer,Integer>();
        aliases.put(0,0);       // There is no alias 0, just a placeholder
        aliases.put(1,1);       // Always alias#1 maps to #1 for ALL MEM
        for( Type t : atypes ) {
            if( t instanceof Field fld   ) gather(aliases,fld._alias);
            if( t instanceof TypeMem mem ) gather(aliases,mem._alias);
        }

        // TODO: Count unique fidxs & fold


        // Count all unique Strings
        var strs = new HashMap<String,Integer>();
        strs.put("",0);         // Null string is always 0
        // Count *published* Strings first
        for( TypeStruct ts : published )
            gather(strs,ts._name);
        // Count strings from all types
        for( Type t : atypes ) {
            if( t instanceof Field fld     ) gather(strs,fld._fname);
            if( t instanceof TypeStruct ts ) gather(strs,ts . _name);
        }
        // Count strings from nodes
        for( Node n : nodes )
            n.gather(strs);
        // Radix sort by string ID#
        String[] astrs = new String[strs.size()];
        for( String s : strs.keySet() )
            astrs[strs.get(s)] = s;

        // Check that published strings and types align
        for( int i=0; i<published.size(); i++ )
            assert astrs[i+1] == ((TypeStruct)atypes[i+1])._name;


        // B - Write unique strings
        baos.packed4(published.size());
        baos.packed4(strs.size());
        // Write in ID# order
        for( String s : astrs )
            baos.packedS(s);

        // C - Write unique Types
        baos.packed4(atypes.length);
        // Write Types in ID# order, no children
        for( Type t : atypes )
            t.packed(baos,strs,aliases);
        // Write Types in ID# order, only child IDs
        for( Type t : atypes ) {
            int nkids = t.nkids();
            for( int i=0; i<nkids; i++ )
                baos.packed4(types.get(t.at(i)));
        }


        // D - Write the nodes.  Since this is expected to be the bulk of the
        // data, we might want to explore various options.

        // How many nodes?
        baos.packed4(nodes._len);

        // Radix sort by Node ID#, new compressed NIDs
        IdentityHashMap<Node,Integer> anodes = new IdentityHashMap<>();
        for( Node n : nodes )
            anodes.put(n,anodes.size()+1); // +1 bias, reserve 0 for null

        // First cut: 1 byte opcode, optional per-node info, includes variable nIns
        for( Node n : nodes ) {
            baos.packed1(n.serialTag().ordinal());
            baos.packed2(types.get(n._type));
            n.packed(baos,strs,types,aliases);
        }
        // Write out the input indices packed
        for( Node n : nodes ) {
            for( int i=0; i<n.nIns(); i++ )
                baos.packed2(n.in(i)==null ? 0 : anodes.get(n.in(i)));
            if( n instanceof FunNode fun )
                baos.packed2(anodes.get(fun.ret()));
        }

        return baos;
    }
    public static <T> void gather( HashMap<T,Integer> strs, T s) {
        if( s!=null && !strs.containsKey(s) )
            strs.put(s,strs.size());
    }

    // Returned array of *public* strings only.
    static String[] read_public_strs(BAOS bais) {
        Type.TAGOFFS();

        // A - Read a header
        if( bais.read()!='C' || bais.read()!='0' || bais.read()!='D' || bais.read()!='E' )
            throw new IllegalArgumentException("Missing magic word");

        // B - Packed read number of public strings, then the strings
        int npublished = bais.read();
        int nstrs = bais.packed4(); // Ignore total strings
        String[] strs = new String[npublished];
        for( int i=0; i<npublished; i++ )
            strs[i] = bais.packedS();
        return strs;
    }


    // --------------------------------------------------

    public static record Results( Ary<Node> nodes, Ary<TypeStruct> published ) {}

    static Results readAll( BAOS bais ) {
        // Initialize the mapping from bits/tags to types
        Type.TAGOFFS();

        // A - Read a header
        if( bais.read()!='C' || bais.read()!='0' || bais.read()!='D' || bais.read()!='E' )
            throw new IllegalArgumentException("Missing magic word");

        // B - Packed read of #strings, then strings
        int npublish = bais.packed4();
        int nstrs = bais.packed4();
        String[] strs = new String[nstrs];
        for( int i=0; i<nstrs; i++ )
            strs[i] = bais.packedS();

        // C - Packed read of #types, then types
        int ntypes = bais.packed4();
        // Map deserialized aliases to local aliases
        AryInt aliases = new AryInt();
        Type[] types = Type.packed(bais,strs,aliases,ntypes, Parser.TYPES, CodeGen.CODE._alias);
        // Also passed aliases used in aliases.at(0)
        CodeGen.CODE._alias = aliases.at(0);
        aliases.set(0,0);
        // Check and collect first published symbols map to TypeStructs
        Ary<TypeStruct> published = new Ary<>(TypeStruct.class);
        for( int i=0; i<npublish; i++ ) {
            TypeStruct ts = (TypeStruct)types[i+1];
            assert ts._name==strs[i+1];
            published.add(ts);
        }

        // D - Read the nodes

        // Number of nodes
        int num = bais.packed4();
        // Packed array of nodes
        Ary<Node> nodes = new Ary<>(Node.class);
        for( int i=0; i<num; i++ ) {
            Node.Tag tag = Node.Tag.VALS[bais.packed1()];
            Type t = types[bais.packed2()];
            Node n = tag.make(bais,strs,types,aliases);
            n._type = t;
            nodes.push(n);
        }

        // Node edges
        for( Node n : nodes ) {
            int len = n.nIns();
            for( int i=0; i<len; i++ ) {
                int idx = bais.packed2();
                n.setDef(i,idx==0 ? null : nodes.at(idx-1));
            }
            if( n instanceof FunNode fun ) {
                ReturnNode ret = (ReturnNode)nodes.at(bais.packed2()-1);
                fun.setRet(ret);
                ret._fun = fun;
            }
        }

        assert check(nodes);

        // TODO: also get back #of fidxs, any other global constants
        return new Results(nodes,published);
    }

    private static boolean check( Ary<Node> nodes ) {
        for( Node n : nodes )
            if( n._type != n.compute() )
                return false;
        return true;
    }

    // --------------------------------------------------
    public static Ary<Node> nodeOrder( CodeGen code, Ary<FunNode> funs ) {
        assert code._start._ltree != null; // Uses loop tree
        BitSet visit = code.visit();
        // Listed functions
        Ary<Node> nodes = new Ary<>(Node.class);
        for( FunNode fun : funs )
            _funWalk(fun,nodes,visit,funs);

        // Just constants used by the listed functions
        Ary<Node> cons = new Ary<>(Node.class);
        cons.add(code._start);
        for( Node n : code._start._outputs ) {
            if( n instanceof ConstantNode con && !visit.get(con._nid) ) {
                for( Node use : con.outs() ) {
                    // Special case for Cast:BOT which is basically a double-indirect constant
                    if( use instanceof CastNode cast && cast.in(0)==null )
                        for( Node useuse : cast.outs() )
                            conUsed(cast,useuse,visit,cons);
                    // Constant used by somebody, so flag for output
                    if( conUsed(con,use,visit,cons) )
                        break;
                }
            }
        }
        cons.addAll(nodes);

        // Make a custom StopNode only listing functions in this function set
        StopNode stop = new StopNode(code._stop._src);
        for( Node ret : code._stop._inputs )
            if( visit.get(ret._nid) )
                stop.addDef(ret);
        cons.add(stop);
        visit.clear();
        return cons;
    }

    private static boolean conUsed( Node con, Node use, BitSet visit, Ary<Node> cons ) {
        if( use == null || !visit.get(use._nid) ) return false;
        cons.add(con);
        visit.set(con._nid);
        return true;
    }

    // --------------------------------------------------
    public static Ary<Node> nodeOrder( CodeGen code ) {
        assert code._start._ltree != null; // Uses loop tree
        Ary<Node> nodes = new Ary<>(Node.class);
        BitSet visit = code.visit();
        nodes.add(code._start);

        // All the global constants
        for( Node n : code._start._outputs )
            if( !(n instanceof FunNode) ) {
                visit.set(n._nid); nodes.add(n);
                // Special for Cast:BOT
                for( Node cast : n._outputs )
                    if( cast!=null && cast.nIns()==2 && cast.in(0)==null )
                        { visit.set(cast._nid); nodes.add(cast); }
            }

        // Collect starting functions
        Ary<FunNode> funs = new Ary<>(FunNode.class);
        for( Node n : code._start._outputs )
            if( n instanceof FunNode fun )
                funs.add(fun);

        // All the functions, including internal ones
        for( int i=0; i<funs._len; i++ )
            _funWalk(funs.at(i),nodes,visit,funs);

        nodes.add(code._stop);
        visit.clear();
        return nodes;
    }

    // Walk and print whole functions at a time, in CG order
    private static void _funWalk( FunNode fun, Ary<Node> rpo, BitSet visit, Ary<FunNode> funs ) {
        int start = rpo._len;
        _funRPO(fun,rpo,visit, funs);
        int len = rpo._len - start;
        for( int i=0; i< len>>1; i++ )
            rpo.swap(start+i,start+len-1-i);
    }

    // Walk and gather RPO nodes.
    private static void _funRPO(Node n, Ary<Node> rpo, BitSet visit, Ary<FunNode> funs) {
        if( visit.get(n._nid) ) return; // Been there, done that
        visit.set(n._nid);              // Stop recursion
        // Walk outputs ordered
        if( n instanceof CFGNode ncfg ) {
            // If nodes walk outer loops before inner, so they hit the
            // Return first, so Return is at the bottom of the RPO.
            if( n instanceof IfNode iff ) {
                CProjNode c0 = iff.cproj(0);
                CProjNode c1 = iff.cproj(1);
                if( c0._ltree.depth() > c1._ltree.depth() )
                    { c0 = c1; c1 = iff.cproj(0); }
                _funRPO(c0,rpo,visit,funs);
                _funRPO(c1,rpo,visit,funs);
            } else {
                // CFG to CFG before CFG to data
                for( Node use : n._outputs )
                    if( use instanceof CFGNode usecfg ) {
                        if( ncfg.skip(usecfg) ) { // Do not walk from Call to Fun
                            //if( usecfg instanceof FunNode fun )
                            //    funs.add(fun);
                        } else {
                            _funRPO(use,rpo,visit,funs);
                        }
                    }
            }
            // Walk CFG to data eventually
            for( Node use : n._outputs )
                if( !(use instanceof CFGNode) )
                    _funRPO(use,rpo,visit,funs);
        } else {
            // Do not walk from a non-CFG to a CFG; CFGs walk to CFGs to
            // preserve CFG order.  Do not walk *into* Phis; wait for the
            // Region to walk the Phis (otherwise you visit some of the
            // Phis ahead of others forcing the Phis to be spread around).
            for( Node use : n._outputs ) {
                if( use!=null && !(use instanceof CFGNode) && !(use instanceof PhiNode) )
                    _funRPO(use,rpo,visit,funs);
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
        Arrays.sort( rpo._es,rpo._len-n.nOuts(),rpo._len, (x,y) -> ((Proj)y).idx() - ((Proj)x).idx() );
        rpo.add(n);
    }

}
