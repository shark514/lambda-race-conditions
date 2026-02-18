package com.epns.lambda.unsafe;

import com.epns.lambda.util.CollisionTestRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * sort() avec Comparator lambda + set() concurrents.
 * 
 * sort réorganise physiquement les éléments dans l'array interne (O(n log n)
 * comparaisons et swaps). Pendant ce tri, un autre thread modifie un élément
 * ou lance son propre sort. Résultat : éléments dupliqués, perdus, ou ordre
 * incohérent. La corruption est SILENCIEUSE — pas d'exception, juste des
 * données fausses.
 */
@DisplayName("sort with comparator lambda — UNSAFE")
public class SortUnsafeTest {

    @Test
    void test() {
        var results = CollisionTestRunner.runAllScales("SortUnsafe", totalHits -> {
            List<Integer> shared = CollisionTestRunner.freshList(100);
            return CollisionTestRunner.executeWorkload(totalHits, (threadId, iter, exceptions, corruptions) -> {
                try {
                    shared.sort(Comparator.reverseOrder());
                    shared.set(0, ThreadLocalRandom.current().nextInt(1000));
                    boolean sorted = true;
                    for (int j = 0; j < shared.size() - 1; j++) {
                        if (shared.get(j) < shared.get(j + 1)) { sorted = false; break; }
                    }
                    if (!sorted) corruptions.incrementAndGet();
                } catch (Exception e) {
                    exceptions.incrementAndGet();
                }
            });
        });
        assertTrue(results.stream().anyMatch(r -> !r.timeout && r.totalCollisions() > 0),
                "Expected collisions at some scale level");
    }
}
