package com.epns.lambda.safe;

import com.epns.lambda.util.CollisionTestRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Collections.synchronizedList wraps each method in a synchronized block.
 * But for compound operations (iteration, replaceAll), you must
 * MANUALLY SYNCHRONIZE on the list. synchronized(shared) ensures
 * only one thread executes replaceAll at a time.
 * 
 * Warning: synchronizedList ALONE is NOT enough for replaceAll/forEach/sort!
 * 
 * When to use: quick drop-in replacement when you control all access points.
 */
@DisplayName("replaceAll on synchronizedList — SAFE")
public class SynchronizedListTest {

    @Test
    void test() {
        var results = CollisionTestRunner.runAllScales("SynchronizedList", totalHits -> {
            List<Integer> shared = Collections.synchronizedList(CollisionTestRunner.freshList(100));
            return CollisionTestRunner.executeWorkload(totalHits, (threadId, iter, exceptions, corruptions) -> {
                try {
                    synchronized (shared) { shared.replaceAll(x -> x + 1); }
                } catch (Exception e) {
                    exceptions.incrementAndGet();
                }
            });
        });
        results.stream().filter(r -> !r.timeout).forEach(r ->
                assertEquals(0, r.totalCollisions(), "Expected 0 collisions at " + r.totalHits + " hits"));
    }
}
