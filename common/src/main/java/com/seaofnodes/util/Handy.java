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


import java.io.BufferedInputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.io.UTFDataFormatException;

import java.lang.reflect.Array;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;

import java.nio.charset.Charset;

import java.util.ArrayList;
import java.util.Arrays;


/**
 * Handy static methods.
 */
public class Handy
    {
    // ----- String formatting ---------------------------------------------------------------------

    /**
     * Take the passed nibble (the low order 4 bits of the int) and return the corresponding hexit
     * (hexadecimal digit) in the range of '0' - 'F'.
     *
     * @param n  the nibble value
     *
     * @return the hexadecimal representation of the passed nibble
     */
    public static char nibbleToChar(int n)
        {
        n = n & 0xF;
        return (n <= 9)
                ? (char) ('0' + n)
                : (char) ('A' - 0xA + n);
        }

    /**
     * Render the passed byte (the low order 8 bits of the int) into the passed StringBuilder as
     * two hexadecimal digits.
     *
     * @param sb  the StringBuilder to append to
     * @param n   the byte value
     *
     * @return the StringBuilder
     */
    public static StringBuilder appendByteAsHex(StringBuilder sb, int n)
        {
        return sb.append(nibbleToChar(n >> 4))
                 .append(nibbleToChar(n));
        }

    /**
     * Format the passed byte (the low order 8 bits of the int) into a String of the form "0xFF".
     *
     * @param n  the byte value
     *
     * @return a String representation of the byte
     */
    public static String byteToHexString(int n)
        {
        return appendByteAsHex(new StringBuilder(4).append("0x"), n).toString();
        }

    /**
     * Append a portion of a byte array to a StringBuilder as a sequence of hex digits.
     *
     * @param sb  the StringBuilder to append to
     * @param ab  the byte array
     * @param of  the offset of the first byte in the byte array to render
     * @param cb  the number bytes from the byte array to render
     *
     * @return the StringBuilder
     */
    public static StringBuilder appendByteArrayAsHex(StringBuilder sb, byte[] ab, int of, int cb)
        {
        sb.ensureCapacity(sb.length() + cb * 2);
        while (--cb >= 0)
            {
            int n = ab[of++];
            sb.append(nibbleToChar(n >> 4))
              .append(nibbleToChar(n));
            }
        return sb;
        }

    /**
     * Append the contents of a byte array to a StringBuilder as a sequence of hex digits.
     *
     * @param sb  the StringBuilder to append to
     * @param ab  the byte array
     *
     * @return the StringBuilder
     */
    public static StringBuilder appendByteArrayAsHex(StringBuilder sb, byte[] ab)
        {
        return appendByteArrayAsHex(sb, ab, 0, ab.length);
        }

    /**
     * Format a portion of the passed byte array into a String of the form "0x01234FF".
     *
     * @param ab  the byte array
     * @param of  the offset of the first byte in the byte array to render
     * @param cb  the number bytes from the byte array to render
     *
     * @return a String representation of the byte array
     */
    public static String byteArrayToHexString(byte[] ab, int of, int cb)
        {
        return appendByteArrayAsHex(new StringBuilder(2 + cb * 2).append("0x"),
                ab, of, cb).toString();
        }

    /**
     * Format the passed byte array into a String of the form "0x01234FF".
     *
     * @param ab  the byte array
     *
     * @return a String representation of the byte array
     */
    public static String byteArrayToHexString(byte[] ab)
        {
        return byteArrayToHexString(ab, 0, ab.length);
        }

    /**
     * Convert a String of hex digits into a <tt>byte[]</tt>.
     *
     * @param s  a String of hexadecimal digits; the leading "0x" is optional
     *
     * @return a byte array
     */
    public static byte[] hexStringToByteArray(String s)
        {
        int ofch = 0;
        int cch  = s.length();
        int ofb  = 0;
        int cb   = (cch + 1) / 2;

        // check for (and skip) the "0x" prefix
        if (s.charAt(0) == '0' && (s.charAt(1) == 'x' || s.charAt(1) == 'X'))
            {
            ofch += 2;
            --cb;
            }

        final byte[] ab = new byte[cb];

        // handle the case where the first nibble is an implied (missing) '0'
        if ((cch & 0x1) != 0)
            {
            ab[ofb++] = (byte) hexitValue(s.charAt(ofch++));
            }

        // convert the remaining pairs of hexits to bytes
        while (ofch < cch)
            {
            ab[ofb++] = (byte) (hexitValue(s.charAt(ofch)) << 4 | hexitValue(s.charAt(ofch+1)));
            ofch += 2;
            }

        return ab;
        }

    /**
     * Format bytes from the passed byte array as a "hex dump", useful for debugging.
     *
     * @param ab             the byte array
     * @param of             the offset of the first byte to show in the hex
     *                       dump
     * @param cb             the number of bytes to show in the hex dump
     * @param cBytesPerLine  the number of bytes to show in each line of the hex
     *                       dump
     *
     * @return a String containing the hex dump
     */
    public static String byteArrayToHexDump(byte[] ab, int of, int cb, int cBytesPerLine)
        {
        assert ab != null;
        assert of >= 0 && cb >= 0 &&  of + cb <= ab.length;
        assert cBytesPerLine > 0;

        // first figure out how many digits it will take to show the address
        final int cchAddr = countHexDigits(of + cb) + 1 & ~0x01;

        // format is 12F0: 00 12 32 A0 ????\n
        // line length is addressLen + 4*cbPerLine + 3
        final int    cch = cchAddr + cBytesPerLine * 4 + 3;
        final char[] ach = new char[cch];
        Arrays.fill(ach, ' ');
        ach[cchAddr] = ':';
        ach[cch-1]   = '\n';

        final int           cLines = Math.max((cb + cBytesPerLine - 1) / cBytesPerLine, 1);
        final StringBuilder sb     = new StringBuilder(cLines * cch);
        final int           ofHex  = cchAddr + 2;
        final int           ofChar = ofHex + cBytesPerLine * 3;

        for (int iLine = 0; iLine < cLines; ++iLine)
            {
            // format the address
            renderIntToHex(of, ach, 0, cchAddr);

            for (int iByte = 0; iByte < cBytesPerLine; ++iByte)
                {
                if (cb > 0)
                    {
                    int b = ab[of];
                    renderByteToHex(b, ach, ofHex + iByte * 3);

                    char ch = (char) (b & 0xFF);
                    ach[ofChar + iByte] = Character.isISOControl(ch) ? '.' : ch;
                    }
                else
                    {
                    ach[ofHex + iByte * 3    ] = ' ';
                    ach[ofHex + iByte * 3 + 1] = ' ';
                    ach[ofChar + iByte       ] = ' ';
                    }

                ++of;
                --cb;
                }

            sb.append(ach, 0, iLine < cLines - 1 ? cch : cch - 1);
            }

        return sb.toString();
        }

    /**
     * Format bytes from the passed byte array as a "hex dump", useful for debugging.
     *
     * @param ab             the byte array
     * @param cBytesPerLine  the number of bytes to show in each line of the hex
     *                       dump
     *
     * @return a String containing the hex dump
     */
    public static String byteArrayToHexDump(byte[] ab, int cBytesPerLine)
        {
        return byteArrayToHexDump(ab, 0, ab.length, cBytesPerLine);
        }

    /**
     * Convert an array of bytes to a long value.
     *
     * @param ab  the array of bytes
     * @param of  the starting index
     *
     * @return the long value
     */
    public static long byteArrayToLong(byte[] ab, int of)
        {
        return   ((long) (ab[of++])        << 56)
               + ((long) (ab[of++] & 0xFF) << 48)
               + ((long) (ab[of++] & 0xFF) << 40)
               + ((long) (ab[of++] & 0xFF) << 32)
               + ((long) (ab[of++] & 0xFF) << 24)
               + (       (ab[of++] & 0xFF) << 16)
               + (       (ab[of++] & 0xFF) << 8 )
               + (        ab[of  ] & 0xFF       );
        }

    /**
     * Produce an array of bytes for the specified long value.
     *
     * @param l  the long value
     *
     * @return the byte array
     */
    static public byte[] toByteArray(long l)
        {
        return new byte[]
            {
            (byte) (l >> 56),
            (byte) (l >> 48),
            (byte) (l >> 40),
            (byte) (l >> 32),
            (byte) (l >> 24),
            (byte) (l >> 16),
            (byte) (l >> 8 ),
            (byte) l,
            };
        }

    /**
     * Render the bytes of the specified long value into an array of bytes at the specified location.
     *
     * @param l   the long value
     * @param ab  the byte array to copy into
     * @param ab  the byte array offset to write the long value at
     *
     * @return the byte array
     */
    static public void toByteArray(long l, byte[] ab, int of)
        {
        ab[of++] = (byte) (l >> 56);
        ab[of++] = (byte) (l >> 48);
        ab[of++] = (byte) (l >> 40);
        ab[of++] = (byte) (l >> 32);
        ab[of++] = (byte) (l >> 24);
        ab[of++] = (byte) (l >> 16);
        ab[of++] = (byte) (l >> 8 );
        ab[of  ] = (byte) l;
        }

    /**
     * Determine how many hex digits it will take to render the passed <tt>int</tt> value as an
     * <b>unsigned</b> hex value.
     *
     * @param n  the int value, which is treated as an unsigned 32-bit value
     *
     * @return the number of hex digits required to render the int value
     */
    public static int countHexDigits(int n)
        {
        return Math.max((32 - Integer.numberOfLeadingZeros(n) + 3) / 4, 1);
        }

    /**
     * Render the passed <tt>int</tt> into the passed StringBuilder as the specified number of hex
     * digits.
     *
     * @param sb  the StringBuilder to append to
     * @param n   the int value
     * @param cch  the number of hex digits to render
     *
     * @return the StringBuilder
     */
    public static StringBuilder appendIntAsHex(StringBuilder sb, int n, int cch)
        {
        assert cch >= 0 && cch <= 8;
        for (int cBits = (cch-1) * 4; cBits >= 0; cBits -= 4)
            {
            sb.append(nibbleToChar(n >> cBits));
            }
        return sb;
        }

    /**
     * Render the passed <tt>int</tt> into the passed StringBuilder as 8 hexadecimal digits.
     *
     * @param sb  the StringBuilder to append to
     * @param n   the int value
     *
     * @return the StringBuilder
     */
    public static StringBuilder appendIntAsHex(StringBuilder sb, int n)
        {
        return appendIntAsHex(sb, n, 8);
        }

    /**
     * Format the passed <tt>int</tt> into a String of the form "0x12345678".
     *
     * @param n  the byte value
     *
     * @return a hexadecimal String representation of the value
     */
    public static String intToHexString(int n)
        {
        return appendIntAsHex(new StringBuilder(10).append("0x"), n).toString();
        }

    /**
     * Determine how many hex digits it will take to render the passed <tt>long</tt> value as an
     * <b>unsigned</b> hex value.
     *
     * @param n  the long value, which is treated as an unsigned 64-bit value
     *
     * @return the number of hex digits required to render the long value
     */
    public static int countHexDigits(long n)
        {
        return Math.max((64 - Long.numberOfLeadingZeros(n) + 3) / 4, 1);
        }

    /**
     * Render the passed <tt>long</tt> into the passed StringBuilder as the specified number of hex
     * digits.
     *
     * @param sb   the StringBuilder to append to
     * @param n    the long value
     * @param cch  the number of hex digits to render
     *
     * @return the StringBuilder
     */
    public static StringBuilder appendLongAsHex(StringBuilder sb, long n, int cch)
        {
        assert cch >= 0 && cch <= 16;
        for (int cBits = (cch-1) * 4; cBits >= 0; cBits -= 4)
            {
            sb.append(nibbleToChar((int) (n >> cBits)));
            }
        return sb;
        }

    /**
     * Render the passed <tt>long</tt> into the passed StringBuilder as 16 hexadecimal digits.
     *
     * @param sb  the StringBuilder to append to
     * @param n   the long value
     *
     * @return the StringBuilder
     */
    public static StringBuilder appendLongAsHex(StringBuilder sb, long n)
        {
        return appendLongAsHex(sb, n, 16);
        }


    /**
     * Format the passed <tt>int</tt> into a String of the form "0x12345678".
     *
     * @param n  the byte value
     *
     * @return a hexadecimal String representation of the value
     */
    public static String longToHexString(long n)
        {
        return appendLongAsHex(new StringBuilder(18).append("0x"), n).toString();
        }

    /**
     * Format the specified byte value into the passed char array as two hex digits.
     *
     * @param n    the byte value
     * @param ach  the char array to render into
     * @param of   the offset into the char array for the first hex digit
     *
     * @return the offset of the first character after the two hex digits
     */
    public static int renderByteToHex(int n, char[] ach, int of)
        {
        ach[of  ] = nibbleToChar(n >> 4);
        ach[of+1] = nibbleToChar(n);
        return of + 2;
        }

    /**
     * Format the specified int value into the passed char array as the specified number of hex
     * digits.
     *
     * @param n    the int value
     * @param ach  the char array to render into
     * @param of   the offset into the char array for the first hex digit
     * @param cch  the number of hex digits to render
     *
     * @return the offset of the first character after the last hex digit
     */
    public static int renderIntToHex(int n, char[] ach, int of, int cch)
        {
        for (int i = 0; i < cch; ++i)
            {
            ach[of+cch-i-1] = nibbleToChar(n);
            n >>>= 4;
            }
        return of + cch;
        }

    /**
     * Format the specified long value into the passed char array as the specified number of hex
     * digits.
     *
     * @param n    the long value
     * @param ach  the char array to render into
     * @param of   the offset into the char array for the first hex digit
     * @param cch  the number of hex digits to render
     *
     * @return the offset of the first character after the last hex digit
     */
    public static int renderLongToHex(long n, char[] ach, int of, int cch)
        {
        for (int i = 0; i < cch; ++i)
            {
            ach[of+cch-i-1] = nibbleToChar((int) n);
            n >>>= 4;
            }
        return of + cch;
        }

    /**
     * Determine if the specified character is a decimal digit.
     *
     * @param ch  a character
     *
     * @return true iff the character is a decimal digit
     */
    public static boolean isDigit(char ch)
        {
        return ch >= '0' & ch <= '9';
        }

    /**
     * For a decimal digit, determine its integer value.
     *
     * @param ch  a decimal digit
     *
     * @return the integer value of the specified decimal digit
     */
    public static int digitValue(char ch)
        {
        assert isDigit(ch);
        return ch - '0';
        }

    /**
     * Determine if the specified character is a hexadecimal digit.
     *
     * @param ch  a character
     *
     * @return true iff the character is a hexadecimal digit
     */
    public static boolean isHexit(char ch)
        {
        // hexits in the unicode range appear in this order:
        //   {..., 0, ..., 9, ..., A, ..., F, ..., a, ..., f, ...}
        // so a hexit has to be in the range 0..f, and the entire character
        // range from 0..f is only 0x36 (so it fits inside a long mask)
        return ch >= '0' & ch <= 'f' & ((1L << ('f' - ch)) &
                // 3               4               5               6
                // 0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456
                // 0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[/]^_`abcdef
                0b01111111111000000011111100000000000000000000000000111111L) != 0;
        }

    /**
     * For a hexadecimal digit, determine its integer value.
     *
     * @param ch  a hexadecimal digit
     *
     * @return the integer value of the specified hexadecimal digit
     */
    public static int hexitValue(char ch)
        {
        assert isHexit(ch);
        return ch <= '9' ? ch - '0' : (ch | 0x20) - ('a' - 0xA);
        }

    /**
     * Determine if the specified character is an ASCII letter, which is in the range 'A' to 'Z',
     * or 'a' to 'z'.
     *
     * @param ch  the character to test
     *
     * @return true iff the character is an ASCII letter
     */
    public static boolean isAsciiLetter(char ch)
        {
        // ASCII letters in the unicode range appear in this order:
        //   {..., A, ..., Z, ..., a, ..., z, ...}
        return ch >= 'A' & ch <= 'z' & ((1L << ('z' - ch)) &
                // 4              5               6               7
                // 123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789A
                // ABCDEFGHIJKLMNOPQRSTUVWXYZ[/]^_`abcdefghijklmnopqrstuvwxyz
                0b01111111111111111111111111100000011111111111111111111111111L) != 0;
        }


    // ----- String manipulation -------------------------------------------------------------------

    /**
     * Count the number occurrences of the specified character in the passed String.
     *
     * @param s   the String to scan
     * @param ch  the character to scan for
     *
     * @return the number of occurrences of the character
     */
    public static int countChar(String s, char ch)
        {
        int c  = 0;
        int of = s.indexOf(ch);
        while (of >= 0)
            {
            ++c;
            of = s.indexOf(ch, of + 1);
            }
        return c;
        }

    /**
     * Using the specified delimiter, parse the passed String into an array of Strings, each of
     * which occurred in the original String but separated by the specified delimiter.
     *
     * @param s        the String to parse
     * @param chDelim  the character delimiter
     *
     * @return the array of Strings parsed from the passed String
     */
    public static String[] parseDelimitedString(String s, char chDelim)
        {
        if (s == null)
            {
            return null;
            }

        if (s.length() == 0)
            {
            return NO_ARGS;
            }

        int of = s.indexOf(chDelim);
        if (of < 0)
            {
            return new String[] {s};
            }

        ArrayList<String> list = new ArrayList<>();
        int ofPrev = 0;
        do
            {
            list.add(s.substring(ofPrev, of));
            ofPrev = of + 1;
            of     = s.indexOf(chDelim, ofPrev);
            }
        while (of >= 0);
        list.add(s.substring(ofPrev));
        return list.toArray(NO_ARGS);
        }

    /**
     * Create a string containing the specified number of the specified character.
     *
     * @param ch   the character to duplicate
     * @param cch  the size of the resulting string
     *
     * @return a string containing the specified number of the specified character
     */
    public static String dup(char ch, int cch)
        {
        StringBuilder sb = new StringBuilder(cch);
        while (cch-- > 0)
            {
            sb.append(ch);
            }
        return sb.toString();
        }

    /**
     * Indent each line of the passed text by prepending the specified indentation String.
     *
     * @param sText    the text to indent
     * @param sIndent  the indentation to use
     *
     * @return the indented String
     */
    public static String indentLines(String sText, String sIndent)
        {
        int cchOld = sText.length();
        if (cchOld == 0)
            {
            return "";
            }

        int cLines = countChar(sText, '\n') + 1;
        int cchNew = cchOld + cLines * sIndent.length();
        StringBuilder sb = new StringBuilder(cchNew);

        int ofLine    = 0;
        int ofNewline = sText.indexOf('\n');
        while (ofNewline >= 0)
            {
            if (ofNewline > ofLine)
                {
                sb.append(sIndent);
                }
            sb.append(sText, ofLine, ofNewline + 1);

            ofLine = ofNewline + 1;
            ofNewline = sText.indexOf('\n', ofLine);
            }

        if (ofLine < cchOld)
            {
            sb.append(sIndent)
              .append(sText, ofLine, cchOld);
            }

        return sb.toString();
        }

    /**
     * Determine if the specified character needs to be escaped in order to be displayed.
     *
     * @param ch  the character to evaluate
     *
     * @return true iff the character should be escaped in order to be displayed
     */
    public static boolean isCharEscaped(char ch)
        {
        if (ch < 0x80)
            {
            return ch < 0x20 || ch == '\'' || ch == '\"' || ch == '\\' || ch == 0x7F;
            }
        else
            {
            return !Character.isValidCodePoint(ch) ||
                    Character.getType(ch) == Character.CONTROL ||
                    ch == '\u2028' || ch == '\u2029';
            }
        }

    /**
     * Append the specified character to the StringBuilder, escaping if necessary.
     *
     * @param sb  the StringBuilder to append to
     * @param ch  the character to escape
     *
     * @return the StringBuilder
     */
    public static StringBuilder appendChar(StringBuilder sb, char ch)
        {
        if (isCharEscaped(ch))
            {
            switch (ch)
                {
                case '\\':
                    return sb.append("\\\\");
                case '\b':
                    return sb.append("\\b");
                case '\f':
                    return sb.append("\\f");
                case '\n':
                    return sb.append("\\n");
                case '\r':
                    return sb.append("\\r");
                case '\t':
                    return sb.append("\\t");
                case '\'':
                    return sb.append("\\'");
                case '\"':
                    return sb.append("\\\"");
                case 0x7F:              // DEL
                    return sb.append("\\d");
                default:
                    return appendIntAsHex(sb.append('\\').append('u'), ch, 4);
                }
            }
        else
            {
            return sb.append(ch);
            }
        }

    /**
     * Render a character as it would appear as a constant in source code.
     *
     * @param ch  the character to quote
     *
     * @return a string showing the specified character, escaped if necessary
     */
    public static String quotedChar(char ch)
        {
        return appendChar(new StringBuilder(9).append('\''), ch).append('\'').toString();
        }

    /**
     * Append the specified String to the StringBuilder, escaping if and as necessary.
     *
     * @param sb  the StringBuilder to append to
     * @param s   the String to escape as necessary
     *
     * @return the StringBuilder
     */
    public static StringBuilder appendString(StringBuilder sb, String s)
        {
        for (int of = 0, cch = s.length(); of < cch; ++of)
            {
            appendChar(sb, s.charAt(of));
            }
        return sb;
        }

    /**
     * Render a String as it would appear as a constant in source code.
     *
     * @param s  the String to quote
     *
     * @return a String showing the quoted version of the passed String,
     *         escaping characters if and when necessary
     */
    public static String quotedString(String s)
        {
        return appendString(new StringBuilder(s.length() + 2).append('\"'), s).append(
                '\"').toString();
        }


    // ----- packed integers -----------------------------------------------------------------------

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
    public static long readPackedLong(DataInput in)
            throws IOException
        {
        return PackedInteger.readLong(in);
        }

    /**
     * Write a signed 64-bit integer to a stream using variable-length encoding.
     *
     * @param out  the <tt>DataOutput</tt> stream to write to
     * @param n    the <tt>long</tt> value to write
     *
     * @throws IOException  if an I/O exception occurs
     */
    public static void writePackedLong(DataOutput out, long n)
            throws IOException
        {
        PackedInteger.writeLong(out, n);
        }

    /**
     * Read a variable-length encoded 32-bit integer from a stream.
     *
     * @param in  a <tt>DataInput</tt> stream to read from
     *
     * @return an <tt>int</tt> value in the range <tt>Integer.MIN_VALUE..Integer.MAX_VALUE</tt>
     *
     * @throws java.io.IOException  if an I/O exception occurs
     */
    public static int readPackedInt(DataInput in)
            throws IOException
        {
        long n = readPackedLong(in);
        if (n < Integer.MIN_VALUE || n > Integer.MAX_VALUE)
            {
            throw new IOException("value (" + n + ") exceeds 32-bit range");
            }

        return (int) n;
        }

    /**
     * Read a variable-length encoded 32-bit integer magnitude from a stream.
     * <p>
     * Note that while the XVM itself is a 64-bit machine, Java does not support 64-bit (or even
     * unsigned 32-bit) magnitudes. This method is a convenience method that verifies that the
     * magnitude is within a range supported by Java.
     *
     * @param in  a <tt>DataInput</tt> stream to read from
     *
     * @return an <tt>int</tt> value in the range <tt>0..Integer.MAX_VALUE</tt>
     *
     * @throws java.io.IOException  if an I/O exception occurs
     */
    public static int readMagnitude(DataInput in)
            throws IOException
        {
        long n = readPackedLong(in);
        if (n > Integer.MAX_VALUE)
            {
            // this is unsupported in Java; arrays are limited in size
            // by their use of signed 32-bit magnitudes and indexes
            throw new IOException("magnitude (" + n + ") exceeds 32-bit maximum");
            }
        else if (n < 0)
            {
            throw new IOException("negative magnitude (" + n + ") is illegal");
            }

        return (int) n;
        }

    /**
     * Read a variable-length encoded 32-bit integer index from a stream.
     * <p>
     * Note that while the XVM itself is a 64-bit machine, Java does not support 64-bit (or even
     * unsigned 32-bit) indexes. This method is a convenience method that verifies that the index is
     * within a range supported by Java.
     *
     * @param in  a <tt>DataInput</tt> stream to read from
     *
     * @return an <tt>int</tt> value in the range <tt>0..Integer.MAX_VALUE</tt>,
     *         or <tt>-1</tt>
     *
     * @throws java.io.IOException  if an I/O exception occurs
     */
    public static int readIndex(DataInput in)
            throws IOException
        {
        long n = readPackedLong(in);
        if (n > Integer.MAX_VALUE)
            {
            // this is unsupported in Java; arrays are limited in size
            // by their use of signed 32-bit magnitudes and indexes
            throw new IOException("index (" + n + ") exceeds 32-bit maximum");
            }
        else if (n < -1)
            {
            throw new IOException("negative index (" + n + ") is illegal");
            }

        return (int) n;
        }


    // ----- unicode -------------------------------------------------------------------------------

    /**
     * Read a single unicode character (code-point) from the passed DataInput, encoded using the
     * UTF-8 format.
     *
     * @param in  the DataInput to read from
     *
     * @return a Unicode code-point (which can exceed the 16-bit <tt>char</tt>
     *         type in Java)
     *
     * @throws IOException  if an I/O exception occurs while reading the data,
     *         or if a UTF-8 format error is detected
     */
    public static int readUtf8Char(DataInput in)
            throws IOException
        {
        int b = in.readUnsignedByte();
        if ((b & 0x80) == 0)
            {
            // ASCII - single byte 0xxxxxxx format
            return b;
            }

        // otherwise the format is based on the number of high-order 1-bits:
        // #1s first byte  trailing  # trailing  bits  code-points
        // --- ----------  --------  ----------  ----  -----------------------
        //  2  110xxxxx    10xxxxxx      1        11   U+0080    - U+07FF
        //  3  1110xxxx    10xxxxxx      2        16   U+0800    - U+FFFF
        //  4  11110xxx    10xxxxxx      3        21   U+10000   - U+1FFFFF
        //  5  111110xx    10xxxxxx      4        26   U+200000  - U+3FFFFFF
        //  6  1111110x    10xxxxxx      5        31   U+4000000 - U+7FFFFFFF
        switch (Integer.highestOneBit(~(0xFFFFFF00 | b)))
            {
            case 0b00100000: // 2-byte format
                return (b & 0b00011111) <<  6 | nextCharBits(in);
            case 0b00010000: // 3-byte format
                return (b & 0b00001111) << 12 | nextCharBits(in) <<  6 | nextCharBits(in);
            case 0b00001000: // 4-byte format
                return (b & 0b00000111) << 18 | nextCharBits(in) << 12 | nextCharBits(in) <<  6 | nextCharBits(in);
            case 0b00000100: // 5-byte format
                return (b & 0b00000011) << 24 | nextCharBits(in) << 18 | nextCharBits(in) << 12 | nextCharBits(in) <<  6 | nextCharBits(in);
            case 0b00000010: // 6-byte format
                return (b & 0b00000001) << 30 | nextCharBits(in) << 24 | nextCharBits(in) << 18 | nextCharBits(in) << 12 | nextCharBits(in) << 6 | nextCharBits(in);
            default:
                throw new UTFDataFormatException("initial byte: " + byteToHexString(b));
            }
        }

    /**
     * Internal: Read a UTF-8 continuation byte and validate/decode it.
     *
     * @param in  the DataInput to read from
     *
     * @return the 6 bits of UTF-8 data from the continuation byte
     *
     * @throws IOException  if an I/O exception occurs while reading the data,
     *         or if a UTF-8 format error is detected
     */
    private static int nextCharBits(DataInput in)
            throws IOException
        {
        int n = in.readUnsignedByte();
        if ((n & 0b11000000) != 0b10000000)
            {
            throw new UTFDataFormatException("trailing unicode byte does not match 10xxxxxx");
            }
        return n & 0b00111111;
        }

    /**
     * Write a single unicode character (code-point) to the passed DataOutput, encoding using the
     * UTF-8 format.
     *
     * @param out  the DataOutput to write to
     * @param ch   the char to write out as UTF-8
     *
     * @return a Unicode code-point (which can exceed the 16-bit <tt>char</tt>
     *         type in Java)
     *
     * @throws IOException  if an I/O exception occurs while writing the data,
     *         or if a UTF-8 format error is detected
     */
    public static void writeUtf8Char(DataOutput out, int ch)
            throws IOException
        {
        if ((ch & ~0x7F) == 0)
            {
            // ASCII - single byte 0xxxxxxx format
            out.write(ch);
            return;
            }

        // otherwise the format is based on the number of significant bits:
        // bits  code-points             first byte  trailing  # trailing
        // ----  ----------------------- ----------  --------  ----------
        //  11   U+0080    - U+07FF      110xxxxx    10xxxxxx      1
        //  16   U+0800    - U+FFFF      1110xxxx    10xxxxxx      2
        //  21   U+10000   - U+1FFFFF    11110xxx    10xxxxxx      3
        //  26   U+200000  - U+3FFFFFF   111110xx    10xxxxxx      4
        //  31   U+4000000 - U+7FFFFFFF  1111110x    10xxxxxx      5
        int cTrail;
        switch (Integer.highestOneBit(ch))
            {
            case 0b00000000000000000000000010000000:
            case 0b00000000000000000000000100000000:
            case 0b00000000000000000000001000000000:
            case 0b00000000000000000000010000000000:
                out.write(0b11000000 | ch >>> 6);
                cTrail = 1;
                break;

            case 0b00000000000000000000100000000000:
            case 0b00000000000000000001000000000000:
            case 0b00000000000000000010000000000000:
            case 0b00000000000000000100000000000000:
            case 0b00000000000000001000000000000000:
                out.write(0b11100000 | ch >>> 12);
                cTrail = 2;
                break;
            case 0b00000000000000010000000000000000:
            case 0b00000000000000100000000000000000:
            case 0b00000000000001000000000000000000:
            case 0b00000000000010000000000000000000:
            case 0b00000000000100000000000000000000:
                out.write(0b11110000 | ch >>> 18);
                cTrail = 3;
                break;
            case 0b00000000001000000000000000000000:
            case 0b00000000010000000000000000000000:
            case 0b00000000100000000000000000000000:
            case 0b00000001000000000000000000000000:
            case 0b00000010000000000000000000000000:
                out.write(0b11111000 | ch >>> 24);
                cTrail = 4;
                break;
            case 0b00000100000000000000000000000000:
            case 0b00001000000000000000000000000000:
            case 0b00010000000000000000000000000000:
            case 0b00100000000000000000000000000000:
            case 0b01000000000000000000000000000000:
                out.write(0b11111100 | ch >>> 30);
                cTrail = 5;
                break;

            default:
                throw new UTFDataFormatException("illegal character: " + intToHexString(ch));
            }

        // write out trailing bytes; each has the same "10xxxxxx" format with 6
        // bits of data
        while (cTrail > 0)
            {
            out.write(0b10_000000 | (ch >>> --cTrail * 6 & 0b00_111111));
            }
        }

    /**
     * Read a length-encoded string of unicode characters (code-point) from the passed DataInput,
     * encoded using the UTF-8 format. The format is the number of bytes in the stream used for the
     * UTF-8 character encoding, encoded as a packed integer, followed by the difference in the
     * number of bytes and the number of characters, the difference being 0 or greater, encoded as
     * a packed integer, followed by the specified number of characters, encoded using UTF-8.
     *
     * @param in  the DataInput to read from
     *
     * @return a Unicode code-point (which can exceed the 16-bit <tt>char</tt>
     *         type in Java)
     *
     * @throws IOException  if an I/O exception occurs while reading the data,
     *         or if a UTF-8 format error is detected
     */
    public static String readUtf8String(DataInput in)
            throws IOException
        {
        int           cb  = readMagnitude(in);
        int           cch = cb - readMagnitude(in);
        StringBuilder sb  = new StringBuilder(cch);
        for (int ofch = 0; ofch < cch; ++ofch)
            {
            int ch = readUtf8Char(in);
            if (ch >= 0xFFFF)
                {
                if (!Character.isSupplementaryCodePoint(ch))
                    {
                    throw new UTFDataFormatException(
                            "Character is outside of UTF-16 (including supplemental) range: " +
                            intToHexString(ch));
                    }

                // the "supplemental" range is composed of two UTF-16 characters
                sb.append(Character.highSurrogate(ch))  // "leading"
                  .append(Character.lowSurrogate(ch));  // "trailing"
                }
            else
                {
                sb.append((char) ch);
                }
            }

        return sb.toString();
        }

    /**
     * Write a length-encoded string of unicode characters (code-points) to the passed DataOutput,
     * encoding using the UTF-8 format. The length encoding is the number of bytes in the stream
     * used for the UTF-8 character encoding, encoded as a packed integer, followed by the
     * difference in the number of bytes and the number of characters, the difference being 0 or
     * greater, encoded as a packed integer.
     *
     * @param out  the DataOutput to write to
     * @param s    the String to write out as UTF-8
     *
     * @throws IOException  if an I/O exception occurs while writing the data,
     *         or if a UTF-8 format error is detected
     */
    public static void writeUtf8String(DataOutput out, String s)
            throws IOException
        {
        // figure out the actual number of unicode code-points (not the number
        // of "Java chars") and how many bytes the UTF-8 format will use
        final int   cch  = s.length();      // count of UTF-16 "Java chars"
        final int[] anch = new int[cch];    // array of unicode code points
        int cb   = 0;                       // count of bytes
        int cnch = 0;                       // count of unicode code points
        for (int ofch = 0; ofch < cch; ++ofch)
            {
            char ch = s.charAt(ofch);
            int  nch;
            if (Character.isSurrogate(ch))
                {
                // first character must be a high surrogate
                if (!Character.isHighSurrogate(ch))
                    {
                    throw new UTFDataFormatException(
                            "low surrogate unexpected: " + intToHexString(ch));
                    }

                // next character must be a low surrogate
                char ch2;
                if (++ofch >= cch || !Character.isLowSurrogate(ch2 = s.charAt(ofch)))
                    {
                    throw new UTFDataFormatException(
                            "low surrogate expected after: " + intToHexString(ch));
                    }

                nch = Character.toCodePoint(ch, ch2);
                }
            else
                {
                nch = ch;
                }

            anch[cnch++] = nch;
            cb += calcUtf8Length(nch);
            }

        // write out the UTF-8 form of the String
        assert cb >= cnch;
        writePackedLong(out, cb);
        writePackedLong(out, cb - cnch);
        for (int of = 0; of < cnch; ++of)
            {
            writeUtf8Char(out, anch[of]);
            }
        }

    /**
     * Internal: Calculate the UTF-8 length for a particular code-point.
     *
     * @param ch  the unicode character
     *
     * @return the number of bytes required to format the unicode character to
     *         UTF-8
     *
     * @throws UTFDataFormatException  if an invalid character is passed
     */
    private static int calcUtf8Length(int ch)
            throws IOException
        {
        if ((ch & ~0x7F) == 0)
            {
            // ASCII - single byte 0xxxxxxx format
            return 1;
            }

        switch (Integer.highestOneBit(ch))
            {
            case 0b00000000000000000000000010000000:
            case 0b00000000000000000000000100000000:
            case 0b00000000000000000000001000000000:
            case 0b00000000000000000000010000000000:
                return 2;

            case 0b00000000000000000000100000000000:
            case 0b00000000000000000001000000000000:
            case 0b00000000000000000010000000000000:
            case 0b00000000000000000100000000000000:
            case 0b00000000000000001000000000000000:
                return 3;

            case 0b00000000000000010000000000000000:
            case 0b00000000000000100000000000000000:
            case 0b00000000000001000000000000000000:
            case 0b00000000000010000000000000000000:
            case 0b00000000000100000000000000000000:
                return 4;

            case 0b00000000001000000000000000000000:
            case 0b00000000010000000000000000000000:
            case 0b00000000100000000000000000000000:
            case 0b00000001000000000000000000000000:
            case 0b00000010000000000000000000000000:
                return 5;

            case 0b00000100000000000000000000000000:
            case 0b00001000000000000000000000000000:
            case 0b00010000000000000000000000000000:
            case 0b00100000000000000000000000000000:
            case 0b01000000000000000000000000000000:
                return 6;

            default:
                throw new UTFDataFormatException("illegal character: " + intToHexString(ch));
            }
        }

    // ----- file I/O ------------------------------------------------------------------------------

    /**
     * Open the specified file as an InputStream.
     *
     * @param file  the file to open
     *
     * @return an InputStream to the specified file
     *
     * @throws IOException  if an IOException occurs while reading the
     *         FileStructure
     */
    public static InputStream toInputStream(File file)
            throws IOException
        {
        if (!file.exists())
            {
            throw new IOException("file does not exist: " + file);
            }

        if (!file.isFile() || !file.canRead())
            {
            throw new IOException("not a readable file: " + file);
            }

        return new BufferedInputStream(new FileInputStream(file));
        }

    /**
     * Read the raw bytes contained in the specified file.
     *
     * @param file  the file to read
     *
     * @return the binary contents of the file
     *
     * @throws IOException  indicates a failure to read the binary contents
     *         of the specified file
     */
    public static byte[] readFileBytes(File file)
            throws IOException
        {
        if (file == null)
            {
            throw new IllegalArgumentException("file required");
            }

        if (!file.exists() || !file.isFile())
            {
            throw new FileNotFoundException(file.toString());
            }

        final long lcb = file.length();
        if (lcb == 0L)
            {
            return EMPTY_BYTE_ARRAY;
            }
        else if (lcb > Integer.MAX_VALUE - 1024)
            {
            // assume a maximum byte array size just under 2GB
            throw new IOException("file exceeds max supported length (2GB): "
                    + file + "=" + lcb + " bytes");
            }

        // read the complete contents of the file
        int    cb = (int) lcb;
        byte[] ab = new byte[cb];
        try (FileInputStream stream = new FileInputStream(file))
            {
            for (int of = 0; of < cb; )
                {
                int cbChunk = stream.read(ab, of, cb-of);
                if (cbChunk < 0)
                    {
                    throw new EOFException("unexpected end-of-file: " + file);
                    }
                of += cbChunk;
                }
            }

        if (file.length() != lcb)
            {
            throw new IOException("file was concurrently modified while being read: " + file);
            }

        return ab;
        }

    /**
     * Read the text contents of the specified file. Supported formats are ASCII, UTF-8, UTF-16, and
     * UTF-32; failure to identify any of those formats will cause the default encoding to be used.
     *
     * @param file  the file to read
     *
     * @return the character contents of the file
     *
     * @throws IOException  indicates a failure to read the character contents
     *         of the specified file
     */
    public static char[] readFileChars(File file)
            throws IOException
        {
        return readFileChars(file, null);
        }

    /**
     * Read the text contents of the specified file using the specified encoding. If no encoding is
     * specified, then determine the encoding in the manner specified by {@link #readFileChars(File)}.
     *
     * @param file       the file to read
     * @param sEncoding  the specific encoding to use, or null to attempt to
     *                   determine the encoding automatically
     *
     * @return the character contents of the file
     *
     * @throws IOException  indicates a failure to read the character contents
     *         of the specified file
     */
    public static char[] readFileChars(File file, String sEncoding)
            throws IOException
        {
        byte[] ab = readFileBytes(file);
        int    cb = ab.length;
        if (cb == 0)
            {
            return EMPTY_CHAR_ARRAY;
            }

        // check to see if there is a byte order mark
        int cbBOM = 0;
        if (sEncoding == null)
            {
            switch (cb)
                {
                default:
                    if (ab[0] == 0x00 && ab[1] == 0x00 && (ab[2] & 0xFF) == 0xFE && (ab[3] & 0xFF) == 0xFF)
                        {
                        sEncoding = "UTF-32BE";
                        cbBOM = 4;
                        break;
                        }
                    else if ((ab[0] & 0xFF) == 0xFF && (ab[1] & 0xFF) == 0xFE && ab[2] == 0x00 && ab[3] == 0x00)
                        {
                        sEncoding = "UTF-32LE";
                        cbBOM = 4;
                        break;
                        }
                case 3:
                    if ((ab[0] & 0xFF) == 0xEF && (ab[1] & 0xFF) == 0xBB && (ab[2] & 0xFF) == 0xBF)
                        {
                        sEncoding = "UTF-8";
                        cbBOM = 3;
                        break;
                        }
                case 2:
                    if ((ab[0] & 0xFF) == 0xFE && (ab[1] & 0xFF) == 0xFF)
                        {
                        sEncoding = "UTF-16BE";
                        cbBOM = 2;
                        break;
                        }
                    else if ((ab[0] & 0xFF) == 0xFF && (ab[1] & 0xFF) == 0xFE)
                        {
                        sEncoding = "UTF-16LE";
                        cbBOM = 2;
                        break;
                        }
                case 1:
                case 0:
                }
            }

        scan: if (sEncoding == null)
            {
            // quick scan to verify that the contents are ASCII
            char[] ach = new char[cb];
            for (int of = 0; of < cb; ++of)
                {
                int b = ab[of] & 0xFF;
                if (b > 0x7F)
                    {
                    // use default encoding
                    break scan;
                    }
                ach[of] = (char) (b & 0xFF);
                }
            return ach;
            }

        // use the encoding that was specified or otherwise determined, or fall
        // back to the default encoding
        Charset    charset = sEncoding == null ? Charset.defaultCharset() : Charset.forName(sEncoding);
        ByteBuffer bytebuf = ByteBuffer.wrap(ab, cbBOM, cb - cbBOM);
        CharBuffer charbuf = charset.decode(bytebuf);
        char[] ach = new char[charbuf.length()];
        charbuf.get(ach);
        return ach;
        }


    // ----- array and collection helpers ----------------------------------------------------------

    /**
     * Given an array of "base" elements and an array of elements that are being evaluated as some
     * form of "addition" to those base elements, determine if there are any duplicates, and return
     * an array that contains the non-duplicates.
     *
     * @param aoBase  the array of "base" elements
     * @param aoAdd   the array of the elements being evaluated in addition to the base elements
     *
     * @return an array containing all of the elements from the {@code aoAdd} array that are not
     *         duplicates of elements in the {@code aoBase} array
     */
    public static <T> T[] dedupAdds(T[] aoBase, T[] aoAdd)
        {
        // there's a fair likelihood that *ALL* of the "adds" will be duplicates, and a fair
        // likelihood that *NONE* of the "adds" will be unique, so assume both up front, and only
        // de-optimize when *BOTH* of those two things have been proven to be false
        int          cBase     = aoBase.length;
        int          cAdd      = aoAdd.length;
        boolean      fAllDups  = true;
        boolean      fNoDups   = true;
        ArrayList<T> listDeDup = null;
        NextLayer: for (int iAdd = 0; iAdd < cAdd; ++iAdd)
            {
            T oAdd = aoAdd[iAdd];
            for (int iBase = 0; iBase < cBase; ++iBase)
                {
                if (oAdd.equals(aoBase[iBase]))
                    {
                    // we found a duplicate; is it the first one?
                    if (fNoDups)
                        {
                        fNoDups = false;

                        // if we already know that there are some that are NOT duplicates, then we
                        // need to start maintaining a list of non-duplicates to add; since this is
                        // the first duplicate encountered, just take everything up to this point
                        if (!fAllDups)
                            {
                            assert listDeDup == null;
                            listDeDup = startList(aoAdd, iAdd);
                            }
                        }

                    // this one was a duplicate, so advance to the next thing to add
                    continue NextLayer;
                    }
                }

            // this one to add is NOT a duplicate; is it the first one?
            if (fAllDups)
                {
                fAllDups = false;

                // if we already know that there are duplicates (i.e. NOT no duplicates), then we
                // need to start maintaining a list of non-duplicates (starting with this one)
                if (!fNoDups)
                    {
                    assert listDeDup == null;
                    listDeDup = new ArrayList<>();
                    }
                }

            // if, for whatever reason, we are maintaining a list of non-duplicates by this point,
            // and since we just verified that this is a non-duplicate, then add it to the list
            if (listDeDup != null)
                {
                listDeDup.add(oAdd);
                }
            }

        // at this point, we finished our check for duplicates; if there are no duplicates, then
        // the original array of things to add is the result
        if (fNoDups)
            {
            assert listDeDup == null;
            return aoAdd;
            }

        // otherwise, there are duplicates, which boils down into two possibilities: they're all
        // duplicates (so we return an empty array), or just some are duplicates (in which case
        // there is a list of the ones that are NOT duplicates that we should return)
        int cResult  = listDeDup == null ? 0 : listDeDup.size();
        T[] aoResult = (T[]) Array.newInstance(aoAdd.getClass().getComponentType(), cResult);
        if (cResult > 0)
            {
            aoResult = listDeDup.toArray(aoResult);
            }
        return aoResult;
        }

    /**
     * Glue two arrays together.
     *
     * @param aoBase  the first array
     * @param aoAdd   the second array
     *
     * @return an array containing all of the elements of both passed arrays
     */
    public static <T> T[] append(T[] aoBase, T[] aoAdd)
        {
        int cBase = aoBase.length;
        if (cBase == 0)
            {
            return aoAdd;
            }

        int cAdd = aoAdd.length;
        if (cAdd == 0)
            {
            return aoBase;
            }

        int cResult  = cBase + cAdd;
        T[] aoResult = (T[]) Array.newInstance(aoAdd.getClass().getComponentType(), cResult);
        System.arraycopy(aoBase, 0, aoResult, 0, cBase);
        System.arraycopy(aoAdd, 0, aoResult, cBase, cAdd);
        return aoResult;
        }

    /**
     * Add the specified element at the top of the specified array.
     *
     * @param aoBase  the array to add to
     * @param oAdd    the element to append at the head
     *
     * @return an array containing all the elements
     */
    public static <T> T[] appendHead(T[] aoBase, T oAdd)
        {
        int c    = aoBase.length;
        T[] aNew = (T[]) Array.newInstance(oAdd.getClass(), c + 1);

        aNew[0] = oAdd;
        System.arraycopy(aoBase, 0, aNew, 1, c);
        return aNew;
        }

    /**
     * Create an ArrayList that contains the first {@code c} elements of the array {@code ao}.
     *
     * @param ao  an array of T
     * @param c   the number of elements of the array to put into the new list
     *
     * @return a new ArrayList of the first {@code c} elements of the {@code ao} array
     */
    public static <T> ArrayList<T> startList(T[] ao, int c)
        {
        return appendList(new ArrayList<>(), ao, 0, c);
        }

    /**
     * Append the specified elements from the passed array to the list.
     *
     * @param list  a list of T to add to
     * @param ao    an array of T to obtain the values-to-add from
     * @param of    the offset into the array
     * @param c     the number of elements from the array to add to the list
     *
     * @return the list
     */
    public static <T> ArrayList<T> appendList(ArrayList<T> list, T[] ao, int of, int c)
        {
        for (int ofEnd = of+c; of < ofEnd; ++of)
            {
            list.add(ao[of]);
            }
        return list;
        }


    // ----- hashing & equality --------------------------------------------------------------------

    /**
     * Perform a hash of the object. This performs a deep hash on arrays.
     *
     * @param o  any object, or null
     *
     * @return a hashcode
     */
    public static int hashCode(final Object o)
        {
        return Hash.of(o);
        }

    /**
     * Perform a comparison for equality. The types of the objects must be equal
     * as well. This performs a deep comparison on arrays.
     *
     * @param o1  any object, or null
     * @param o2  any object, or null
     *
     * @return true iff <tt>o1</tt> is equals to <tt>o2</tt>
     */
    public static boolean equals(final Object o1, final Object o2)
        {
        if (o1 == null)
            {
            return o2 == null;
            }
        if (o2 == null)
            {
            return false;
            }

        final Class clz1 = o1.getClass();
        final Class clz2 = o1.getClass();
        if (clz1 != clz2)
            {
            return false;
            }

        Class<?> clzComp = clz1.getComponentType();
        return clzComp == null || clzComp != clz2.getComponentType()
            ? o1.equals(o2)
            : clzComp.isPrimitive()
                ? clzComp == int   .class ? Arrays.equals(    (int[]) o1,     (int[]) o2)
                : clzComp == long  .class ? Arrays.equals(   (long[]) o1,    (long[]) o2)
                : clzComp == byte  .class ? Arrays.equals(   (byte[]) o1,    (byte[]) o2)
                : clzComp == char  .class ? Arrays.equals(   (char[]) o1,    (char[]) o2)
                : clzComp == double.class ? Arrays.equals( (double[]) o1,  (double[]) o2)
                : clzComp == float .class ? Arrays.equals(  (float[]) o1,   (float[]) o2)
                : clzComp == short .class ? Arrays.equals(  (short[]) o1,   (short[]) o2)
                                          : Arrays.equals((boolean[]) o1, (boolean[]) o2)
            : Arrays.equals((Object[]) o1, (Object[]) o2);
        }

    /**
     * Perform a comparison of two arrays for ordering. This performs a deep
     * comparison on the arrays.
     *
     * @param ao1  an array of Comparable, or null
     * @param ao2  an array of Comparable, or null
     *
     * @return negative, zero, or positive iff <tt>ao1</tt> is less than, equal
     *         to, or greater than <tt>ao2</tt>
     */
    public static int compareArrays(final Comparable[] ao1, final Comparable[] ao2)
        {
        if (ao1 == ao2)
            {
            return 0;
            }

        if (ao1 == null)
            {
            return -1;
            }

        if (ao2 == null)
            {
            return 1;
            }

        final int c1 = ao1.length;
        final int c2 = ao2.length;
        for (int i = 0, c = Math.min(c1, c2); i < c; ++i)
            {
            final int n = ao1[i].compareTo(ao2[i]);
            if (n != 0)
                {
                return n;
                }
            }

        return c1 - c2;
        }

    /**
     * Verify that the array is non-null and all elements are non-null.
     *
     * @param ao  an array of objects
     *
     * @return true iff all elements are non-null
     *
     * @throws IllegalArgumentException  if the array is null, or any elements
     *         of the array are null
     */
    public static boolean checkElementsNonNull(Object[] ao)
        {
        if (ao == null)
            {
            throw new IllegalArgumentException("array is null");
            }

        for (Object o : ao)
            {
            if (o == null)
                {
                throw new IllegalArgumentException("array element is null");
                }
            }

        return true;
        }


    // ----- constants -----------------------------------------------------------------------------

    /**
     * A constant empty array of <tt>byte</tt>.
     */
    public final static byte[] EMPTY_BYTE_ARRAY = new byte[0];

    /**
     * A constant empty array of <tt>char</tt>.
     */
    public final static char[] EMPTY_CHAR_ARRAY = new char[0];

    /**
     * A constant empty array of <tt>String</tt>.
     */
    public final static String[] NO_ARGS = new String[0];
    }