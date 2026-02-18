package com.epns.lambda.safe;

import com.epns.lambda.util.CollisionTestRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Copie défensive avant stream : on prend un synchronized sur la liste,
 * on fait new ArrayList<>(shared), puis on stream la copie librement.
 * 
 * La copie crée un snapshot isolé. Peu importe ce que les autres threads
 * font après — notre copie ne bouge plus. Le stream travaille sur des données figées.
 * 
 * Quand l'utiliser : quand on doit streamer/itérer longuement sans bloquer les écritures.
 */
@DisplayName("stream with defensive copy — SAFE")
public class DefensiveCopyTest {

    @Test
    void test() {
        var results = CollisionTestRunner.runAllScales("DefensiveCopy", totalHits -> {
            List<Integer> shared = CollisionTestRunner.freshList(100);
            return CollisionTestRunner.executeWorkload(totalHits, (threadId, iter, exceptions, corruptions) -> {
                try {
                    if (threadId % 2 == 0) {
                        List<Integer> snapshot;
                        synchronized (shared) { snapshot = new ArrayList<>(shared); }
                        List<Integer> result = snapshot.stream().map(x -> x * 2).collect(Collectors.toList());
                        if (result.contains(null)) corruptions.incrementAndGet();
                    } else {
                        synchronized (shared) {
                            shared.add(ThreadLocalRandom.current().nextInt(100));
                            if (shared.size() > 200) shared.subList(100, shared.size()).clear();
                        }
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
