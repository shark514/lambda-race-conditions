package com.epns.lambda.scenarios;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BaselineForLoopTest {
    @Test
    public void testBaselineForLoop() throws InterruptedException {
        ArrayList<Integer> list = new ArrayList<>(Collections.nCopies(10, 0));
        ScenarioRunner.runScenario("BaselineForLoop", list, l -> {
            for (int i = 0; i < l.size(); i++) {
                l.set(i, l.get(i) + 1);
            }
        });
    }
}
