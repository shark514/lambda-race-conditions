package com.epns.lambda.scenarios;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;

/**
 * DefensiveCopy WITH RETURN — the "correct" defensive copy pattern.
 *
 * Each thread copies the list, increments the copy, and RETURNS it
 * without writing back to the original. This is what defensive copy
 * is actually supposed to do.
 *
 * Result: the original list is NEVER modified → 100% loss rate because
 * no thread ever successfully mutates the shared list. Zero exceptions,
 * but every single hit is "lost" from the shared state's perspective.
 *
 * This proves that true defensive copy is incompatible with shared
 * mutable state. You either:
 * 1. Write back → race condition (DefensiveCopyTest: 64% loss)
 * 2. Don't write back → 100% loss (this test)
 * 3. Synchronize → works but you don't need the copy (SynchronizedListTest)
 */
public class DefensiveCopyReturnTest {

    @Test
    public void testDefensiveCopyReturn() throws InterruptedException {
        ArrayList<Integer> list = new ArrayList<>(Collections.nCopies(10, 0));

        ScenarioRunner.runScenario("DefensiveCopyReturn", list, (shared) -> {
            // Copy, transform, return — never write back
            ArrayList<Integer> copy = new ArrayList<>(shared);
            copy.replaceAll(x -> x + 1);
            // copy is discarded — shared list untouched
        });
    }
}
