package com.epns.lambda.safe;

import com.epns.lambda.util.CollisionTestRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * synchronized block manuel avec un objet lock dédié.
 * Le verrou garantit l'exclusion mutuelle : un seul thread à la fois.
 * 
 * Avantages : contrôle total sur la granularité, peut protéger plusieurs
 * opérations atomiquement.
 * Inconvénients : verbeux, si on oublie un synchronized quelque part → unsafe.
 * 
 * Quand l'utiliser : quand on veut un contrôle fin et qu'on est discipliné.
 */
@DisplayName("replaceAll with synchronized block — SAFE")
public class SynchronizedBlockTest {

    @Test
    void test() {
        var results = CollisionTestRunner.runAllScales("SynchronizedBlock", totalHits -> {
            List<Integer> shared = CollisionTestRunner.freshList(100);
            Object lock = new Object();
            return CollisionTestRunner.executeWorkload(totalHits, (threadId, iter, exceptions, corruptions) -> {
                try {
                    synchronized (lock) {
                        shared.replaceAll(x -> x + 1);
                        if (shared.size() != 100) corruptions.incrementAndGet();
                    }
                } catch (Exception e) {
                    exceptions.incrementAndGet();
                }
            });
        });
        results.stream().filter(r -> !r.timeout).forEach(r ->
                assertEquals(0, r.totalCollisions(), "Expected 0 collisions at " + r.totalHits + " hits"));
    }
}
