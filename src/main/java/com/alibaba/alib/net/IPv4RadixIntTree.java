package com.alibaba.alib.net;

/*
 * Copyright (C) 2012 alib
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import java.io.*;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * A minimalistic, memory size-savvy and fairly fast radix tree (AKA Patricia trie)
 * implementation that uses IPv4 addresses with netmasks as keys and 32-bit signed
 * integers as values.
 *
 * This tree is generally uses in read-only manner: there are no key removal operation
 * and the whole thing works best in pre-allocated fashion.
 */
public class IPv4RadixIntTree {
    /**
     * Special value that designates that there are no value stored in the key so far.
     * One can't use store value in a tree.
     */
    public static final int NO_VALUE = -1;

    private static final int NULL_PTR = -1;
    private static final int ROOT_PTR = 0;

    private static final long MAX_IPV4_BIT = 0x80000000L;

    private int[] rights;
    private int[] lefts;
    private int[] values;

    private int allocatedSize;
    private int size;

    /**
     * Initializes IPv4 radix tree with default capacity of 1024 nodes. It should
     * be sufficient for small databases.
     */
    public IPv4RadixIntTree() {
        init(1024);
    }

    /**
     * Initializes IPv4 radix tree with a given capacity.
     * @param allocatedSize initial capacity to allocate
     */
    public IPv4RadixIntTree(int allocatedSize) {
        init(allocatedSize);
    }

    private void init(int allocatedSize) {
        this.allocatedSize = allocatedSize;

        rights = new int[this.allocatedSize];
        lefts = new int[this.allocatedSize];
        values = new int[this.allocatedSize];

        size = 1;
        lefts[0] = NULL_PTR;
        rights[0] = NULL_PTR;
        values[0] = NO_VALUE;
    }

    /**
     * Puts a key-value pair in a tree.
     * @param key IPv4 network prefix
     * @param mask IPv4 netmask in networked byte order format (for example,
     * 0xffffff00L = 4294967040L corresponds to 255.255.255.0 AKA /24 network
     * bitmask)
     * @param value an arbitrary value that would be stored under a given key
     */
    public void put(long key, long mask, int value) {
        long bit = MAX_IPV4_BIT;
        int node = ROOT_PTR;
        int next = ROOT_PTR;

        while ((bit & mask) != 0) {
            next = ((key & bit) != 0) ? rights[node] : lefts[node];
            if (next == NULL_PTR)
                break;
            bit >>= 1;
            node = next;
        }

        if (next != NULL_PTR) {
//            if (node.value != NO_VALUE) {
//                throw new IllegalArgumentException();
//            }

            values[node] = value;
            return;
        }

        while ((bit & mask) != 0) {
            if (size == allocatedSize)
                expandAllocatedSize();

            next = size;
            values[next] = NO_VALUE;
            rights[next] = NULL_PTR;
            lefts[next] = NULL_PTR;

            if ((key & bit) != 0) {
                rights[node] = next;
            } else {
                lefts[node] = next;
            }

            bit >>= 1;
            node = next;
            size++;
        }

        values[node] = value;
    }

    private void expandAllocatedSize() {
        int oldSize = allocatedSize;
        allocatedSize = allocatedSize * 2;

        int[] newLefts = new int[allocatedSize];
        System.arraycopy(lefts, 0, newLefts, 0, oldSize);
        lefts = newLefts;

        int[] newRights = new int[allocatedSize];
        System.arraycopy(rights, 0, newRights, 0, oldSize);
        rights = newRights;

        int[] newValues = new int[allocatedSize];
        System.arraycopy(values, 0, newValues, 0, oldSize);
        values = newValues;
    }

