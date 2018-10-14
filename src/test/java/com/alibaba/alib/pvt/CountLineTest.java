package com.alibaba.alib.pvt;

import junit.framework.TestCase;

public class CountLineTest extends TestCase {
    String file = this.getClass().getClassLoader().getResource("test/ip-prefix-base.txt").getFile();

    public void test_perf() throws Exception {
        for (int i = 0; i < 10; ++i) {
            perf();
        }
    }

    void perf() throws Exception {
        long start = System.currentTimeMillis();
        for (int i = 0; i < 10 * 1; ++i) {
//            IPv4RadixIntTree.countLines1(file);
        }
        long millis = System.currentTimeMillis() - start;
        System.out.println("millis : " + millis);
    }
}
