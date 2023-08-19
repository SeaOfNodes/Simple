package com.seaofnodes.print;

import java.util.ArrayList;
import java.util.Arrays;
import static com.seaofnodes.print.IRAdapter.Kind;

/** Small independent contracts for the shared printers. Run with assertions enabled. */
public class PrinterTest {
    static class N extends BaseNode<N> {
        final String name;
        final Kind kind;
        final ArrayList<N> ins=new ArrayList<>(), outs=new ArrayList<>();
        N(int id, String name, Kind kind, N... defs) {
            super(id);
            this.name=name; this.kind=kind;
            for( N def : defs ) add(def);
        }
        @Override public N in(int i) { return ins.get(i); }
        @Override public int nIns() { return ins.size(); }
        @Override public N out(int i) { return outs.get(i); }
        @Override public int nOuts() { return outs.size(); }
        @Override public String label() { return name; }
        @Override protected String repeatName() { return kind==Kind.CONSTANT ? null : name; }
        @Override protected String format() { return ins.isEmpty() ? null : "(%0+%1)"; }
        void add(N def) { ins.add(def); if( def!=null ) def.outs.add(this); }
    }
    static class Adapter extends IRAdapter<N> {
        @Override public Kind kind(N n) { return n.kind; }
    }

    public static void main(String[] args) {
        expression(); loop(); functions(); assembly();
        System.out.println("Shared printer contracts passed");
    }
    static void expression() {
        N one=new N(1,"1",Kind.CONSTANT), add=new N(2,"Add",Kind.DATA,one,one);
        assert add.print().equals("(1+1)");
        add.ins.set(1,add);
        assert add.print().equals("(1+Add)");
        // A failed callback must not leave another print's repeat state behind.
        N bad=new N(3,"Bad",Kind.DATA) {
            @Override protected ExprPrinter<N> _print1(ExprPrinter<N> p) { throw new IllegalStateException(); }
        };
        try { bad.print(); } catch( IllegalStateException expected ) {}
        assert add.print().equals("(1+Add)");
        N fmt=new N(4,"Format",Kind.DATA) {
            @Override protected ExprPrinter<N> _print1(ExprPrinter<N> p) { return p.prt(this,"%10 %% %0 %12"); }
        };
        for( int i=0; i<11; i++ ) fmt.add(one);
        assert fmt.print().equals("1 % 1 ____") : fmt.print();
        N empty=new N(5,"Empty",Kind.DATA) {
            @Override protected ExprPrinter<N> _print1(ExprPrinter<N> p) { return p.open().unchar(',').unchar(", ").close(); }
        };
        assert empty.print().equals("()");
    }
    static void loop() {
        N fun=new N(1,"Fun",Kind.FUN);
        N loop=new N(2,"Loop",Kind.LOOP,null,fun);
        N phi=new N(3,"Phi",Kind.PHI,loop,null);
        N branch=new N(4,"If",Kind.CTRL,loop,phi);
        N yes=new N(5,"True",Kind.CPROJ,branch), no=new N(6,"False",Kind.CPROJ,branch);
        N left=new N(7,"Left",Kind.CTRL,yes), right=new N(8,"Right",Kind.CTRL,no);
        N merge=new N(9,"Merge",Kind.REGION,null,left,right);
        N value=new N(10,"Add",Kind.DATA,null,phi);
        phi.add(value); loop.add(merge);
        N exit=new N(11,"Return",Kind.RETURN,merge,value);
        // A call linkage must not bring another function's body into this dump.
        N foreign=new N(12,"Other",Kind.FUN,exit);
        new N(13,"Foreign",Kind.RETURN,foreign);
        var nodes=new ArrayList<>(Arrays.asList(fun,loop,phi,branch,yes,no,left,right,merge,value,exit));
        var inputs=new ArrayList<ArrayList<N>>(); var outputs=new ArrayList<ArrayList<N>>();
        for( N n : nodes ) { inputs.add(new ArrayList<>(n.ins)); outputs.add(new ArrayList<>(n.outs)); }
        IRPrinter<N> printer=new IRPrinter<>(new Adapter());
        String text=printer.function(fun);
        assert text.equals(printer.function(fun));
        assert !text.contains("Foreign");
        int li=row(text,"Loop"), pi=row(text,"Phi"), le=row(text,"Left"), ri=row(text,"Right"), mi=row(text,"Merge");
        assert li>=0 && pi>li && le>pi && ri>pi && mi>le && mi>ri : text;
        String[] lines=text.split("\n");
        for( int i=0; i<lines.length; i++ )
            if( lines[i].matches("\\s*2 Loop.*") ) assert lines[i+1].matches("\\s*3 Phi.*") : text;
        for( int i=0; i<nodes.size(); i++ ) {
            N n=nodes.get(i);
            assert n.ins.equals(inputs.get(i)) && n.outs.equals(outputs.get(i));
            int count=0;
            for( String line : lines ) if( line.matches("\\s*"+n._nid+" "+n.name+" .*")) count++;
            assert count==1 : n.name+" appears "+count+" times\n"+text;
        }
        assert printer.prettyPrint(null,2).isEmpty();
    }
    static int row(String text,String name) { return text.indexOf(" "+name+" "); }

