package com.epns.lambda.unsafe;

import com.epns.lambda.util.CollisionTestRunner;
import com.epns.lambda.util.CollisionTestRunner.ScaleResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * replaceAll() sur une ArrayList partagée entre threads.
 * 
 * replaceAll itère sur chaque élément et le remplace. Pendant cette itération,
 * un autre thread fait la même chose. Java détecte parfois la modification
 * concurrente via modCount (ConcurrentModificationException), mais quand deux
 * threads modifient modCount en même temps, la détection saute — corruption silencieuse.
 */
@DisplayName("replaceAll on shared ArrayList — UNSAFE")
public class ReplaceAllUnsafeTest {

    @Test
    void test() {
        var results = CollisionTestRunner.runAllScales("ReplaceAllUnsafe", totalHits -> {
            List<Integer> shared = CollisionTestRunner.freshList(100);
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
