package com.epns.lambda.safe;

import com.epns.lambda.util.CollisionTestRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Manual synchronized block with a dedicated lock object.
 * The lock guarantees mutual exclusion: only one thread at a time.
 * 
 * Pros: total control over granularity, can protect multiple operations atomically.
 * Cons: verbose, if you forget a synchronized somewhere → unsafe.
 * 
 * When to use: when you want fine-grained control and are disciplined.
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
