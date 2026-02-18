package com.epns.lambda.unsafe;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Démontre la corruption de données avec replaceAll sur ArrayList non synchronisée.
 *
 * Chaque thread fait :
 *   1. Lire list[0] → avant
 *   2. list.replaceAll(x -> x + 1)
 *   3. Lire list[0] → après
 *   4. Log : "Thread-N : avant=X après=Y" — si après != avant+1 → ANOMALIE
 *
 * Le tableau final = résumé des logs (OK vs ANOMALIE par niveau de hits).
 */
public class ReplaceAllDataCorruptionTest {

    private static final int POOL_SIZE = 50;
    private static final int[] HIT_COUNTS = {100, 1_000, 10_000, 100_000};
    private static final long TIMEOUT_SECONDS = 60;

    @Test
    void replaceAllCorruptionAtAllScales() {
        System.out.println();
        System.out.println("=== replaceAll Data Corruption Test ===");
        System.out.println("Chaque hit : lire list[0], replaceAll(x -> x+1), relire list[0]");
        System.out.println("Si après != avant+1 → ANOMALIE (collision ou exception)");
        System.out.println();

        boolean anyCorruption = false;

        // Stocker les résumés pour le tableau final
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

            // On log les premiers hits pour montrer le format
            boolean verbose = (totalHits <= 100);
            // Pour les gros niveaux, on log seulement les 20 premières anomalies
            AtomicInteger anomaliesLogged = new AtomicInteger(0);

            ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE);
            CountDownLatch ready = new CountDownLatch(POOL_SIZE);
            CountDownLatch go = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(POOL_SIZE);

            System.out.printf("--- %,d hits (%d threads × %d itérations) ---%n", actualTotal, POOL_SIZE, itersPerThread);

            for (int t = 0; t < POOL_SIZE; t++) {
                final int threadId = t;
                pool.submit(() -> {
                    ready.countDown();
                    try { go.await(); } catch (InterruptedException e) { return; }

                    for (int i = 0; i < itersPerThread; i++) {
                        try {
                            int avant = list.get(0);
                            list.replaceAll(x -> x + 1);
                            int apres = list.get(0);

                            if (apres == avant + 1) {
                                okCount.incrementAndGet();
                                if (verbose && threadId < 3) {
                                    System.out.printf("  Thread-%02d iter %d : avant=%-6d après=%-6d ✅ OK%n",
                                            threadId, i, avant, apres);
                                }
                            } else {
                                anomalyCount.incrementAndGet();
                                if (verbose || anomaliesLogged.incrementAndGet() <= 20) {
                                    System.out.printf("  Thread-%02d iter %d : avant=%-6d après=%-6d ⚠️ ANOMALIE (attendu %d)%n",
                                            threadId, i, avant, apres, avant + 1);
                                }
                            }
                        } catch (Exception e) {
                            exceptionCount.incrementAndGet();
                            if (verbose || anomaliesLogged.incrementAndGet() <= 20) {
                                System.out.printf("  Thread-%02d iter %d : ❌ EXCEPTION %s%n",
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

        // Tableau récapitulatif
        System.out.println("=== RÉCAPITULATIF ===");
        System.out.println("| Hits      | OK        | Anomalies | Exceptions| Taux erreur    |");
        System.out.println("|-----------|-----------|-----------|-----------|----------------|");
        for (String s : summaries) {
            System.out.println(s);
        }
        System.out.println();

        assertTrue(anyCorruption, "Au moins un niveau devrait montrer de la corruption");
    }
}
