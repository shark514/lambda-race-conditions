package com.epns.lambda.unsafe;

import com.epns.lambda.util.CollisionTestRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * stream().map().collect() sur une source modifiée concurremment.
 * 
 * Les streams Java sont LAZY — ils ne copient pas la source. stream() crée
 * un pipeline qui lit directement depuis l'ArrayList sous-jacente. Si un autre
 * thread modifie cette liste pendant que le stream la parcourt, on obtient des
 * ConcurrentModificationException, ArrayIndexOutOfBoundsException, des null
 * dans le résultat, ou un résultat de taille incohérente.
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
