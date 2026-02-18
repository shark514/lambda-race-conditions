package com.epns.lambda.unsafe;

import com.epns.lambda.util.CollisionTestRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * removeIf() + addAll() concurrently on the same ArrayList.
 * 
 * removeIf iterates through the list and removes elements matching the predicate.
 * Meanwhile, addAll adds elements. The internal array can be resized while
 * removeIf traverses it — ArrayIndexOutOfBoundsException,
 * ConcurrentModificationException, or nulls in the list.
 */
@DisplayName("removeIf with lambda — UNSAFE")
public class RemoveIfUnsafeTest {

    @Test
    void test() {
        var results = CollisionTestRunner.runAllScales("RemoveIfUnsafe", totalHits -> {
            List<Integer> shared = CollisionTestRunner.freshList(100);
            return CollisionTestRunner.executeWorkload(totalHits, (threadId, iter, exceptions, corruptions) -> {
                try {
                    shared.removeIf(x -> x % 2 == 0);
                    shared.addAll(Arrays.asList(2, 4, 6, 8, 10));
                    if (shared.contains(null) || shared.size() > 10000) corruptions.incrementAndGet();
                } catch (Exception e) {
                    exceptions.incrementAndGet();
                }
            });
        });
        assertTrue(results.stream().anyMatch(r -> !r.timeout && r.totalCollisions() > 0),
                "Expected collisions at some scale level");
    }
}
