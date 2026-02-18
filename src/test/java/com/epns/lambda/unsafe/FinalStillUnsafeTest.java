package com.epns.lambda.unsafe;

import com.epns.lambda.util.CollisionTestRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * final ArrayList — TOUJOURS unsafe.
 * 
 * final empêche de RÉASSIGNER la variable (shared = autreList ne compile pas).
 * Mais final ne protège absolument pas le CONTENU de l'objet.
 * shared.replaceAll(), shared.add(), shared.clear() fonctionnent exactement pareil.
 * C'est comme mettre un cadenas sur le panneau d'affichage mais laisser les
 * feuilles épinglées dessus accessibles à tout le monde.
 * 
 * Piège classique en entrevue : final ≠ immutable ≠ thread-safe.
 */
@DisplayName("final ArrayList is STILL unsafe")
public class FinalStillUnsafeTest {

    @Test
    void test() {
        var results = CollisionTestRunner.runAllScales("FinalStillUnsafe", totalHits -> {
            final ArrayList<Integer> shared = CollisionTestRunner.freshList(100);
            return CollisionTestRunner.executeWorkload(totalHits, (threadId, iter, exceptions, corruptions) -> {
                try {
                    shared.replaceAll(x -> x + 1);
                    Set<Integer> unique = new HashSet<>(shared);
                    if (unique.size() > 1) corruptions.incrementAndGet();
                } catch (Exception e) {
                    exceptions.incrementAndGet();
                }
            });
        });
        assertTrue(results.stream().anyMatch(r -> !r.timeout && r.totalCollisions() > 0),
                "Expected collisions at some scale level");
    }
}
