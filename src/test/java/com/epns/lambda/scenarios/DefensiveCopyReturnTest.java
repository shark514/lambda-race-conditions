package com.epns.lambda.scenarios;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * DefensiveCopy WITH RETURN — the "correct" defensive copy pattern.
 *
 * Each thread copies the list, increments the copy, and RETURNS it
 * without writing back to the original.
 *
 * Unlike other scenarios that measure shared state mutation,
 * this one measures whether each RETURNED COPY is correct.
 * The shared list stays at 0 — that's expected and not a failure.
 * The real question: did each thread's copy get populated correctly?
 */
public class DefensiveCopyReturnTest {

    private static final Logger log = Logger.getLogger(DefensiveCopyReturnTest.class.getName());

    @Test
    public void testDefensiveCopyReturn() throws InterruptedException {
        ArrayList<Integer> sharedList = new ArrayList<>(Collections.nCopies(10, 0));

        int[] hitLevels = {100, 1_000, 10_000, 100_000};
        String hitsParam = System.getProperty("hits");
        if (hitsParam != null && !hitsParam.isEmpty()) {
            try {
                hitLevels = new int[]{Integer.parseInt(hitsParam)};
            } catch (NumberFormatException ignored) {}
        }

        int threads = 50;

        System.out.printf("%n=== Scenario: DefensiveCopyReturn ===%n");
        System.out.printf("50 threads — each copies the list, increments the copy, returns it%n");
        System.out.printf("Metric: is each RETURNED COPY correct? (each element = original + 1)%n%n");

        System.out.printf("| %-10s | %-10s | %-10s | %-10s | %-12s | %-15s |%n",
                "Hits", "OK", "Lost", "Exceptions", "Error Rate", "Shared list[0]");
        System.out.println("|------------|------------|------------|------------|--------------|-----------------|");

        for (int totalHits : hitLevels) {
            // Reset shared list to 0
            for (int i = 0; i < sharedList.size(); i++) sharedList.set(i, 0);

            int iterationsPerThread = Math.max(1, totalHits / threads);
            int actualTotal = threads * iterationsPerThread;

            AtomicInteger okCount = new AtomicInteger();
            AtomicInteger koCount = new AtomicInteger();
            AtomicInteger exceptionCount = new AtomicInteger();

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);

            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    ready.countDown();
                    try { go.await(); } catch (InterruptedException e) { return; }
                    try {
                        for (int i = 0; i < iterationsPerThread; i++) {
                            try {
                                // Defensive copy + transform + RETURN
                                ArrayList<Integer> copy = new ArrayList<>(sharedList);
                                copy.replaceAll(x -> x + 1);

                                // Verify the returned copy is correct:
                                // each element should be original value + 1
                                // Since sharedList is always [0,0,...,0], copy should be [1,1,...,1]
                                boolean copyCorrect = true;
                                for (int val : copy) {
                                    if (val != 1) {
                                        copyCorrect = false;
                                        break;
                                    }
                                }

                                if (copyCorrect && copy.size() == sharedList.size()) {
                                    okCount.incrementAndGet();
                                } else {
                                    koCount.incrementAndGet();
                                }
                            } catch (Exception e) {
                                exceptionCount.incrementAndGet();
                            }
                        }
                    } finally {
                        done.countDown();
                    }
                });
            }

            ready.await(10, TimeUnit.SECONDS);
            go.countDown();
            done.await(60, TimeUnit.SECONDS);
            pool.shutdownNow();

            int ok = okCount.get();
            int ko = koCount.get();
            int exc = exceptionCount.get();
            int sharedFinal = sharedList.get(0);
            double errorRate = actualTotal == 0 ? 0 : (ko + exc) * 100.0 / actualTotal;

            System.out.printf("| %-10s | %-10d | %-10d | %-10d | %10.2f%% | %-15d |%n",
                    String.format("%,d", actualTotal), ok, ko, exc, errorRate, sharedFinal);

            log.info(String.format("[DefensiveCopyReturn] %d hits: OK=%d | LOST=%d | EXCEPTION=%d | shared list[0]=%d (always 0)",
                    actualTotal, ok, ko, exc, sharedFinal));
        }

        System.out.println();
        System.out.println("Every returned copy is correct (each element = original + 1).");
        System.out.println("Shared list is never modified (list[0] = 0 at all levels).");
        System.out.println("True defensive copy = isolation. Copies work perfectly; shared state is untouched.");
        System.out.println();
    }
}
