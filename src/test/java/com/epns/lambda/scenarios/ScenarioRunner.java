package com.epns.lambda.scenarios;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ScenarioRunner {

    @FunctionalInterface
    public interface TransformAction {
        void transform(List<Integer> list) throws Exception;
    }

    public record LevelResult(int hits, int expected, int actual, int exceptions, boolean timeout) {
        public int lost() { return expected - actual - exceptions; }
        public double errorRate() {
            if (timeout) return -1;
            int totalProblems = expected - actual;
            return hits == 0 ? 0 : Math.max(0, totalProblems) * 100.0 / hits;
        }
    }

    /**
     * Lance un niveau de test.
     * 
     * Métrique : valeur finale de list[0] vs valeur attendue.
     * Chaque thread fait N itérations de transform(list) qui incrémente chaque élément de 1.
     * Si tout est thread-safe, list[0] == totalHits à la fin.
     * La différence = incréments perdus par collision.
     */
    public static LevelResult runLevel(String scenarioName, List<Integer> list, int totalHits, int threads,
                                        TransformAction action) throws InterruptedException {
        // Reset list
        for (int i = 0; i < list.size(); i++) list.set(i, 0);

        int iterationsPerThread = totalHits / threads;
        int actualTotal = threads * iterationsPerThread;

        AtomicInteger exceptionCount = new AtomicInteger();
        AtomicInteger anomalyLogged = new AtomicInteger();
        int maxAnomalyLogs = totalHits <= 100 ? Integer.MAX_VALUE : 20;

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
                        try {
                            action.transform(list);
                        } catch (Exception e) {
                            int ec = exceptionCount.incrementAndGet();
                            if (anomalyLogged.getAndIncrement() < maxAnomalyLogs) {
                                System.out.printf("  [EXCEPTION] thread=%d iter=%d %s%n",
                                        threadId, i, e.getClass().getSimpleName());
                            }
                        }
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(10, TimeUnit.SECONDS);
        go.countDown(); // Tous partent en même temps
        boolean finished = done.await(60, TimeUnit.SECONDS);
        pool.shutdownNow();

        if (!finished) {
            System.out.println("  ⚠ TIMEOUT after 60s");
            return new LevelResult(actualTotal, actualTotal, 0, exceptionCount.get(), true);
        }

        int finalValue = list.get(0);
        return new LevelResult(actualTotal, actualTotal, finalValue, exceptionCount.get(), false);
    }

    public static void runScenario(String name, List<Integer> list, TransformAction action) throws InterruptedException {
        int[] hitLevels = {100, 1_000, 10_000, 100_000};
        int threads = 50;

        System.out.printf("%n=== Scénario: %s ===%n", name);
        System.out.printf("50 threads, list[0] commence à 0, chaque hit fait +1%n");
        System.out.printf("| %-10s | %-10s | %-10s | %-10s | %-10s | %-12s |%n",
                "Hits", "Attendu", "Obtenu", "Perdus", "Exceptions", "Taux perte");
        System.out.println("|------------|------------|------------|------------|------------|--------------|");

        for (int hits : hitLevels) {
            LevelResult r = runLevel(name, list, hits, threads, action);
            if (r.timeout()) {
                System.out.printf("| %-10d | %-10d | TIMEOUT    | -          | %-10d | TIMEOUT      |%n",
                        r.hits(), r.expected(), r.exceptions());
            } else {
                int lost = r.expected() - r.actual();
                System.out.printf("| %-10d | %-10d | %-10d | %-10d | %-10d | %10.2f%% |%n",
                        r.hits(), r.expected(), r.actual(), lost, r.exceptions(), r.errorRate());
            }
        }
        System.out.println();
    }
}
