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
 * without writing back to the original. This is what defensive copy
 * is actually supposed to do.
 *
 * Result: the original list is NEVER modified → 100% "loss" from the
 * shared list's perspective, but 0% corruption.
 *
 * This proves that true defensive copy is incompatible with shared
 * mutable state. You either:
 * 1. Write back → race condition (DefensiveCopyTest: 64% loss)
 * 2. Don't write back → no mutation at all (this test)
 * 3. Synchronize → works but you don't need the copy (SynchronizedListTest)
 */
public class DefensiveCopyReturnTest {

    private static final Logger log = Logger.getLogger(DefensiveCopyReturnTest.class.getName());

    @FunctionalInterface
    interface CopyTransform {
        List<Integer> transform(List<Integer> list);
    }

    @Test
    public void testDefensiveCopyReturn() throws InterruptedException {
        ArrayList<Integer> sharedList = new ArrayList<>(Collections.nCopies(10, 0));

        CopyTransform safeTransform = (list) -> {
            ArrayList<Integer> copy = new ArrayList<>(list);
            copy.replaceAll(x -> x + 1);
            return copy; // return the copy — never touch the original
        };

        int[] hitLevels = {100, 1_000, 10_000, 100_000};
        String hitsParam = System.getProperty("hits");
        if (hitsParam != null && !hitsParam.isEmpty()) {
            try {
                hitLevels = new int[]{Integer.parseInt(hitsParam)};
            } catch (NumberFormatException ignored) {}
        }

        int threads = 50;

        System.out.printf("%n=== Scenario: DefensiveCopyReturn ===%n");
        System.out.printf("50 threads — each copies, increments, RETURNS copy (never writes back)%n%n");

        System.out.printf("| %-10s | %-15s | %-15s | %-12s |%n",
                "Hits", "Shared final", "Expected", "Shared loss");
        System.out.println("|------------|-----------------|-----------------|--------------|");

        for (int totalHits : hitLevels) {
            // Reset
            for (int i = 0; i < sharedList.size(); i++) sharedList.set(i, 0);

            int iterationsPerThread = Math.max(1, totalHits / threads);
            int actualTotal = threads * iterationsPerThread;

            AtomicInteger localSum = new AtomicInteger(0);

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
                            List<Integer> result = safeTransform.transform(sharedList);
                            // Each thread gets its own result — never shared
                            localSum.addAndGet(result.get(0));
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

            int sharedFinal = sharedList.get(0);

            System.out.printf("| %-10s | %-15d | %-15d | %10.2f%% |%n",
                    String.format("%,d", actualTotal),
                    sharedFinal,
                    actualTotal,
                    (actualTotal - sharedFinal) * 100.0 / actualTotal);

            log.info(String.format("[DefensiveCopyReturn] %d hits: shared list[0]=%d (expected %d) → %d lost = %.1f%%",
                    actualTotal, sharedFinal, actualTotal,
                    actualTotal - sharedFinal,
                    (actualTotal - sharedFinal) * 100.0 / actualTotal));
            log.info(String.format("[DefensiveCopyReturn] Sum of local copies: %d (proves threads DID work, just not shared)",
                    localSum.get()));
        }

        System.out.println();
        System.out.println("Conclusion: shared list is NEVER modified (0 increments landed).");
        System.out.println("Each thread worked on its own copy → zero corruption, zero shared mutation.");
        System.out.println("True defensive copy = isolation. If you need shared mutation, use synchronization.");
        System.out.println();
    }
}
