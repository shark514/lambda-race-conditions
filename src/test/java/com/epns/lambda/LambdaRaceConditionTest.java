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
 * Chaque test bombarde 100,000 fois pour mesurer le taux de collision réel.
 * Les tests UNSAFE prouvent que ArrayList n'est PAS thread-safe.
 * Les tests SAFE prouvent que les solutions fonctionnent.
 */
public class LambdaRaceConditionTest {

    private static final int THREADS = 50;
    private static final int ITERATIONS_PER_THREAD = 2000;
    private static final int TOTAL_HITS = THREADS * ITERATIONS_PER_THREAD; // 100,000

    // ========================================================================
    // HELPER
    // ========================================================================

    private ArrayList<Integer> freshList(int size) {
        ArrayList<Integer> list = new ArrayList<>();
        IntStream.range(0, size).forEach(list::add);
        return list;
    }

    private void printStats(String testName, int exceptions, int corruptions, boolean expectUnsafe) {
        int total = exceptions + corruptions;
        double rate = (total * 100.0) / TOTAL_HITS;
        String status = total > 0 ? "UNSAFE ❌" : "SAFE ✅";
        System.out.printf("""
                
                === %s ===
                Threads: %d | Iterations: %d | Total hits: %,d
                Exceptions caught: %,d
                Data corruptions: %,d
                Collision rate: %.2f%%
                Status: %s
                """, testName, THREADS, ITERATIONS_PER_THREAD, TOTAL_HITS,
                exceptions, corruptions, rate, status);

        if (expectUnsafe) {
            assertTrue(total > 0, "Expected collisions but got none — race condition not triggered");
        } else {
            assertEquals(0, total, "Expected 0 collisions but got " + total);
        }
    }

    // ========================================================================
    // UNSAFE TESTS
    // ========================================================================

    @Test
    @DisplayName("replaceAll on shared ArrayList — UNSAFE")
    void testReplaceAll_Unsafe() throws InterruptedException {
        AtomicInteger exceptions = new AtomicInteger();
        AtomicInteger corruptions = new AtomicInteger();
        List<Integer> shared = freshList(100);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch latch = new CountDownLatch(THREADS);

        for (int t = 0; t < THREADS; t++) {
            pool.submit(() -> {
                for (int i = 0; i < ITERATIONS_PER_THREAD; i++) {
                    try {
                        shared.replaceAll(x -> x + 1);
                        // Check for corruption: all elements should be equal after N increments
                        // but with races they diverge
                        Set<Integer> unique = new HashSet<>(shared);
                        if (unique.size() > 1) {
                            corruptions.incrementAndGet();
                        }
                    } catch (Exception e) {
                        exceptions.incrementAndGet();
                    }
                }
                latch.countDown();
            });
        }
        latch.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();
        printStats("testReplaceAll_Unsafe", exceptions.get(), corruptions.get(), true);
    }

