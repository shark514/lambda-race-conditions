package com.epns.lambda.scenarios;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class ScenarioRunner {

    private static final Logger log = Logger.getLogger(ScenarioRunner.class.getName());

    @FunctionalInterface
    public interface TransformAction {
        void transform(List<Integer> list) throws Exception;
    }

    public record LevelResult(int hits, int ok, int ko, int exceptions) {
        public double errorRate() {
            return hits == 0 ? 0 : (ko + exceptions) * 100.0 / hits;
        }
    }

    public static LevelResult runLevel(String scenarioName, List<Integer> list, int totalHits, int threads,
                                        TransformAction action) throws InterruptedException {
        // Reset list
        for (int i = 0; i < list.size(); i++) list.set(i, 0);

        int iterationsPerThread = Math.max(1, totalHits / threads);
        int actualTotal = threads * iterationsPerThread;

        AtomicInteger okCount = new AtomicInteger();
        AtomicInteger koCount = new AtomicInteger();
        AtomicInteger exceptionCount = new AtomicInteger();
        AtomicInteger hitCounter = new AtomicInteger();

        // For large volumes, log the first 30 + anomalies (max 30)
        int maxVerboseLogs = 30;
        int maxAnomalyLogs = 30;
        AtomicInteger anomalyLogged = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            pool.submit(() -> {
                ready.countDown();
                try { go.await(); } catch (InterruptedException e) { return; }
                try {
                    for (int i = 0; i < iterationsPerThread; i++) {
                        int hitNum = hitCounter.incrementAndGet();
                        try {
                            int before = list.get(0);
                            int expected = before + 1;
                            action.transform(list);
                            int obtained = list.get(0);

                            if (obtained >= expected) {
                                okCount.incrementAndGet();
                                if (hitNum <= maxVerboseLogs) {
                                    log.info(String.format("[%s] Hit #%d (thread-%d): expected=%d obtained=%d ✅ OK",
                                            scenarioName, hitNum, threadId, expected, obtained));
                                }
                            } else {
                                koCount.incrementAndGet();
                                if (anomalyLogged.getAndIncrement() < maxAnomalyLogs) {
                                    log.warning(String.format("[%s] Hit #%d (thread-%d): expected=%d obtained=%d ❌ LOST",
                                            scenarioName, hitNum, threadId, expected, obtained));
                                }
                            }
                        } catch (Exception e) {
                            exceptionCount.incrementAndGet();
                            if (anomalyLogged.getAndIncrement() < maxAnomalyLogs) {
                                log.severe(String.format("[%s] Hit #%d (thread-%d): ❌ EXCEPTION %s",
                                        scenarioName, hitNum, threadId, e.getClass().getSimpleName()));
                            }
                        }
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(10, TimeUnit.SECONDS);
        go.countDown();
        boolean finished = done.await(60, TimeUnit.SECONDS);
        pool.shutdownNow();

        if (!finished) {
            log.severe(String.format("[%s] ⚠ TIMEOUT after 60s", scenarioName));
        }

        int ok = okCount.get();
        int ko = koCount.get();
        int exc = exceptionCount.get();

        // Log the final count
        log.info(String.format("[%s] %d hits: OK=%d | LOST=%d | EXCEPTION=%d",
                scenarioName, actualTotal, ok, ko, exc));

        // Final value of list[0] vs expected
        int finalValue = list.get(0);
        log.info(String.format("[%s] Final value list[0]=%d (expected=%d) → %d lost increments",
                scenarioName, finalValue, actualTotal, actualTotal - finalValue));

        return new LevelResult(actualTotal, ok, ko, exc);
    }

    public static void runScenario(String name, List<Integer> list, TransformAction action) throws InterruptedException {
        int[] hitLevels;
        String hitsParam = System.getProperty("hits");
        if (hitsParam != null && !hitsParam.isEmpty()) {
            try {
                hitLevels = new int[]{Integer.parseInt(hitsParam)};
            } catch (NumberFormatException e) {
                hitLevels = new int[]{100, 1_000, 10_000, 100_000};
            }
        } else {
            hitLevels = new int[]{100, 1_000, 10_000, 100_000};
        }
        int threads = 50;

        System.out.printf("%n=== Scenario: %s ===%n", name);
        System.out.printf("50 threads, list[0]=0, each hit does +1%n%n");

        LevelResult[] results = new LevelResult[hitLevels.length];

        for (int i = 0; i < hitLevels.length; i++) {
            System.out.printf("--- %,d hits ---%n", hitLevels[i]);
            results[i] = runLevel(name, list, hitLevels[i], threads, action);
            System.out.println();
        }

        // Summary table
        System.out.printf("=== Summary %s ===%n", name);
        System.out.printf("| %-10s | %-10s | %-10s | %-10s | %-12s |%n",
                "Hits", "OK", "Lost", "Exceptions", "Error Rate");
        System.out.println("|------------|------------|------------|------------|--------------|");
        for (LevelResult r : results) {
            System.out.printf("| %-10d | %-10d | %-10d | %-10d | %10.2f%% |%n",
                    r.hits(), r.ok(), r.ko(), r.exceptions(), r.errorRate());
        }
        System.out.println();
    }
}
