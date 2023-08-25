/**
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Contributor(s):
 *
 * The Initial Developer of the Original Software is
 * The Ecstasy Project (https://github.com/xtclang/).
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


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.math.BigInteger;


/**
 * A PackedInteger represents a signed, 2's-complement integer of 1-8192 bits (1-1024 bytes) in
 * size. (The size limit is arbitrary, and temporary.)
 * <p/>
 * Values up to 8 bytes can be accessed as a <tt>long</tt> value, while values of any size can be
 * accessed as a BigInteger.
 * <p/>
 * The storage format is compressed as much as possible. There are five storage formats:
 * <ul><li>
 * <b>Tiny</b>: For a value in the range -64..63 (7 bits), the value can be encoded in one byte.
 * The least significant 7 bits of the value are shifted left by 1 bit, and the 0x1 bit is set to 1.
 * When reading in a packed integer, if bit 0x1 of the first byte is 1, then it's Tiny.
 * </li><li>
 * <b>Small</b>: For a value in the range -4096..4095 (13 bits), the value can be encoded in two
 * bytes. The first byte contains the value 0x2 (010) in the least significant 3 bits, and bits 8-12
 * of the integer in bits 3-7; the second byte contains bits 0-7 of the integer.
 * </li><li>
 * <b>Medium</b>: For a value in the range -1048576..1048575 (21 bits), the value can be encoded in
 * three bytes. The first byte contains the value 0x6 (110) in the least significant 3 bits, and
 * bits 16-20 of the integer in bits 3-7; the second byte contains bits 8-15 of the integer; the
 * third byte contains bits 0-7 of the integer.
 * </li><li>
 * <b>Large</b>: For a value in the range -(2^503)..2^503-1 (63 bytes), a value with `{@code s}`
 * significant bits can be encoded in no less than {@code 1+max(1,(s+7)/8)} bytes; let `{@code b}`
 * be the selected encoding length, in bytes. The first byte contains the value 0x0 (00) in the
 * least significant 2 bits, and the least 6 significant bits of {@code (b-2)} in bits 2-7. The
 * following {@code (b-1)} bytes contain the least significant {@code (b-1)*8} bits of the integer.
 * </li><li>
 * <b>Huge</b>: For any larger value (arbitrarily limited to the range -(2^8191)..2^8191-1 (1KB)),
 * the first byte is 0b111111_00, followed by an embedded packed integer specifying the number of
 * significant bytes of the enclosing packed integer, followed by that number of bytes of data.
 * </li></ul>
 * <p/>
 * To maximize density and minimize pipeline stalls, the algorithms in this file use the smallest
 * possible encoding for each value. Since an 8 significant-bit value can be encoded in two bytes
 * using either a small or large encoding, we choose large to eliminate the potential for a
 * (conditional-induced) pipeline stall. Since a 14..16 significant-bit value can be encoded in
 * three bytes using either a medium or large encoding, we choose large for the same reason.
 * <pre><code>
 *     significant bits  encoding  bytes
 *     ----------------  --------  -----
 *           <= 7        Tiny      1
 *            8          Large     2
 *           9-13        Small     2
 *          14-16        Large     3
 *          17-21        Medium    3
 *          >= 22        Large     4 or more
 *          >= 512       Huge      *
 * </code></pre>
 * <p/>
 * Similarly, by examining the least significant 3 bits of the first byte of an encoded value, the
 * encoding can be determined as follows:
 * <pre><code>
 *     first byte  encoding  bytes
 *     ----------  --------  -----
 *      ???????1   Tiny      1
 *      ?????010   Small     2
 *      ?????110   Medium    3
 *      ??????00   Large     2-65
 *      11111100   Huge      *
 * </code></pre>
 */
