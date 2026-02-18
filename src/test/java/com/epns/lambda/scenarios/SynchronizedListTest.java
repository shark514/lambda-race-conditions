package com.epns.lambda.scenarios;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SynchronizedListTest {
    @Test
    public void testSynchronizedList() throws InterruptedException {
        List<Integer> list = Collections.synchronizedList(new ArrayList<>(Collections.nCopies(10, 0)));
        ScenarioRunner.runScenario("SynchronizedList", list, l -> {
            synchronized (l) {
                l.replaceAll(x -> x + 1);
            }
        });
    }
}
