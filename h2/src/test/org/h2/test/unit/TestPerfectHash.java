/*
 * Copyright 2004-2014 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.unit;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.h2.dev.hash.MinimalPerfectHash;
import org.h2.dev.hash.MinimalPerfectHash.LongHash;
import org.h2.dev.hash.MinimalPerfectHash.StringHash;
import org.h2.dev.hash.MinimalPerfectHash.UniversalHash;
import org.h2.dev.hash.PerfectHash;
import org.h2.test.TestBase;

/**
 * Tests the perfect hash tool.
 */
public class TestPerfectHash extends TestBase {

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String... a) throws Exception {
        TestPerfectHash test = (TestPerfectHash) TestBase.createCaller().init();
        largeFile();
        test.test();
        test.measure();
    }
    
    private static void largeFile() throws IOException {
        String fileName = System.getProperty("user.home") + "/temp/enwiki-20140811-all-titles.txt";
        RandomAccessFile f = new RandomAccessFile(fileName, "r");
        byte[] data = new byte[(int) f.length()];
        f.readFully(data);
        UniversalHash<Text> hf = new UniversalHash<Text>() {

            @Override
            public int hashCode(Text o, int index, int seed) {
                return o.hashCode(index, seed);
            }
            
        };
        HashSet<Text> set = new HashSet<Text>();
        Text t = new Text(data, 0);
        while (true) {
            set.add(t);
            int end = t.getEnd();
            if (end >= data.length - 1) {
                break;
            }
            t = new Text(data, end + 1);
            if (set.size() % 1000000 == 0) {
                System.out.println("size: " + set.size());
            }
        }
        System.out.println("size: " + set.size());
        byte[] desc = MinimalPerfectHash.generate(set, hf);
        System.out.println("len: " + desc.length);
        int bits = desc.length * 8;
        System.out.println(((double) bits / set.size()) + " bits/key");
    }

    /**
     * Measure the hash functions.
     */
    public void measure() {
        int size = 1000000;
        testMinimal(size / 10);
        int s;
        long time = System.currentTimeMillis();
        s = testMinimal(size);
        time = System.currentTimeMillis() - time;
        System.out.println((double) s / size + " bits/key (minimal) in " +
                time + " ms");

        time = System.currentTimeMillis();
        s = testMinimalWithString(size);
        time = System.currentTimeMillis() - time;
        System.out.println((double) s / size +
                " bits/key (minimal; String keys) in " + 
                time + " ms");

        time = System.currentTimeMillis();
        s = test(size, true);
        time = System.currentTimeMillis() - time;
        System.out.println((double) s / size + " bits/key (minimal old) in " + 
                time + " ms");
        time = System.currentTimeMillis();
        s = test(size, false);
        time = System.currentTimeMillis() - time;
        System.out.println((double) s / size + " bits/key (not minimal) in " + 
                time + " ms");
    }

    @Override
    public void test() {
        testBrokenHashFunction();
        for (int i = 0; i < 100; i++) {
            testMinimal(i);
        }
        for (int i = 100; i <= 100000; i *= 10) {
            testMinimal(i);
        }
        for (int i = 0; i < 100; i++) {
            test(i, true);
            test(i, false);
        }
        for (int i = 100; i <= 100000; i *= 10) {
            test(i, true);
            test(i, false);
        }
    }
    
    private void testBrokenHashFunction() {
        int size = 10000;
        Random r = new Random(10000);
        HashSet<String> set = new HashSet<String>(size);
        while (set.size() < size) {
            set.add("x " + r.nextDouble());
        }
        for (int test = 1; test < 10; test++) {
            final int badUntilLevel = test;
            UniversalHash<String> badHash = new UniversalHash<String>() {

                @Override
                public int hashCode(String o, int index, int seed) {
                    if (index < badUntilLevel) {
                        return 0;
                    }
                    return StringHash.getFastHash(o, index);
                }
                
            };
            byte[] desc = MinimalPerfectHash.generate(set, badHash);
            testMinimal(desc, set, badHash);
        }
    }

    private int test(int size, boolean minimal) {
        Random r = new Random(size);
        HashSet<Integer> set = new HashSet<Integer>();
        while (set.size() < size) {
            set.add(r.nextInt());
        }
        byte[] desc = PerfectHash.generate(set, minimal);
        int max = test(desc, set);
        if (minimal) {
            assertEquals(size - 1, max);
        } else {
            if (size > 10) {
                assertTrue(max < 1.5 * size);
            }
        }
        return desc.length * 8;
    }

    private int test(byte[] desc, Set<Integer> set) {
        int max = -1;
        HashSet<Integer> test = new HashSet<Integer>();
        PerfectHash hash = new PerfectHash(desc);
        for (int x : set) {
            int h = hash.get(x);
            assertTrue(h >= 0);
            assertTrue(h <= set.size() * 3);
            max = Math.max(max, h);
            assertFalse(test.contains(h));
            test.add(h);
        }
        return max;
    }

    private int testMinimal(int size) {
        Random r = new Random(size);
        HashSet<Long> set = new HashSet<Long>(size);
        while (set.size() < size) {
            set.add((long) r.nextInt());
        }
        LongHash hf = new LongHash();
        byte[] desc = MinimalPerfectHash.generate(set, hf);
        int max = testMinimal(desc, set, hf);
        assertEquals(size - 1, max);
        return desc.length * 8;
    }
    
    private int testMinimalWithString(int size) {
        Random r = new Random(size);
        HashSet<String> set = new HashSet<String>(size);
        while (set.size() < size) {
            set.add("x " + r.nextDouble());
        }
        StringHash hf = new StringHash();
        byte[] desc = MinimalPerfectHash.generate(set, hf);
        int max = testMinimal(desc, set, hf);
        assertEquals(size - 1, max);
        return desc.length * 8;
    }

    private <K> int testMinimal(byte[] desc, Set<K> set, UniversalHash<K> hf) {
        int max = -1;
        BitSet test = new BitSet();
        MinimalPerfectHash<K> hash = new MinimalPerfectHash<K>(desc, hf);
        for (K x : set) {
            int h = hash.get(x);
            assertTrue(h >= 0);
            assertTrue(h <= set.size() * 3);
            max = Math.max(max, h);
            assertFalse(test.get(h));
            test.set(h);
        }
        return max;
    }
    
    /**
     * A text.
     */
    static class Text {
        
        final byte[] data;
        final int start;
        
        Text(byte[] data, int start) {
            this.data = data;
            this.start = start;
        }
        
        public int hashCode(int index, int seed) {
            if (index < 4) {
                int result = 0;
                int x = seed + index;
                int end = start;
                while (data[end] != '\n') {
                    x = 31 + x * 0x9f3b;
                    result += x * (1 + (data[end] & 255));
                    end++;
                }
                return result;
            }
            int end = getEnd();
            return StringHash.getSipHash24(data, start, end, index, seed);
        }

        int getEnd() {
            int end = start;
            while (data[end] != '\n') {
                end++;
            }
            return end;
        }
        
        @Override
        public int hashCode() {
            return hashCode(0, 0);
        }
        
        @Override
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            } else if (!(other instanceof Text)) {
                return false;
            }
            Text o = (Text) other;
            int end = getEnd();
            int s2 = o.start;
            int e2 = o.getEnd();
            if (e2 - s2 != end - start) {
                return false;
            }
            for (int s1 = start; s1 < end; s1++, s2++) {
                if (data[s1] != o.data[s2]) {
                    return false;
                }
            }
            return true;
        }
        
        @Override
        public String toString() {
            return new String(data, start, getEnd() - start);
        }
    }

}