    /**
     * Selects a value for a given IPv4 address, traversing tree and choosing
     * most specific value available for a given address.
     * @param key IPv4 address to look up
     * @return value at most specific IPv4 network in a tree for a given IPv4
     * address
     */
    public int selectValue(long key) {
        long bit = MAX_IPV4_BIT;
        int value = NO_VALUE;
        int node = ROOT_PTR;

        while (node != NULL_PTR) {
            if (values[node] != NO_VALUE)
                value = values[node];
            node = ((key & bit) != 0) ? rights[node] : lefts[node];
            bit >>= 1;
        }

        return value;
    }

    /**
     * Puts a key-value pair in a tree, using a string representation of IPv4 prefix.
     * @param ipNet IPv4 network as a string in form of "a.b.c.d/e", where a, b, c, d
     * are IPv4 octets (in decimal) and "e" is a netmask in CIDR notation
     * @param value an arbitrary value that would be stored under a given key
     * @throws UnknownHostException
     */
    public void put(String ipNet, int value) throws UnknownHostException {
        int pos = ipNet.indexOf('/');
        String ipStr = ipNet.substring(0, pos);
        long ip = inet_aton(ipStr);

        String netmaskStr = ipNet.substring(pos + 1);
        int cidr = Integer.parseInt(netmaskStr);
        long netmask =  ((1L << (32 - cidr)) - 1L) ^ 0xffffffffL;

        put(ip, netmask, value);
    }


    /**
     * Returns a size of tree in number of nodes (not number of prefixes stored).
     * @return a number of nodes in current tree
     */
    public int size() { return size; }

    /**
     * Selects a value for a given IPv4 address, traversing tree and choosing
     * most specific value available for a given address.
     * @param ip IPv4 address to look up, in string form (i.e. "a.b.c.d")
     * @return value at most specific IPv4 network in a tree for a given IPv4
     * address
     * @throws UnknownHostException
     */
    public int selectValue(String ip) throws UnknownHostException {
        return selectValue(
                inet_aton(ip)
        );
    }

    /**
     * Helper function that reads IPv4 radix tree from a local file in tab-separated format:
     * (IPv4 net => value)
     * @param filename name of a local file to read
     * @return a fully constructed IPv4 radix tree from that file
     * @throws IOException
     */
    public static IPv4RadixIntTree loadFromLocalFile(String filename) throws IOException {
        return loadFromLocalFile(filename, false);
    }

    /**
     * Helper function that reads IPv4 radix tree from a local file in tab-separated format:
     * (IPv4 net => value)
     * @param filename name of a local file to read
     * @param nginxFormat if true, then file would be parsed as nginx web server configuration file:
     * "value" would be treated as hex and last symbol at EOL would be stripped (as normally nginx
     * config files has lines ending with ";")
     * @return a fully constructed IPv4 radix tree from that file
     * @throws IOException
     */
    public static IPv4RadixIntTree loadFromLocalFile(String filename, boolean nginxFormat) throws IOException {
        byte[] bytes = new byte[1024 * 16];

        int lines = countLines(filename, bytes);
        IPv4RadixIntTree tr = new IPv4RadixIntTree(lines);
        FileInputStream in = new FileInputStream(filename);
        try {
            tr.init(in, nginxFormat, bytes);
        } finally {
            in.close();
        }

        return tr;
    }

    private int readLine(InputStream in, ByteBuffer buf, byte[] dest) throws IOException {
        byte[] bytes = buf.array();
        int limit = buf.limit();

        int pos = buf.position();
        int remaining = limit - pos;
        if (remaining < 32) {
            if (remaining > 0) {
                System.arraycopy(bytes, pos, bytes, 0, remaining);
                buf.position(pos = 0);
                buf.limit(limit = remaining);
            }
            int len = in.read(bytes, limit, bytes.length / 2);
            if (len == -1) {
                return -1;
            }

            if (len > 0) {
                limit += len;
                buf.limit(limit);
            }
        }


        for (int i = pos; i < limit; ++i) {
            byte b = bytes[i];
            if (b == '\n') {
                int len = i - pos;
                System.arraycopy(bytes, pos, dest, 0, len);
                buf.position(i + 1);
                return len;
            }
        }

        return -1;
    }

