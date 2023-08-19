package com.seaofnodes.print;

import java.util.IdentityHashMap;

/** One expression's buffer and repeat tracking, shared by recursive node hooks. */
public final class ExprPrinter<N extends BaseNode<N>> {
    private final StringBuilder _text=new StringBuilder();
    private final IdentityHashMap<N,Boolean> _seen=new IdentityHashMap<>();
    private ExprPrinter() {}

    public static <N extends BaseNode<N>> String print(N node) {
        return new ExprPrinter<N>().n(node).toString();
    }

    public ExprPrinter<N> n(N node) {
        if( node==null ) return p("____");
        if( node.isDead() ) return p(node.uniqueName()).p(":DEAD");
        if( _seen.put(node,Boolean.TRUE)!=null ) {
            String ref=node.repeatName();
            if( ref!=null ) return p(ref);
        }
        node._print1(this);
        return this;
    }

    /** Input slots are decimal numbers. Missing inputs print like null inputs. */
    public ExprPrinter<N> prt(N node, String fmt) {
        for( int i=0; i<fmt.length(); i++ ) {
            char c=fmt.charAt(i);
            if( c!='%' ) { p(c); continue; }
            if( ++i==fmt.length() ) throw new IllegalArgumentException("Trailing % in "+fmt);
            if( fmt.charAt(i)=='%' ) { p('%'); continue; }
            int start=i;
            while( i<fmt.length() && fmt.charAt(i)>='0' && fmt.charAt(i)<='9' ) i++;
            if( start==i ) throw new IllegalArgumentException("Expected input slot in "+fmt);
            int slot=Integer.parseInt(fmt.substring(start,i--));
            n(node==null || slot>=node.nIns() ? null : node.in(slot));
        }
        return this;
    }

    public ExprPrinter<N> p(Object text) { _text.append(text); return this; }
    public ExprPrinter<N> open() { return p('('); }
    public ExprPrinter<N> close() { return p(')'); }
    public ExprPrinter<N> unchar() { return unchar(1); }
    public ExprPrinter<N> unchar(int n) { _text.setLength(_text.length()-n); return this; }
    /** Remove a trailing separator only when it matches. */
    public ExprPrinter<N> unchar(char c) {
        return _text.length()>0 && _text.charAt(_text.length()-1)==c ? unchar() : this;
    }
    public ExprPrinter<N> unchar(String suffix) {
        int start=_text.length()-suffix.length();
        return start>=0 && _text.indexOf(suffix,start)==start ? unchar(suffix.length()) : this;
    }
    @Override public String toString() { return _text.toString(); }
}
