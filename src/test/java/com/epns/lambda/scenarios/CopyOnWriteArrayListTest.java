package com.epns.lambda.scenarios;

import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;

public class CopyOnWriteArrayListTest {
    @Test
    public void testCopyOnWriteArrayList() throws InterruptedException {
        CopyOnWriteArrayList<Integer> list = new CopyOnWriteArrayList<>(Collections.nCopies(10, 0));
        ScenarioRunner.runScenario("CopyOnWriteArrayList", list, l -> l.replaceAll(x -> x + 1));
    }
}
