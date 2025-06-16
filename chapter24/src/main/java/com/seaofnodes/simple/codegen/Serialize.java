package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.*;
import java.util.*;

abstract public class Serialize {
    static BAOS serialize( CodeGen code ) {
        // Get all Nodes in a sane order
        Ary<Node> nodes = nodeOrder(code);

        // Compress into bytes
        BAOS baos = write(nodes);

        //// Inflate into POJOs; renumbers everything
        //Ary<Node> nodes2 = read(new BAOS(baos.toByteArray()));
        //BAOS baos2 = write(nodes2);
        //
        //// Bi-jection check
        //for( int i=0; i<baos.size(); i++ )
        //    assert baos.buf()[i]==baos2.buf()[i];
        //assert baos.size()==baos2.size();
        //assert Arrays.equals(baos.buf(),baos2.buf());

        return baos;
    }

    // --------------------------------------------------
    static BAOS write(Ary<Node> nodes /*other CodeGen inputs, #aliases, #fidxs*/) {
        // Initialize some type tag writing stuff
        Type.TAGOFFS();

        BAOS baos = new BAOS();
        // A - Print a header
        baos.write('C'); baos.write('0'); baos.write('D'); baos.write('E');
        //

        // Count unique Types
        var types = new HashMap<Type,Integer>();
        for( Node n : nodes ) {
            n._type.gather(types);
            if( n instanceof      FunNode fun ) fun.sig().gather(types);
            if( n instanceof ConstantNode con ) con._con .gather(types);
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

        // Count unique Strings
        var strs = new HashMap<String,Integer>();
        strs.put("",0);         // Null string is always 0
        for( Type t : atypes ) {
            if( t instanceof Field fld     ) gather(strs,fld._fname);
            if( t instanceof TypeStruct ts ) gather(strs,ts . _name);
        }
        for( Node n : nodes )
            n.gather(strs);

        // B - Write unique strings
        baos.packed4(strs.size());
        // Radix sort by string ID#
        String[] astrs = new String[strs.size()];
        for( String s : strs.keySet() )
            astrs[strs.get(s)] = s;
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

        // Radix sort by Node ID#
        IdentityHashMap<Node,Integer> anodes = new IdentityHashMap<>();
        for( Node n : nodes )
            anodes.put(n,anodes.size()+1); // +1 bias, reserve 0 for null

        // First cut: 1 byte opcode, optional per-node info, includes variable nIns
        for( Node n : nodes )
            n.packed(baos.write(n.serialTag().ordinal()),strs,types,aliases);
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

    static Ary<Node> read(BAOS bais) {
        // A - Read a header
        if( bais.read()!='C' || bais.read()!='0' || bais.read()!='D' || bais.read()!='E' )
            throw new IllegalArgumentException("Missing magic word");

        // B - Packed read of #strings, then strings
        int nstrs = bais.packed4();
        String[] strs = new String[nstrs];
        for( int i=0; i<nstrs; i++ )
            strs[i] = bais.packedS();

        // C - Packed read of #types, then types
        int ntypes = bais.packed4();
        // Map deserialized aliases to local aliases
        AryInt aliases = new AryInt();
        Type[] types = Type.packed(bais,strs,aliases,ntypes);


        // D - Read the nodes

        // Number of nodes
        int num = bais.packed4();
        // Packed array of nodes
        Ary<Node> nodes = new Ary<>(Node.class);
        for( int i=0; i<num; i++ )
            nodes.push(Node.Tag.VALS[bais.read()].make(bais,strs,types,aliases));

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

        // Set the type field
        for( Node n : nodes )
            if( n._type == null )
                n.setType(n.compute());

        for( Node n : nodes )
            if( n._type != null )
                n.setType(n.compute());
        assert check(nodes);

        // TODO: also get back #of aliases, #of fidxs, any other global constants
        return nodes;
    }

    private static boolean check( Ary<Node> nodes ) {
        for( Node n : nodes )
            if( n._type != n.compute() )
                return false;
        return true;
    }

    // --------------------------------------------------
    public static Ary<Node> nodeOrder( CodeGen code ) {
        if( code._start._ltree==null )
            code._start.buildLoopTree(code._stop);
        Ary<Node> nodes = new Ary<>(Node.class);
        BitSet visit = code.visit();
        nodes.add(code._start);

        // All the global constants
        for( Node n : code._start._outputs )
            if( !(n instanceof FunNode) )
                nodes.add(n);

        // All the functions
        for( Node n : code._start._outputs )
            if( n instanceof FunNode fun )
                _funWalk(fun,nodes,visit);

        visit.clear();
        return nodes;
    }

    // Walk and print whole functions at a time, in CG order
    private static void _funWalk( FunNode fun, Ary<Node> rpo, BitSet visit ) {
        if( fun._ltree._par._head instanceof FunNode fun2 )
            _funWalk(fun2,rpo,visit);
        int start = rpo._len;
        _funRPO(fun,rpo,visit);
        int len = rpo._len - start;
        for( int i=0; i< len>>1; i++ )
            rpo.swap(start+i,start+len-1-i);

    }

    // Walk and gather RPO nodes.
    private static void _funRPO(Node n, Ary<Node> rpo, BitSet visit) {
        if( visit.get(n._nid) ) return; // Been there, done that
        visit.set(n._nid);              // Stop recursion
        // Walk outputs ordered
        if( !(n instanceof ReturnNode) ) {
            if( n instanceof CFGNode ) {
                // If nodes walk outer loops before inner, so they hit the
                // Return first, so Return is at the bottom of the RPO.
                if( n instanceof IfNode iff ) {
                    CProjNode c0 = iff.cproj(0);
                    CProjNode c1 = iff.cproj(1);
                    if( c0._ltree.depth() > c1._ltree.depth() )
                        { c0 = c1; c1 = iff.cproj(0); }
                    _funRPO(c0,rpo,visit);
                    _funRPO(c1,rpo,visit);
                } else {
                    // CFG to CFG before CFG to data
                    for( Node use : n._outputs )
                        if( use instanceof CFGNode && !(use instanceof FunNode) ) // Do not walk from a CallNode to a FunNode
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
