package com.epns.lambda.scenarios;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ScenarioRunner {

    @FunctionalInterface
    public interface TransformAction {
        void transform(List<Integer> list) throws Exception;
    }

    public record LevelResult(int hits, int ok, int anomalies, int exceptions) {
        public double errorRate() {
            return hits == 0 ? 0 : (anomalies + exceptions) * 100.0 / hits;
        }
    }

    public static LevelResult runLevel(String scenarioName, List<Integer> list, int totalHits, int threads,
                                        TransformAction action) throws InterruptedException {
        // Reset list
        for (int i = 0; i < list.size(); i++) list.set(i, 0);

        int iterationsPerThread = totalHits / threads;
        boolean verbose = totalHits <= 100;
        int maxAnomalyLogs = verbose ? Integer.MAX_VALUE : 20;

        AtomicInteger okCount = new AtomicInteger();
        AtomicInteger anomalyCount = new AtomicInteger();
        AtomicInteger exceptionCount = new AtomicInteger();
        AtomicInteger anomalyLogged = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < iterationsPerThread; i++) {
                        try {
                            int before = list.get(0);
                            action.transform(list);
                            int after = list.get(0);

                            if (after != before + 1) {
                                int ac = anomalyCount.incrementAndGet();
                                if (anomalyLogged.getAndIncrement() < maxAnomalyLogs) {
                                    System.out.printf("  [ANOMALIE] thread=%d iter=%d before=%d after=%d%n",
                                            threadId, i, before, after);
                                }
                            } else {
                                okCount.incrementAndGet();
                                if (verbose && threadId < 10) {
                                    System.out.printf("  [OK] thread=%d iter=%d before=%d after=%d%n",
                                            threadId, i, before, after);
                                }
                            }
                        } catch (Exception e) {
                            int ec = exceptionCount.incrementAndGet();
                            if (anomalyLogged.getAndIncrement() < maxAnomalyLogs) {
                                System.out.printf("  [EXCEPTION] thread=%d iter=%d %s: %s%n",
                                        threadId, i, e.getClass().getSimpleName(), e.getMessage());
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(60, TimeUnit.SECONDS);
        pool.shutdownNow();
        if (!finished) {
            System.out.println("  ⚠ TIMEOUT after 60s");
        }

        LevelResult result = new LevelResult(totalHits, okCount.get(), anomalyCount.get(), exceptionCount.get());
        return result;
    }

    public static void runScenario(String name, List<Integer> list, TransformAction action) throws InterruptedException {
        int[] hitLevels = {100, 1_000, 10_000, 100_000};
        int threads = 50;

        System.out.printf("%n--- Scénario: %s ---%n", name);
        System.out.printf("| %-10s | %-10s | %-10s | %-10s | %-12s |%n",
                "Hits", "OK", "Anomalies", "Exceptions", "Taux erreur");
        System.out.println("|------------|------------|------------|------------|--------------|");

        for (int hits : hitLevels) {
            LevelResult r = runLevel(name, list, hits, threads, action);
            System.out.printf("| %-10d | %-10d | %-10d | %-10d | %10.2f%% |%n",
                    r.hits(), r.ok(), r.anomalies(), r.exceptions(), r.errorRate());
        }
        System.out.println();
    }
}
