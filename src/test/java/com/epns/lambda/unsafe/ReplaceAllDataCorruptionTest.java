package com.epns.lambda.unsafe;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Demonstrates data corruption with replaceAll on an unsynchronized ArrayList.
 *
 * Each thread does:
 *   1. Read list[0] → before
 *   2. list.replaceAll(x -> x + 1)
 *   3. Read list[0] → after
 *   4. Log: "Thread-N: before=X after=Y" — if after != before+1 → ANOMALY
 *
 * The final table = summary of logs (OK vs ANOMALY per hit level).
 */
public class ReplaceAllDataCorruptionTest {

    private static final int POOL_SIZE = 50;
    private static final int[] HIT_COUNTS = {100, 1_000, 10_000, 100_000};
    private static final long TIMEOUT_SECONDS = 60;

    @Test
    void replaceAllCorruptionAtAllScales() {
        System.out.println();
        System.out.println("=== replaceAll Data Corruption Test ===");
        System.out.println("Each hit: read list[0], replaceAll(x -> x+1), re-read list[0]");
        System.out.println("If after != before+1 → ANOMALY (collision or exception)");
        System.out.println();

        boolean anyCorruption = false;

        // Store summaries for the final table
        String[] summaries = new String[HIT_COUNTS.length];

        for (int level = 0; level < HIT_COUNTS.length; level++) {
            int totalHits = HIT_COUNTS[level];
            int itersPerThread = totalHits / POOL_SIZE;
            int actualTotal = POOL_SIZE * itersPerThread;

            ArrayList<Integer> list = new ArrayList<>();
            list.add(0);

            AtomicInteger okCount = new AtomicInteger(0);
            AtomicInteger anomalyCount = new AtomicInteger(0);
            AtomicInteger exceptionCount = new AtomicInteger(0);

            // Log the first hits to show the format
            boolean verbose = (totalHits <= 100);
            // For large levels, log only the first 20 anomalies
            AtomicInteger anomaliesLogged = new AtomicInteger(0);

            ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE);
            CountDownLatch ready = new CountDownLatch(POOL_SIZE);
            CountDownLatch go = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(POOL_SIZE);

            System.out.printf("--- %,d hits (%d threads × %d iterations) ---%n", actualTotal, POOL_SIZE, itersPerThread);

            for (int t = 0; t < POOL_SIZE; t++) {
                final int threadId = t;
                pool.submit(() -> {
                    ready.countDown();
                    try { go.await(); } catch (InterruptedException e) { return; }

                    for (int i = 0; i < itersPerThread; i++) {
                        try {
                            int before = list.get(0);
                            list.replaceAll(x -> x + 1);
                            int after = list.get(0);

                            if (after == before + 1) {
                                okCount.incrementAndGet();
                                if (verbose && threadId < 3) {
                                    System.out.printf("  Thread-%02d iter %d: before=%-6d after=%-6d ✅ OK%n",
                                            threadId, i, before, after);
                                }
                            } else {
                                anomalyCount.incrementAndGet();
                                if (verbose || anomaliesLogged.incrementAndGet() <= 20) {
                                    System.out.printf("  Thread-%02d iter %d: before=%-6d after=%-6d ⚠️ ANOMALY (expected %d)%n",
                                            threadId, i, before, after, before + 1);
                                }
                            }
                        } catch (Exception e) {
                            exceptionCount.incrementAndGet();
                            if (verbose || anomaliesLogged.incrementAndGet() <= 20) {
                                System.out.printf("  Thread-%02d iter %d: ❌ EXCEPTION %s%n",
                                        threadId, i, e.getClass().getSimpleName());
                            }
                        }
                    }
                    done.countDown();
                });
            }

            boolean finished;
            try {
                ready.await(10, TimeUnit.SECONDS);
                go.countDown();
                finished = done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                finished = false;
            }
            pool.shutdownNow();

            int ok = okCount.get();
            int anomalies = anomalyCount.get();
            int exceptions = exceptionCount.get();
            int totalProblems = anomalies + exceptions;

            if (!finished) {
                summaries[level] = String.format("| %,-9d | TIMEOUT   | TIMEOUT   | TIMEOUT   | TIMEOUT        |",
                        actualTotal);
                anyCorruption = true;
            } else {
                double anomalyRate = (totalProblems * 100.0) / actualTotal;
                summaries[level] = String.format("| %,-9d | %,-9d | %,-9d | %,-9d | %5.2f%%         |",
                        actualTotal, ok, anomalies, exceptions, anomalyRate);
                if (totalProblems > 0) anyCorruption = true;
            }

            System.out.printf("  → OK=%,d | Anomalies=%,d | Exceptions=%,d%n%n", ok, anomalies, exceptions);
        }

        // Summary table
        System.out.println("=== SUMMARY ===");
        System.out.println("| Hits      | OK        | Anomalies | Exceptions| Error Rate     |");
        System.out.println("|-----------|-----------|-----------|-----------|----------------|");
        for (String s : summaries) {
            System.out.println(s);
        }
        System.out.println();

        assertTrue(anyCorruption, "At least one level should show corruption");
    }
}
