package com.epns.lambda;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Démonstration MASSIVE des race conditions avec les lambdas sur ArrayList.
 * 
 * Chaque test est exécuté à 5 niveaux de charge (100 → 1,000,000 hits)
 * pour montrer l'augmentation du risque de collision.
 */
public class LambdaRaceConditionTest {

    private static final int[] SCALE_LEVELS = {100, 1_000, 10_000, 100_000, 1_000_000};
    private static final long TIMEOUT_PER_LEVEL_MS = 30_000;

    // ========================================================================
    // SCALE RESULT
    // ========================================================================

    static class ScaleResult {
        final int totalHits;
        final int exceptions;
        final int corruptions;
        final boolean timeout;

        ScaleResult(int totalHits, int exceptions, int corruptions, boolean timeout) {
            this.totalHits = totalHits;
            this.exceptions = exceptions;
            this.corruptions = corruptions;
            this.timeout = timeout;
        }

        double collisionRate() {
            return totalHits == 0 ? 0 : ((exceptions + corruptions) * 100.0) / totalHits;
        }
    }

    // ========================================================================
    // HELPER
    // ========================================================================

    private ArrayList<Integer> freshList(int size) {
        ArrayList<Integer> list = new ArrayList<>();
        IntStream.range(0, size).forEach(list::add);
        return list;
    }

    @FunctionalInterface
    interface ScaleTest {
        ScaleResult run(int totalHits) throws Exception;
    }

    private List<ScaleResult> runAllScales(String testName, ScaleTest test) {
        List<ScaleResult> results = new ArrayList<>();
        for (int hits : SCALE_LEVELS) {
            try {
                ScaleResult r = test.run(hits);
                results.add(r);
            } catch (Exception e) {
                results.add(new ScaleResult(hits, 0, 0, true));
            }
        }
        printTable(testName, results);
        return results;
    }

    /**
     * Compute threads/iterations for a given total hits.
     * Cap threads at 100, adjust iterations accordingly.
     */
    private int[] threadsAndIterations(int totalHits) {
        int threads = Math.min(100, totalHits);
        int iterations = totalHits / threads;
        return new int[]{threads, iterations};
    }

    private void printTable(String testName, List<ScaleResult> results) {
        System.out.printf("%n=== %s ===%n", testName);
        System.out.println("| Hits      | Exceptions | Corruptions | Collision Rate |");
        System.out.println("|-----------|------------|-------------|----------------|");
        for (ScaleResult r : results) {
            if (r.timeout) {
                System.out.printf("| %,-9d | TIMEOUT    | TIMEOUT     | TIMEOUT        |%n", r.totalHits);
            } else {
                System.out.printf("| %,-9d | %,-10d | %,-11d | %5.2f%%         |%n",
                        r.totalHits, r.exceptions, r.corruptions, r.collisionRate());
            }
        }
        System.out.println();
    }

    // ========================================================================
    // UNSAFE TESTS
    // ========================================================================

