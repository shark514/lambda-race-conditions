package com.epns.lambda.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * Helper partagé pour exécuter des tests de collision à différents niveaux de charge.
 * 
 * Le nombre de hits peut être contrôlé via -Dhits=N pour un seul niveau,
 * ou laissé par défaut pour exécuter les 5 niveaux (100, 1K, 10K, 100K, 1M).
 */
public final class CollisionTestRunner {

    private static final int[] DEFAULT_SCALE_LEVELS = {100, 1_000, 10_000, 100_000, 1_000_000};
    private static final long TIMEOUT_PER_LEVEL_MS = 30_000;

    private CollisionTestRunner() {}

    // ========================================================================
    // Result
    // ========================================================================

    public static class ScaleResult {
        public final int totalHits;
        public final int exceptions;
        public final int corruptions;
        public final boolean timeout;

        public ScaleResult(int totalHits, int exceptions, int corruptions, boolean timeout) {
            this.totalHits = totalHits;
            this.exceptions = exceptions;
            this.corruptions = corruptions;
            this.timeout = timeout;
        }

        public double collisionRate() {
            return totalHits == 0 ? 0 : ((exceptions + corruptions) * 100.0) / totalHits;
        }

        public int totalCollisions() {
            return exceptions + corruptions;
        }
    }

    // ========================================================================
    // Functional interface for test logic
    // ========================================================================

    @FunctionalInterface
    public interface ScaleTest {
        ScaleResult run(int totalHits) throws Exception;
    }

    // ========================================================================
    // Public API
    // ========================================================================

    /**
     * Returns the scale levels to test. If -Dhits=N is set, returns just that one level.
     * Otherwise returns the 5 default levels.
     */
    public static int[] getScaleLevels() {
        String hitsParam = System.getProperty("hits");
        if (hitsParam != null && !hitsParam.isEmpty()) {
            try {
                return new int[]{Integer.parseInt(hitsParam)};
            } catch (NumberFormatException e) {
                System.err.println("Invalid -Dhits value: " + hitsParam + ", using defaults");
            }
        }
        return DEFAULT_SCALE_LEVELS;
    }

    /**
     * Runs the test at all configured scale levels and prints the results table.
     */
    public static List<ScaleResult> runAllScales(String testName, ScaleTest test) {
        List<ScaleResult> results = new ArrayList<>();
        for (int hits : getScaleLevels()) {
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
     * Compute optimal threads/iterations for a given total hits.
     * Cap threads at 100, adjust iterations accordingly.
     */
    public static int[] threadsAndIterations(int totalHits) {
        int threads = Math.min(100, totalHits);
        int iterations = totalHits / threads;
        return new int[]{threads, iterations};
    }

    /**
     * Execute a concurrent workload and return the result.
     */
    public static ScaleResult executeWorkload(int totalHits, ConcurrentWorkload workload) throws InterruptedException {
        int[] ti = threadsAndIterations(totalHits);
        int threads = ti[0], iterations = ti[1];
        AtomicInteger exceptions = new AtomicInteger();
        AtomicInteger corruptions = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            pool.submit(() -> {
                for (int i = 0; i < iterations; i++) {
                    workload.execute(threadId, i, exceptions, corruptions);
                }
                latch.countDown();
            });
        }

        boolean finished = latch.await(TIMEOUT_PER_LEVEL_MS, TimeUnit.MILLISECONDS);
        pool.shutdownNow();
        return new ScaleResult(totalHits, exceptions.get(), corruptions.get(), !finished);
    }

    @FunctionalInterface
    public interface ConcurrentWorkload {
        void execute(int threadId, int iteration, AtomicInteger exceptions, AtomicInteger corruptions);
    }

    /**
     * Create a fresh ArrayList of given size with values 0..size-1.
     */
    public static ArrayList<Integer> freshList(int size) {
        ArrayList<Integer> list = new ArrayList<>();
        IntStream.range(0, size).forEach(list::add);
        return list;
    }

    // ========================================================================
    // Table printing
    // ========================================================================

    public static void printTable(String testName, List<ScaleResult> results) {
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
}
