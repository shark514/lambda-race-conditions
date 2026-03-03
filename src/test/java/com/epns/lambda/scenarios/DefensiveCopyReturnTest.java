package com.epns.lambda.scenarios;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DefensiveCopy WITH RETURN — the "correct" defensive copy pattern.
 *
 * Same setup as all other scenarios: 50 threads, shared ArrayList,
 * each thread tries to increment every element.
 *
 * The difference: each thread copies the list, increments the copy,
 * and returns it — never writes back to the shared list.
 *
 * Result: 0% collision. Complete isolation. The shared list is never
 * mutated, so there's nothing to collide on.
 */
public class DefensiveCopyReturnTest {

    @Test
    public void testDefensiveCopyReturn() throws InterruptedException {
        ArrayList<Integer> list = new ArrayList<>(Collections.nCopies(10, 0));

        ScenarioRunner.runScenario("DefensiveCopyReturn", list, (shared) -> {
            // Copy the shared list
            ArrayList<Integer> copy = new ArrayList<>(shared);
            // Increment the copy
            copy.replaceAll(x -> x + 1);
            // Return — never write back to shared
            // (copy is discarded, shared list untouched)
        });
    }
}