    static void functions() {
        N start=new N(1,"Start",Kind.START), stop=new N(2,"Stop",Kind.STOP);
        N one=new N(3,"One",Kind.CONSTANT,start);
        N sum=new N(4,"Sum",Kind.DATA,null,one,one);
        N caller=new N(5,"Caller",Kind.FUN,start), callee=new N(6,"Callee",Kind.FUN,start);
        N arg=new N(7,"Arg",Kind.PARM,caller);
        N load=new N(8,"Load",Kind.DATA,caller,arg);
        N parm=new N(9,"Parm",Kind.PARM,callee,load);
        N ret1=new N(10,"Ret1",Kind.RETURN,caller,load,sum);
        N ret2=new N(11,"Ret2",Kind.RETURN,callee,parm,sum);
        stop.add(ret1); stop.add(ret2);
        IRPrinter<N> printer=new IRPrinter<>(new Adapter());
        var funs=new ArrayList<>(Arrays.asList(caller,callee));
        var units=new ArrayList<IRPrinter.Unit<N>>();
        units.add(new IRPrinter.Unit<>("",null,null,funs));
        String text=printer.program(start,stop,units);
        for( N n : Arrays.asList(start,stop,one,sum,caller,callee,arg,load,parm,ret1,ret2) ) {
            long count=text.lines().filter(line -> line.matches("\\s*"+n._nid+" .*" )).count();
            assert count==1 : n.name+" appears "+count+" times\n"+text;
        }
        assert row(text,"Sum")<row(text,"Caller") : text;
        assert !printer.function(callee).contains(" Load ") : text;

        N alloc=new N(12,"New",Kind.MULTI,caller), ptr=new N(13,"Ptr",Kind.PROJ,alloc);
        String scheduled=printer.scheduled(new ArrayList<>(Arrays.asList(caller,ret1)));
        assert row(scheduled,"Ptr")>row(scheduled,"New") : scheduled;
    }

    static void assembly() {
        N fun=new N(1,"Fun",Kind.FUN), op=new N(2,"op",Kind.DATA,fun);
        N ret=new N(3,"Return",Kind.RETURN,fun,op);
        boolean[] encoded={true};
        var adapter=new AssemblyAdapter<N>(new Adapter()) {
            @Override public ArrayList<N> blocks() { return new ArrayList<>(Arrays.asList(fun,ret)); }
            @Override public String op(N n) { return n.name; }
            @Override public String operands(N n) { return n==op ? "\nfirst\nsecond" : ""; }
            @Override public String register(N n) { return "r0"; }
            @Override public boolean hidden(N n) { return n==ret; }
            @Override public boolean encoded() { return encoded[0]; }
            @Override public int defaultSize() { return 2; }
            @Override public int size(N n) { return 4; }
            @Override public int offset(N n) { return 0; }
            @Override public byte[] bytes() { return encoded[0] ? new byte[]{1,2,3,4} : null; }
            @Override public boolean littleEndian() { return false; }
        };
        ASMPrinter<N> printer=new ASMPrinter<>(adapter);
        String asm=printer.print();
        assert asm.equals(printer.print());
        assert asm.contains("0201") && asm.contains("0403") && asm.contains("second") : asm;
        encoded[0]=false;
        asm=new ASMPrinter<>(adapter).print();
        assert asm.contains("first") && asm.contains("second") : asm;
        Object type=new Object();
        var entries=new ArrayList<ASMPrinter.Data>();
        entries.add(new ASMPrinter.Data(type,"Pair",1,2));
        entries.add(new ASMPrinter.Data(type,"Pair",1,2));
        String pool=ASMPrinter.pool(new ASMPrinter.Pool("Constants",new byte[]{5,6},0,16,entries));
        assert pool.indexOf("Pair")==pool.lastIndexOf("Pair") : pool;
        assert pool.contains("0010  0506") : pool;
    }
}
