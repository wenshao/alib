package com.alibaba.alib.bvt;

import com.alibaba.alib.IPv4RadixIntTree;
import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class RadixTreeTests extends TestCase {
    public void testCidrInclusion() {
        IPv4RadixIntTree tr = new IPv4RadixIntTree(100);
        tr.put(0x0a000000, 0xffffff00, 42);
        tr.put(0x0a000000, 0xff000000, 69);

        assertEquals(tr.selectValue(0x0a202020), 69);
        assertEquals(tr.selectValue(0x0a000020), 42);
        assertEquals(tr.selectValue(0x0b010203), IPv4RadixIntTree.NO_VALUE);
    }

    public void testRealistic() throws IOException {
        String file = this.getClass().getClassLoader().getResource("test/ip-prefix-base.txt").getFile();
        String file1 = this.getClass().getClassLoader().getResource("test/test-1.txt").getFile();

        IPv4RadixIntTree tr = IPv4RadixIntTree.loadFromLocalFile(file);
        BufferedReader br = new BufferedReader(new FileReader(file1));
        String l;
        int n = 0;
        while ((l = br.readLine()) != null) {
            String[] c = l.split("\t", -1);
            assertEquals("Mismatch in line #" + n, tr.selectValue(c[0]), Integer.parseInt(c[1]));
            n++;
        }
        System.out.println(tr.size());
    }

    public void testNginx() throws IOException {
        String file = this.getClass().getClassLoader().getResource("test/ip-prefix-nginx.txt").getFile();
        String file1 = this.getClass().getClassLoader().getResource("test/test-nginx.txt").getFile();
        IPv4RadixIntTree tr = IPv4RadixIntTree.loadFromLocalFile(file, true);
        BufferedReader br = new BufferedReader(new FileReader(file1));
        String l;
        int n = 0;
        while ((l = br.readLine()) != null) {
            String[] c = l.split("\t", -1);
            assertEquals("Mismatch in line #" + n
                    , Integer.parseInt(c[1])
                    , tr.selectValue(c[0]));
            n++;
        }
        System.out.println(tr.size());
    }
}
