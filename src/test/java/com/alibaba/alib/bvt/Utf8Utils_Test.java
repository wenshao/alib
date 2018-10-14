package com.alibaba.alib.bvt;

import com.alibaba.alib.lang.Utf8Utils;
import junit.framework.TestCase;

import java.nio.charset.Charset;

public class Utf8Utils_Test extends TestCase {
    protected void setUp() throws Exception {
        System.out.println("jdk : " + System.getProperty("java.runtime.version"));
    }

    public void test_0() throws Exception {

        {
            char[] chars = S0.toCharArray();
            byte[] bytes = new byte[chars.length * 3];
            for (int i = 0; i < 5; ++i) {
//            perf(chars, bytes); // 388
//            perf_str(S0, bytes); // 517
            perf_2(S0, bytes); // 390
            }
        }
        {
            char[] chars = S1.toCharArray();
            byte[] bytes = new byte[chars.length * 3];
            for (int i = 0; i < 5; ++i) {
//            perf(chars, bytes); //
//            perf_str(S1, bytes); //
//                perf_2(S1, bytes); // 21
            }
        }
    }

    public void test_decode() throws Exception {
        for (int i = 0; i < 5; ++i) {
//            perf_decode_chinese(); // 707
//            perf_decode_chinese_str(); // 780
//            perf_decode_chinese_unsafe(); // 591
            perf_decode_chinese_unsafe2(); // 750
        }

    }

    static void perf_decode_chinese() throws Exception {
        byte[] bytes = S0.getBytes(Charset.forName("utf8"));
        char[] chars = new char[bytes.length];

        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000 * 1000; ++i) {
            int dp = Utf8Utils.decodeUTF8(bytes, 0, bytes.length, chars, 0);
            new String(chars, 0, dp);
        }
        System.out.println("decode chinese millis : " + (System.currentTimeMillis() - start));
    }

    static void perf_decode_chinese_unsafe() throws Exception {
        byte[] bytes = S0.getBytes(Charset.forName("utf8"));
        char[] chars = new char[bytes.length];

        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000 * 1000; ++i) {
            int dp = Utf8Utils.decodeUTF8_unsafe(bytes, 0, bytes.length, chars, 0);
            new String(chars, 0, dp);
        }
        System.out.println("decode chinese unsafe millis : " + (System.currentTimeMillis() - start));
    }

    static void perf_decode_chinese_unsafe2() throws Exception {
        byte[] bytes = S0.getBytes(Charset.forName("utf8"));

        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000 * 1000; ++i) {
            Utf8Utils.decodeUTF8_unsafe(bytes, 0, bytes.length);
        }
        System.out.println("decode chinese unsafe millis : " + (System.currentTimeMillis() - start));
    }

    static void perf_decode_chinese_str() throws Exception {
        Charset utf8 = Charset.forName("utf8");
        byte[] bytes = S0.getBytes(utf8);
        char[] chars = new char[bytes.length];

        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000 * 1000; ++i) {
            new String(bytes, 0, bytes.length, utf8);
        }
        System.out.println("decode chinese str millis : " + (System.currentTimeMillis() - start));
    }

    static final Charset UTF8 = Charset.forName("utf8");
    static void perf_str(String str, byte[] bytes) {
        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000 * 1000; ++i) {
            bytes = str.getBytes(UTF8);
        }
        System.out.println("millis : " + (System.currentTimeMillis() - start));
    }

    static void perf(char[] chars, byte[] bytes) {
        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000 * 1000; ++i) {
            Utf8Utils.encodeUTF8(chars, 0, chars.length, bytes, 0);
        }
        System.out.println("unsafe chars millis : " + (System.currentTimeMillis() - start));
    }

    static void perf_2(String str, byte[] bytes) throws Exception {
        {
            int len = Utf8Utils.encodeUTF8(str, bytes, 0);
            String strD = new String(bytes, 0, len, "utf8");
            if (!strD.equals(str)) {
                throw new IllegalStateException("not support.");
            }
        }

        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000 * 1000; ++i) {
            Utf8Utils.encodeUTF8(str, bytes, 0);
        }
        System.out.println("unsafe str millis : " + (System.currentTimeMillis() - start));
    }

    public static String S0 = "不过，比起这些人渣以及那个美国记者，真正需要咱们中国人关注和关心的，还是目前身在美国或打算去美国留学的中国学子们的命运。毕竟，自从中美建交以来，不论中美关系多么紧张，踏踏实实在象牙塔里学知识的中国留学生们都不太会受到美国政治风向影响。可这届特朗普政府却不仅被曝出打算禁止所有中国留学生来美国念书——理由是中国学生都是间谍；就连自诩客观中立的美国媒体都在纷纷迎合美国政府这种排华反华的“恐慌政治营销”，不惜把千里迢迢来美国求学的中国莘莘学子“污名化”、“妖魔化”。";
    public static String S1 = "Amazing Stories is an American science fiction magazine first launched in April 1926 by Hugo Gernsback's Experimenter Publishing, and continuing since 2012 as an online magazine. As the first magazine that ran only science fiction stories, it helped define a new genre of pulp fiction, and science fiction fandom traces its beginnings to the letters-to-the-editor columns in Amazing and its competitors. Gernsback's initial editorial approach was to blend instruction with entertainment; he believed science fiction could educate readers, but his audience rapidly showed a preference for implausible adventures. The magazine was published, with some interruptions, for almost eighty years, going through a half-dozen owners and many editors, including Raymond A. Palmer, as it struggled to be profitable. Amazing was nominated for the Hugo Award three times in the 1970s during Ted White's tenure as editor. Several owners attempted to create a modern incarnation of the magazine, but the print publication was suspended after the March 2005 issue.";
}
