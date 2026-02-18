package com.epns.lambda.safe;

import com.epns.lambda.util.CollisionTestRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CopyOnWriteArrayList creates a COPY of the internal array on each modification.
 * Each replaceAll works on its own copy. Reads are never blocked and never
 * see an intermediate state.
 * 
 * Advantages: no manual synchronization, very fast reads.
 * Disadvantages: each write copies the entire array → O(n) per modification.
 * 
 * When to use: frequent reads, rare writes (listeners, config).
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
