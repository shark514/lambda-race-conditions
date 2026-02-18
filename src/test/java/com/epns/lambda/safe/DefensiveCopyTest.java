package com.epns.lambda.safe;

import com.epns.lambda.util.CollisionTestRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Defensive copy before stream: we take a synchronized lock on the list,
 * make a new ArrayList<>(shared), then stream the copy freely.
 * 
 * The copy creates an isolated snapshot. No matter what other threads do
 * afterwards — our copy doesn't change. The stream works on frozen data.
 * 
 * When to use: when you need to stream/iterate at length without blocking writes.
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
