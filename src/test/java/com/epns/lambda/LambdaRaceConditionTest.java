package com.epns.lambda;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Démonstration des race conditions avec les lambdas sur ArrayList.
 *
 * ArrayList n'est PAS thread-safe. Quand on utilise des lambdas (replaceAll, forEach, removeIf, sort)
 * depuis plusieurs threads simultanément, on obtient :
 * - ConcurrentModificationException
 * - Corruption silencieuse des données
 * - Comportement imprévisible
 *
 * Ces tests PROUVENT le problème et montrent les solutions.
 */
public class LambdaRaceConditionTest {

    private static final int THREAD_COUNT = 10;
    private static final int LIST_SIZE = 1000;
    private static final int REPEAT_COUNT = 5; // Répéter pour fiabilité

    // ========================================================================
    // HELPER : crée une ArrayList pré-remplie
    // ========================================================================
    private ArrayList<Integer> createList() {
        ArrayList<Integer> list = new ArrayList<>();
        IntStream.range(0, LIST_SIZE).forEach(list::add);
        return list;
    }

    // ========================================================================
    // CAS DANGEREUX : replaceAll
    // ========================================================================

    /**
     * replaceAll applique une lambda à chaque élément.
     * Si un autre thread modifie la liste en même temps → ConcurrentModificationException.
     *
     * On lance N threads qui font replaceAll pendant qu'un autre thread ajoute des éléments.
     */
    @RepeatedTest(REPEAT_COUNT)
    @DisplayName("UNSAFE - replaceAll lance ConcurrentModificationException")
    void testReplaceAllRaceCondition_UNSAFE() throws InterruptedException {
        ArrayList<Integer> list = createList();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean cmeDetected = new AtomicBoolean(false);

        // Thread 1 : replaceAll en boucle (applique x -> x * 2)
        Thread writer = new Thread(() -> {
            try {
                latch.await();
                for (int i = 0; i < 100; i++) {
                    try {
                        list.replaceAll(x -> x * 2);
                    } catch (ConcurrentModificationException e) {
                        cmeDetected.set(true);
                        return;
                    }
                    // Sleep pour laisser le temps aux collisions
                    Thread.sleep(0, 1);
                }
            } catch (InterruptedException ignored) {}
        });

        // Thread 2 : ajoute et supprime des éléments en parallèle
        Thread mutator = new Thread(() -> {
            try {
                latch.await();
                for (int i = 0; i < 200; i++) {
                    list.add(999);
                    Thread.sleep(0, 1);
                    if (!list.isEmpty()) list.remove(list.size() - 1);
                }
            } catch (ConcurrentModificationException e) {
                cmeDetected.set(true);
            } catch (Exception ignored) {}
        });

        writer.start();
        mutator.start();
        latch.countDown(); // Go !

        writer.join(5000);
        mutator.join(5000);

        // On s'attend à détecter une ConcurrentModificationException
        assertTrue(cmeDetected.get(),
                "Une ConcurrentModificationException aurait dû être détectée ! " +
                "ArrayList.replaceAll n'est pas thread-safe.");
    }

    // ========================================================================
    // CAS DANGEREUX : forEach + add
    // ========================================================================

    /**
     * forEach itère avec une lambda. Si on modifie la liste pendant l'itération
     * (même depuis le même thread via la lambda), on obtient une CME.
     * En multi-thread, c'est encore pire.
     */
    @RepeatedTest(REPEAT_COUNT)
    @DisplayName("UNSAFE - forEach + add en parallèle = ConcurrentModificationException")
    void testForEachPlusAddRaceCondition_UNSAFE() throws InterruptedException {
        ArrayList<Integer> list = createList();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean cmeDetected = new AtomicBoolean(false);

        // Thread 1 : forEach qui lit chaque élément
        Thread reader = new Thread(() -> {
            try {
                latch.await();
                for (int i = 0; i < 100; i++) {
                    try {
                        list.forEach(x -> {
                            // Simule un traitement lent pour maximiser la fenêtre de collision
                            if (x % 100 == 0) {
                                try { Thread.sleep(0, 100); } catch (InterruptedException ignored) {}
                            }
                        });
                    } catch (ConcurrentModificationException e) {
                        cmeDetected.set(true);
                        return;
                    }
                }
            } catch (InterruptedException ignored) {}
        });

        // Thread 2 : ajoute des éléments pendant le forEach
        Thread adder = new Thread(() -> {
            try {
                latch.await();
                for (int i = 0; i < 500; i++) {
                    list.add(i);
                    Thread.sleep(0, 1);
                }
            } catch (Exception ignored) {}
        });

        reader.start();
        adder.start();
        latch.countDown();

        reader.join(5000);
        adder.join(5000);

        assertTrue(cmeDetected.get(),
                "forEach + add concurrent aurait dû lancer une ConcurrentModificationException !");
    }

