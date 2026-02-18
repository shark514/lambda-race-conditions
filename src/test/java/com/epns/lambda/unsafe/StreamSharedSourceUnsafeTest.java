package com.epns.lambda.unsafe;

import com.epns.lambda.util.CollisionTestRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * stream().map().collect() on a source modified concurrently.
 * 
 * Java streams are LAZY — they don't copy the source. stream() creates
 * a pipeline that reads directly from the underlying ArrayList. If another
 * thread modifies this list while the stream traverses it, you get
 * ConcurrentModificationException, ArrayIndexOutOfBoundsException, nulls
 * in the result, or a result of inconsistent size.
 */
@DisplayName("stream on shared source — UNSAFE")
public class StreamSharedSourceUnsafeTest {

    @Test
    void test() {
        var results = CollisionTestRunner.runAllScales("StreamSharedSourceUnsafe", totalHits -> {
            List<Integer> shared = CollisionTestRunner.freshList(100);
            return CollisionTestRunner.executeWorkload(totalHits, (threadId, iter, exceptions, corruptions) -> {
                try {
                    if (threadId % 2 == 0) {
                        List<Integer> result = shared.stream().map(x -> x * 2).collect(Collectors.toList());
                        if (result.size() != shared.size() || result.contains(null)) corruptions.incrementAndGet();
                    } else {
                        shared.add(ThreadLocalRandom.current().nextInt(100));
                        if (shared.size() > 200) shared.subList(100, shared.size()).clear();
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