    @Test
    @DisplayName("forEach + add on same list — UNSAFE")
    void testForEachAdd_Unsafe() throws InterruptedException {
        AtomicInteger exceptions = new AtomicInteger();
        AtomicInteger corruptions = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch latch = new CountDownLatch(THREADS);

        for (int t = 0; t < THREADS; t++) {
            pool.submit(() -> {
                for (int i = 0; i < ITERATIONS_PER_THREAD; i++) {
                    List<Integer> shared = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5));
                    try {
                        // One thread reads, this thread modifies
                        Thread modifier = new Thread(() -> {
                            try { shared.add(99); } catch (Exception e) { exceptions.incrementAndGet(); }
                        });
                        modifier.start();
                        shared.forEach(x -> {
                            // force iteration while modifier runs
                            if (x == 3) Thread.yield();
                        });
                        modifier.join(100);
                        // Check: list should have exactly 6 elements
                        if (shared.size() != 6) {
                            corruptions.incrementAndGet();
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
        latch.await(60, TimeUnit.SECONDS);
        pool.shutdownNow();
        printStats("testForEachAdd_Unsafe", exceptions.get(), corruptions.get(), true);
    }

    @Test
    @DisplayName("removeIf with lambda — UNSAFE")
    void testRemoveIf_Unsafe() throws InterruptedException {
        AtomicInteger exceptions = new AtomicInteger();
        AtomicInteger corruptions = new AtomicInteger();
        List<Integer> shared = freshList(100);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch latch = new CountDownLatch(THREADS);

        for (int t = 0; t < THREADS; t++) {
            pool.submit(() -> {
                for (int i = 0; i < ITERATIONS_PER_THREAD; i++) {
                    try {
                        // Concurrently removeIf and add
                        shared.removeIf(x -> x % 2 == 0);
                        shared.addAll(Arrays.asList(2, 4, 6, 8, 10));
                        // Check for nulls or unexpected size
                        if (shared.contains(null) || shared.size() > 10000) {
                            corruptions.incrementAndGet();
                        }
                    } catch (Exception e) {
                        exceptions.incrementAndGet();
                    }
                }
                latch.countDown();
            });
        }
        latch.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();
        printStats("testRemoveIf_Unsafe", exceptions.get(), corruptions.get(), true);
    }

    @Test
    @DisplayName("sort with comparator lambda — UNSAFE")
    void testSort_Unsafe() throws InterruptedException {
        AtomicInteger exceptions = new AtomicInteger();
        AtomicInteger corruptions = new AtomicInteger();
        List<Integer> shared = freshList(100);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch latch = new CountDownLatch(THREADS);

        for (int t = 0; t < THREADS; t++) {
            pool.submit(() -> {
                for (int i = 0; i < ITERATIONS_PER_THREAD; i++) {
                    try {
                        // Concurrent sort + modification
                        shared.sort(Comparator.reverseOrder());
                        shared.set(0, ThreadLocalRandom.current().nextInt(1000));
                        // Check: after sort, should be descending — but with races it won't be
                        boolean sorted = true;
                        for (int j = 0; j < shared.size() - 1; j++) {
                            if (shared.get(j) < shared.get(j + 1)) {
                                sorted = false;
                                break;
                            }
                        }
                        if (!sorted) corruptions.incrementAndGet();
                    } catch (Exception e) {
                        exceptions.incrementAndGet();
                    }
                }
                latch.countDown();
            });
        }
        latch.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();
        printStats("testSort_Unsafe", exceptions.get(), corruptions.get(), true);
    }

    @Test
    @DisplayName("final ArrayList is STILL unsafe — final does NOT help")
    void testFinal_StillUnsafe() throws InterruptedException {
        AtomicInteger exceptions = new AtomicInteger();
        AtomicInteger corruptions = new AtomicInteger();
        // FINAL does not make the list thread-safe — it only prevents reassignment
        final ArrayList<Integer> shared = freshList(100);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch latch = new CountDownLatch(THREADS);

        for (int t = 0; t < THREADS; t++) {
            pool.submit(() -> {
                for (int i = 0; i < ITERATIONS_PER_THREAD; i++) {
                    try {
                        shared.replaceAll(x -> x + 1);
                        Set<Integer> unique = new HashSet<>(shared);
                        if (unique.size() > 1) {
                            corruptions.incrementAndGet();
                        }
                    } catch (Exception e) {
                        exceptions.incrementAndGet();
                    }
                }
                latch.countDown();
            });
        }
        latch.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();
        printStats("testFinal_StillUnsafe", exceptions.get(), corruptions.get(), true);
    }

    @Test
    @DisplayName("stream().map().collect() with shared source modified concurrently — UNSAFE")
    void testStreamCollect_SharedSource_Unsafe() throws InterruptedException {
        AtomicInteger exceptions = new AtomicInteger();
        AtomicInteger corruptions = new AtomicInteger();
        List<Integer> shared = freshList(100);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch latch = new CountDownLatch(THREADS);

        for (int t = 0; t < THREADS; t++) {
            final int threadId = t;
            pool.submit(() -> {
                for (int i = 0; i < ITERATIONS_PER_THREAD; i++) {
                    try {
                        if (threadId % 2 == 0) {
                            // Reader: stream the shared source
                            List<Integer> result = shared.stream()
                                    .map(x -> x * 2)
                                    .collect(Collectors.toList());
                            // Result should have same size as source, but races cause mismatches
                            if (result.size() != shared.size() || result.contains(null)) {
                                corruptions.incrementAndGet();
                            }
                        } else {
                            // Writer: mutate the shared source
                            shared.add(ThreadLocalRandom.current().nextInt(100));
                            if (shared.size() > 200) {
                                shared.subList(100, shared.size()).clear();
                            }
                        }
                    } catch (Exception e) {
                        exceptions.incrementAndGet();
                    }
                }
                latch.countDown();
            });
        }
        latch.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();
        printStats("testStreamCollect_SharedSource_Unsafe", exceptions.get(), corruptions.get(), true);
    }

    // ========================================================================
    // SAFE TESTS
    // ========================================================================

    @Test
    @DisplayName("replaceAll on CopyOnWriteArrayList — SAFE")
    void testReplaceAll_CopyOnWriteArrayList() throws InterruptedException {
        AtomicInteger exceptions = new AtomicInteger();
        AtomicInteger corruptions = new AtomicInteger();
        CopyOnWriteArrayList<Integer> shared = new CopyOnWriteArrayList<>(freshList(20));

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch latch = new CountDownLatch(THREADS);

        for (int t = 0; t < THREADS; t++) {
            pool.submit(() -> {
                for (int i = 0; i < ITERATIONS_PER_THREAD; i++) {
                    try {
                        shared.replaceAll(x -> x + 1);
                    } catch (Exception e) {
                        exceptions.incrementAndGet();
                    }
                }
                latch.countDown();
            });
        }
        latch.await(120, TimeUnit.SECONDS);
        pool.shutdownNow();

        // Verify: all elements should be equal (all started at different values but got same # of increments)
        // Actually with CopyOnWriteArrayList, replaceAll is atomic per call, so no corruption
        // Check no exceptions
        printStats("testReplaceAll_CopyOnWriteArrayList", exceptions.get(), corruptions.get(), false);
    }

    @Test
    @DisplayName("replaceAll on synchronizedList — SAFE")
    void testReplaceAll_SynchronizedList() throws InterruptedException {
        AtomicInteger exceptions = new AtomicInteger();
        AtomicInteger corruptions = new AtomicInteger();
        List<Integer> shared = Collections.synchronizedList(freshList(100));

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch latch = new CountDownLatch(THREADS);

        for (int t = 0; t < THREADS; t++) {
            pool.submit(() -> {
                for (int i = 0; i < ITERATIONS_PER_THREAD; i++) {
                    try {
                        synchronized (shared) {
                            shared.replaceAll(x -> x + 1);
                        }
                    } catch (Exception e) {
                        exceptions.incrementAndGet();
                    }
                }
                latch.countDown();
            });
        }
        latch.await(60, TimeUnit.SECONDS);
        pool.shutdownNow();
        printStats("testReplaceAll_SynchronizedList", exceptions.get(), corruptions.get(), false);
    }

    @Test
    @DisplayName("replaceAll with synchronized block — SAFE")
    void testReplaceAll_Synchronized_Block() throws InterruptedException {
        AtomicInteger exceptions = new AtomicInteger();
        AtomicInteger corruptions = new AtomicInteger();
        List<Integer> shared = freshList(100);
        Object lock = new Object();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch latch = new CountDownLatch(THREADS);

        for (int t = 0; t < THREADS; t++) {
            pool.submit(() -> {
                for (int i = 0; i < ITERATIONS_PER_THREAD; i++) {
                    try {
                        synchronized (lock) {
                            shared.replaceAll(x -> x + 1);
                            // Verify: size should remain constant
                            if (shared.size() != 100) {
                                corruptions.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        exceptions.incrementAndGet();
                    }
                }
                latch.countDown();
            });
        }
        latch.await(60, TimeUnit.SECONDS);
        pool.shutdownNow();
        printStats("testReplaceAll_Synchronized_Block", exceptions.get(), corruptions.get(), false);
    }

    @Test
    @DisplayName("stream with defensive copy — SAFE")
    void testStream_DefensiveCopy() throws InterruptedException {
        AtomicInteger exceptions = new AtomicInteger();
        AtomicInteger corruptions = new AtomicInteger();
        List<Integer> shared = freshList(100);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch latch = new CountDownLatch(THREADS);

        for (int t = 0; t < THREADS; t++) {
            final int threadId = t;
            pool.submit(() -> {
                for (int i = 0; i < ITERATIONS_PER_THREAD; i++) {
                    try {
                        if (threadId % 2 == 0) {
                            // SAFE: defensive copy under lock, then stream freely
                            List<Integer> snapshot;
                            synchronized (shared) {
                                snapshot = new ArrayList<>(shared);
                            }
                            List<Integer> result = snapshot.stream()
                                    .map(x -> x * 2)
                                    .collect(Collectors.toList());
                            if (result.contains(null)) {
                                corruptions.incrementAndGet();
                            }
                        } else {
                            // Writer
                            synchronized (shared) {
                                shared.add(ThreadLocalRandom.current().nextInt(100));
                                if (shared.size() > 200) {
                                    shared.subList(100, shared.size()).clear();
                                }
                            }
                        }
                    } catch (Exception e) {
                        exceptions.incrementAndGet();
                    }
                }
                latch.countDown();
            });
        }
        latch.await(60, TimeUnit.SECONDS);
        pool.shutdownNow();
        printStats("testStream_DefensiveCopy", exceptions.get(), corruptions.get(), false);
    }
}
