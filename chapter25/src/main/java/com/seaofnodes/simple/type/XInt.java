package com.seaofnodes.simple.type;

import com.seaofnodes.simple.util.*;
import java.lang.Integer;
import java.util.Arrays;

// Stupidly overkill interned immutable int[]'s
public abstract class XInt {
    private static final Ary<Ary<int[]>> FREES = new Ary(Ary.class);
    private static final Ary<Ary<int[]>> INTERNS = new Ary(Ary.class);
    private static int PROBES, HITS, NEWS;

    public static final int[] EMPTY = intern(new int[]{0, 0});
    public static final int[] FULL  = intern(new int[]{0,-1});
    public static final int[] SET1  = make(1);
    public static final int[] SET3  = make(SET1, 2);

    static int idx(int x) { return (x>>5)+1; }
    static int mask(int x) { return 1<<(x&31); }

    // No hash set, still mutable
    public static boolean isMutable(int[] xs) { return xs[0]==0; }
    // Return hash code; must be set hence frozen
    public static int hash(int[] xs) { return xs[0]; }

    // High bit set (infinite 1's extension)
    public static boolean isHigh(int[] xs) { return xs[xs.length-1] < 0; }

    // Check for a bit
    public static boolean bit(int[] xs, int x) {
        int idx = idx(x);
        if( idx >= xs.length )
            return isHigh(xs);
        return (xs[idx] & mask(x))!=0;
    }

    // Number of bits set
    public static int bitCount(int[] xs) {
        assert !isHigh(xs);       // Dont ask if infinite
        int cnt=0;
        for( int i=1; i<xs.length; i++ )
            cnt += Integer.bitCount(xs[i]);
        return cnt;
    }

    // Largest bit
    public static int max(int[] xs) {
        assert !isHigh(xs);
        return Integer.numberOfTrailingZeros(xs[xs.length-1])+((xs.length-1-1)<<5);
    }

    // Has 1 bit set
    public static boolean isConstant(int[] xs) { return !isHigh(xs) && bitCount(xs)==1; }
    // Return the 1 set bit or assert
    public static int onlyBit(int[] xs) {
        assert isConstant(xs);
        for( int i=1; i<xs.length; i++ )
            if( xs[i] != 0 )
                return Integer.numberOfTrailingZeros(xs[i]) + ((i-1)<<5);
        throw Utils.TODO("Should not reach here");
    }

    // Subset check: ys is a subset of xs
    public static boolean subset(int[] xs, int[] ys) {
        assert !isMutable(ys);
        if( xs==ys ) return true;
        if( xs==FULL ) return true;
        if( ys==EMPTY ) return true;
        int min = Math.min(xs.length,ys.length);
        for( int i=1; i<min; i++ )
            if( (xs[i] | ys[i])!=xs[i] )
                return false;
        if( xs.length == ys.length )
            return true;
        if( ys.length < xs.length )
            return !isHigh(ys); // If ys is infinite, it'll hit one of the xs extra zeros
        return isHigh(xs);
    }


    // Construct a set with 1 bit set
    public static int[] make(int x ) { return make(EMPTY,x); }

    // Add a bit
    public static int[] make(int[] xs, int x ) {
        assert !isMutable(xs);
        int idx = idx(x);
        if( idx >= xs.length && isHigh(xs) )
            return xs;          // Set in the default infinite
        if( idx < xs.length && (mask(x) & xs[idx])!=0 )
            return xs;          // Already set
        int len = Math.max(xs.length,idx+1);
        if( (x&31)==31 && idx+1==len )
            len++;
        int[] ys = free(len);
        System.arraycopy(xs,1,ys,1,xs.length-1);
        ys[idx] |= mask(x);
        return intern(ys);
    }

    // Interning, and life-cycle management
    // Intern the array
    public static int[] intern(int[] xs) {
        if( !isMutable(xs) ) return xs; // Already interned
        PROBES++;

        // Trim trailing 0s or -1s, use the infinite extend bit
        int len = xs.length;
        while( len > 2 &&
               ((xs[len-1]==  0 && xs[len-2]>=0) ||
                (xs[len-1]== -1 && xs[len-2]< 0) ) )
            len--;
        // Replace with trimmed
        if( len < xs.length ) {
            var ys = free(len);
            System.arraycopy(xs,1,ys,1,len-1);
            xs = _free(xs,ys);
        }

        // Compute lame hash code
        int hash = 0;
        for( int i=1; i<len; i++ )
            hash += xs[i];
        if( hash == 0 ) hash = 0xCAFEBABE;
        xs[0] = hash;           // Set lame hashcode, and mark immutable

        // Get length-specific INTERN table
        var xss = INTERNS.atX(len);
        if( xss==null )
            INTERNS.setX( xs.length, xss = new Ary<>(int[].class));

        // Probe for a hit
        for( int[] ys : xss )
            if( Arrays.equals(xs,ys) ) // Hit!
                // Free new array and return old array
                { HITS++;  return _free(xs,ys);  }

        // Intern new array and return
        return xss.push(xs);
    }

