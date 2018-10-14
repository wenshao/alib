package com.alibaba.alib.lang;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;

public class Utf8Utils {
    final static Unsafe UNSAFE = getUnsafe();
    final static long CHAR_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(char[].class);
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
            if (code == 0) {
                System.arraycopy(bytes, 0, dest, dp, bytes.length);
                return dp + bytes.length;
            }
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