public class PackedInteger
        implements Comparable<PackedInteger>
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an uninitialized PackedInteger.
     */
    public PackedInteger()
        {
        }

    /**
     * Construct a PackedInteger using a <tt>long</tt> value.
     *
     * @param lVal  the <tt>long</tt> value for the PackedInteger
     */
    public PackedInteger(long lVal)
        {
        setLong(lVal);
        }

    /**
     * Construct a PackedInteger using a <tt>BigInteger</tt> value.
     *
     * @param bigint  the <tt>BigInteger</tt> value for the PackedInteger
     */
    public PackedInteger(BigInteger bigint)
        {
        setBigInteger(bigint);
        }

    /**
     * Construct a PackedInteger by reading the packed value from a DataInput
     * stream.
     *
     * @param in  a DataInput stream to read from
     *
     * @throws IOException  if an I/O exception occurs while reading the data
     */
    public PackedInteger(DataInput in)
            throws IOException
        {
        readObject(in);
        }


    // ----- public methods ------------------------------------------------------------------------

    /**
     * Obtain a PackedInteger that has the specified <tt>long</tt> value. This
     * method is useful for taking advantage of the built-in "cache" of
     * commonly-used PackedInteger instances.
     *
     * @param lVal  the <tt>long</tt> value for the PackedInteger
     */
    public static PackedInteger valueOf(long lVal)
        {
        if (lVal >= CACHE_MIN & lVal <= CACHE_MAX)
            {
            final int iCache = (int) (lVal - CACHE_MIN);

            PackedInteger pint = CACHE[iCache];
            if (pint == null)
                {
                pint = new PackedInteger(lVal);
                CACHE[iCache] = pint;
                }

            return pint;
            }

        return new PackedInteger(lVal);
        }

    /**
     * The size of the "native" 2's-complement signed integer that would be
     * necessary to hold the value.
     *
     * @return a value between 1 and 32 inclusive
     */
    public int getSignedByteSize()
        {
        verifyInitialized();

        int nBytes = m_fBig
                ? calculateSignedByteCount(m_bigint)
                : Math.max(1, (((64 - Long.numberOfLeadingZeros(Math.max(m_lValue, ~m_lValue))) & 0x3F) + 7) / 8);

        assert nBytes >= 1 && nBytes <= 1024; // arbitrary limit
        return nBytes;
        }

    /**
     * The size of the unsigned integer that would be necessary to hold the value.
     *
     * @return a value between 1 and 32 inclusive
     */
    public int getUnsignedByteSize()
        {
        verifyInitialized();
        if (m_fBig && m_bigint.signum() < 0)
            {
            throw new IllegalStateException("negative value");
            }

        int nBytes = m_fBig
                ? calculateUnsignedByteCount(m_bigint)
                : Math.max(1, (((64 - Long.numberOfLeadingZeros(m_lValue)) + 7) / 8));

        assert nBytes >= 1 && nBytes <= 32; // arbitrary limit of 32 for the prototype
        return nBytes;
        }

    /**
     * Determine if the value of the PackedInteger is "big". The value is
     * considered to be "big" if it cannot fit into a <tt>long</tt>.
     *
     * @return true if the value of the PackedInteger does not fit into a long
     */
    public boolean isBig()
        {
        verifyInitialized();
        return m_fBig;
        }

    /**
     * Range check the PackedInteger.
     *
     * @param nLo  the low end of the range (inclusive)
     * @param nHi  the high end of the range (inclusive)
     *
     * @return true iff the PackedInteger is in the specified range
     */
    public boolean checkRange(long nLo, long nHi)
        {
        if (isBig())
            {
            return false;
            }

        return m_lValue >= nLo && m_lValue <= nHi;
        }

    /**
     * @return true iff the value is less than zero
     */
    public boolean isNegative()
        {
        verifyInitialized();
        return m_fBig
                ? m_bigint.signum() < 0
                : m_lValue < 0;
        }

    /**
     * Helper to grab the value as an int, with a range check to be safe.
     *
     * @return the value as a 32-bit signed int
     */
    public int getInt()
        {
        long n = getLong();
        if (n < Integer.MIN_VALUE || n > Integer.MAX_VALUE)
            {
            throw new IllegalStateException("too big!");
            }

        return (int) n;
        }

    /**
     * Obtain the <tt>long</tt> value of the PackedInteger. If the PackedInteger
     * is "big", i.e. if the {@link #isBig} method returns <tt>true</tt>, then
     * this method will throw an IllegalStateException, because the value cannot
     * be expressed as a <tt>long</tt> without losing data.
     *
     * @return the <tt>long</tt> value of this PackedInteger
     *
     * @throws IllegalStateException if the value of this PackedInteger does not
     *         fit into a long
     */
    public long getLong()
        {
        verifyInitialized();
        if (m_fBig && m_lValue == 0)
            {
            throw new IllegalStateException("too big!");
            }

        return m_lValue;
        }

    /**
     * Initialize the PackedInteger using a <tt>long</tt> value.
     *
     * @param lVal  the <tt>long</tt> value for the PackedInteger
     */
    public void setLong(long lVal)
        {
        verifyUninitialized();
        m_lValue       = lVal;
        m_fInitialized = true;
        }

    /**
     * Obtain the BigInteger value of the PackedInteger. Whether or not the
     * PackedInteger value is too large to be held in a <tt>long</tt>, the
     * caller can request the BigInteger value; one will be lazily instantiated
     * (and subsequently cached) if necessary.
     *
     * @return the BigInteger that represents the integer value of this
     *         PackedInteger object
     */
    public BigInteger getBigInteger()
        {
        verifyInitialized();
        BigInteger bigint = m_bigint;
        if (bigint == null)
            {
            bigint = BigInteger.valueOf(m_lValue);
            m_bigint = bigint;
            }
        return bigint;
        }

    /**
     * Initialize the PackedInteger using a BigInteger value.
     *
     * @param bigint  the BigInteger value for the PackedInteger
     */
    public void setBigInteger(BigInteger bigint)
        {
        verifyUninitialized();

        if (bigint == null)
            {
            throw new IllegalArgumentException("big integer value required");
            }

        // determine if the number of bytes allows the BigInteger value to be
        // stored in a long value
        if (!(m_fBig = calculateSignedByteCount(bigint) > 8))
            {
            m_lValue = bigint.longValue();
            }
        else
            {
            if (calculateUnsignedByteCount(bigint) <= 8)
                {
                // the unsigned value still fits the long
                m_lValue = bigint.longValue();
                }
            else
                {
                m_lValue = 0;
                }
            }

        m_bigint       = bigint;
        m_fInitialized = true;
        }

    /**
     * @return the negated form of this packed integer
     */
    public PackedInteger negate()
        {
        return new PackedInteger(this.getBigInteger().negate());
        }

    /**
     * @return the complemented form of this packed integer
     */
    public PackedInteger complement()
        {
        return new PackedInteger(this.getBigInteger().not());
        }

    /**
     * @return the packed integer that is one less than this packed integer
     */
    public PackedInteger previous()
        {
        return sub(ONE);
        }

    /**
     * @return the packed integer that is one more than this packed integer
     */
    public PackedInteger next()
        {
        return add(ONE);
        }

    /**
     * Add the value of a specified PackedInteger to this PackedInteger, resulting in a new
     * PackedInteger.
     *
     * @param that  a second PackedInteger to add to this
     *
     * @return the resulting PackedInteger
     */
    public PackedInteger add(PackedInteger that)
        {
        return new PackedInteger(this.getBigInteger().add(that.getBigInteger()));
        }

    /**
     * Subtract the value of a specified PackedInteger from this PackedInteger, resulting in a new
     * PackedInteger.
     *
     * @param that  a second PackedInteger to subtract from this
     *
     * @return the resulting PackedInteger
     */
    public PackedInteger sub(PackedInteger that)
        {
        return new PackedInteger(this.getBigInteger().subtract(that.getBigInteger()));
        }

    /**
     * Multiply the value of a specified PackedInteger by this PackedInteger, resulting in a new
     * PackedInteger.
     *
     * @param that  a second PackedInteger to multiply this by
     *
     * @return the resulting PackedInteger
     */
    public PackedInteger mul(PackedInteger that)
        {
        return new PackedInteger(this.getBigInteger().multiply(that.getBigInteger()));
        }

    /**
     * Divide the value of this PackedInteger by the specified PackedInteger, resulting in a new
     * PackedInteger.
     *
     * @param that  a second PackedInteger to divide this by
     *
     * @return the resulting PackedInteger
     */
    public PackedInteger div(PackedInteger that)
        {
        return new PackedInteger(this.getBigInteger().divide(that.getBigInteger()));
        }

    /**
     * Calculate the modulo of the value of this PackedInteger and the value of a specified
     * PackedInteger, resulting in a new PackedInteger.
     *
     * @param that  a second PackedInteger to divide this by, resulting in a modulo
     *
     * @return the resulting PackedInteger (never negative - modulo not remainder!)
     */
    public PackedInteger mod(PackedInteger that)
        {
        return new PackedInteger(this.getBigInteger().mod(that.getBigInteger()));
        }

    /**
     * Divide the value of this PackedInteger by the specified PackedInteger, resulting in a new
     * PackedInteger quotient and a new PackedInteger remainder.
     *
     * @param that  a second PackedInteger to divide this by
     *
     * @return an array of the resulting PackedInteger quotient and remainder
     */
    public PackedInteger[] divrem(PackedInteger that)
        {
        BigInteger   [] aBigInt = this.getBigInteger().divideAndRemainder(that.getBigInteger());
        PackedInteger[] aPacked = new PackedInteger[2];
        aPacked[0] = new PackedInteger(aBigInt[0]);
        aPacked[1] = new PackedInteger(aBigInt[1]);
        return aPacked;
        }

    /**
     * Calculate the bit-and of the value of this PackedInteger and the value of a specified
     * PackedInteger, resulting in a new PackedInteger.
     *
     * @param that  a second PackedInteger to bit-and with this PackedInteger
     *
     * @return the resulting PackedInteger
     */
    public PackedInteger and(PackedInteger that)
        {
        return new PackedInteger(this.getBigInteger().and(that.getBigInteger()));
        }

    /**
     * Calculate the bit-or of the value of this PackedInteger and the value of a specified
     * PackedInteger, resulting in a new PackedInteger.
     *
     * @param that  a second PackedInteger to bit-or with this PackedInteger
     *
     * @return the resulting PackedInteger
     */
    public PackedInteger or(PackedInteger that)
        {
        return new PackedInteger(this.getBigInteger().or(that.getBigInteger()));
        }

    /**
     * Calculate the bit-xor of the value of this PackedInteger and the value of a specified
     * PackedInteger, resulting in a new PackedInteger.
     *
     * @param that  a second PackedInteger to bit-xor with this PackedInteger
     *
     * @return the resulting PackedInteger
     */
    public PackedInteger xor(PackedInteger that)
        {
        return new PackedInteger(this.getBigInteger().xor(that.getBigInteger()));
        }

    /**
     * Shift left the bits in the value of this PackedInteger by the value of a specified
     * PackedInteger, resulting in a new PackedInteger.
     *
     * @param that  a second PackedInteger specifying the shift amount
     *
     * @return the resulting PackedInteger
     */
    public PackedInteger shl(PackedInteger that)
        {
        return shl(that.getInt());
        }

    /**
     * Shift left the bits in the value of this PackedInteger by the specified count,
     * resulting in a new PackedInteger.
     *
     * @param count  the shift count
     *
     * @return the resulting PackedInteger
     */
    public PackedInteger shl(int count)
        {
        return new PackedInteger(this.getBigInteger().shiftLeft(count));
        }

    /**
     * Logical shift right the bits in the value of this PackedInteger by the value of a specified
     * PackedInteger, resulting in a new PackedInteger.
     *
     * @param that  a second PackedInteger specifying the shift amount
     *
     * @return the resulting PackedInteger
     */
    public PackedInteger shr(PackedInteger that)
        {
        return shr(that.getInt());
        }

    /**
     * Logical shift right the bits in the value of this PackedInteger by the specified count,
     * resulting in a new PackedInteger.
     *
     * @param count  the shift count
     *
     * @return the resulting PackedInteger
     */
    public PackedInteger shr(int count)
        {
        return count <= 0
            ? this
            : new PackedInteger(this.getBigInteger().shiftRight(count));
        }

    /**
     * Arithmetic (aka "unsigned") shift right the bits in the value of this PackedInteger by the
     * value of a specified PackedInteger, resulting in a new PackedInteger.
     *
     * @param that  a second PackedInteger specifying the shift amount
     *
     * @return the resulting PackedInteger
     */
    public PackedInteger ushr(PackedInteger that)
        {
        return shr(that.getInt());
        }

    /**
     * Arithmetic (aka "unsigned") shift right the bits in the value of this PackedInteger by the
     * specified count, resulting in a new PackedInteger.
     *
     * @param count  the shift count
     *
     * @return the resulting PackedInteger
     */
    public PackedInteger ushr(int count)
        {
        return shr(count);
        }

    /**
     * Compare the value of a specified PackedInteger with the value of this PackedInteger.
     *
     * @param that  a second PackedInteger to compare to
     *
     * @return -1, 0, or 1 if this is less than, equal to, or greater than that
     */
    public int cmp(PackedInteger that)
        {
        return this.getBigInteger().compareTo(that.getBigInteger());
        }

    /**
     * Format the PackedInteger as a String of the specified radix, including a radix prefix for
     * any non-decimal radix.
     *
     * @param radix  2, 8, 10, or 16
     *
     * @return the String value of this PackedInteger in the format of the specified radix
     */
    public String toString(int radix)
        {
        if (radix == 10)
            {
            return toString();
            }

        StringBuilder sb = new StringBuilder();
        if (isNegative())
            {
            sb.append('-');
            }

        switch (radix)
            {
            case 2:
                sb.append("0b");
                break;

            case 8:
                sb.append("0o");
                break;

            case 16:
                sb.append("0x");
                break;

            default:
                throw new IllegalArgumentException("radix=" + radix);
            }

        sb.append(isBig()
                ? getBigInteger().abs().toString(radix)
                : Long.toUnsignedString(Math.abs(getLong()), radix));

        return sb.toString();
        }

    /**
     * Read a PackedInteger from a stream.
     *
     * @param in  a DataInput stream to read from
     *
     * @throws IOException  if an I/O exception occurs while reading the data
     */
    public void readObject(DataInput in)
            throws IOException
        {
        verifyUninitialized();

        final int b = in.readByte();
        // check for large or huge format with more than 8 trailing bytes (needs BigInteger)
        if ((b & 0b11) == 0b00 && (b & 0b111000_00) != 0)
            {
            final int cBytes;
            if ((b & 0xFF) == 0b111111_00)
                {
                // huge format
                long len = readLong(in);
                if (len > 1024)
                    {
                    throw new IOException("integer size of " + len + " bytes; maximum is 1024");
                    }
                cBytes = (int) len;
                }
            else
                {
                // large format
                cBytes = 1 + ((b & 0xFC) >>> 2);
                }

            final byte[] ab = new byte[cBytes];
            in.readFully(ab);
            setBigInteger(new BigInteger(ab));
            }
        else
            {
            // tiny, small, medium, and large (up to 8 trailing bytes) format values fit into
            // a Java long
            setLong(readLong(in, b));
            }
        }

    /**
     * Write a PackedInteger to a stream.
     *
     * @param out  a DataOutput stream to write to
     *
     * @throws IOException  if an I/O exception occurs while writing the data
     */
    public void writeObject(DataOutput out)
            throws IOException
        {
        verifyInitialized();

        if (isBig())
            {
            // the value is supposed to be "big", so figure out how many bytes
            // (minimum) it needs to hold its significant bits
            byte[] ab = m_bigint.toByteArray();
            int    cb = ab.length;
            int    of = 0;

            // truncate any redundant bytes
            boolean fNeg  = (ab[0] & 0x80) != 0;
            int     bSkip = fNeg ? 0xFF : 0x00;
            while (of < cb-1 && ab[of] == bSkip && (ab[of+1] & 0x80) == (bSkip & 0x80))
                {
                ++of;
                }
            cb -= of;
            assert cb > 8 && cb <= 1024;

            if (cb < 64)
                {
                // large format: length encoded in 6 MSBs of first byte, then the bytes of the int
                out.writeByte((cb-1) << 2);
                }
            else
                {
                // huge format: first byte is 0, then the length as a packed int, then the bytes
                out.writeByte(0b111111_00);
                writeLong(out, cb);
                }
            out.write(ab, of, cb);
            }
        else
            {
            writeLong(out, m_lValue);
            }
        }

    /**
     * Verify that the PackedInteger is still uninitialized (has no integer
     * value).
     *
     * @throws IllegalStateException if the value of the PackedInteger has
     *         already been set
     */
    public void verifyUninitialized()
        {
        if (m_fInitialized)
            {
            throw new IllegalStateException("already initialized");
            }
        }

    /**
     * Verify that the PackedInteger has been successfully initialized to an
     * integer value.
     *
     * @throws IllegalStateException if the value of the PackedInteger has
     *         <b>not</b> yet been set
     */
    public void verifyInitialized()
        {
        if (!m_fInitialized)
            {
            throw new IllegalStateException("not yet initialized");
            }
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return isBig() ? m_bigint.hashCode() : Long.hashCode(m_lValue);
        }

    @Override
    public boolean equals(Object obj)
        {
        if (!(obj instanceof PackedInteger that))
            {
            return false;
            }

        return this.isBig()
                ? that.isBig() && this.getBigInteger().equals(that.getBigInteger())
                : !that.isBig() && this.getLong() == that.getLong();
        }

    @Override
    public String toString()
        {
        return isBig() ? m_bigint.toString() : Long.toString(m_lValue);
        }


    // ----- Comparable methods --------------------------------------------------------------------

    @Override
    public int compareTo(PackedInteger that)
        {
        if (this.isBig() || that.isBig())
            {
            return this.getBigInteger().compareTo(that.getBigInteger());
            }

        long lThis = this.m_lValue;
        long lThat = that.m_lValue;
        return Long.compare(lThis, lThat);
        }


    // ----- public helpers ------------------------------------------------------------------------

    /**
     * Write a signed 64-bit integer to a stream using variable-length
     * encoding.
     *
     * @param out  the <tt>DataOutput</tt> stream to write to
     * @param l    the <tt>long</tt> value to write
     *
     * @throws IOException  if an I/O exception occurs
     */
    public static void writeLong(DataOutput out, long l)
            throws IOException
        {
        // test for Tiny
        if (l <= 63 && l >= -64)
            {
            out.writeByte(((int) l) << 1 | 0x01);
            return;
            }

        final int cBits = 65 - Long.numberOfLeadingZeros(Math.max(l, ~l));

        // test for Small and Medium
        int i = (int) l;
        if (((1L << cBits) & 0x3E3E00L) != 0)           // test against bits 9-13 and 17-21
            {
            if (cBits <= 13)
                {
                out.writeShort(0b010_00000000           // 0x2 marker at 0..2 in byte #1
                        | (i & 0x1F00) << 3             // bits 8..12 at 3..7 in byte #1
                        | (i & 0x00FF));                // bits 0..7  at 0..7 in byte #2
                }
            else
                {
                out.writeByte(0b110                     // 0x6 marker  at 0..2 in byte #1
                        | (i & 0x1F0000) >>> 13);       // bits 16..20 at 3..7 in byte #1
                out.writeShort(i);                      // bits 8..15  at 0..7 in byte #2
                }                                       // bits 0..7   at 0..7 in byte #3
            return;
            }

        int cBytes = (cBits + 7) >>> 3;
        out.writeByte((cBytes - 1) << 2);
        switch (cBytes)
            {
            case 1:
                out.writeByte(i);
                break;
            case 2:
                out.writeShort(i);
                break;
            case 3:
                out.writeByte(i >>> 16);
                out.writeShort(i);
                break;
            case 4:
                out.writeInt(i);
                break;
            case 5:
                out.writeByte((int) (l >>> 32));
                out.writeInt(i);
                break;
            case 6:
                out.writeShort((int) (l >>> 32));
                out.writeInt(i);
                break;
            case 7:
                out.writeByte((int) (l >>> 48));
                out.writeShort((int) (l >>> 32));
                out.writeInt(i);
                break;
            case 8:
                out.writeLong(l);
                break;
            default:
                throw new IllegalStateException("n=" + l);
            }
        }

    /**
     * Read a variable-length encoded integer value from a stream.
     *
     * @param in  a <tt>DataInput</tt> stream to read from
     *
     * @return a <tt>long</tt> value
     *
     * @throws IOException  if an I/O exception occurs
     * @throws NumberFormatException  if the integer does not fit into
     *         a <tt>long</tt> value
     */
    public static long readLong(DataInput in)
            throws IOException
        {
        return readLong(in, in.readByte());
        }

    /**
     * Determine the number of bytes of the packed integer at the specified offset in the provided
     * byte array.
     *
     * @param ab  the byte array containing a packed integer
     * @param of  the byte offset at which the packed integer is located
     *
     * @return the number of bytes used to encode the packed integer
     */
    public static int packedLength(byte[] ab, int of)
        {
        int b = ab[of];
        if ((b & 0x01) != 0)
            {
            // Tiny format
            return 1;
            }

        if ((b & 0x02) != 0)
            {
            // Small or Medium format
            return ((b & 0x04) == 0) ? 2 : 3;
            }

        if ((b & 0xFF) == 0b111111_00)
            {
            // Huge format
            long lVals = unpackInt(ab, of+1);
            return 1 + (int) (lVals >>> 32) + (int) lVals;
            }

        // Large format
        return 2 + ((b & 0xFC) >>> 2);
        }

    /**
     * Determine the number of bytes that the specified value would use if it were packed.
     *
     * @param n  the long integer value to estimate a packed length for
     *
     * @return the smallest number of bytes necessary to encode the packed integer
     */
    public static int packedLength(long n)
        {
        // test for Tiny
        if (n <= 63 && n >= -64)
            {
            return 1;
            }

        // test for Small and Medium
        final int cBits = 65 - Long.numberOfLeadingZeros(Math.max(n, ~n));
        if (((1L << cBits) & 0x3E3E00L) != 0)           // test against bits 9-13 and 17-21
            {
            return cBits <= 13 ? 2 : 3;
            }

        // Large format
        int cBytes = (cBits + 7) >>> 3;
        return 1 + cBytes;
        }

    /**
     * Extract an integer from a byte array, and report back both the integer value and its size in
     * terms of the number of bytes.
     *
     * @param ab  the byte array to unpack an integer from
     * @param of  the byte offset at which the integer is located
     *
     * @return the int value encoded as bits 0..31, and the number of bytes used by the value
     *         encoded as bits 32..63
     */
    public static long unpackInt(byte[] ab, int of)
        {
        int n;
        int cb;

        int b = ab[of];
        if ((b & 0x01) != 0)
            {
            // Tiny format: the first bit of the first byte is used to indicate a single byte
            // format, in which the entire value is contained in the 7 MSBs
            n  = b >> 1;
            cb = 1;
            }
        else if ((b & 0x02) != 0)
            {
            // the third bit is used to indicate 0: 1 trailing byte, or 1: 2 trailing bytes
            if ((b & 0x04) == 0)
                {
                // Small format: bits 3..7 of the first byte are bits 8..12 of the result, and the
                // next byte provides bits 0..7 of the result (note: and then also sign-extend)
                n  = (b & 0xFFFFFFF8) << 5 | ab[of+1] & 0xFF;
                cb = 2;
                }
            else
                {
                // Medium format: bits 3..7 of the first byte are bits 16..20 of the result, and the
                // next byte provides bits 8..15 of the result, and the next byte provides bits 0..7
                // of the result (note: and then also sign-extend)
                n  = (b & 0xFFFFFFF8) << 13 | (ab[of+1] & 0xFF) << 8 | ab[of+2] & 0xFF;
                cb = 3;
                }
            }
        else
            {
            // Large format: the first two bits of the first byte are 0, so bits 2..7 of the
            // first byte are the trailing number of bytes minus 1
            // Huge format: the first byte is 0b111111_00, so it is followed by a packed integer
            // that specifies the length of this packed integer
            final int cBytes;
            if ((b & 0xFF) == 0b111111_00)
                {
                // Huge format
                long lVals = unpackInt(ab, of+1);
                int  cbcb  = (int) (lVals >>> 32);
                cBytes = (int) lVals;
                cb     = 1 + cbcb + cBytes;
                of    += 1 + cbcb;
                }
            else
                {
                // Large format
                cBytes = 1 + ((b & 0xFC) >>> 2);
                cb     = 1 + cBytes;
                ++of;
                }

            switch (cBytes)
                {
                case 1:
                    n  = ab[of];
                    break;
                case 2:
                    n  = ab[of] << 8 | ab[of+1] & 0xFF;
                    break;
                case 3:
                    n  = ab[of] << 16 | (ab[of+1] & 0xFF) << 8 | ab[of+2] & 0xFF;
                    break;
                case 4:
                    n  = ab[of] << 24 | (ab[of+1] & 0xFF) << 16 | (ab[of+2] & 0xFF) << 8 | ab[of+3] & 0xFF;
                    break;
                default:
                    throw new IllegalStateException("# trailing bytes=" + cBytes);
                }
            }

        return ((long) cb) << 32 | n & 0xFFFFFFFFL;
        }


    // ----- internal ------------------------------------------------------------------------------

    private static long readLong(DataInput in, int b)
            throws IOException
        {
        if ((b & 0x01) != 0)
            {
            // Tiny format: the first bit of the first byte is used to indicate a single byte
            // format, in which the entire value is contained in the 7 MSBs
            return b >> 1;
            }

        if ((b & 0x02) != 0)
            {
            // the third bit is used to indicate 0: 1 trailing byte, or 1: 2 trailing bytes
            if ((b & 0x04) == 0)
                {
                // Small format: bits 3..7 of the first byte are bits 8..12 of the result, and the
                // next byte provides bits 0..7 of the result (note: and then also sign-extend)
                return (long) (b & 0xFFFFFFF8) << 5 | in.readUnsignedByte();
                }
            else
                {
                // Medium format: bits 3..7 of the first byte are bits 16..20 of the result, and the
                // next byte provides bits 8..15 of the result, and the next byte provides bits 0..7
                // of the result (note: and then also sign-extend)
                return (long) (b & 0xFFFFFFF8) << 13 | in.readUnsignedShort();
                }
            }

        // Large format: the first two bits of the first byte are 0, so bits 2..7 of the
        // first byte are the trailing number of bytes minus 1
        // Huge format: the first byte is 0, so it is followed by a packed integer that
        // specifies the length of this packed integer
        int cBytes = (b & 0xFF) == 0b111111_00 ? (int) readLong(in) : 1 + ((b & 0xFC) >>> 2);
        switch (cBytes)
            {
            case 1:
                return in.readByte();
            case 2:
                return in.readShort();
            case 3:
                return in.readByte() << 16 | in.readUnsignedShort();
            case 4:
                return in.readInt();
            case 5:
                return ((long) in.readByte()) << 32 | readUnsignedInt(in);
            case 6:
                return ((long) in.readShort()) << 32 | readUnsignedInt(in);
            case 7:
                return ((long) in.readByte()) << 48
                        | ((long) in.readUnsignedShort()) << 32
                        | readUnsignedInt(in);
            case 8:
                return in.readLong();
            default:
                throw new IllegalStateException("# trailing bytes=" + cBytes);
            }
        }

    private static long readUnsignedInt(DataInput in)
            throws IOException
        {
        return in.readInt() & 0xFFFFFFFFL;
        }

    /**
     * Determine how many bytes is necessary to hold the specified BigInteger.
     *
     * @return the minimum number of bytes to hold the integer value in
     *         2s-complement form
     *
     * @throws IllegalArgumentException if the BigInteger is out of range
     */
    private static int calculateSignedByteCount(BigInteger bigint)
        {
        return bigint.bitLength() / 8 + 1;
        }

    /**
     * Determine how many bytes is necessary to hold the specified BigInteger.
     *
     * @return the minimum number of bytes to hold the integer value in
     *         2s-complement form
     *
     * @throws IllegalArgumentException if the BigInteger is out of range
     */
    private static int calculateUnsignedByteCount(BigInteger bigint)
        {
        return (bigint.bitLength() + 7) / 8;
        }


    // ----- data members --------------------------------------------------------------------------

    /**
     * Set to true once the value has been set.
     */
    private boolean m_fInitialized;

    /**
     * Set to true if the value is too large to fit into a <tt>long</tt> (for signed values).
     */
    private boolean m_fBig;

    /**
     * The <tt>long</tt> value if the value fits into a <tt>long</tt>. Note, the value could be "big",
     * as a signed one but still fit the long as unsigned.
     */
    private long m_lValue;

    /**
     * The <tt>BigInteger</tt> value, which is non-null in several different
     * cases, including if the PackedInteger was constructed with a BigInteger
     * value, if the value was set to a BigInteger, or if the BigInteger was
     * lazily instantiated by a call to {@link #getBigInteger}.
     */
    private BigInteger m_bigint;

    /**
     * Cache of PackedInteger instances for small integers.
     */
    private static final PackedInteger[] CACHE = new PackedInteger[0x1000];


    // ----- constants -----------------------------------------------------------------------------

    /**
     * Smallest integer value to cache. One quarter of the cache size is
     * reserved for small negative integer values.
     */
    private static final long CACHE_MIN = -(CACHE.length >> 2);

    /**
     * Largest integer value to cache. Three-quarters of the cache size is
     * reserved for small positive integer values.
     */
    private static final long CACHE_MAX = CACHE_MIN + CACHE.length - 1;

    /**
     * The PackedInteger for the value <tt>0</tt>. Also used as the smallest 1-, 2-, 4-,
     * 8-, 16-, and 32-byte <b>un</b>signed integer value.
     */
    public static final PackedInteger ZERO       = valueOf(0L);
    /**
     * The PackedInteger for the value <tt>1</tt>.
     */
    public static final PackedInteger ONE        = valueOf(1L);
    /**
     * The PackedInteger for the value <tt>-1</tt>.
     */
    public static final PackedInteger NEG_ONE    = valueOf(-1L);

    /**
     * Smallest 1-byte (8-bit) signed integer value.
     */
    public static final PackedInteger SINT1_MIN  = valueOf(-0x80L);
    /**
     * Smallest 2-byte (16-bit) signed integer value.
     */
    public static final PackedInteger SINT2_MIN  = valueOf(-0x8000L);
    /**
     * Smallest 4-byte (32-bit) signed integer value.
     */
    public static final PackedInteger SINT4_MIN  = valueOf(-0x80000000L);
    /**
     * Smallest 8-byte (64-bit) signed integer value.
     */
    public static final PackedInteger SINT8_MIN  = valueOf(-0x8000000000000000L);
    /**
     * Smallest 16-byte (128-bit) signed integer value.
     */
    public static final PackedInteger SINT16_MIN = new PackedInteger(new BigInteger("-8" + "0".repeat(31), 16));
    /**
     * Smallest N-byte signed integer value (arbitrary 1KB limit).
     */
    public static final PackedInteger SINTN_MIN = new PackedInteger(new BigInteger("-8" + "0".repeat(2047), 16));
    /**
     * Largest 1-byte (8-bit) signed integer value.
     */
    public static final PackedInteger SINT1_MAX  = valueOf(0x7F);
    /**
     * Largest 2-byte (16-bit) signed integer value.
     */
    public static final PackedInteger SINT2_MAX  = valueOf(0x7FFF);
    /**
     * Largest 4-byte (32-bit) signed integer value.
     */
    public static final PackedInteger SINT4_MAX  = valueOf(0x7FFF_FFFF);
    /**
     * Largest 8-byte (64-bit) signed integer value.
     */
    public static final PackedInteger SINT8_MAX  = valueOf(0x7FFF_FFFF_FFFF_FFFFL);
    /**
     * Largest 16-byte (128-bit) signed integer value.
     */
    public static final PackedInteger SINT16_MAX = new PackedInteger(new BigInteger("7" + "F".repeat(31), 16));
    /**
     * Largest N-byte signed integer value (arbitrary 1KB limit).
     */
    public static final PackedInteger SINTN_MAX = new PackedInteger(new BigInteger("7" + "F".repeat(2047), 16));
    /**
     * Largest 1-byte (8-bit) unsigned integer value.
     */
    public static final PackedInteger UINT1_MAX  = valueOf(0xFF);
    /**
     * Largest 2-byte (16-bit) unsigned integer value.
     */
    public static final PackedInteger UINT2_MAX  = valueOf(0xFFFF);
    /**
     * Largest 4-byte (32-bit) unsigned integer value.
     */
    public static final PackedInteger UINT4_MAX  = valueOf(0xFFFF_FFFFL);
    /**
     * Largest 8-byte (64-bit) unsigned integer value.
     */
    public static final PackedInteger UINT8_MAX  = new PackedInteger(new BigInteger("F".repeat(16), 16));
    /**
     * Largest 16-byte (128-bit) unsigned integer value.
     */
    public static final PackedInteger UINT16_MAX = new PackedInteger(new BigInteger("F".repeat(32), 16));
    /**
     * Largest N-byte unsigned integer value (arbitrary 1KB limit).
     */
    public static final PackedInteger UINTN_MAX = SINTN_MAX;

    /**
     * Decimal "Kilo".
     */
    public static final PackedInteger KB = valueOf(1000);
    /**
     * Binary "Kilo".
     */
    public static final PackedInteger KI = valueOf(1024);
    /**
     * Decimal "Mega".
     */
    public static final PackedInteger MB = valueOf(1000 * 1000);
    /**
     * Binary "Mega".
     */
    public static final PackedInteger MI = valueOf(1024 * 1024);
    /**
     * Decimal "Giga".
     */
    public static final PackedInteger GB = valueOf(1000 * 1000 * 1000);
    /**
     * Binary "Giga".
     */
    public static final PackedInteger GI = valueOf(1024 * 1024 * 1024);
    /**
     * Decimal "Tera".
     */
    public static final PackedInteger TB = valueOf(1000L * 1000 * 1000 * 1000);
    /**
     * Binary "Tera".
     */
    public static final PackedInteger TI = valueOf(1024L * 1024 * 1024 * 1024);
    /**
     * Decimal "Peta".
     */
    public static final PackedInteger PB = valueOf(1000L * 1000 * 1000 * 1000 * 1000);
    /**
     * Binary "Peta".
     */
    public static final PackedInteger PI = valueOf(1024L * 1024 * 1024 * 1024 * 1024);
    /**
     * Decimal "Exa".
     */
    public static final PackedInteger EB = valueOf(1000L * 1000 * 1000 * 1000 * 1000 * 1000);
    /**
     * Binary "Exa".
     */
    public static final PackedInteger EI = valueOf(1024L * 1024 * 1024 * 1024 * 1024 * 1024);
    /**
     * Decimal "Zetta".
     */
    public static final PackedInteger ZB = EB.mul(KB);
    /**
     * Binary "Zetta".
     */
    public static final PackedInteger ZI = EI.mul(KI);
    /**
     * Decimal "Yotta".
     */
    public static final PackedInteger YB = ZB.mul(KB);
    /**
     * Binary "Yotta".
     */
    public static final PackedInteger YI = ZI.mul(KI);

    /**
     * The decimal (1000x) factors.
     */
    public static final PackedInteger[] xB_FACTORS = {KB, MB, GB, TB, PB, EB, ZB, YB, };
    /**
     * The binary (1024x) factors.
     */
    public static final PackedInteger[] xI_FACTORS = {KI, MI, GI, TI, PI, EI, ZI, YI, };
    }