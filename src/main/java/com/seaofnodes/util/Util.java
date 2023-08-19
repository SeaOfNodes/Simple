/**
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Contributor(s):
 *
 * The Initial Developer of the Original Software is Cliff Click.
 *
 * This file is part of the Sea Of Nodes Simple Programming language
 * implementation. See https://github.com/SeaOfNodes/Simple
 *
 * The contents of this file are subject to the terms of the
 * Apache License Version 2 (the "APL"). You may not use this
 * file except in compliance with the License. A copy of the
 * APL may be obtained from:
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.seaofnodes.util;

import java.util.Arrays;

public class Util {
  // Fast linear scan for a hit, returns index or -1
  public static int find( int[] es, int e ) {
    for( int i=0; i<es.length; i++ ) if( es[i]==e ) return i;
    return -1;
  }
  // Fast linear scan for a hit, returns index or -1
  public static <E> int find( E[] es, E e ) {
    for( int i=0; i<es.length; i++ ) if( es[i]==e ) return i;
    return -1;
  }

  // String-equals, with expected interned strings
  public static boolean eq( String s0, String s1 ) {
    if( s0==s1 ) return true;
    if( s0==null || s1==null ) return false;
    assert !s0.equals(s1) : "Not interned: "+s0;
    return false;
  }


  // Copied from http://burtleburtle.net/bob/c/lookup3.c
  // Call add_hash as many times as you like, then get_hash at the end.
  // Uses global statics, does not nest.
  public static long rot(long x, int k) { return (x<<k) | (x>>>(64-k)); }
  private static long a,b,c;
  private static int x;
  static public void add_hash( long h ) {
    switch( x ) {
    case 0: a+=h; x++; return;
    case 1: b+=h; x++; return;
    case 2: c+=h;
      a -= c;  a ^= rot(c, 4);  c += b;
      b -= a;  b ^= rot(a, 6);  a += c;
      c -= b;  c ^= rot(b, 8);  b += a;
      a -= c;  a ^= rot(c,16);  c += b;
      b -= a;  b ^= rot(a,19);  a += c;
      c -= b;  c ^= rot(b, 4);  b += a;
      x=0;
    }
  }
  // Return the resulting hash, which is never 0
  static public long get_hash() {
    if( x!=0 ) {
      c ^= b; c -= rot(b,14);
      a ^= c; a -= rot(c,11);
      b ^= a; b -= rot(a,25);
      c ^= b; c -= rot(b,16);
      a ^= c; a -= rot(c, 4);
      b ^= a; b -= rot(a,14);
      c ^= b; c -= rot(b,24);
    }
    long hash=c;
    if( hash==0 ) hash=b;
    if( hash==0 ) hash=a;
    if( hash==0 ) hash=0xcafebabe;
    a=b=c=x=0;
    return hash;
  }
  // Single-use hash spreader
  static public long mix_hash(long h0) {
    add_hash(h0);
    return get_hash();
  }
  static public long mix_hash( long h0, long h1 ) {
    add_hash(h0);
    add_hash(h1);
    return get_hash();
  }
  static public long mix_hash( long h0, long h1, long h2 ) {
    add_hash(h0);
    add_hash(h1);
    add_hash(h2);
    return get_hash();
  }
  static public long mix_hash( long h0, long h1, long h2, long h3 ) {
    add_hash(h0);
    add_hash(h1);
    add_hash(h2);
    add_hash(h3);
    return get_hash();
  }
  static public long mix_hash( long h0, long h1, long h2, long h3, long h4 ) {
    add_hash(h0);
    add_hash(h1);
    add_hash(h2);
    add_hash(h3);
    add_hash(h4);
    return get_hash();
  }

  static public int gcd(int x, int y) {
    if( x==0 || y== 0 ) return 0;
    int a = Math.max(x,y), r;
    int b = Math.min(x,y);
    while((r=(a % b)) != 0) { a = b;  b = r; }
    return b;
  }

  static public boolean isUpperCase( String s ) {
    for( int i=0; i<s.length() ; i++ )
      if( !Character.isUpperCase(s.charAt(i)) )
        return false;
    return true;
  }

  // Return a merged sorted array from two sorted arrays, removing dups.
  // If the merge is the same as either array, return that array.
  // If the merge is the same as BOTH arrays, return the array with the smaller
  // hashCode (goal: canonicalize identical arrays, so the fast == check works
  // more often, and we do not make lots of equals-not-== arrays).
  static public short[] merge_sort( short[] es0, short[] es1 ) {
    if( es0==es1 ) return es0;
    if( Arrays.equals(es0,es1) )
      throw new UnsupportedOperationException("Not implemented");
    short[] xs = new short[es0.length+es1.length];
    int i=0, j=0, k=0;
    while( i<es0.length || j<es1.length ) {
      short s0 = i<es0.length ? es0[i] : Short.MAX_VALUE, s1 = j<es1.length ? es1[j] : Short.MAX_VALUE, s2=s0;
      if( s0<=s1 ) { i++; s2=s0; }
      if( s1<=s0 ) { j++; s2=s1; }
      xs[k++] = s2;
    }
    assert k>i && k>j; // Arrays were not equals, so at least one element from each copied to other
    if( k < xs.length )
      throw new UnsupportedOperationException("Not implemented"); // Compact
    return xs;
  }
  
}
