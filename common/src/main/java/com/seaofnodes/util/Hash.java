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

import java.util.Arrays;

/**
 * Helper functions for producing compound hashes.
 *
 * <p>
 * For example given a class with fields {@code a, b, c, d} of any type, a compound hash can be
 * computed via:
 * <pre>{@code
 * int hash = Hash.of(a,
 *            Hash.of(b,
 *            Hash.of(c,
 *            Hash.of(d))));
 * }
 * </pre>
 * 
 * @author mf
 */
public final class Hash
    {
    private Hash()
        {
        // blocked constructor
        }

    /**
     * Incorporate a value into a running hash.
     *
     * @param v the value (or hash) to incorporate into the running hash
     * @param h the running hash to combine with
     *
     * @return the compound hash
     */
    public static int of(int v, int h)
        {
        return 31 * h + v;
        }

    /**
     * Incorporate a value into a running hash.
     *
     * @param v the value to incorporate into the hash
     * @param h the running hash to combine with
     *
     * @return the compound hash
     */
    public static int of(boolean v, int h)
        {
        return of(of(v), h);
        }

    /**
     * Incorporate a value into a running hash.
     *
     * @param v the value to incorporate into the hash
     * @param h the running hash to combine with
     *
     * @return the compound hash
     */
    public static int of(byte v, int h)
        {
        return of(of(v), h);
        }

    /**
     * Incorporate a value into a running hash.
     *
     * @param v the value to incorporate into the hash
     * @param h the running hash to combine with
     *
     * @return the compound hash
     */
    public static int of(char v, int h)
        {
        return of(of(v), h);
        }

    /**
     * Incorporate a value into a running hash.
     *
     * @param v the value to incorporate into the hash
     * @param h the running hash to combine with
     *
     * @return the compound hash
     */
    public static int of(short v, int h)
        {
        return of(of(v), h);
        }

    /**
     * Incorporate a value into a running hash.
     *
     * @param v the value to incorporate into the hash
     * @param h the running hash to combine with
     *
     * @return the compound hash
     */
    public static int of(float v, int h)
        {
        return of(of(v), h);
        }

    /**
     * Incorporate a value into a running hash.
     *
     * @param v the value to incorporate into the hash
     * @param h the running hash to combine with
     *
     * @return the compound hash
     */
    public static int of(long v, int h)
        {
        return of(of(v), h);
        }

    /**
     * Incorporate a value into a running hash.
     *
     * @param v the value to incorporate into the hash
     * @param h the running hash to combine with
     *
     * @return the compound hash
     */
    public static int of(double v, int h)
        {
        return of(of(v), h);
        }

    /**
     * Incorporate a value into a running hash.
     *
     * @param v the value to incorporate into the hash
     * @param h the running hash to combine with
     *
     * @return the compound hash
     */
    public static int of(Object v, int h)
        {
        return of(of(v), h);
        }

    /**
     * Return a hash of the value.
     *
     * @param v the value
     *
     * @return the hash
     */
    public static int of(boolean v)
        {
        return Boolean.hashCode(v);
        }

    /**
     * Return a hash of the value.
     *
     * @param v the value
     *
     * @return the hash
     */
    public static int of(byte v)
        {
        return Byte.hashCode(v);
        }

    /**
     * Return a hash of the value.
     *
     * @param v the value
     *
     * @return the hash
     */
    public static int of(short v)
        {
        return Short.hashCode(v);
        }

    /**
     * Return a hash of the value.
     *
     * @param v the value
     *
     * @return the hash
     */
    public static int of(char v)
        {
        return Character.hashCode(v);
        }

    /**
     * Return a hash of the value.
     *
     * @param v the value
     *
     * @return the hash
     */
    public static int of(int v)
        {
        return Integer.hashCode(v);
        }

    /**
     * Return a hash of the value.
     *
     * @param v the value
     *
     * @return the hash
     */
    public static int of(float v)
        {
        return Float.hashCode(v);
        }

    /**
     * Return a hash of the value.
     *
     * @param v the value
     *
     * @return the hash
     */
    public static int of(long v)
        {
        return Long.hashCode(v);
        }

    /**
     * Return a hash of the value.
     *
     * @param v the value
     *
     * @return the hash
     */
    public static int of(double v)
        {
        return Double.hashCode(v);
        }

    /**
     * Return a hash of the value.
     *
     * <p>
     * If the value is an array the hash will be via {@link java.util.Arrays#hashCode}.
     *
     * @param v the value
     *
     * @return the hash
     */
    public static int of(Object v)
        {
        return v == null
            ? 0
            : v.getClass().isArray()
                ? ofArray(v)
                : v.hashCode();
        }

    /**
     * Return the hash of an array.
     *
     * @param v the non-null array
     *
     * @return the hash
     */
    private static int ofArray(Object v)
        {
        Class<?> clzComp = v.getClass().getComponentType();
        return
            clzComp.isPrimitive()
                ? clzComp == int   .class ? Arrays.hashCode(    (int[]) v)
                : clzComp == long  .class ? Arrays.hashCode(   (long[]) v)
                : clzComp == byte  .class ? Arrays.hashCode(   (byte[]) v)
                : clzComp == char  .class ? Arrays.hashCode(   (char[]) v)
                : clzComp == double.class ? Arrays.hashCode( (double[]) v)
                : clzComp == float .class ? Arrays.hashCode(  (float[]) v)
                : clzComp == short .class ? Arrays.hashCode(  (short[]) v)
                                          : Arrays.hashCode((boolean[]) v)
            : Arrays.hashCode((Object[]) v);
        }
    }
