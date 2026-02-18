package com.epns.lambda.safe;

import com.epns.lambda.util.CollisionTestRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Collections.synchronizedList wrappe chaque méthode dans un synchronized.
 * Mais pour les opérations composées (itération, replaceAll), il faut
 * SYNCHRONISER MANUELLEMENT sur la liste. synchronized(shared) garantit
 * qu'un seul thread exécute replaceAll à la fois.
 * 
 * Attention : synchronizedList SEUL ne suffit PAS pour replaceAll/forEach/sort !
 * 
 * Quand l'utiliser : drop-in replacement rapide quand on contrôle tous les accès.
 */
@DisplayName("replaceAll on synchronizedList — SAFE")
public class SynchronizedListTest {

    @Test
    void test() {
        var results = CollisionTestRunner.runAllScales("SynchronizedList", totalHits -> {
            List<Integer> shared = Collections.synchronizedList(CollisionTestRunner.freshList(100));
            return CollisionTestRunner.executeWorkload(totalHits, (threadId, iter, exceptions, corruptions) -> {
                try {
                    synchronized (shared) { shared.replaceAll(x -> x + 1); }
                } catch (Exception e) {
                    exceptions.incrementAndGet();
                }
            });
        });
        results.stream().filter(r -> !r.timeout).forEach(r ->
                assertEquals(0, r.totalCollisions(), "Expected 0 collisions at " + r.totalHits + " hits"));
    }
}