    // Return Ary of free int[]s of fixed size
    private static Ary<int[]> _xss(int len) {
        Ary<int[]> xss = FREES.atX(len);
        if( xss==null )
            FREES.setX(len, xss = new Ary<>(int[].class));
        return xss;
    }
    // Pull free array from free list
    static int[] free(int len) {
        int[] xs = _xss(len).pop();
        if( xs==null )
            { NEWS++;  xs = new int[len];  }
        assert isMutable(xs);
        return xs;
    }
    // Return ys.  Put xs on free list
    static int[] _free(int[] xs, int[] ys) {
        Arrays.fill(xs,0);      // Mutable again
        _xss(xs.length).push(xs);
        return ys;
    }

    // Meet: Set union
    public static int[] meet(int[] xs, int[] ys) {
        if( xs==ys ) return xs;
        boolean isX=true, isY=true;
        int min = Math.min(xs.length,ys.length);
        for( int i=1; i<min; i++ ) {
            if( (xs[i] | ys[i])!=xs[i] ) isX=false;
            if( (xs[i] | ys[i])!=ys[i] ) isY=false;
        }
        if( !isHigh(xs) && min < ys.length )
            isX = false; // YS has *some* more bits set, and XS does not
        if( !isHigh(ys) && min < xs.length )
            isY = false; // XS has *some* more bits set, and YS does not
        if( isX ) return xs;
        if( isY ) return ys;
        // Get a free one
        int[] ms = free(Math.max(xs.length,ys.length));
        for( int i=1; i<min; i++ )
            ms[i] = xs[i] | ys[i];
        if( min < xs.length )  System.arraycopy(xs,min,ms,min,xs.length-min);
        if( min < ys.length )  System.arraycopy(ys,min,ms,min,ys.length-min);
        return intern(ms);
    }

    // Dual: flip all the bits
    public static int[] dual(int[] xs) {
        int[] ds = free(xs.length);
        for( int i=1; i<xs.length; i++ )
            ds[i] = ~xs[i];
        return intern(ds);
    }


    public static void packed(BAOS baos, int[] xs) {
        baos.packed1(xs.length);
        for( int i=1; i<xs.length; i++ )
            baos.packed4(xs[i]);
    }
    public static int[] packed(BAOS bais) {
        int len = bais.packed1();
        int[] xs = free(len);
        for( int i=1; i<len; i++ )
            xs[i] = bais.packed4();
        return intern(xs);
    }

    // Next set bit, or -1
    // for( int bit = XInt.next(bits,0); bit >=0; bit = XInt.next(bits,bit) ) {...bit...}
    public static int next(int[] xs, int x) {
        assert !isHigh(xs);     // No iterating infinite bitsets
        x++;                    // Skip current
        for( ; !bit(xs,x); x++ )
            if( idx(x)>=xs.length )
                return -1;
        return x;
    }

    // Remap all bits
    public static int[] remap( int[] xs, AryInt map ) {
        if( xs==FULL ) return xs;
        assert !isHigh(xs);
        int[] ys = free(xs.length);
        for( int bit = next(xs,0); bit >=0; bit = next(xs,bit) ) {
            int bat = map.at(bit);
            ys[idx(bat)] |= mask(bat);
        }
        return intern(ys);
    }



    public static String str(int[] xs) { return str(new SB(),xs).toString(); }
    public static SB str(SB sb, int[] xs) {
        sb.p("[ ");
        // Printing ranges;
        //   1,2,3 prints 1-3
        //   1,2,4 prints 1,2,4
        //
        int max = (xs.length-1)<<5;
        int low = 0;   // No low range
        for( int i=1; i<max; i++ ) {
            if( bit(xs,i) ) {       // Set bit, start or extend range
                if( low==0 ) low=i; // No range, start one
            } else {                // Break in sequence, ending a range
                range(sb,low,i-1);  // Print prior range, and start new one
                low=0;              // Reset range
            }
        }
        // Print any partial range
        range(sb,low,max-1).unchar();
        if( isHigh(xs) )
            sb.p(",...");
        return sb.p("]");
    }

    private static SB range( SB sb, int low, int mid ) {
        if( low==0 ) ;
        else if( low==mid ) sb.p(low).p(',');
        else if( low==mid-1 ) sb.p(low).p(',').p(mid).p(',');
        else sb.p(low).p('-').p(mid).p(',');
        return sb;
    }
}
