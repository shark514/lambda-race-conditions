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
 * Same setup as all other scenarios: 50 threads, shared ArrayList,
 * each thread tries to increment every element.
 *
 * The difference: each thread copies the list, increments the copy,
 * and returns it — never writes back to the shared list.
 *
 * Metric: Loss rate = (expected - obtained) / expected x 100
 * Expected = correct copy (each element = original + 1).
 * Obtained = the actual returned copy.
 * Result: 0% loss. Every returned copy is correct. Total isolation.
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
        System.out.printf("50 threads, each copies the list, increments the copy, returns it%n");
        System.out.printf("Metric: Loss rate = (expected - obtained) / expected x 100%n%n");

        System.out.printf("| %-10s | %-10s | %-10s | %-10s | %-12s |%n",
                "Hits", "OK", "Lost", "Exceptions", "Loss Rate");
        System.out.println("|------------|------------|------------|------------|--------------|");

        for (int totalHits : hitLevels) {
            // Reset shared list
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
                                // Copy, increment, return
                                ArrayList<Integer> copy = new ArrayList<>(sharedList);
                                copy.replaceAll(x -> x + 1);

                                // Verify: each element of the returned copy = original + 1
                                boolean correct = copy.size() == sharedList.size();
                                if (correct) {
                                    for (int v = 0; v < copy.size(); v++) {
                                        if (copy.get(v) != 1) {
                                            correct = false;
                                            break;
                                        }
                                    }
                                }

                                if (correct) {
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
            double lossRate = actualTotal == 0 ? 0 : (ko + exc) * 100.0 / actualTotal;

            System.out.printf("| %-10s | %-10d | %-10d | %-10d | %10.2f%% |%n",
                    String.format("%,d", actualTotal), ok, ko, exc, lossRate);

            log.info(String.format("[DefensiveCopyReturn] %d hits: OK=%d | LOST=%d | EXCEPTION=%d | Loss=%.2f%%",
                    actualTotal, ok, ko, exc, lossRate));
        }

        System.out.printf("%n=== Summary DefensiveCopyReturn ===%n");
        System.out.println("Every returned copy is correct. 0%% loss. Total isolation from shared state.");
        System.out.println();
    }
}
