package com.epns.lambda.unsafe;

import com.epns.lambda.util.CollisionTestRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * sort() with Comparator lambda + concurrent set().
 * 
 * sort physically rearranges elements in the internal array (O(n log n)
 * comparisons and swaps). During this sort, another thread modifies an element
 * or launches its own sort. Result: duplicated elements, lost elements, or
 * inconsistent ordering. The corruption is SILENT — no exception, just
 * wrong data.
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
