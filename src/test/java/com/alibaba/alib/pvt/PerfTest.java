package com.alibaba.alib.pvt;

import com.alibaba.alib.IPv4RadixIntTree;
import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.UnknownHostException;

public class PerfTest extends TestCase {
    public void testRealistic() throws IOException {
        String file = this.getClass().getClassLoader().getResource("test/ip-prefix-base.txt").getFile();
        String file1 = this.getClass().getClassLoader().getResource("test/test-1.txt").getFile();

        IPv4RadixIntTree tr = IPv4RadixIntTree.loadFromLocalFile(file);

        for (int i = 0; i < 10; ++i) {
            perf(tr);
        }

//        BufferedReader br = new BufferedReader(new FileReader(file1));
//        String l;
//        int n = 0;
//        while ((l = br.readLine()) != null) {
//            String[] c = l.split("\t", -1);
//            assertEquals("Mismatch in line #" + n, tr.selectValue(c[0]), Integer.parseInt(c[1]));
//            n++;
//        }
//        System.out.println(tr.size());
    }

    private void perf(IPv4RadixIntTree tr) throws UnknownHostException {
        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000 * 1000 * 10; ++i) {
//            tr.selectValue("95.26.186.69");
//            tr.selectValue(1595587141L);
            IPv4RadixIntTree.inet_aton("95.26.186.69");
        }
        long millis = System.currentTimeMillis() - start;
        System.out.println("millis : " + millis);
    }
}
