package com.epns.lambda.scenarios;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;

public class FinalReplaceAllTest {
    @Test
    public void testFinalReplaceAll() throws InterruptedException {
        final ArrayList<Integer> list = new ArrayList<>(Collections.nCopies(10, 0));
        ScenarioRunner.runScenario("FinalReplaceAll", list, l -> l.replaceAll(x -> x + 1));
    }
}