    // ========================================================================
    // CAS DANGEREUX : removeIf
    // ========================================================================

    /**
     * removeIf utilise une lambda pour filtrer. En multi-thread,
     * la structure interne de l'ArrayList peut être corrompue.
     */
    @RepeatedTest(REPEAT_COUNT)
    @DisplayName("UNSAFE - removeIf en parallèle = CME ou corruption")
    void testRemoveIfRaceCondition_UNSAFE() throws InterruptedException {
        ArrayList<Integer> list = createList();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean problemDetected = new AtomicBoolean(false);

        // Plusieurs threads font removeIf en même temps
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);

        for (int t = 0; t < THREAD_COUNT; t++) {
            final int threadId = t;
            pool.submit(() -> {
                try {
                    latch.await();
                    for (int i = 0; i < 50; i++) {
                        try {
                            // Chaque thread supprime les multiples de son ID
                            list.removeIf(x -> x % (threadId + 2) == 0);
                            // Et en rajoute pour maintenir du contenu
                            list.add(threadId * 1000 + i);
                            Thread.sleep(0, 1);
                        } catch (ConcurrentModificationException | ArrayIndexOutOfBoundsException |
                                 NullPointerException e) {
                            // NullPointerException et ArrayIndexOutOfBoundsException = corruption interne !
                            problemDetected.set(true);
                            return;
                        }
                    }
                } catch (InterruptedException ignored) {}
            });
        }

