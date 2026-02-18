package com.epns.lambda.scenarios;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;

public class ReplaceAllUnsafeTest {
    @Test
    public void testReplaceAllUnsafe() throws InterruptedException {
        ArrayList<Integer> list = new ArrayList<>(Collections.nCopies(10, 0));
        ScenarioRunner.runScenario("ReplaceAllUnsafe", list, l -> l.replaceAll(x -> x + 1));
    }
}