    private void init(InputStream in, boolean nginxFormat, byte[] bytes) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        buf.flip();

        byte[] line = new byte[32];
        for (;;) {
            int len = readLine(in, buf, line);

            if (len == -1) {
                break;
            }

            int b0 = 0, b1 = 0, b2 = 0, b3 = 0;
            int cidr = 0, port = 0;
            for (int i = 0
                 , val = 0
                 , p = 0
                 ; i < len; ++i)
            {
                char ch = (char) line[i];

                if (ch >= '0' && ch <= '9') {
                    if (nginxFormat && p == 5) {
                        val = val * 16 + (ch - '0');
                    } else {
                        val = val * 10 + (ch - '0');
                    }
                } else if (nginxFormat && ch >= 'a' && ch <= 'f') {
                    val = val * 16 + (ch - 87);
                } else if (ch == '.') {
                    if (ch > 255) {
                        throw new UnknownHostException("illegal ip : " + new String(line, 0, len, "iso-8859-1"));
                    }

                    if (p == 0) {
                        b0 = val;
                    } else if (p == 1) {
                        b1 = val;
                    } else if (p == 2) {
                        b2 = val;
                    } else {
                        throw new UnknownHostException("illegal ip : " + new String(line, 0, len, "iso-8859-1"));
                    }

                    val = 0;
                    p++;
                } else if (ch == '\t' || ch == '/') {
                    if (p == 3) {
                        b3 = val;
                        val = 0;
                    } else if (p == 4) {
                        cidr = val;
                        val = 0;
                    } else if (p == 5) {
                        port = val;
                    } else {
                        throw new UnknownHostException("illegal ip : " + new String(line, 0, len, "iso-8859-1"));
                    }
                    p++;
                } else if (ch == ';' && i == len - 1) {
                    // skip
                } else {
                    throw new UnknownHostException("illegal ip : " + new String(line, 0, len, "iso-8859-1"));
                }

                if (i == len - 1) {
                    port = val;
                }
            }

            int address = b3 | (b2 << 8) | (b1 << 16) | (b0 << 24);
            long ip = address & 0xFFFFFFFFL;
            long netmask =  ((1L << (32 - cidr)) - 1L) ^ 0xffffffffL;
            put(ip, netmask, port);
        }
    }

    private static long inet_aton(String line) throws UnknownHostException {
        int address = 0;
        for (int i = 0
             , len = line.length()
             , end = len - 1
             , b = 0
             , p = 0
             ; i < len; ++i)
        {
            char ch = line.charAt(i);
            if (ch >= '0' && ch <= '9') {
                b = b * 10 + (ch - '0');
            } else if (ch == '.') {
                if (ch > 255) {
                    throw new UnknownHostException("illegal ip : " + line);
                }

                if (p == 0) {
                    address |= (b << 24);
                } else if (p == 1) {
                    address |= (b << 16);
                } else if (p == 2) {
                    address |= (b << 8);
                } else {
                    throw new UnknownHostException("illegal ip : " + line);
                }

                b = 0;
                p++;
            }

            if (i == end) {
                if (p == 3) {
                    address |= b;
                } else {
                    throw new UnknownHostException("illegal ip : " + line);
                }
            }
        }

        return address & 0xFFFFFFFFL;
    }

    public static int countLines(String filename) throws IOException {
        byte[] bytes = new byte[8192];
        return countLines(filename, bytes);
    }

    private static int countLines(String filename, byte[] bytes) throws IOException {
        InputStream in = null;
        try {
            in = new FileInputStream(filename);
            int lines = 0;

            byte b = 0;
            for (;;) {
                int len = in.read(bytes);
                if (len < 0) {
                    if (b != '\n') {
                        lines++;
                    }
                    break;
                }
                for (int i = 0; i < len; ++i) {
                    b = bytes[i];
                    if (b == '\n') {
                        lines++;
                    }
                }
            }

            return lines;
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

}