        latch.countDown();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        assertTrue(problemDetected.get(),
                "removeIf concurrent aurait dû provoquer une erreur ! " +
                "La liste est corrompue ou une CME a été lancée.");
    }

    // ========================================================================
    // CAS DANGEREUX : sort
    // ========================================================================

    /**
     * sort avec un Comparator lambda. En multi-thread avec des modifications
     * concurrentes, le tri peut corrompre la liste ou lancer une exception.
     */
    @RepeatedTest(REPEAT_COUNT)
    @DisplayName("UNSAFE - sort concurrent = CME ou résultat incohérent")
    void testSortRaceCondition_UNSAFE() throws InterruptedException {
        ArrayList<Integer> list = createList();
        // Mélanger pour que le sort ait du travail
        Collections.shuffle(list);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean problemDetected = new AtomicBoolean(false);

        // Thread 1 : sort en boucle
        Thread sorter = new Thread(() -> {
            try {
                latch.await();
                for (int i = 0; i < 100; i++) {
                    try {
                        // Lambda de tri : ordre naturel avec un petit sleep pour élargir la fenêtre
                        list.sort((a, b) -> {
                            try { Thread.sleep(0, 1); } catch (InterruptedException ignored) {}
                            return Integer.compare(a, b);
                        });
                    } catch (ConcurrentModificationException | ArrayIndexOutOfBoundsException |
                             NullPointerException | IllegalArgumentException e) {
                        problemDetected.set(true);
                        return;
                    }
                }
            } catch (InterruptedException ignored) {}
        });

        // Thread 2 : modifie la liste pendant le tri
        Thread mutator = new Thread(() -> {
            try {
                latch.await();
                for (int i = 0; i < 300; i++) {
                    try {
                        if (!list.isEmpty()) {
                            list.set(0, -i); // Modifie un élément pendant le tri
                            list.add(i);
                        }
                    } catch (Exception e) {
                        problemDetected.set(true);
                        return;
                    }
                    Thread.sleep(0, 1);
                }
            } catch (InterruptedException ignored) {}
        });

        sorter.start();
        mutator.start();
        latch.countDown();

        sorter.join(10000);
        mutator.join(10000);

        assertTrue(problemDetected.get(),
                "sort concurrent aurait dû provoquer une erreur ou une incohérence !");
    }

    // ========================================================================
    // CAS SAFE : CopyOnWriteArrayList
    // ========================================================================

    /**
     * CopyOnWriteArrayList crée une copie du tableau interne à chaque modification.
     * Les itérations (forEach, replaceAll) travaillent sur un snapshot → pas de CME.
     *
     * ATTENTION : performant seulement si les lectures >> écritures.
     */
    @Test
    @DisplayName("SAFE - CopyOnWriteArrayList supporte forEach + add concurrent")
    void testForEachWithCopyOnWriteArrayList_SAFE() throws InterruptedException {
        CopyOnWriteArrayList<Integer> list = new CopyOnWriteArrayList<>();
        IntStream.range(0, LIST_SIZE).forEach(list::add);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean cmeDetected = new AtomicBoolean(false);
        AtomicReference<Exception> unexpectedException = new AtomicReference<>();

        Thread reader = new Thread(() -> {
            try {
                latch.await();
                for (int i = 0; i < 50; i++) {
                    try {
                        // forEach sur CopyOnWriteArrayList = safe, itère sur un snapshot
                        list.forEach(x -> {
                            if (x % 100 == 0) {
                                try { Thread.sleep(0, 100); } catch (InterruptedException ignored) {}
                            }
                        });
                    } catch (ConcurrentModificationException e) {
                        cmeDetected.set(true);
                    }
                }
            } catch (Exception e) {
                unexpectedException.set(e);
            }
        });

        Thread adder = new Thread(() -> {
            try {
                latch.await();
                for (int i = 0; i < 200; i++) {
                    list.add(i);
                    Thread.sleep(0, 1);
                }
            } catch (Exception e) {
                unexpectedException.set(e);
            }
        });

        reader.start();
        adder.start();
        latch.countDown();

        reader.join(5000);
        adder.join(5000);

        assertNull(unexpectedException.get(), "Aucune exception inattendue ne devrait survenir");
        assertFalse(cmeDetected.get(),
                "CopyOnWriteArrayList ne devrait JAMAIS lancer ConcurrentModificationException !");
    }

    // ========================================================================
    // CAS SAFE : synchronized
    // ========================================================================

    /**
     * Synchroniser manuellement sur la liste protège toutes les opérations.
     * C'est la solution la plus simple et la plus flexible.
     */
    @Test
    @DisplayName("SAFE - synchronized protège replaceAll + modifications concurrentes")
    void testReplaceAllWithSynchronized_SAFE() throws InterruptedException {
        ArrayList<Integer> list = createList();
        // On utilise la liste elle-même comme monitor
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean cmeDetected = new AtomicBoolean(false);

        Thread writer = new Thread(() -> {
            try {
                latch.await();
                for (int i = 0; i < 50; i++) {
                    synchronized (list) {
                        // Toute l'opération replaceAll est protégée
                        list.replaceAll(x -> x + 1);
                    }
                    Thread.sleep(0, 1);
                }
            } catch (ConcurrentModificationException e) {
                cmeDetected.set(true);
            } catch (InterruptedException ignored) {}
        });

        Thread mutator = new Thread(() -> {
            try {
                latch.await();
                for (int i = 0; i < 100; i++) {
                    synchronized (list) {
                        list.add(999);
                        if (list.size() > LIST_SIZE + 10) {
                            list.remove(list.size() - 1);
                        }
                    }
                    Thread.sleep(0, 1);
                }
            } catch (ConcurrentModificationException e) {
                cmeDetected.set(true);
            } catch (InterruptedException ignored) {}
        });

        writer.start();
        mutator.start();
        latch.countDown();

        writer.join(5000);
        mutator.join(5000);

        assertFalse(cmeDetected.get(),
                "Avec synchronized, aucune ConcurrentModificationException ne devrait survenir !");
    }

    /**
     * synchronized protège aussi removeIf + sort en parallèle.
     */
    @Test
    @DisplayName("SAFE - synchronized protège removeIf + sort concurrents")
    void testRemoveIfAndSortWithSynchronized_SAFE() throws InterruptedException {
        ArrayList<Integer> list = createList();
        Collections.shuffle(list);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean problemDetected = new AtomicBoolean(false);

        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);

        for (int t = 0; t < THREAD_COUNT; t++) {
            final int threadId = t;
            pool.submit(() -> {
                try {
                    latch.await();
                    for (int i = 0; i < 20; i++) {
                        synchronized (list) {
                            if (threadId % 2 == 0) {
                                list.removeIf(x -> x % 7 == 0);
                                // Remettre des éléments pour ne pas vider la liste
                                for (int j = 0; j < 10; j++) list.add(threadId * 100 + j);
                            } else {
                                list.sort(Integer::compareTo);
                            }
                        }
                        Thread.sleep(0, 1);
                    }
                } catch (Exception e) {
                    problemDetected.set(true);
                }
            });
        }

        latch.countDown();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        assertFalse(problemDetected.get(),
                "Avec synchronized, removeIf et sort concurrents sont safe !");
    }
}
