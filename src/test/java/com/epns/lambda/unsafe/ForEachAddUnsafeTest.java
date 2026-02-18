package com.epns.lambda.unsafe;

import com.epns.lambda.util.CollisionTestRunner;
import com.epns.lambda.util.CollisionTestRunner.ScaleResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * forEach() sur une liste pendant qu'un autre thread fait add().
 * 
 * forEach utilise un itérateur interne qui vérifie modCount à chaque étape.
 * Quand add modifie la liste pendant l'itération, le modCount change et
 * l'itérateur lance ConcurrentModificationException. Parfois add se glisse
 * entre deux vérifications et le résultat est une liste corrompue.
 */
@DisplayName("forEach + add on same list — UNSAFE")
public class ForEachAddUnsafeTest {

    @Test
    void test() {
        var results = CollisionTestRunner.runAllScales("ForEachAddUnsafe", totalHits -> {
            List<Integer> shared = CollisionTestRunner.freshList(50);
            return CollisionTestRunner.executeWorkload(totalHits, (threadId, iter, exceptions, corruptions) -> {
                try {
                    if (threadId % 2 == 0) {
                        shared.forEach(x -> { if (x == 3) Thread.yield(); });
                    } else {
                        shared.add(ThreadLocalRandom.current().nextInt(100));
                        if (shared.size() > 200) {
                            try { shared.subList(50, shared.size()).clear(); } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception e) {
                    exceptions.incrementAndGet();
                }
            });
        });
        assertTrue(results.stream().anyMatch(r -> !r.timeout && r.totalCollisions() > 0),
                "Expected collisions at some scale level");
    }
}