    @Test
    @DisplayName("replaceAll on shared ArrayList — UNSAFE")
    void testReplaceAll_Unsafe() {
        List<ScaleResult> results = runAllScales("testReplaceAll_Unsafe", totalHits -> {
            int[] ti = threadsAndIterations(totalHits);
            int threads = ti[0], iterations = ti[1];
            AtomicInteger exceptions = new AtomicInteger();
            AtomicInteger corruptions = new AtomicInteger();
            List<Integer> shared = freshList(100);

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    for (int i = 0; i < iterations; i++) {
                        try {
                            shared.replaceAll(x -> x + 1);
                            Set<Integer> unique = new HashSet<>(shared);
                            if (unique.size() > 1) corruptions.incrementAndGet();
                        } catch (Exception e) {
                            exceptions.incrementAndGet();
                        }
                    }
                    latch.countDown();
                });
            }
            boolean finished = latch.await(TIMEOUT_PER_LEVEL_MS, TimeUnit.MILLISECONDS);
            pool.shutdownNow();
            if (!finished) return new ScaleResult(totalHits, exceptions.get(), corruptions.get(), true);
            return new ScaleResult(totalHits, exceptions.get(), corruptions.get(), false);
        });
        // At least the higher levels should show collisions
        assertTrue(results.stream().anyMatch(r -> !r.timeout && (r.exceptions + r.corruptions) > 0),
                "Expected collisions at some scale level");
    }

    @Test
    @DisplayName("forEach + add on same list — UNSAFE")
    void testForEachAdd_Unsafe() {
        List<ScaleResult> results = runAllScales("testForEachAdd_Unsafe", totalHits -> {
            int[] ti = threadsAndIterations(totalHits);
            int threads = ti[0], iterations = ti[1];
            AtomicInteger exceptions = new AtomicInteger();
            AtomicInteger corruptions = new AtomicInteger();
            // Shared list between readers (forEach) and writers (add)
            List<Integer> shared = freshList(50);

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);
            for (int t = 0; t < threads; t++) {
                final int threadId = t;
                pool.submit(() -> {
                    for (int i = 0; i < iterations; i++) {
                        try {
                            if (threadId % 2 == 0) {
                                // Reader
                                shared.forEach(x -> { if (x == 3) Thread.yield(); });
                            } else {
                                // Writer
                                shared.add(ThreadLocalRandom.current().nextInt(100));
                                if (shared.size() > 200) {
                                    try { shared.subList(50, shared.size()).clear(); } catch (Exception ignored) {}
                                }
                            }
                        } catch (ConcurrentModificationException e) {
                            exceptions.incrementAndGet();
                        } catch (Exception e) {
                            exceptions.incrementAndGet();
                        }
                    }
                    latch.countDown();
                });
            }
            boolean finished = latch.await(TIMEOUT_PER_LEVEL_MS, TimeUnit.MILLISECONDS);
            pool.shutdownNow();
            if (!finished) return new ScaleResult(totalHits, exceptions.get(), corruptions.get(), true);
            return new ScaleResult(totalHits, exceptions.get(), corruptions.get(), false);
        });
        assertTrue(results.stream().anyMatch(r -> !r.timeout && (r.exceptions + r.corruptions) > 0),
                "Expected collisions at some scale level");
    }

    @Test
    @DisplayName("removeIf with lambda — UNSAFE")
    void testRemoveIf_Unsafe() {
        List<ScaleResult> results = runAllScales("testRemoveIf_Unsafe", totalHits -> {
            int[] ti = threadsAndIterations(totalHits);
            int threads = ti[0], iterations = ti[1];
            AtomicInteger exceptions = new AtomicInteger();
            AtomicInteger corruptions = new AtomicInteger();
            List<Integer> shared = freshList(100);

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    for (int i = 0; i < iterations; i++) {
                        try {
                            shared.removeIf(x -> x % 2 == 0);
                            shared.addAll(Arrays.asList(2, 4, 6, 8, 10));
                            if (shared.contains(null) || shared.size() > 10000) corruptions.incrementAndGet();
                        } catch (Exception e) {
                            exceptions.incrementAndGet();
                        }
                    }
                    latch.countDown();
                });
            }
            boolean finished = latch.await(TIMEOUT_PER_LEVEL_MS, TimeUnit.MILLISECONDS);
            pool.shutdownNow();
            if (!finished) return new ScaleResult(totalHits, exceptions.get(), corruptions.get(), true);
            return new ScaleResult(totalHits, exceptions.get(), corruptions.get(), false);
        });
        assertTrue(results.stream().anyMatch(r -> !r.timeout && (r.exceptions + r.corruptions) > 0),
                "Expected collisions at some scale level");
    }

    @Test
    @DisplayName("sort with comparator lambda — UNSAFE")
    void testSort_Unsafe() {
        List<ScaleResult> results = runAllScales("testSort_Unsafe", totalHits -> {
            int[] ti = threadsAndIterations(totalHits);
            int threads = ti[0], iterations = ti[1];
            AtomicInteger exceptions = new AtomicInteger();
            AtomicInteger corruptions = new AtomicInteger();
            List<Integer> shared = freshList(100);

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    for (int i = 0; i < iterations; i++) {
                        try {
                            shared.sort(Comparator.reverseOrder());
                            shared.set(0, ThreadLocalRandom.current().nextInt(1000));
                            boolean sorted = true;
                            for (int j = 0; j < shared.size() - 1; j++) {
                                if (shared.get(j) < shared.get(j + 1)) { sorted = false; break; }
                            }
                            if (!sorted) corruptions.incrementAndGet();
                        } catch (Exception e) {
                            exceptions.incrementAndGet();
                        }
                    }
                    latch.countDown();
                });
            }
            boolean finished = latch.await(TIMEOUT_PER_LEVEL_MS, TimeUnit.MILLISECONDS);
            pool.shutdownNow();
            if (!finished) return new ScaleResult(totalHits, exceptions.get(), corruptions.get(), true);
            return new ScaleResult(totalHits, exceptions.get(), corruptions.get(), false);
        });
        assertTrue(results.stream().anyMatch(r -> !r.timeout && (r.exceptions + r.corruptions) > 0),
                "Expected collisions at some scale level");
    }

    @Test
    @DisplayName("final ArrayList is STILL unsafe — final does NOT help")
    void testFinal_StillUnsafe() {
        List<ScaleResult> results = runAllScales("testFinal_StillUnsafe", totalHits -> {
            int[] ti = threadsAndIterations(totalHits);
            int threads = ti[0], iterations = ti[1];
            AtomicInteger exceptions = new AtomicInteger();
            AtomicInteger corruptions = new AtomicInteger();
            final ArrayList<Integer> shared = freshList(100);

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    for (int i = 0; i < iterations; i++) {
                        try {
                            shared.replaceAll(x -> x + 1);
                            Set<Integer> unique = new HashSet<>(shared);
                            if (unique.size() > 1) corruptions.incrementAndGet();
                        } catch (Exception e) {
                            exceptions.incrementAndGet();
                        }
                    }
                    latch.countDown();
                });
            }
            boolean finished = latch.await(TIMEOUT_PER_LEVEL_MS, TimeUnit.MILLISECONDS);
            pool.shutdownNow();
            if (!finished) return new ScaleResult(totalHits, exceptions.get(), corruptions.get(), true);
            return new ScaleResult(totalHits, exceptions.get(), corruptions.get(), false);
        });
        assertTrue(results.stream().anyMatch(r -> !r.timeout && (r.exceptions + r.corruptions) > 0),
                "Expected collisions at some scale level");
    }

    @Test
    @DisplayName("stream().map().collect() with shared source modified concurrently — UNSAFE")
    void testStreamCollect_SharedSource_Unsafe() {
        List<ScaleResult> results = runAllScales("testStreamCollect_SharedSource_Unsafe", totalHits -> {
            int[] ti = threadsAndIterations(totalHits);
            int threads = ti[0], iterations = ti[1];
            AtomicInteger exceptions = new AtomicInteger();
            AtomicInteger corruptions = new AtomicInteger();
            List<Integer> shared = freshList(100);

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);
            for (int t = 0; t < threads; t++) {
                final int threadId = t;
                pool.submit(() -> {
                    for (int i = 0; i < iterations; i++) {
                        try {
                            if (threadId % 2 == 0) {
                                List<Integer> result = shared.stream().map(x -> x * 2).collect(Collectors.toList());
                                if (result.size() != shared.size() || result.contains(null)) corruptions.incrementAndGet();
                            } else {
                                shared.add(ThreadLocalRandom.current().nextInt(100));
                                if (shared.size() > 200) shared.subList(100, shared.size()).clear();
                            }
                        } catch (Exception e) {
                            exceptions.incrementAndGet();
                        }
                    }
                    latch.countDown();
                });
            }
            boolean finished = latch.await(TIMEOUT_PER_LEVEL_MS, TimeUnit.MILLISECONDS);
            pool.shutdownNow();
            if (!finished) return new ScaleResult(totalHits, exceptions.get(), corruptions.get(), true);
            return new ScaleResult(totalHits, exceptions.get(), corruptions.get(), false);
        });
        assertTrue(results.stream().anyMatch(r -> !r.timeout && (r.exceptions + r.corruptions) > 0),
                "Expected collisions at some scale level");
    }

    // ========================================================================
    // SAFE TESTS
    // ========================================================================

    @Test
    @DisplayName("replaceAll on CopyOnWriteArrayList — SAFE")
    void testReplaceAll_CopyOnWriteArrayList() {
        List<ScaleResult> results = runAllScales("testReplaceAll_CopyOnWriteArrayList", totalHits -> {
            int[] ti = threadsAndIterations(totalHits);
            int threads = ti[0], iterations = ti[1];
            AtomicInteger exceptions = new AtomicInteger();
            AtomicInteger corruptions = new AtomicInteger();
            CopyOnWriteArrayList<Integer> shared = new CopyOnWriteArrayList<>(freshList(20));

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    for (int i = 0; i < iterations; i++) {
                        try {
                            shared.replaceAll(x -> x + 1);
                        } catch (Exception e) {
                            exceptions.incrementAndGet();
                        }
                    }
                    latch.countDown();
                });
            }
            boolean finished = latch.await(TIMEOUT_PER_LEVEL_MS, TimeUnit.MILLISECONDS);
            pool.shutdownNow();
            if (!finished) return new ScaleResult(totalHits, exceptions.get(), corruptions.get(), true);
            return new ScaleResult(totalHits, exceptions.get(), corruptions.get(), false);
        });
        // All non-timeout results should be 0 collisions
        results.stream().filter(r -> !r.timeout).forEach(r ->
                assertEquals(0, r.exceptions + r.corruptions,
                        "Expected 0 collisions at " + r.totalHits + " hits"));
    }

    @Test
    @DisplayName("replaceAll on synchronizedList — SAFE")
    void testReplaceAll_SynchronizedList() {
        List<ScaleResult> results = runAllScales("testReplaceAll_SynchronizedList", totalHits -> {
            int[] ti = threadsAndIterations(totalHits);
            int threads = ti[0], iterations = ti[1];
            AtomicInteger exceptions = new AtomicInteger();
            AtomicInteger corruptions = new AtomicInteger();
            List<Integer> shared = Collections.synchronizedList(freshList(100));

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    for (int i = 0; i < iterations; i++) {
                        try {
                            synchronized (shared) { shared.replaceAll(x -> x + 1); }
                        } catch (Exception e) {
                            exceptions.incrementAndGet();
                        }
                    }
                    latch.countDown();
                });
            }
            boolean finished = latch.await(TIMEOUT_PER_LEVEL_MS, TimeUnit.MILLISECONDS);
            pool.shutdownNow();
            if (!finished) return new ScaleResult(totalHits, exceptions.get(), corruptions.get(), true);
            return new ScaleResult(totalHits, exceptions.get(), corruptions.get(), false);
        });
        results.stream().filter(r -> !r.timeout).forEach(r ->
                assertEquals(0, r.exceptions + r.corruptions,
                        "Expected 0 collisions at " + r.totalHits + " hits"));
    }

    @Test
    @DisplayName("replaceAll with synchronized block — SAFE")
    void testReplaceAll_Synchronized_Block() {
        List<ScaleResult> results = runAllScales("testReplaceAll_Synchronized_Block", totalHits -> {
            int[] ti = threadsAndIterations(totalHits);
            int threads = ti[0], iterations = ti[1];
            AtomicInteger exceptions = new AtomicInteger();
            AtomicInteger corruptions = new AtomicInteger();
            List<Integer> shared = freshList(100);
            Object lock = new Object();

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    for (int i = 0; i < iterations; i++) {
                        try {
                            synchronized (lock) {
                                shared.replaceAll(x -> x + 1);
                                if (shared.size() != 100) corruptions.incrementAndGet();
                            }
                        } catch (Exception e) {
                            exceptions.incrementAndGet();
                        }
                    }
                    latch.countDown();
                });
            }
            boolean finished = latch.await(TIMEOUT_PER_LEVEL_MS, TimeUnit.MILLISECONDS);
            pool.shutdownNow();
            if (!finished) return new ScaleResult(totalHits, exceptions.get(), corruptions.get(), true);
            return new ScaleResult(totalHits, exceptions.get(), corruptions.get(), false);
        });
        results.stream().filter(r -> !r.timeout).forEach(r ->
                assertEquals(0, r.exceptions + r.corruptions,
                        "Expected 0 collisions at " + r.totalHits + " hits"));
    }

    @Test
    @DisplayName("stream with defensive copy — SAFE")
    void testStream_DefensiveCopy() {
        List<ScaleResult> results = runAllScales("testStream_DefensiveCopy", totalHits -> {
            int[] ti = threadsAndIterations(totalHits);
            int threads = ti[0], iterations = ti[1];
            AtomicInteger exceptions = new AtomicInteger();
            AtomicInteger corruptions = new AtomicInteger();
            List<Integer> shared = freshList(100);

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);
            for (int t = 0; t < threads; t++) {
                final int threadId = t;
                pool.submit(() -> {
                    for (int i = 0; i < iterations; i++) {
                        try {
                            if (threadId % 2 == 0) {
                                List<Integer> snapshot;
                                synchronized (shared) { snapshot = new ArrayList<>(shared); }
                                List<Integer> result = snapshot.stream().map(x -> x * 2).collect(Collectors.toList());
                                if (result.contains(null)) corruptions.incrementAndGet();
                            } else {
                                synchronized (shared) {
                                    shared.add(ThreadLocalRandom.current().nextInt(100));
                                    if (shared.size() > 200) shared.subList(100, shared.size()).clear();
                                }
                            }
                        } catch (Exception e) {
                            exceptions.incrementAndGet();
                        }
                    }
                    latch.countDown();
                });
            }
            boolean finished = latch.await(TIMEOUT_PER_LEVEL_MS, TimeUnit.MILLISECONDS);
            pool.shutdownNow();
            if (!finished) return new ScaleResult(totalHits, exceptions.get(), corruptions.get(), true);
            return new ScaleResult(totalHits, exceptions.get(), corruptions.get(), false);
        });
        results.stream().filter(r -> !r.timeout).forEach(r ->
                assertEquals(0, r.exceptions + r.corruptions,
                        "Expected 0 collisions at " + r.totalHits + " hits"));
    }
}
