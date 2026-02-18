package com.epns.lambda.unsafe;

import com.epns.lambda.util.CollisionTestRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * final ArrayList — ALWAYS unsafe.
 * 
 * final prevents REASSIGNING the variable (shared = otherList won't compile).
 * But final provides absolutely no protection for the object's CONTENT.
 * shared.replaceAll(), shared.add(), shared.clear() work exactly the same.
 * It's like putting a padlock on the bulletin board but leaving the
 * papers pinned to it accessible to everyone.
 * 
 * Classic interview trap: final ≠ immutable ≠ thread-safe.
 */
@DisplayName("final ArrayList is STILL unsafe")
public class FinalStillUnsafeTest {

    @Test
    void test() {
        var results = CollisionTestRunner.runAllScales("FinalStillUnsafe", totalHits -> {
            final ArrayList<Integer> shared = CollisionTestRunner.freshList(100);
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
