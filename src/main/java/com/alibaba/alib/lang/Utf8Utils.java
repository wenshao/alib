package com.alibaba.alib.lang;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;

public class Utf8Utils {
    final static Unsafe UNSAFE = getUnsafe();
    final static int CHAR_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(char[].class);
    final static int BYTE_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);

    final static long STRING_VALUE_OFFSET;
    final static long STRING_VALUE_CODE;
    final static boolean BYTES;
    final static Charset UTF8 = Charset.forName("UTF8");

    static {
        long valueOffset = -1L, codeOffSet = -1L;
        boolean type = false;
        try {
            Field valueField = String.class.getDeclaredField("value");
            valueOffset = UNSAFE.objectFieldOffset(valueField);
            if (valueField.getType() == byte[].class) {
                type = true;
                Field codeField = String.class.getDeclaredField("coder");
                codeOffSet = UNSAFE.objectFieldOffset(codeField);
            }
        } catch (Throwable e) {
            e.printStackTrace();
            // skip
        }
        STRING_VALUE_OFFSET = valueOffset;
        STRING_VALUE_CODE = codeOffSet;
        BYTES = type;
    }

    public static int encodeUTF8(char[] chars, final int off, int len, byte[] bytes, final int dp) {
        return encodeUTF8Internal(chars, off, len, bytes, dp);
    }

    public static int encodeUTF8(String str, byte[] dest, final int dp) {
        if (STRING_VALUE_OFFSET == -1) {
            byte[] bytes = str.getBytes(UTF8);
            System.arraycopy(bytes, 0, dest, dp, bytes.length);
            return dp + bytes.length;
        }

        Object value = UNSAFE.getObject(str, STRING_VALUE_OFFSET);
        if (BYTES) { // support JDK 9/10/11
            byte[] bytes = (byte[]) value;
            byte code = UNSAFE.getByte(str, STRING_VALUE_CODE);
            if (code == 0) { // latin1
                System.arraycopy(bytes, 0, dest, dp, bytes.length);
                return dp + bytes.length;
            }
            // utf16
            return encodeUTF8Internal(bytes, 0, bytes.length/2, dest, dp);
        } else {
            char[] chars = (char[]) value;
            return encodeUTF8Internal(chars, 0, chars.length, dest, dp);
        }
    }

    static int encodeUTF8Internal(Object chars, final int off, int len, byte[] dest, final int dp) {
        long unsafe_off = CHAR_ARRAY_BASE_OFFSET + off * 2;
        final long unsafe_sl = unsafe_off + len * 2;
        long unsafe_last_off = unsafe_sl - 2;
        long usafe_dp = BYTE_ARRAY_BASE_OFFSET + dp;

        while (unsafe_off < unsafe_sl) {
            char c = UNSAFE.getChar(chars, unsafe_off);
            unsafe_off += 2;

            if (c < 0x80) {
                // Have at most seven bits
                UNSAFE.putByte(dest, usafe_dp++, (byte) c);
            } else if (c < 0x800) {
                // 2 dest, 11 bits
                UNSAFE.putByte(dest, usafe_dp++, (byte) (0xc0 | (c >> 6)));
                UNSAFE.putByte(dest, usafe_dp++, (byte) (0x80 | (c & 0x3f)));
            } else if (c >= '\uD800' && c < '\uE000') { //Character.isSurrogate(c) but 1.7
                int uc;
                if (c < '\uDC00') { // Character.isHighSurrogate(c)
                    if (unsafe_off > unsafe_last_off) {
                        UNSAFE.putByte(dest, usafe_dp++,(byte) '?');
                        return (int) usafe_dp - BYTE_ARRAY_BASE_OFFSET;
                    }

                    char d = UNSAFE.getChar(chars, unsafe_off);
                    if (d >= '\uDC00' && d < '\uE000') { // Character.isLowSurrogate(d)
                        uc = (c << 10) + d + 0xfca02400; // Character.toCodePoint(c, d)
                    } else {
                        throw new RuntimeException("encodeUTF8 error", new MalformedInputException(1));
                    }
                } else {
                    uc = c;
                }

                UNSAFE.putByte(dest, usafe_dp++, (byte) (0xf0 | ((uc >> 18))));
                UNSAFE.putByte(dest, usafe_dp++, (byte) (0x80 | ((uc >> 12) & 0x3f)));
                UNSAFE.putByte(dest, usafe_dp++, (byte) (0x80 | ((uc >> 6) & 0x3f)));
                UNSAFE.putByte(dest, usafe_dp++, (byte) (0x80 | (uc & 0x3f)));
                unsafe_off += 2; // 2 chars
            } else {
                // 3 dest, 16 bits
                UNSAFE.putByte(dest, usafe_dp++, (byte) (0xe0 | ((c >> 12))));
                UNSAFE.putByte(dest, usafe_dp++, (byte) (0x80 | ((c >> 6) & 0x3f)));
                UNSAFE.putByte(dest, usafe_dp++, (byte) (0x80 | (c & 0x3f)));
            }
        }
        return (int) usafe_dp - BYTE_ARRAY_BASE_OFFSET;
    }

    public static int decodeUTF8(byte[] sa, int sp, int len, char[] da, int dp) {
        final int sl = sp + len;
        int dlASCII = Math.min(len, da.length);

        // ASCII only optimized loop
        while (dp < dlASCII && sa[sp] >= 0)
            da[dp++] = (char) sa[sp++];

        while (sp < sl) {
            int b1 = sa[sp++];
            if (b1 >= 0) {
                // 1 byte, 7 bits: 0xxxxxxx
                da[dp++] = (char) b1;
            } else if ((b1 >> 5) == -2 && (b1 & 0x1e) != 0) {
                // 2 bytes, 11 bits: 110xxxxx 10xxxxxx
                if (sp < sl) {
                    int b2 = sa[sp++];
                    if ((b2 & 0xc0) != 0x80) { // isNotContinuation(b2)
                        return -1;
                    } else {
                        da[dp++] = (char) (((b1 << 6) ^ b2)^
                                (((byte) 0xC0 << 6) ^
                                        ((byte) 0x80 << 0)));
                    }
                    continue;
                }
                return -1;
            } else if ((b1 >> 4) == -2) {
                // 3 bytes, 16 bits: 1110xxxx 10xxxxxx 10xxxxxx
                if (sp + 1 < sl) {
                    int b2 = sa[sp++];
                    int b3 = sa[sp++];
                    if ((b1 == (byte) 0xe0 && (b2 & 0xe0) == 0x80) //
                            || (b2 & 0xc0) != 0x80 //
                            || (b3 & 0xc0) != 0x80) { // isMalformed3(b1, b2, b3)
                        return -1;
                    } else {
                        char c = (char)((b1 << 12) ^
                                (b2 <<  6) ^
                                (b3 ^
                                        (((byte) 0xE0 << 12) ^
                                                ((byte) 0x80 <<  6) ^
                                                ((byte) 0x80 <<  0))));
                        boolean isSurrogate = c >= '\uD800' && c < ('\uDFFF' + 1);
                        if (isSurrogate) {
                            return -1;
                        } else {
                            da[dp++] = c;
                        }
                    }
                    continue;
                }
                return -1;
            } else if ((b1 >> 3) == -2) {
                // 4 bytes, 21 bits: 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
                if (sp + 2 < sl) {
                    int b2 = sa[sp++];
                    int b3 = sa[sp++];
                    int b4 = sa[sp++];
                    int uc = ((b1 << 18) ^
                            (b2 << 12) ^
                            (b3 <<  6) ^
                            (b4 ^
                                    (((byte) 0xF0 << 18) ^
                                            ((byte) 0x80 << 12) ^
                                            ((byte) 0x80 <<  6) ^
                                            ((byte) 0x80 <<  0))));
                    if (((b2 & 0xc0) != 0x80 || (b3 & 0xc0) != 0x80 || (b4 & 0xc0) != 0x80) // isMalformed4
                            ||
                            // shortest form check
                            !(uc >= 0x010000 && uc <  0X10FFFF + 1) // !Character.isSupplementaryCodePoint(uc)
                    ) {
                        return -1;
                    } else {
                        da[dp++] =  (char) ((uc >>> 10) + ('\uD800' - (0x010000 >>> 10))); // Character.highSurrogate(uc);
                        da[dp++] = (char) ((uc & 0x3ff) + '\uDC00'); // Character.lowSurrogate(uc);
                    }
                    continue;
                }
                return -1;
            } else {
                return -1;
            }
        }
        return dp;
    }

    public static int decodeUTF8_unsafe(byte[] sa, int sp, int len, char[] da, int dp) {
        final int daLen = da.length;
        long dlASCII = (len <= daLen) ? (len * 2) + CHAR_ARRAY_BASE_OFFSET : (daLen * 2) + CHAR_ARRAY_BASE_OFFSET;
        long udp = (dp * 2) + CHAR_ARRAY_BASE_OFFSET;
        long usp = BYTE_ARRAY_BASE_OFFSET + sp;
        long usl = usp + len;

        // ASCII only optimized loop
        for (;udp < dlASCII; usp++, udp+=2) {
            byte b = UNSAFE.getByte(sa, usp);
            if (b < 0) {
                break;
            }
            UNSAFE.putChar(da, udp, (char) b);
        }

        while (usp < usl) {
            byte b1 = UNSAFE.getByte(sa, usp++);
            if (b1 >= 0) {
                // 1 byte, 7 bits: 0xxxxxxx
                UNSAFE.putChar(da, udp, (char) b1);
                udp+=2;
            } else if ((b1 >> 5) == -2 && (b1 & 0x1e) != 0) {
                // 2 bytes, 11 bits: 110xxxxx 10xxxxxx
                if (usp < usl) {
                    byte b2 = UNSAFE.getByte(sa, usp++);
                    if ((b2 & 0xc0) != 0x80) { // isNotContinuation(b2)
                        return -1;
                    } else {
                        char c2 = (char) (((b1 << 6) ^ b2)^
                                (((byte) 0xC0 << 6) ^
                                        ((byte) 0x80 << 0)));
                        UNSAFE.putChar(da, udp, c2);
                        udp+=2;
                    }
                    continue;
                }
                return -1;
            } else if ((b1 >> 4) == -2) {
                // 3 bytes, 16 bits: 1110xxxx 10xxxxxx 10xxxxxx
                if (usp + 1 < usl) {
                    int b2 = UNSAFE.getByte(sa, usp++);
                    int b3 = UNSAFE.getByte(sa, usp++);
                    if ((b1 == (byte) 0xe0 && (b2 & 0xe0) == 0x80) //
                            || (b2 & 0xc0) != 0x80 //
                            || (b3 & 0xc0) != 0x80) { // isMalformed3(b1, b2, b3)
                        return -1;
                    } else {
                        char c = (char)((b1 << 12) ^
                                (b2 <<  6) ^
                                (b3 ^
                                        (((byte) 0xE0 << 12) ^
                                                ((byte) 0x80 <<  6) ^
                                                ((byte) 0x80 <<  0))));
                        boolean isSurrogate = c >= '\uD800' && c < ('\uDFFF' + 1);
                        if (isSurrogate) {
                            return -1;
                        } else {
                            UNSAFE.putChar(da, udp, (char) c);
                            udp += 2;
                        }
                    }
                    continue;
                }
                return -1;
            } else if ((b1 >> 3) == -2) {
                // 4 bytes, 21 bits: 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
                if (usp + 2 < usl) {
                    int b2 = UNSAFE.getByte(sa, usp++);
                    int b3 = UNSAFE.getByte(sa, usp++);
                    int b4 = UNSAFE.getByte(sa, usp++);
                    int uc = ((b1 << 18) ^
                            (b2 << 12) ^
                            (b3 <<  6) ^
                            (b4 ^
                                (((byte) 0xF0 << 18) ^
                                        ((byte) 0x80 << 12) ^
                                        ((byte) 0x80 <<  6) ^
                                        ((byte) 0x80 <<  0))));
                    if (((b2 & 0xc0) != 0x80 || (b3 & 0xc0) != 0x80 || (b4 & 0xc0) != 0x80) // isMalformed4
                            ||
                            // shortest form check
                            !(uc >= 0x010000 && uc <  0X10FFFF + 1) // !Character.isSupplementaryCodePoint(uc)
                    ) {
                        return -1;
                    } else {
                        char c1 = (char) ((uc >>> 10) + ('\uD800' - (0x010000 >>> 10))); // Character.highSurrogate(uc);
                        char c2 = (char) ((uc & 0x3ff) + '\uDC00'); // Character.lowSurrogate(uc);;
                        UNSAFE.putChar(da, udp, c1);
                        udp += 2;
                        UNSAFE.putChar(da, udp, c2);
                        udp +=2;
                    }
                    continue;
                }
                return -1;
            } else {
                return -1;
            }
        }
        return (int) (udp - (dp * 2) + CHAR_ARRAY_BASE_OFFSET) / 2;
    }

    public static String decodeUTF8_unsafe(byte[] sa, int sp, int len) {
        char[] da = new char[len];

        final int daLen = da.length;
        long dlASCII = (len <= daLen) ? (len * 2) + CHAR_ARRAY_BASE_OFFSET : (daLen * 2) + CHAR_ARRAY_BASE_OFFSET;
        long udp = CHAR_ARRAY_BASE_OFFSET;
        long usp = BYTE_ARRAY_BASE_OFFSET + sp;
        long usl = usp + len;

        // ASCII only optimized loop
        for (;udp < dlASCII; usp++, udp+=2) {
            byte b = UNSAFE.getByte(sa, usp);
            if (b < 0) {
                break;
            }
            UNSAFE.putChar(da, udp, (char) b);
        }

        while (usp < usl) {
            byte b1 = UNSAFE.getByte(sa, usp++);
            if (b1 >= 0) {
                // 1 byte, 7 bits: 0xxxxxxx
                UNSAFE.putChar(da, udp, (char) b1);
                udp+=2;
            } else if ((b1 >> 5) == -2 && (b1 & 0x1e) != 0) {
                // 2 bytes, 11 bits: 110xxxxx 10xxxxxx
                if (usp < usl) {
                    byte b2 = UNSAFE.getByte(sa, usp++);
                    if ((b2 & 0xc0) != 0x80) { // isNotContinuation(b2)
                        throw new RuntimeException("decodeUTF8 error", new MalformedInputException(1));
                    } else {
                        char c2 = (char) (((b1 << 6) ^ b2)^
                                (((byte) 0xC0 << 6) ^
                                        ((byte) 0x80 << 0)));
                        UNSAFE.putChar(da, udp, c2);
                        udp+=2;
                    }
                    continue;
                }
                throw new RuntimeException("decodeUTF8 error", new MalformedInputException(1));
            } else if ((b1 >> 4) == -2) {
                // 3 bytes, 16 bits: 1110xxxx 10xxxxxx 10xxxxxx
                if (usp + 1 < usl) {
                    int b2 = UNSAFE.getByte(sa, usp++);
                    int b3 = UNSAFE.getByte(sa, usp++);
                    if ((b1 == (byte) 0xe0 && (b2 & 0xe0) == 0x80) //
                            || (b2 & 0xc0) != 0x80 //
                            || (b3 & 0xc0) != 0x80) { // isMalformed3(b1, b2, b3)
                        throw new RuntimeException("decodeUTF8 error", new MalformedInputException(1));
                    } else {
                        char c = (char)((b1 << 12) ^
                                (b2 <<  6) ^
                                (b3 ^
                                        (((byte) 0xE0 << 12) ^
                                                ((byte) 0x80 <<  6) ^
                                                ((byte) 0x80 <<  0))));
                        boolean isSurrogate = c >= '\uD800' && c < ('\uDFFF' + 1);
                        if (isSurrogate) {
                            throw new RuntimeException("decodeUTF8 error", new MalformedInputException(1));
                        } else {
                            UNSAFE.putChar(da, udp, (char) c);
                            udp += 2;
                        }
                    }
                    continue;
                }
                throw new RuntimeException("decodeUTF8 error", new MalformedInputException(1));
            } else if ((b1 >> 3) == -2) {
                // 4 bytes, 21 bits: 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
                if (usp + 2 < usl) {
                    int b2 = UNSAFE.getByte(sa, usp++);
                    int b3 = UNSAFE.getByte(sa, usp++);
                    int b4 = UNSAFE.getByte(sa, usp++);
                    int uc = ((b1 << 18) ^
                            (b2 << 12) ^
                            (b3 <<  6) ^
                            (b4 ^
                                    (((byte) 0xF0 << 18) ^
                                            ((byte) 0x80 << 12) ^
                                            ((byte) 0x80 <<  6) ^
                                            ((byte) 0x80 <<  0))));
                    if (((b2 & 0xc0) != 0x80 || (b3 & 0xc0) != 0x80 || (b4 & 0xc0) != 0x80) // isMalformed4
                            ||
                            // shortest form check
                            !(uc >= 0x010000 && uc <  0X10FFFF + 1) // !Character.isSupplementaryCodePoint(uc)
                    ) {
                        throw new RuntimeException("decodeUTF8 error", new MalformedInputException(1));
                    } else {
                        char c1 = (char) ((uc >>> 10) + ('\uD800' - (0x010000 >>> 10))); // Character.highSurrogate(uc);
                        char c2 = (char) ((uc & 0x3ff) + '\uDC00'); // Character.lowSurrogate(uc);;
                        UNSAFE.putChar(da, udp, c1);
                        udp += 2;
                        UNSAFE.putChar(da, udp, c2);
                        udp +=2;
                    }
                    continue;
                }
                throw new RuntimeException("decodeUTF8 error", new MalformedInputException(1));
            } else {
                throw new RuntimeException("decodeUTF8 error", new MalformedInputException(1));
            }
        }

        return new String(da, 0, (int) ((udp - CHAR_ARRAY_BASE_OFFSET) / 2));
        //return (int) (udp - (dp * 2) + CHAR_ARRAY_BASE_OFFSET) / 2;
    }

    private static sun.misc.Unsafe getUnsafe() {
        sun.misc.Unsafe unsafe = null;
        try {
            unsafe =
                    AccessController.doPrivileged(
                            new PrivilegedExceptionAction<Unsafe>() {
                                public sun.misc.Unsafe run() throws Exception {
                                    Class<sun.misc.Unsafe> k = sun.misc.Unsafe.class;

                                    for (Field f : k.getDeclaredFields()) {
                                        f.setAccessible(true);
                                        Object x = f.get(null);
                                        if (k.isInstance(x)) {
                                            return k.cast(x);
                                        }
                                    }
                                    // The sun.misc.Unsafe field does not exist.
                                    return null;
                                }
                            });
        } catch (Throwable e) {
            // Catching Throwable here due to the fact that Google AppEngine raises NoClassDefFoundError
            // for Unsafe.
        }
        return unsafe;
    }
}
