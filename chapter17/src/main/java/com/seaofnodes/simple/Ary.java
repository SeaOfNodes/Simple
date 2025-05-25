package com.seaofnodes.simple;

import java.lang.StringBuilder;
import java.lang.reflect.Array;
import java.util.*;

// ArrayList with saner syntax
public class Ary<E> extends AbstractList<E> implements List<E> {
    public E[] _es;
    public int _len;

    public Ary(E[] es) { this(es,es.length); }
    public Ary(E[] es, int len) { if( es.length==0 ) es=Arrays.copyOf(es,1); _es=es; _len=len; }
    @SuppressWarnings("unchecked")
    public Ary(Class<E> clazz) { this((E[]) Array.newInstance(clazz, 1),0); }

    /** @return list is empty */
    public boolean isEmpty() { return _len==0; }
    /** @return active list length */
    public int len() { return _len; }
    public int size() { return len(); }

    /** @param i element index
     *  @return element being returned; throws if OOB
     *  @exception AIOOBE if !(0 <= i < _len)
     */
    public E get( int i ) { return at(i); }
    public E at( int i ) {
        range_check(i);
        return _es[i];
    }

    /** @return last element */
    public E last( ) { return at(_len-1); }

    /** Add element in amortized constant time
     *  @param e Element to add at end of list
     **/
    public E push( E e ) {
        if( _len >= _es.length ) _es = Arrays.copyOf(_es,Math.max(1,_es.length<<1));
        return (_es[_len++] = e);
    }
    /** @return remove and return last element */
    public E pop( ) {
        range_check(0);
        return _es[--_len];
    }
    @Override public E removeLast() { return pop(); }


    /** Add element in amortized constant time
     *  @param e Element to add at end of list
     *  @return true */
    public boolean add( E e ) {
        if( _len >= _es.length ) _es = Arrays.copyOf(_es,Math.max(1,_es.length<<1));
        _es[_len++] = e;
        return true;
    }

    ///** Slow, linear-time, element insert.  Preserves order.
    // *  @param i index to insert at, between 0 and _len inclusive.
    // *  @param e Element to insert
    // */
    //public void insert( int i, E e ) {
    //    if( i < 0 || i>_len )
    //        throw new ArrayIndexOutOfBoundsException(""+i+" >= "+_len);
    //    if( _len >= _es.length ) _es = Arrays.copyOf(_es,Math.max(1,_es.length<<1));
    //    System.arraycopy(_es,i,_es,i+1,(_len++)-i);
    //    _es[i] = e;
    //}

    /** Fast, constant-time, element removal.  Does not preserve order
     *  @param i element to be removed
     *  @return element removed */
    public E del( int i ) {
        range_check(i);
        E tmp = _es[i];
        _es[i]=_es[--_len];
        return tmp;
    }

    ///** Element removal, using '=='.  Does not preserve order.
    // *  @param e element to be removed
    // *  @return element removed */
    //public E del( E e ) {
    //    for( int i=0; i<_len; i++ ) {
    //        E tmp = _es[i];
    //        if( tmp==e ) {
    //            _es[i]=_es[--_len];
    //            return tmp;
    //        }
    //    }
    //    return null;
    //}

    /** Slow, linear-time, element removal.  Preserves order.
     *  @param i element to be removed
     *  @return element removed */
    @Override public E remove( int i ) {
        range_check(i);
        E e = _es[i];
        System.arraycopy(_es,i+1,_es,i,(--_len)-i);
        return e;
    }

    /** Remove all elements */
    public void clear( ) { _len=0; }

    /** Set existing element
     *  @param i element to set
     *  @param e value to set
     *  @return old value
     *  @exception AIOOBE if !(0 <= i < _len)
     */
    public E set( int i, E e ) {
        range_check(i);
        E old = _es[i];
        _es[i] = e;
        return old;
    }

    public Ary<E> setLen( int len ) {
        if( len > _len )
            while( len>= _es.length ) _es = Arrays.copyOf(_es,_es.length<<1);
        _len = len;
        while( _es.length > (len<<1) ) // Shrink if hugely too large
            _es = Arrays.copyOf(_es,_es.length>>1);
        Arrays.fill(_es,len,_es.length,null);
        return this;
    }

    /** @param c Collection to be added */
    public boolean addAll( Collection<? extends E> c ) {
        if( c==null ) return false;
        for( E e : c ) add(e);
        return true;
    }
    public Ary<E> addAll( Iterable<? extends E> it ) { for( E e : it ) add(e); return this; }

    /** @param es Array to be added */
    public <F extends E> Ary<E> addAll( F[] es ) {
        if( es==null || es.length==0 ) return this;
        while( _len+es.length > _es.length ) _es = Arrays.copyOf(_es,_es.length<<1);
        System.arraycopy(es,0,_es,_len,es.length);
        _len += es.length;
        return this;
    }

    /** @param c Collection to be added */
    public Ary<E> addAll( Ary<? extends E> c ) {
        if( c==null || c._len==0 ) return this;
        while( _len+c._len > _es.length ) _es = Arrays.copyOf(_es,_es.length<<1);
        System.arraycopy(c._es,0,_es,_len,c._len);
        _len += c._len;
        return this;
    }

  /** @return compact array version */
  public E[] asAry() { return Arrays.copyOf(_es,_len); }

  /** Sorts in-place
   *  @param c Comparator to sort by */
  public void sort_update(Comparator<? super E> c ) { Arrays.sort(_es, 0, _len, c);  }

    /** Find the first matching element using ==, or -1 if none.  Note that
     *  most del calls shuffle the list, so the first element might be random.
     *  @param e Element to find
     *  @return index of first matching element, or -1 if none */
    public int find( E e ) {
        for( int i=0; i<_len; i++ )  if( _es[i]==e )  return i;
        return -1;
    }


    /** @return an iterator */
    @Override public Iterator<E> iterator() { return new Iter(); }
    private class Iter implements Iterator<E> {
        int _i=0;
        @Override public boolean hasNext() { return _i<_len; }
        @Override public E next() { return _es[_i++]; }
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder().append('{');
        for( int i=0; i<_len; i++ ) {
            if( i>0 ) sb.append(',');
            if( _es[i] != null ) sb.append(_es[i].toString());
        }
        return sb.append('}').toString();
    }

    private void range_check( int i ) {
        if( i < 0 || i>=_len )
            throw new ArrayIndexOutOfBoundsException(""+i+" >= "+_len);
    }

    // Note that the hashCode() and equals() are not invariant to changes in the
    // underlying array.  If the hashCode() is used (e.g., inserting into a
    // HashMap) and the then the array changes, the hashCode() will change also.
    @Override public boolean equals( Object o ) {
        if( this==o ) return true;
        if( !(o instanceof Ary ary) ) return false;
        if( _len != ary._len ) return false;
        if( _es == ary._es ) return true;
        for( int i=0; i<_len; i++ )
            if( !(_es[i]==null ? (ary._es[i] == null) : _es[i].equals(ary._es[i])) )
                return false;
        return true;
    }
    @Override public int hashCode( ) {
        int sum=_len;
        for( int i=0; i<_len; i++ )
            sum += _es[i]==null ? 0 : _es[i].hashCode();
        return sum;
    }

}
