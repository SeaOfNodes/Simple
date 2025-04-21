package com.seaofnodes.simple;

/** Tight/tiny StringBuilder wrapper.
 *  Short short names on purpose; so they don't obscure the printing.
 *  Can't believe this wasn't done long, long ago. */
public final class SB {
  private final StringBuilder _sb;
  private int _indent = 0;
  public SB(        ) { _sb = new StringBuilder( ); }
  public SB(String s) { _sb = new StringBuilder(s); }
  public SB p( String s ) { _sb.append(s); return this; }
  public SB p( float  s ) {
    if( Float.isNaN(s) )
      _sb.append( "Float.NaN");
    else if( Float.isInfinite(s) ) {
      _sb.append(s > 0 ? "Float.POSITIVE_INFINITY" : "Float.NEGATIVE_INFINITY");
    } else _sb.append(s);
    return this;
  }
  public SB p( double s ) {
    if( Double.isNaN(s) )
      _sb.append("Double.NaN");
    else if( Double.isInfinite(s) ) {
      _sb.append(s > 0 ? "Double.POSITIVE_INFINITY" : "Double.NEGATIVE_INFINITY");
    } else _sb.append(s);
    return this;
  }
  public SB p( char   s ) { _sb.append(s); return this; }
  public SB p( int    s ) { _sb.append(s); return this; }
  public SB p( long   s ) { _sb.append(s); return this; }
  public SB p( boolean s) { _sb.append(s); return this; }
  // 1 byte, 2 hex digits, 8 bits
  public SB hex1(int s) {
    int digit = (s>>4) & 0xf;
    _sb.append((char)((digit <= 9 ? '0' : ('A'-10))+digit));
    digit = s & 0xf;
    _sb.append((char)((digit <= 9 ? '0' : ('A'-10))+digit));
    return this;
  }
  // 2 bytes, 4 hex digits, 16 bits, Big Endian
  public SB hex2(int s) { return hex1(s>> 8).hex1(s); }
  // 4 bytes, 8 hex digits, 32 bits, Big Endian
  public SB hex4(int s) { return hex2(s>>16).hex2(s); }
  // 8 bytes, 16 hex digits, 64 bits, Big Endian
    public SB hex8(long s) { return hex4((int)(s>>32)).hex4((int)s); }

  // Fixed width field
  public SB fix( int sz, String s ) {
    for( int i=0; i<sz; i++ )
      _sb.append( i < s.length() ? s.charAt(i) : ' ');
    return this;
  }
  public char at(int idx ) { return _sb.charAt(idx); }

  // Not spelled "p" on purpose: too easy to accidentally say "p(1.0)" and
  // suddenly call the autoboxed version.
  public SB pobj( Object s ) { _sb.append(s.toString()); return this; }
  public SB i( int d ) { for( int i=0; i<d+_indent; i++ ) p("  "); return this; }
  public SB i( ) { return i(0); }
  public SB ip(String s) { return i().p(s); }
  public SB s() { _sb.append(' '); return this; }

  // Increase indentation
  public SB ii( int i) { _indent += i; return this; }
  public SB ii() { return ii(1); }
  // Decrease indentation
  public SB di( int i) { _indent -= i; return this; }
  public SB di() { return di(1); }

  public SB nl( ) { return p(System.lineSeparator()); }
  // Last printed a nl
  public boolean was_nl() {
    int len = _sb.length();
    String nl = System.lineSeparator();
    int nlen = nl.length();
    if( len < nlen ) return false;
    for( int i=0; i<nlen; i++ )
      if( _sb.charAt(len-nlen+i)!=nl.charAt(i) )
        return false;
    return true;
  }

  // Delete last char.  Useful when doing string-joins and JSON printing and an
  // extra separater char needs to be removed:
  //
  //   sb.p('[');
  //   for( Foo foo : foos )
  //     sb.p(foo).p(',');
  //   sb.unchar().p(']');  // remove extra trailing comma
  //
  public SB unchar() { return unchar(1); }
  public SB unchar(int x) { _sb.setLength(_sb.length()-x); return this; }
  public SB setLen(int len) { _sb.setLength(len); return this; }
  public String subString(int start, int end ) { return _sb.substring(start,end); }

  public SB clear() { _sb.setLength(0); return this; }
  public int len() { return _sb.length(); }
  @Override public String toString() { return _sb.toString(); }
}
