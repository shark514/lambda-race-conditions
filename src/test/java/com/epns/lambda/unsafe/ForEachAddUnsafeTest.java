package com.epns.lambda.unsafe;

import com.epns.lambda.util.CollisionTestRunner;
import com.epns.lambda.util.CollisionTestRunner.ScaleResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * forEach() on a list while another thread calls add().
 * 
 * forEach uses an internal iterator that checks modCount at each step.
 * When add modifies the list during iteration, the modCount changes and
 * the iterator throws ConcurrentModificationException. Sometimes add slips
 * between two checks and the result is a corrupted list.
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
