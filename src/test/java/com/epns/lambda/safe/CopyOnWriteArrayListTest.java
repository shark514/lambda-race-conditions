package com.epns.lambda.safe;

import com.epns.lambda.util.CollisionTestRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CopyOnWriteArrayList crée une COPIE de l'array interne à chaque modification.
 * Chaque replaceAll travaille sur sa propre copie. Les lectures ne sont jamais
 * bloquées et ne voient jamais un état intermédiaire.
 * 
 * Avantages : aucune synchronisation manuelle, lectures très rapides.
 * Inconvénients : chaque écriture copie l'array entier → O(n) par modification.
 * 
 * Quand l'utiliser : lectures fréquentes, écritures rares (listeners, config).
 */
@DisplayName("replaceAll on CopyOnWriteArrayList — SAFE")
public class CopyOnWriteArrayListTest {

    @Test
    void test() {
        var results = CollisionTestRunner.runAllScales("CopyOnWriteArrayList", totalHits -> {
            CopyOnWriteArrayList<Integer> shared = new CopyOnWriteArrayList<>(CollisionTestRunner.freshList(20));
            return CollisionTestRunner.executeWorkload(totalHits, (threadId, iter, exceptions, corruptions) -> {
                try {
                    shared.replaceAll(x -> x + 1);
                } catch (Exception e) {
                    exceptions.incrementAndGet();
                }
            });
        });
        results.stream().filter(r -> !r.timeout).forEach(r ->
                assertEquals(0, r.totalCollisions(), "Expected 0 collisions at " + r.totalHits + " hits"));
    }
}
